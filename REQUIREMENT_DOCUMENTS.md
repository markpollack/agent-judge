# Requirement documents: syntax, strict loading and the linter

> On main, not yet released (0.19.0-SNAPSHOT). Strict loading, whole-paragraph reading, the
> completeness rule and the linter are not in 0.18.0. The syntax itself is unchanged since 0.16.0.

The EARS and RFC 2119 judges take their requirements from Markdown documents written before
the code. This page is the format those documents must follow, what the readers do with it,
and how to check a document while you write it. Every example below is checked by
`RequirementDocumentsGuideTest`: the valid ones lint clean, and the refused one produces
exactly the report shown under it.

| Reader | Behaviour |
|---|---|
| `EarsRequirement.from(path, revision)`, `Rfc2119Requirement.from(path, revision)` | Permissive: a malformed entry is left out, a visibly incomplete sentence is kept, and each problem is logged at WARN |
| `EarsRequirement.fromStrict(path, revision)`, `Rfc2119Requirement.fromStrict(path, revision)` | Strict: any problem refuses the whole document with a `RequirementDocumentException` that names every problem with its line |
| `RequirementDocuments.lint(directory)` | Reads every `.md` file below a directory with both strict readers and returns a `Report` |

Documents are read as CommonMark. Fenced and indented code blocks, HTML blocks and thematic
breaks are never read, so an example requirement inside a fence (like every example on this
page) is documentation. Paragraphs inside lists and block quotes are read like any other, and
their list and quote markers are not part of the text.

## EARS acceptance criteria

A criterion is a level-3 heading `### UCn-ACm: Title`, followed in the same entry by its
requirement sentence. The entry ends at the next heading of any level or at the end of the
document.

- **The sentence** is the first paragraph of the entry that is not a metadata line, read in full.
  It must contain `shall` and end in `.`, `!` or `?` (closing brackets, quotes and inline
  markup may follow the punctuation).
- **Metadata lines** begin with `Covers:`, `Rule:`, `Rules:`, `Tests:` or `Status:`, bold or
  plain. They are traceability, never the requirement, and they cannot stand in for a
  missing sentence.
- **`Applies when:` condition** (also `**Applies when:** condition`) declares the optional
  applicability, once per criterion, anywhere in the entry. Declaring it is what makes
  "not applicable" an available answer for that criterion; an EARS trigger such as `When`,
  `While` or `If` inside the sentence never declares applicability.
- Later paragraphs of the entry are prose and are not read.

<!-- lint: valid -->
```markdown
### UC1-AC1: Record a placed order

When a customer places an order, the system shall record the order
and send exactly one confirmation.

**Covers:** UC1-B1
**Tests:** OrderServiceTest

### UC1-AC2: Show open orders while the shop is closed

While the shop is closed, the system shall show only orders in the `OPEN` state.

Applies when: the change touches the order list view

This paragraph is prose: it explains the criterion and is not read.
```

## RFC 2119 constraints

A rule is a level-3 heading `### RULE-n` or `### UCn-RULEm` with nothing else on its line,
followed in the same entry by exactly one keyword statement and exactly one reason.

- **The statement** starts a line with a bold keyword: `**MUST**`, `**MUST NOT**`,
  `**SHOULD**`, `**SHOULD NOT**` or `**MAY**`. It continues through its paragraph up to the
  next keyword, `**Reason:**`, `**Covers:**` or `Applies when:` line, and must end in `.`, `!`
  or `?`.
- **`**Reason:**`** gives the reason on its own line.
- **`**Covers:**`** is optional traceability. **`Applies when:`** declares the optional
  applicability, as for criteria.
- These lines may also sit in a list item or a block quote.

<!-- lint: valid -->
```markdown
### RULE-1

**MUST NOT** call the payment gateway from inside
a database transaction.

**Reason:** a rollback cannot undo a charge.

### UC1-RULE2

- **SHOULD** log every refused payment with its order identifier.
- **Reason:** support needs the identifier to answer the customer.
- **Covers:** UC1-B4
- Applies when: the change touches payment code
```

## Wrapped lines

A sentence or statement wrapped across lines is joined with single spaces, as Markdown
renders a soft wrap. A hard break (two trailing spaces, or a trailing backslash) joins the
same way, without its backslash. So `UC1-AC1` above is read as one sentence ending in "send
exactly one confirmation.", and `RULE-1` as "call the payment gateway from inside a database
transaction."

## What a strict load refuses

Each problem is reported as `file:line ID: reason`, on the line where the entry, sentence or
statement starts.

| Problem | Applies to |
|---|---|
| A heading that resembles a criterion or rule but is not written exactly as above | both |
| No requirement sentence, or no keyword statement, before the next heading or the end of the document | both |
| A sentence with no `shall` | EARS |
| A sentence or statement that does not end in `.`, `!` or `?`, so may be truncated | both |
| A keyword with no statement after it; no `**Reason:**` line; `**Reason:**` with nothing after it | RFC 2119 |
| A second keyword statement, a second `**Reason:**` or a second `Applies when:` in one entry | RFC 2119 (a second `Applies when:` also for EARS) |
| A blank `Applies when:` | both |
| An identifier an earlier heading already declared | both |

<!-- lint: refused -->
```markdown
### UC2-AC1: Cancel an order

When a customer cancels an order,

### UC2-AC1: Refund a cancelled order

When an order is cancelled, the order is refunded.

### RULE-3

**MUST** keep every cancelled order
```

The linter reports:

```text
example.md:3 UC2-AC1: requirement sentence has no `shall`; an EARS requirement states what the system shall do
example.md:3 UC2-AC1: requirement sentence does not end in terminal punctuation (. ! ?); it may be truncated
example.md:5 UC2-AC1: duplicate identifier; an earlier heading already declared it
example.md:7 UC2-AC1: requirement sentence has no `shall`; an EARS requirement states what the system shall do
example.md:9 RULE-3: no `**Reason:**` line before the next heading or the end of the document
example.md:11 RULE-3: keyword statement does not end in terminal punctuation (. ! ?); it may be truncated
```

`fromStrict` refuses this document. `from` would keep both `UC2-AC1` sentences as written, and
leave `RULE-3` out for want of a reason.

## Lint a specification while you write it

`RequirementDocuments.lint(Path.of("spec"))` returns a `Report` with the documents read, how
many criteria and constraints loaded, and every problem. Files that hold no recognisable
heading, such as a README, contribute nothing. One test keeps a specification repository
valid; it fails with every problem listed:

```java
@Test
void specificationIsValid() {
    RequirementDocuments.assertValid(Path.of("spec"));
}
```

From the command line,
`java -cp <agent-judge-ai-core and its dependencies> io.github.markpollack.judge.ai.requirements.RequirementDocuments spec`
prints the report and exits with status 1 when there is any problem.
