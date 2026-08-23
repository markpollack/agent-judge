#!/usr/bin/env python3
"""Resolve and enforce every published Agent Judge runtime consumer shape.

The gate installs release-profile artifacts into an isolated Maven repository, discovers
publishable JAR modules from the reactor, and resolves one fresh no-parent/no-BOM consumer per
module. It always writes the complete matrix before returning dependency-policy failures.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


GROUP_ID = "io.github.markpollack"
DEPENDENCY_PLUGIN = "3.8.1"
JACKSON2_FLOOR = "2.21.6"
JACKSON2_ANNOTATIONS_LINE = "2.21"
JACKSON3_FLOOR = "3.1.6"
NETWORKNT_FLOOR = "3.0.7"
EXPECTED_CLASS_MAJOR = 65


class GateError(RuntimeError):
    """A gate prerequisite could not be established."""


@dataclass(frozen=True)
class Module:
    artifact_id: str
    pom: Path
    packaging: str
    skip_publishing: bool


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_text(root: ET.Element, name: str) -> str | None:
    for child in root:
        if local_name(child.tag) == name and child.text:
            return child.text.strip()
    return None


def direct_children(root: ET.Element, name: str) -> list[ET.Element]:
    return [child for child in root if local_name(child.tag) == name]


def discover_reactor(root_pom: Path) -> tuple[list[Module], list[Module]]:
    """Walk source-declared modules; Central skipPublishing is the publication boundary."""

    seen: set[Path] = set()
    all_modules: list[Module] = []

    def walk(pom: Path) -> None:
        pom = pom.resolve()
        if pom in seen:
            return
        seen.add(pom)
        model = ET.parse(pom).getroot()
        artifact_id = child_text(model, "artifactId")
        if not artifact_id:
            raise GateError(f"artifactId missing from {pom}")
        packaging = child_text(model, "packaging") or "jar"
        skip = any(
            local_name(element.tag) == "skipPublishing"
            and (element.text or "").strip().lower() == "true"
            for element in model.iter()
        )
        all_modules.append(Module(artifact_id, pom, packaging, skip))
        for modules in direct_children(model, "modules"):
            for module in modules:
                if local_name(module.tag) != "module" or not module.text:
                    continue
                child = (pom.parent / module.text.strip() / "pom.xml").resolve()
                if not child.is_file():
                    raise GateError(f"declared reactor module is missing: {child}")
                walk(child)

    walk(root_pom)
    published_runtime = sorted(
        (module for module in all_modules if module.packaging == "jar" and not module.skip_publishing),
        key=lambda module: module.artifact_id,
    )
    return all_modules, published_runtime


def run(command: list[str], cwd: Path, log: Path, capture: bool = False) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(completed.stdout or "", encoding="utf-8")
    if completed.returncode:
        raise subprocess.CalledProcessError(completed.returncode, command, completed.stdout)
    return completed.stdout or "" if capture else ""


def maven(wrapper: Path, local_repo: Path, settings: Path, *arguments: str) -> list[str]:
    return [
        str(wrapper),
        "-B",
        "-s",
        str(settings),
        f"-Dmaven.repo.local={local_repo}",
        *arguments,
    ]


def derive_version(wrapper: Path, repository: Path, local_repo: Path, settings: Path, log: Path) -> str:
    output = run(
        maven(wrapper, local_repo, settings, "-N", "-q", "help:evaluate", "-Dexpression=project.version", "-DforceStdout"),
        repository,
        log,
        capture=True,
    )
    candidates = [
        line.strip()
        for line in re.sub(r"\x1b\[[0-9;]*m", "", output).splitlines()
        if re.fullmatch(r"[0-9][A-Za-z0-9._-]*", line.strip())
    ]
    if len(candidates) != 1:
        raise GateError(f"could not derive one resolved project version: {candidates!r}")
    return candidates[0]


def consumer_pom(artifact_id: str, version: str, staged_repository: Path | None = None) -> str:
    repositories = ""
    if staged_repository is not None:
        repositories = f"""  <repositories>
    <repository>
      <id>staged-release</id>
      <url>{staged_repository.as_uri()}</url>
    </repository>
  </repositories>
"""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.markpollack.diligence.consumer</groupId>
  <artifactId>{artifact_id}-consumer</artifactId>
  <version>1.0.0</version>
{repositories}  <dependencies>
    <dependency>
      <groupId>{GROUP_ID}</groupId>
      <artifactId>{artifact_id}</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
</project>
"""


def walk_tree(node: dict) -> Iterable[dict]:
    yield node
    for child in node.get("children", []):
        yield from walk_tree(child)


def numeric_version(value: str) -> tuple[int, ...]:
    if not re.fullmatch(r"\d+(?:\.\d+)*", value):
        raise ValueError(value)
    return tuple(int(part) for part in value.split("."))


def version_at_least(actual: str, floor: str) -> bool:
    left, right = numeric_version(actual), numeric_version(floor)
    width = max(len(left), len(right))
    return left + (0,) * (width - len(left)) >= right + (0,) * (width - len(right))


def class_majors(jar: Path) -> list[int]:
    majors: set[int] = set()
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.endswith(".class"):
                continue
            header = archive.read(name)[:8]
            if len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe":
                majors.add(struct.unpack(">H", header[6:8])[0])
    return sorted(majors)


def selected_dependencies(tree: dict) -> list[dict]:
    selected: dict[tuple[str, str, str, str], dict] = {}
    for node in walk_tree(tree):
        group = node.get("groupId")
        artifact = node.get("artifactId")
        version = node.get("version")
        if not group or not artifact or not version or group == "io.github.markpollack.diligence.consumer":
            continue
        dependency_type = node.get("type") or "jar"
        classifier = node.get("classifier") or ""
        selected[(group, artifact, dependency_type, classifier)] = {
            "groupId": group,
            "artifactId": artifact,
            "type": dependency_type,
            "classifier": classifier,
            "version": version,
            "scope": node.get("scope") or "compile",
            "optional": node.get("optional") in (True, "true"),
        }
    return sorted(
        selected.values(),
        key=lambda item: (item["groupId"], item["artifactId"], item["type"], item["classifier"]),
    )


def coordinate(dependency: dict) -> str:
    return ":".join(
        (
            dependency["groupId"],
            dependency["artifactId"],
            dependency.get("type") or "jar",
            dependency.get("classifier") or "",
            dependency["version"],
        )
    )


def purl_coordinate(purl: str) -> str:
    match = re.fullmatch(r"pkg:maven/([^/]+)/([^@]+)@([^?]+)(?:\?(.*))?", purl)
    if not match:
        raise GateError(f"unsupported Maven purl: {purl}")
    from urllib.parse import parse_qs, unquote

    qualifiers = parse_qs(match.group(4) or "")
    return ":".join(
        (
            unquote(match.group(1)),
            unquote(match.group(2)),
            qualifiers.get("type", ["jar"])[0],
            qualifiers.get("classifier", [""])[0],
            unquote(match.group(3)),
        )
    )


def required_sbom_coordinates(sbom_path: Path) -> list[str]:
    sbom = json.loads(sbom_path.read_text(encoding="utf-8"))
    components = [sbom["metadata"]["component"]]
    components.extend(
        component for component in sbom.get("components", []) if component.get("scope", "required") == "required"
    )
    coordinates = []
    for component in components:
        purl = component.get("purl")
        if not purl:
            raise GateError(f"component has no purl in {sbom_path}: {component.get('bom-ref', component)}")
        coordinates.append(purl_coordinate(purl))
    return sorted(set(coordinates))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def extract_bundle(bundle: Path, destination: Path) -> None:
    with zipfile.ZipFile(bundle) as archive:
        root = destination.resolve()
        for member in archive.infolist():
            target = (destination / member.filename).resolve()
            if target != root and root not in target.parents:
                raise GateError(f"staged bundle has an unsafe path: {member.filename}")
        archive.extractall(destination)


def family(dependencies: list[dict], predicate) -> list[dict]:
    return [dependency for dependency in dependencies if predicate(dependency)]


def assess(
    artifact_id: str,
    version: str,
    flattened_pom: Path,
    dependencies: list[dict],
    internal_artifacts: set[str],
) -> list[str]:
    failures: list[str] = []
    text = flattened_pom.read_text(encoding="utf-8")
    model = ET.fromstring(text)
    tags = {local_name(element.tag) for element in model.iter()}
    if "parent" in tags:
        failures.append("flattened published POM retains a parent")
    if "repositories" in tags or "pluginRepositories" in tags:
        failures.append("flattened published POM exports repository declarations")
    if "${" in text:
        failures.append("flattened published POM contains an unresolved property")

    for dependency in dependencies:
        group = dependency["groupId"]
        name = dependency["artifactId"]
        actual = dependency["version"]
        coordinate = f"{group}:{name}:{actual}"
        if actual.endswith("-SNAPSHOT"):
            own_current = group == GROUP_ID and name in internal_artifacts and actual == version
            if not own_current:
                failures.append(f"external or drifted SNAPSHOT dependency: {coordinate}")
        if group == GROUP_ID and name in internal_artifacts and actual != version:
            failures.append(f"internal version drift: {coordinate}; expected {version}")
        try:
            if group.startswith("com.fasterxml.jackson"):
                if name == "jackson-annotations":
                    if not (actual == JACKSON2_ANNOTATIONS_LINE or actual.startswith(JACKSON2_ANNOTATIONS_LINE + ".")):
                        failures.append(f"Jackson 2 annotations line violation: {coordinate}")
                elif not version_at_least(actual, JACKSON2_FLOOR):
                    failures.append(f"Jackson 2 floor violation: {coordinate} < {JACKSON2_FLOOR}")
            elif group.startswith("tools.jackson") and not version_at_least(actual, JACKSON3_FLOOR):
                failures.append(f"Jackson 3 floor violation: {coordinate} < {JACKSON3_FLOOR}")
            elif (
                group == "com.networknt"
                and name == "json-schema-validator"
                and not version_at_least(actual, NETWORKNT_FLOOR)
            ):
                failures.append(f"NetworkNT floor violation: {coordinate} < {NETWORKNT_FLOOR}")
        except ValueError:
            failures.append(f"cannot compare required dependency version: {coordinate}")
    if not any(item["groupId"] == GROUP_ID and item["artifactId"] == artifact_id for item in dependencies):
        failures.append("primary published module is absent from its consumer closure")
    return failures


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, help="Matrix/closure directory (default: target/published-consumer-gate)")
    parser.add_argument("--skip-install", action="store_true", help="Use already installed release-profile artifacts")
    parser.add_argument("--staged-bundle", type=Path, help="Resolve and compare artifacts from this Central bundle")
    parser.add_argument("--project-version", help="Exact staged version (required with --staged-bundle)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if bool(args.staged_bundle) != bool(args.project_version):
        raise GateError("--staged-bundle and --project-version must be supplied together")
    if args.staged_bundle and args.skip_install:
        raise GateError("--skip-install cannot be combined with --staged-bundle")
    repository = Path(__file__).resolve().parent.parent
    wrapper = repository / "mvnw"
    output = (args.output_dir or repository / "target" / "published-consumer-gate").resolve()
    output.mkdir(parents=True, exist_ok=True)
    consumers_output = output / "consumers"
    matrix_output = output / "matrix.json"
    if consumers_output.exists():
        shutil.rmtree(consumers_output)
    if matrix_output.exists():
        matrix_output.unlink()
    with tempfile.TemporaryDirectory(prefix="agent-judge-consumer-gate-") as scratch_text:
        scratch = Path(scratch_text)
        local_repo = (
            scratch / "consumer-m2"
            if args.staged_bundle
            else Path(os.environ.get("MAVEN_REPO_LOCAL", scratch / "m2"))
        ).resolve()
        local_repo.mkdir(parents=True, exist_ok=True)
        staged_repository = None
        bundle_sha256 = None
        if args.staged_bundle:
            bundle = args.staged_bundle.resolve()
            if not bundle.is_file():
                raise GateError(f"staged bundle is missing: {bundle}")
            staged_repository = scratch / "staged-repository"
            staged_repository.mkdir()
            extract_bundle(bundle, staged_repository)
            bundle_sha256 = sha256(bundle)
        settings = scratch / "settings.xml"
        settings.write_text(
            """<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors><mirror><id>central-only</id><url>https://repo.maven.apache.org/maven2</url><mirrorOf>central</mirrorOf></mirror></mirrors>
</settings>
""",
            encoding="utf-8",
        )
        version = args.project_version or derive_version(
            wrapper, repository, local_repo, settings, scratch / "derive-version.log"
        )
        all_modules, modules = discover_reactor(repository / "pom.xml")
        internal_artifacts = {module.artifact_id for module in modules}

        if not args.skip_install and not args.staged_bundle:
            install_log = scratch / "release-profile-install.log"
            try:
                run(
                    maven(wrapper, local_repo, settings, "-Prelease", "-Dgpg.skip=true", "-DskipTests", "clean", "install"),
                    repository,
                    install_log,
                )
            except subprocess.CalledProcessError as error:
                sys.stderr.write(error.stdout or "")
                raise GateError(f"release-profile install failed; log: {install_log}") from error

        rows: list[dict] = []
        all_failures: list[str] = []
        group_path = GROUP_ID.replace(".", "/")

        for module in modules:
            consumer = output / "consumers" / module.artifact_id
            closure = consumer / "closure"
            closure.mkdir(parents=True)
            pom = consumer / "pom.xml"
            pom.write_text(consumer_pom(module.artifact_id, version, staged_repository), encoding="utf-8")
            tree_path = consumer / "runtime-tree.json"
            failures: list[str] = []
            try:
                run(
                    maven(
                        wrapper,
                        local_repo,
                        settings,
                        "-q",
                        "-f",
                        str(pom),
                        f"org.apache.maven.plugins:maven-dependency-plugin:{DEPENDENCY_PLUGIN}:tree",
                        "-Dscope=runtime",
                        "-DoutputType=json",
                        f"-DoutputFile={tree_path}",
                    ),
                    repository,
                    consumer / "tree.log",
                )
                run(
                    maven(
                        wrapper,
                        local_repo,
                        settings,
                        "-q",
                        "-f",
                        str(pom),
                        f"org.apache.maven.plugins:maven-dependency-plugin:{DEPENDENCY_PLUGIN}:copy-dependencies",
                        "-DincludeScope=runtime",
                        f"-DoutputDirectory={closure}",
                    ),
                    repository,
                    consumer / "copy.log",
                )
                tree = json.loads(tree_path.read_text(encoding="utf-8"))
                dependencies = selected_dependencies(tree)
                artifact_repository = staged_repository or local_repo
                artifact_directory = artifact_repository / group_path / module.artifact_id / version
                flattened = artifact_directory / f"{module.artifact_id}-{version}.pom"
                if not flattened.is_file():
                    failures.append(f"installed flattened POM missing: {flattened}")
                else:
                    failures.extend(assess(module.artifact_id, version, flattened, dependencies, internal_artifacts))
                consumer_coordinates = sorted(coordinate(dependency) for dependency in dependencies)
                sbom_path = artifact_directory / f"{module.artifact_id}-{version}-cyclonedx.json"
                if not sbom_path.is_file():
                    sbom_coordinates = []
                    sbom_only = []
                    consumer_only = consumer_coordinates
                    failures.append(f"staged CycloneDX artifact missing: {sbom_path}")
                else:
                    sbom_coordinates = required_sbom_coordinates(sbom_path)
                    sbom_only = sorted(set(sbom_coordinates) - set(consumer_coordinates))
                    consumer_only = sorted(set(consumer_coordinates) - set(sbom_coordinates))
                    if sbom_only or consumer_only:
                        failures.append(
                            f"SBOM/consumer coordinate mismatch: {len(sbom_only)} SBOM-only, "
                            f"{len(consumer_only)} consumer-only"
                        )
                if staged_repository is not None:
                    resolved_pom = local_repo / group_path / module.artifact_id / version / flattened.name
                    if not resolved_pom.is_file():
                        failures.append(f"consumer did not cache the staged POM: {resolved_pom}")
                    elif sha256(resolved_pom) != sha256(flattened):
                        failures.append("consumer POM differs byte-for-byte from the staged POM")
                jars = sorted(path.name for path in closure.glob("*.jar"))
                primary = closure / f"{module.artifact_id}-{version}.jar"
                majors = class_majors(primary) if primary.is_file() else []
                if majors and majors != [EXPECTED_CLASS_MAJOR]:
                    failures.append(f"published classes have majors {majors}; expected [{EXPECTED_CLASS_MAJOR}] (Java 21)")
            except (subprocess.CalledProcessError, json.JSONDecodeError, zipfile.BadZipFile) as error:
                dependencies, jars, majors = [], [], []
                consumer_coordinates, sbom_coordinates, sbom_only, consumer_only = [], [], [], []
                failures.append(f"consumer resolution/inspection failed: {error}")

            row = {
                "module": module.artifact_id,
                "coordinate": f"{GROUP_ID}:{module.artifact_id}:{version}",
                "sourcePom": str(module.pom.relative_to(repository)),
                "runtimeJarCount": len(jars),
                "runtimeJarFiles": jars,
                "runtimeArtifacts": dependencies,
                "consumerCoordinates": consumer_coordinates,
                "sbomRequiredCoordinates": sbom_coordinates,
                "sbomOnly": sbom_only,
                "consumerOnly": consumer_only,
                "coordinateEquality": not sbom_only and not consumer_only,
                "jackson2": family(dependencies, lambda item: item["groupId"].startswith("com.fasterxml.jackson")),
                "jackson3": family(dependencies, lambda item: item["groupId"].startswith("tools.jackson")),
                "networknt": family(
                    dependencies,
                    lambda item: item["groupId"] == "com.networknt" and item["artifactId"] == "json-schema-validator",
                ),
                "springAi": family(dependencies, lambda item: item["groupId"] == "org.springframework.ai"),
                "springBoot": family(dependencies, lambda item: item["groupId"] == "org.springframework.boot"),
                "internalAgentJudge": family(
                    dependencies,
                    lambda item: item["groupId"] == GROUP_ID and item["artifactId"] in internal_artifacts,
                ),
                "classFileMajors": majors,
                "failures": failures,
                "result": "pass" if not failures else "fail",
            }
            rows.append(row)
            all_failures.extend(f"{module.artifact_id}: {failure}" for failure in failures)
            print(f"{row['result'].upper():4} {module.artifact_id}: {len(jars)} runtime JARs", flush=True)

        matrix = {
            "schemaVersion": 1,
            "projectVersion": version,
            "repository": str(repository),
            "mavenRepository": str(local_repo),
            "settingsSha256": sha256(settings),
            "stagedBundle": str(args.staged_bundle.resolve()) if args.staged_bundle else None,
            "stagedBundleSha256": bundle_sha256,
            "discovery": {
                "reactorProjectCount": len(all_modules),
                "publishedRuntimeModuleCount": len(modules),
                "publishedRuntimeModules": [module.artifact_id for module in modules],
                "skippedPublishing": [module.artifact_id for module in all_modules if module.skip_publishing],
            },
            "acceptedFloors": {
                "jackson2": JACKSON2_FLOOR,
                "jackson2AnnotationsLine": JACKSON2_ANNOTATIONS_LINE,
                "jackson3": JACKSON3_FLOOR,
                "networkntJsonSchemaValidator": NETWORKNT_FLOOR,
            },
            "consumers": rows,
            "failureCount": len(all_failures),
            "failures": all_failures,
            "result": "pass" if not all_failures else "fail",
        }
        matrix_output.write_text(json.dumps(matrix, indent=2) + "\n", encoding="utf-8")
        print(f"matrix: {matrix_output}")
        if all_failures:
            print(f"published-consumer gate failed with {len(all_failures)} finding(s)", file=sys.stderr)
            for failure in all_failures:
                print(f"  {failure}", file=sys.stderr)
            return 1
        print(f"published-consumer gate passed for {len(rows)} module(s)")
        return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (GateError, OSError, ET.ParseError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)
