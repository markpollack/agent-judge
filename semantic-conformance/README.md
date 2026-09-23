# Semantic evaluation fixtures

Shared test resources for evidence provenance and native provider response handling.
These are test assets, not a library or a source directory to compile. Modules can add
`semantic-conformance/src/test/resources` as an unfiltered Maven test resource.

`manifest.json` pins every data artifact by SHA-256 and byte length. The manifest does
not hash itself. PetClinic files were copied with `git show <commit>:<sourceGitPath>`
from the recorded Tutorial commit, without formatting or source edits. Input excerpts
use inclusive, one-based line selectors, retaining their original LF terminators and
decoding UTF-8. Tests reproduce each excerpt from the complete retained source files.
The serialized input files themselves are hashed as exact bytes; parsing and reserializing
JSON is not a substitute for hashing the retained artifact.

Within the original `semantic-conformance` resource namespace, only
`petclinic/inputs/rule-4.json` and `petclinic/inputs/uc6-ac8.json` are inference states. Each contains requirement and source excerpts only. Expected labels and source
review rationale live separately in `petclinic/expectations.json`; the existing mutation
patch is under `petclinic/mutation/`. Do not send the whole fixture directory as state.
No historical evaluator responses, private sessions, or model reasoning are included.

RULE-4 includes the complete `createStaffOffer` method, the locking helper and ID-sorting
method, and the complete `processOverdueFallback` supporting caller. Its requirement pins
Owner → Pet → Vet → SchedulingRequest → Appointment → Reservation with ascending IDs
within a type. The source review identifies a lock-order counterexample; it does not
claim a newly executed deadlock. AC8 includes the complete owner cancellation method.
Its positive label concerns the BOOKED equality boundary only. The preserved patch has
historical introductory prose (including its original test-count wording); that prose is
neither an inference input nor new execution evidence. Neither baseline nor mutant was
executed to produce these assets, and no semantic mutation kill is claimed.

`jev/requests` and `jev/responses` contain **constructed, offline wire examples** based
on the pinned primary SDK schema cited in `jev/cases.json`. They are not historical or
live provider results. The three native valid responses preserve probability and confidence
as distinct native fields; Noul has no confidence field. Score's ordered toy rubric is
for transport fixtures, not a reviewed real-case evaluation rubric or calibration claim.
Malformed bodies each change one JSON location from their named valid base; the catalog
records that transformation and intended defect. Tests verify fixture identity and these
transformations, not an adapter implementation or future result representation.

`digests/vectors.json` records exact hexadecimal bytes, lengths and SHA-256 values.
Unicode normalization, line-ending conversion, JSON key order and whitespace are all
observable: no canonicalization is implied. `.gitattributes` disables text conversion
for these resources. These vectors are retained inputs for independent implementations.

The additive sibling `evidence-compilation/v1` namespace supplies versioned recipes,
source sufficiency reviews, and bounded controls. It reuses the original positive input
without changing any original resource bytes. Its own manifest enumerates its eligible
input bundles; see [evidence compilation](EVIDENCE-COMPILATION.md).

See [NOTICE](NOTICE) for attribution and the preserved upstream license.
