# Requirements (draft)

**Status:** Draft v0.1, 2026-11-21. Written from the project brief as a starting point for review. Nothing here is final until it has been read, challenged and edited by the maintainer.

Priorities use MoSCoW: **M**ust, **S**hould, **C**ould. Requirement IDs are stable: issues, tests and ADRs refer to them.

## 1. Purpose and audience

RoboReplacer watches the system clipboard and, using user-defined rules, automatically applies search-and-replace to the text that passes through it.

The target users move data between systems by copy and paste, for example accounts receivable and payable assistants copying between bank exports, spreadsheets and ERP screens, where source and destination use different formats. They are not developers. Anything they must do to set up a rule has to be understandable without regular-expression knowledge.

## 2. Example scenarios (synthetic)

- **S1. Number formats.** A bank export shows `1 234,56`; the destination wants `1234.56`.
- **S2. Date formats.** A source shows `21/11/2026`; the destination wants `2026-11-21`.
- **S3. Tidy-up.** A spreadsheet cell copied with a trailing space and a line break should paste as clean text.
- **S4. Reference numbers.** `1234 5678-90` should paste as `1234567890`.

## 3. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-001 | While enabled, the application detects when the clipboard text changes. | M |
| FR-002 | It applies the active rules, in order, to the clipboard text and writes the result back to the clipboard. | M |
| FR-003 | Rules support literal search and replace, and regular expressions with capture groups, with case-sensitivity options. Literal mode is the default. | M |
| FR-004 | Rules can be enabled, disabled and reordered, and grouped into named profiles (for example "Bank export to ERP"). | M |
| FR-005 | A rule or profile can be limited by a trigger pattern, so it only fires when the clipboard content matches. | S |
| FR-006 | The user can pause and resume processing at any time (tray menu, optionally a hotkey), and the current state is always visible. | M |
| FR-007 | A test panel lets the user paste sample text and see the result of the rules before enabling them. | S |
| FR-008 | The user can restore the original clipboard content after a transformation. | S |
| FR-009 | Rules can be exported to and imported from a versioned file, so a team can share them. | S |
| FR-010 | Non-text clipboard content (images, files) is never modified. | M |
| FR-011 | A rule or profile can be limited to content copied from a specific source application. | C |

## 4. Non-functional requirements

| ID | Requirement | Priority |
|---|---|---|
| NFR-001 | **Local only.** All processing happens on the user's machine. The application makes no network calls at runtime. Any future network feature, such as an update check, is opt-in and off by default. | M |
| NFR-002 | **No clipboard content in logs, ever,** including at debug level. Logs may contain rule identifiers, counts and timings. | M |
| NFR-003 | **No feedback loops.** The application never re-processes its own output. | M |
| NFR-004 | **Robust against bad rules.** A user-defined regular expression that backtracks catastrophically cannot hang the application; evaluation is time-limited and failure leaves the clipboard unchanged. | M |
| NFR-005 | **Fast enough to be invisible.** Typical clipboard text (up to a few thousand lines) is processed in well under 100 ms. The figure is a target to validate. | S |
| NFR-006 | **Fails safe.** If a rule fails or a write-back fails, the original clipboard content is left intact and the user is told. | M |
| NFR-007 | **Platforms.** Windows 10 and 11 first. macOS and Linux later, if there is interest. *Confirmed by the maintainer, 2026-11-21.* | M |
| NFR-008 | **Easy to install.** A single installer, bundled runtime and no administrator rights where possible. | S |
| NFR-009 | **Internationalisation.** The UI is English first, with strings externalised so other languages can be added. *Confirmed by the maintainer, 2026-11-21.* | S |
| NFR-010 | **Test data is synthetic.** No real financial or personal data in the repository, tests, issues or screenshots. | M |

## 5. Out of scope for now

Cloud sync, mobile apps, OCR or image processing, macro or scripting languages beyond search and replace, and any telemetry.

## 6. Open questions

- **OQ-1. Multi-format clipboards.** Spreadsheets put several formats on the clipboard at once (plain text, HTML, and more). If only the plain-text flavour is rewritten, a rich-text target may still paste the old content. Do we rewrite all text flavours, drop the rich ones, or let the user choose? Needs a spike on the target platform.
- **OQ-2. Clipboard history tools.** Windows clipboard history and third-party managers will see both the original and the transformed content. Is that acceptable, and do we document it?
- **OQ-3. Technology choices.** Resolved in [ADR-0004](../adr/0004-java-25-maven-swing-flatlaf.md): Maven, Java 25, Swing with FlatLaf. The clipboard integration approach stays open until the v0.2.0 spike.
- **OQ-4. UI languages.** Resolved: English first, with strings externalised (NFR-009). Further languages, Swedish included, are handled later as an internationalisation feature.
