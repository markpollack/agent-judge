# Bounded PetClinic evidence recipes

`src/test/resources/evidence-compilation/v1` adds requirement-specific evidence recipes
and controls. All original `semantic-conformance` resources remain unchanged, including
the original manifest and the positive UC6-AC8 input.

The manifest hashes source references, selected requirement/diff bytes, recipes, external
source sufficiency reviews, and exact serialized input bundles. SHA-256 always describes
retained bytes, with no JSON or newline normalization. Inclusive source line selectors
and half-open byte selectors reconstruct content; each selector has its own digest/length.
No adapter, retrieval mechanism, generic evidence compiler or source-analysis API is implied.

Only the manifest's `inputs` are provider states. All recipes, reviews, expected labels,
variant categories, source archives and manifests remain external. Inputs contain selected
requirement/source/diff content and, for RULE-4, a declared analysis scope and the verbatim
required-order sentence. No old responses or model reasoning are inputs. Each bundle has a
reviewed 24 KiB UTF-8 ceiling; crossing it is an error, never permission to truncate or retrieve.

| Recipe | Subject | Purpose |
|---|---|---|
| rule-4-path-v2 | Pinned original source | Scoped source counterexample with controller entry, transactional service, claim check and actual locking helper |
| rule-4-source-only-v1 | Same original source | Source-only control; historical PR diff is unavailable |
| rule-4-no-helper-v1 | Same original source | Removes actual locking-helper semantics while keeping callers |
| uc6-ac8-change-v1 | Baseline plus retained patch | Complete post-change cancellation method |
| uc6-ac8-diff-only-v1 | Same post-change subject | Only the authentic unified diff, excluding answer-revealing patch prose |
| uc6-ac8-no-time-guard-v1 | Same post-change subject | Omits time-guard evidence and diff, preserving explicit source line gaps |
| uc6-ac8-baseline-v1 | Pinned baseline | Reuses the exact original positive input and the same sufficient-source extraction recipe/version as the mutant |

The baseline/mutant sufficient pair uses the same requirement and inclusive cancellation
method bounds. Only source bytes and subject/derivation provenance differ. The mutant source
is mechanically derived by applying the one retained patch hunk with exact context; no
PetClinic program is built or executed. This is a source transformation, not an observed
runtime fact or a computed lock graph. Derived semantic facts are explicitly absent.

RULE-4 sufficiency is scoped to a valid claimed request entering the direct controller path
without pre-held locks, under ordinary Spring/JPA annotation semantics. The controller and
service declarations and the claim helper are visible. This permits a source-level order
counterexample; it does not prove an actual deadlock, all deployment configuration, or all
conjuncts of RULE-4. AC8 concerns source behavior at the equality boundary with normally
completing collaborators. Missing-guard snippets retain their original noncontiguous line
numbers; missing evidence must not be interpreted as proof the source lacks that guard.

Sufficiency/expected-truth records are manual source reviews, not model outputs. Diff-only
AC8 shows a suspicious guard but lacks the surrounding state-transition proof. Missing
context does not become a negative binary label: complete-evidence binary routes are marked
ineligible for insufficient bundles. A future preflight refusal would not establish that a
model recognized insufficiency. No new inference result, fresh execution or mutation kill
is reported by these fixtures.

## Attribution

The new `sources/StaffQueueController.java` is an exact copy from Tutorial commit
`27756e9f1d4248bb0229c182f23d403a8bc5d2ec`, vendoring Anton Arhipov's Spring PetClinic
experiment at attributed upstream commit `fc9df4af46171bf7b6146d0477cc68d70e8532ad`.
The derived `AppointmentService.java` is modified solely by the retained Tutorial patch;
its derivation is explicit in the manifest. These source files and their excerpts retain
Apache License 2.0 licensing. See the preserved [subject license](src/test/resources/semantic-conformance/petclinic/source/LICENSE.txt),
[upstream readme](src/test/resources/semantic-conformance/petclinic/source/README.md),
and [Tutorial patch license](src/test/resources/semantic-conformance/petclinic/mutation/LICENSE).
The surrounding project license does not relicense these upstream files. See also [NOTICE](NOTICE).
