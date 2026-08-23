#!/usr/bin/env python3
"""Deterministically re-root the bounded Plan B1 consumer CycloneDX document."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path


CONSUMER_PURL = (
    "pkg:maven/io.github.markpollack.diligence.consumer/"
    "agent-judge-langchain4j-consumer@1.0.0?type=jar"
)
TARGET_PURL = (
    "pkg:maven/io.github.markpollack/"
    "agent-judge-langchain4j@0.16.0-phase0?type=jar"
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_sha256(value: object) -> str:
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def one(items: list[dict], description: str) -> dict:
    if len(items) != 1:
        raise ValueError(f"expected exactly one {description}, found {len(items)}")
    return items[0]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("record", type=Path)
    args = parser.parse_args()

    if args.input.resolve() == args.output.resolve():
        raise ValueError("input and output must be distinct")
    if args.output.exists() or args.record.exists():
        raise ValueError("output and transformation record must not already exist")

    original = json.loads(args.input.read_text(encoding="utf-8"))
    transformed = copy.deepcopy(original)
    metadata = transformed.get("metadata")
    if not isinstance(metadata, dict) or not isinstance(metadata.get("component"), dict):
        raise ValueError("input has no metadata.component")
    consumer_component = metadata["component"]
    if consumer_component.get("purl") != CONSUMER_PURL:
        raise ValueError(f"unexpected synthetic consumer root: {consumer_component.get('purl')}")
    consumer_ref = consumer_component.get("bom-ref")
    if not isinstance(consumer_ref, str) or not consumer_ref:
        raise ValueError("synthetic consumer root has no bom-ref")

    components = transformed.get("components")
    dependencies = transformed.get("dependencies")
    if not isinstance(components, list) or not all(isinstance(item, dict) for item in components):
        raise ValueError("input components is malformed")
    if not isinstance(dependencies, list) or not all(isinstance(item, dict) for item in dependencies):
        raise ValueError("input dependencies is malformed")

    target_component = one(
        [component for component in components if component.get("purl") == TARGET_PURL],
        "target component",
    )
    target_ref = target_component.get("bom-ref")
    if not isinstance(target_ref, str) or not target_ref:
        raise ValueError("target component has no bom-ref")
    if any(component.get("bom-ref") == consumer_ref for component in components):
        raise ValueError("synthetic consumer unexpectedly appears in components")

    consumer_node = one(
        [entry for entry in dependencies if entry.get("ref") == consumer_ref],
        "synthetic consumer dependency node",
    )
    if consumer_node.get("dependsOn") != [target_ref]:
        raise ValueError(
            "synthetic consumer does not have exactly one direct edge to the staged target"
        )
    target_node = one(
        [entry for entry in dependencies if entry.get("ref") == target_ref],
        "target dependency node",
    )
    target_node_before = copy.deepcopy(target_node)

    metadata["component"] = copy.deepcopy(target_component)
    transformed["components"] = [
        component
        for component in components
        if component.get("bom-ref") not in {consumer_ref, target_ref}
    ]
    transformed["dependencies"] = [
        entry for entry in dependencies if entry.get("ref") != consumer_ref
    ]

    rooted_component = transformed["metadata"]["component"]
    remaining_components = transformed["components"]
    remaining_dependencies = transformed["dependencies"]
    identity_refs = [rooted_component.get("bom-ref")] + [
        component.get("bom-ref") for component in remaining_components
    ]
    if consumer_ref in identity_refs:
        raise ValueError("synthetic consumer bom-ref remains in component identities")
    if identity_refs.count(target_ref) != 1:
        raise ValueError("target bom-ref does not appear exactly once as component identity")
    if any(
        entry.get("ref") == consumer_ref or consumer_ref in entry.get("dependsOn", [])
        for entry in remaining_dependencies
    ):
        raise ValueError("synthetic consumer bom-ref remains in dependency graph")

    target_node_after = one(
        [entry for entry in remaining_dependencies if entry.get("ref") == target_ref],
        "re-rooted target dependency node",
    )
    if target_node_after != target_node_before:
        raise ValueError("target dependency node or dependsOn edges changed during re-rooting")
    if any(entry.get("ref") in entry.get("dependsOn", []) for entry in remaining_dependencies):
        raise ValueError("re-rooted dependency graph contains a self-edge")

    known_refs = set(identity_refs)
    unresolved: set[str] = set()
    for entry in remaining_dependencies:
        if entry.get("ref") not in known_refs:
            unresolved.add(str(entry.get("ref")))
        unresolved.update(
            target for target in entry.get("dependsOn", []) if target not in known_refs
        )
    if unresolved:
        raise ValueError(f"re-rooted dependency graph has unresolved refs: {sorted(unresolved)}")

    args.output.write_text(json.dumps(transformed, indent=2) + "\n", encoding="utf-8")
    record = {
        "schemaVersion": 1,
        "input": str(args.input.resolve()),
        "inputSha256": sha256(args.input),
        "output": str(args.output.resolve()),
        "syntheticConsumer": {"purl": CONSUMER_PURL, "bomRef": consumer_ref},
        "target": {"purl": TARGET_PURL, "bomRef": target_ref},
        "operations": [
            "promoted the existing target component to metadata.component",
            "removed the target component duplicate from components",
            "removed the synthetic consumer root and dependency node",
            "preserved the target dependency node and dependsOn edges byte-for-byte canonically",
        ],
        "targetDependencyNodeBeforeSha256": canonical_sha256(target_node_before),
        "targetDependencyNodeAfterSha256": canonical_sha256(target_node_after),
        "assertions": {
            "syntheticConsumerAbsent": True,
            "targetIdentityCount": identity_refs.count(target_ref),
            "targetDependencyNodeUnchanged": target_node_after == target_node_before,
            "selfEdgeCount": 0,
            "unresolvedReferenceCount": 0,
        },
        "outputSha256": sha256(args.output),
    }
    args.record.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
