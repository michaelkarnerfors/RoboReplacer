# Requirements (draft)

**Status:** Draft v0.2, 2026-11-22. v0.1 (2026-11-21) was written from the project brief. v0.2 adds what the two clipboard spikes found (section 7) and the maintainer's decisions of 2026-11-22. Nothing here is final until it has been read, challenged and edited by the maintainer.

Priorities use MoSCoW: **M**ust, **S**hould, **C**ould. Requirement IDs are stable: issues, tests and ADRs refer to them. Section numbers are stable too. In this version, *(changed in v0.2)* and *(new in v0.2)* mark what moved, and section 8 lists every change. *Decided* marks what the maintainer has confirmed; everything else is a proposal until reviewed. The "Basis" column says where a requirement comes from: v0.1, a spike finding (F-numbers, section 7), a scenario, or a decision.

## 1. Purpose and audience

RoboReplacer watches the system clipboard and, using user-defined rules, automatically applies search-and-replace to the text that passes through it.

The target users move data between systems by copy and paste, for example accounts receivable and payable assistants copying between bank exports, spreadsheets and ERP screens, where source and destination use different formats. They are not developers. Anything they must do to set up a rule has to be understandable without regular-expression knowledge.

The first release handles plain text only (FR-012). *(new in v0.2)*

## 2. Example scenarios (synthetic)

- **S1. Number formats.** A bank export shows `1 234,56`; the destination wants `1234.56`.
- **S2. Date formats.** A source shows `21/11/2026`; the destination wants `2026-11-21`.
- **S3. Tidy-up.** A spreadsheet cell copied with a trailing space and a line break should paste as clean text. *(Note, v0.2: Excel puts a line break at the end of every copy, so the trailing line break is the normal case, not an exception. See FR-014.)*
- **S4. Reference numbers.** `1234 5678-90` should paste as `1234567890`.
- **S5. Multi-line cell.** *(new in v0.2)* A cell that contains a line break arrives wrapped in double quotes, as `"line one`, a line break, `line two"`. The destination wants the quotes removed and the line break replaced by a space.
- **S6. Same value, many cells.** *(new in v0.2)* The user copies the same code from one cell after another. Every copy is processed, even though the text is identical to the previous copy. (Decided 2026-11-22.)

## 3. Functional requirements

| ID | Requirement | Priority | Basis |
|---|---|---|---|
| FR-001 | While enabled, the application detects every new copy, including a copy of text identical to the previous one and a copy whose set of formats is unchanged. The several notifications that one copy action can cause count as one copy, and each copy gets at most one reaction. *(changed in v0.2. Decided 2026-11-22: a user in flow often copies the same value from different cells, and each copy must be processed.)* | M | v0.1, F2, F3, F4, S6 |
| FR-002 | It applies the active rules, in order, to the clipboard text. If, and only if, the text changed, it writes the result back as plain text. If no rule changed the text, the clipboard is left exactly as it was, with all its formats. *(changed in v0.2)* | M | v0.1, F5, FR-012 |
| FR-003 | Rules support literal search and replace, and regular expressions with capture groups, with case-sensitivity options. Literal mode is the default. | M | v0.1 |
| FR-004 | Rules can be enabled, disabled and reordered, and grouped into named profiles (for example "Bank export to ERP"). | M | v0.1 |
| FR-005 | A rule or profile can be limited by a content condition, so it only fires when the clipboard text matches. *(changed in v0.2: renamed from "trigger pattern". "Trigger" now means the clipboard detection described in the trigger ADR.)* | S | v0.1 |
| FR-006 | The user can pause and resume processing at any time (tray menu, optionally a hotkey), and the current state is always visible. While paused, the clipboard is not read at all. *(changed in v0.2)* | M | v0.1, NFR-001 |
| FR-007 | A test panel lets the user paste sample text and see the result of the rules before enabling them. Invisible characters (tab, no-break space, line ends, a trailing newline) are made visible in both the input and the result. *(changed in v0.2)* | S | v0.1, F7, S1, S3 |
| FR-008 | The user can restore the original clipboard text after a transformation. Only the text comes back: other formats are never read (FR-012), so they cannot be restored. The original is held in memory only, never stored on disk. *(changed in v0.2)* | S | v0.1, F5, OQ-2 |
| FR-009 | Rules can be exported to and imported from a versioned file, so a team can share them. | S | v0.1 |
| FR-010 | Clipboard content without plain text (images, file lists) is never read or modified. When a copy has plain text and a rule rewrites it, the other formats of that copy are dropped (FR-012), and the user documentation says so. *(changed in v0.2)* | M | v0.1, F5, F10 |
| FR-011 | A rule or profile can be limited to content copied from a specific source application. Finding the source application needs a native lookup that no spike has tested (OQ-6). | C | v0.1 |
| FR-012 | **Plain text only.** The application reads only the plain-text format of a copy and writes only plain text. HTML, RTF, images and file lists are never read, parsed or written. Rich-text support is a later feature. Closes OQ-1. *(new in v0.2. Decided 2026-11-22.)* | M | F1, F5, F6 |
| FR-013 | **Respect exclusion.** A copy that carries the Windows clipboard format `ExcludeClipboardContentFromMonitorProcessing` is neither read nor modified. This covers items picked from Windows clipboard history, and it is the format Windows provides for sensitive content such as passwords. The flags `CanIncludeInClipboardHistory` and `CanUploadToCloudClipboard` are not honoured in the first release (OQ-5). *(new in v0.2. Decided 2026-11-22.)* | M | F9 |
| FR-014 | **Line ends and trailing newline.** Rules see the text with LF line ends, as Java delivers it. A trailing newline is part of the text the rules see (Excel adds one to every copy). The documentation says how to match or remove it, and the test panel shows it (FR-007). Which line end the written text gets is settled in OQ-6. *(new in v0.2)* | M | F7, S3, S5 |
| FR-015 | **Feedback in the tray.** The tray icon shows whether processing is enabled, paused or in a warning state. The tray also offers a count of rewrites since start, and the time and rule names of the last rewrite. No popups by default, and no clipboard content is shown there. *(new in v0.2. Decided 2026-11-22: tray only for now.)* | S | FR-006, NFR-002 |

## 4. Non-functional requirements

| ID | Requirement | Priority | Basis |
|---|---|---|---|
| NFR-001 | **Local only.** All processing happens on the user's machine. The application makes no network calls at runtime. Any future network feature, such as an update check, is opt-in and off by default. | M | v0.1 |
| NFR-002 | **No clipboard content in logs, ever,** including at debug level, and no value derived from it, such as a hash or fingerprint. Logs may contain rule identifiers, counts and timings. *(changed in v0.2: the spike logged a fingerprint of the content to compare copies. That was fine for a spike and is not allowed in the product.)* | M | v0.1 |
| NFR-003 | **No feedback loops.** The application never re-processes its own output. Its own output is recognised by the clipboard sequence number noted right after the write, never by comparing content, because copying identical text again is a new copy (FR-001). *(changed in v0.2)* | M | v0.1, F8 |
| NFR-004 | **Robust against bad rules.** A user-defined regular expression that backtracks catastrophically cannot hang the application; evaluation is time-limited and failure leaves the clipboard unchanged. | M | v0.1 |
| NFR-005 | **Rule processing is fast enough to be invisible.** Applying the rules to typical clipboard text (up to a few thousand lines) takes well under 100 ms. The figure is a target to validate. The end-to-end figure is NFR-011. *(changed in v0.2: scope narrowed to rule processing.)* | S | v0.1 |
| NFR-006 | **Fails safe.** If a rule fails or a write-back fails, the original clipboard content is left intact and the user is told. Windows empties the clipboard before new content is set, so a failed write could leave it empty. The original text is therefore held in memory until the write has succeeded; a failed write is retried a bounded number of times, then the original is put back and the user is told. *(changed in v0.2. The empty-clipboard risk is inferred from how the Windows API works; no spike has reproduced it.)* | M | v0.1, F11 |
| NFR-007 | **Platforms.** Windows 10 and 11 first. macOS and Linux later, if there is interest. The platform-specific clipboard code sits behind one interface, so that other platforms do not touch the rule engine. *(Confirmed by the maintainer, 2026-11-21. Interface sentence new in v0.2.)* | M | v0.1, F3 |
| NFR-008 | **Easy to install.** A single installer, bundled runtime and no administrator rights where possible. The launcher passes `--enable-native-access=ALL-UNNAMED`, because the manifest attribute only works for `java -jar` and not for an installer made with jpackage. To be verified when the first installer is built. *(changed in v0.2)* | S | v0.1, F3 |
| NFR-009 | **Internationalisation.** The UI is English first, with strings externalised so other languages can be added. *(Confirmed by the maintainer, 2026-11-21.)* | S | v0.1 |
| NFR-010 | **Test data is synthetic.** No real financial or personal data in the repository, tests, issues or screenshots. | M | v0.1 |
| NFR-011 | **End-to-end latency.** For typical text (up to a few thousand lines), the rewritten clipboard is in place under 300 ms after the copy ends. A paste made inside that window gets the original text, and the documentation says so. The figure is a target to validate: the spike's detect-and-read stage took about 110 to 180 ms, and rule processing and the write come on top. *(new in v0.2. Decided 2026-11-22 as a target.)* | S | F4, F6 |
| NFR-012 | **Size limit.** Text above a limit (default 5 million characters, configurable) is left untouched. The user is warned by an audible signal (a chime or similar, which can be switched off) and by the tray state (FR-015). The figures are targets to validate. The text has to be read before its size is known, so the limit protects rule processing and memory, not the read. The spike read 875 thousand characters in 59 ms; a read of 5 million has not been measured. *(new in v0.2. Decided 2026-11-22.)* | S | F6 |
| NFR-013 | **Polite to other applications.** The clipboard is open only for the short read and the short write, and never while rules run. If another application holds it, the application retries a bounded number of times and then leaves the clipboard alone. (The spike's reads all succeeded on the first attempt.) *(new in v0.2)* | M | F6 |
| NFR-014 | **Low idle cost.** Waiting for copies uses under 1 % of one processor core. The spike polled every 20 ms and did not measure the idle cost; 50 ms is the fallback if the figure is too high. *(new in v0.2)* | S | F3 |

## 5. Out of scope for now

Cloud sync, mobile apps, OCR or image processing, macro or scripting languages beyond search and replace, and any telemetry. Also, from v0.2: rich-text formats (HTML, RTF), images and file lists (FR-012), and notifications beyond the tray (FR-015).

## 6. Open questions

- **OQ-1. Multi-format clipboards.** Resolved in v0.2 (Decided 2026-11-22): plain text only for the first release (FR-012). Other formats come later, as a separate feature, with their own design. The reason is in F5.
- **OQ-2. Clipboard history tools.** Resolved in v0.2 (Decided 2026-11-22): accept and document. Windows clipboard history and third-party managers will see both the original and the rewritten text, because the application writes no exclusion flags on its own output. Picking an older item from Windows clipboard history is not processed again (F9). The documentation tells users this. Third-party managers are untested. Revisit if users want rewritten text kept out of history.
- **OQ-3. Technology choices.** Resolved in [ADR-0004](../adr/0004-java-25-maven-swing-flatlaf.md): Maven, Java 25, Swing with FlatLaf. The clipboard trigger is the open part: the spikes recommend polling the clipboard sequence number with a quiet period before reading (F2 to F4, F8). A new ADR records the decision. It is still to be written and accepted.
- **OQ-4. UI languages.** Resolved: English first, with strings externalised (NFR-009). Further languages, Swedish included, are handled later as an internationalisation feature.
- **OQ-5. Exclusion signals.** *(new in v0.2)* Decided 2026-11-22 for the first release: honour only `ExcludeClipboardContentFromMonitorProcessing` (FR-013). The other two flags also appear on ordinary copies (F9), so their presence alone proves nothing. Revisit once password-manager behaviour has been tested.
- **OQ-6. Unverified assumptions.** *(new in v0.2)* None of these blocks the next step. Each gets a check before the feature that depends on it.
  - Whether Java writes LF or CRLF when the application writes text back, and which one the destination applications expect (FR-014).
  - Whether a no-break space survives in a number format copied from Excel with a Swedish locale (S1).
  - Which flags password managers set (FR-013). Not tested: the spike case was skipped.
  - How long browsers and Word take to finish a copy, and whether a source can change the clipboard twice, more than 100 ms apart (F10).
  - The idle cost of polling (NFR-014).
  - How to find the source application of a copy (FR-011).

## 7. Findings from the spikes

Round 1 is `docs/spike/clipboard-test-cases.md` (what a copy contains). Round 2 is `docs/spike/trigger-comparison.md` (how to detect a copy). Round 2 ran on Windows 11 with Java 25, polling every 20 ms with 100 ms of quiet. The raw logs stay on the maintainer's machine and are not in the repository (NFR-010). Cases are named as in those documents.

| ID | Finding | Evidence | Drives |
|---|---|---|---|
| F1 | A copy puts a set of formats on the clipboard, and Java shows only part of it. Excel has about 30 native formats and Java shows about 11. Notepad has 7 and Java shows 4. A few native formats fan out into many Java flavours: plain text gives 25, HTML plus text 92, the Excel or Word set 96. | Round 1 | FR-012 |
| F2 | Java's `FlavorListener` reacts when the set of formats changes, not on every copy. In the trigger run it missed 3 of about 13 identifiable copies (a second Notepad copy with the same formats, a repeated copy, and our own repeated write) and fired twice for each of the 5 Excel copies, 5 to 22 ms apart. | R1, R2, R3, R6 | FR-001, OQ-3 |
| F3 | The Windows clipboard sequence number changes on every copy, repeats included. The size of the step means nothing (Notepad +8, Excel more than +30, our own write +6). Polling it caught every identifiable copy. | R1 to R4 | FR-001, NFR-007, NFR-014 |
| F4 | One copy can be a burst of changes. Two changes 22 ms apart arrived as one burst. After 100 ms of quiet, the single read always found the text and never an empty clipboard. | R3, R4 | FR-001, NFR-011 |
| F5 | Rewriting rich formats is risky. A regular expression meant for the text leaks into HTML and RTF markup: attributes and styles, entities (no-break space included), text split over several runs, the Windows HTML header with its fixed byte offsets, RTF control words and escapes. Rewriting only the text format leaves a rich-text destination pasting the old content. Rewriting only plain text drops the other formats of that copy. | Round 1 (Excel, web page, Word cases) | FR-002, FR-008, FR-010, FR-012 |
| F6 | Reading plain text is fast and cheap: 0 to 18 ms typically, and 875 thousand characters in 59 ms, against 3.7 s for reading every format. Every read succeeded on the first attempt. | R3, R5, Round 1 big range | FR-012, NFR-011, NFR-012, NFR-013 |
| F7 | The text has a shape rules must handle. Java delivers LF only (CRLF appears only inside RTF). Excel text ends with a newline. A cell with a line break arrives in double quotes. A range is tab-separated. | Round 1, R3, R4 | FR-007, FR-014, S3, S5 |
| F8 | Our own writes can be recognised without comparing content. The burst a write causes ends at the sequence number noted right after the write. Our first write fired the listener and an identical second write did not. `lostOwnership` fires on any replacement, including our own next write, so it cannot mean "someone else copied". A later Excel copy was read normally. | R6 | NFR-003 |
| F9 | Items picked from Windows clipboard history (Win+V) change the sequence number and carry `ExcludeClipboardContentFromMonitorProcessing`, so they are skipped. `CanIncludeInClipboardHistory` and `CanUploadToCloudClipboard` also appeared on an ordinary Snipping Tool copy. Password-manager flags are untested. | R7, R8 | FR-013, OQ-2, OQ-5 |
| F10 | Copies without text (a Snipping Tool image, an Explorer file list) give nothing to read. The image copy came as two bursts 168 ms apart, longer than the quiet period. Not seen with text sources; browsers and Word are untested. | R7 | FR-010, OQ-6 |
| F11 | Windows sets the clipboard by emptying it first and then adding formats. A failure between the two could leave it empty. Inferred from the API, not reproduced. | none | NFR-006 |

## 8. Change log

**v0.2 (2026-11-22)**

- Decisions of 2026-11-22 recorded: a repeated copy of identical text is processed again; OQ-2 accepted and documented, with no exclusion flags on our own output; only `ExcludeClipboardContentFromMonitorProcessing` is honoured; feedback in the tray only; targets of 300 ms and 5 million characters accepted, with an audible warning for oversize input. Earlier, plain text only was decided (OQ-1).
- Changed: FR-001, FR-002, FR-005 (renamed), FR-006, FR-007, FR-008, FR-010, NFR-002, NFR-003, NFR-005, NFR-006, NFR-007, NFR-008.
- New: FR-012, FR-013, FR-014, FR-015, NFR-011, NFR-012, NFR-013, NFR-014, scenarios S5 and S6, OQ-5, OQ-6, section 7 (findings) and this section.
- Resolved: OQ-1, OQ-2. OQ-3 is narrowed to the clipboard trigger ADR, still to be written.
- Unchanged: FR-003, FR-004, FR-009, FR-011 (one note added), NFR-001, NFR-004, NFR-009, NFR-010.
- Added a Basis column to both requirement tables.

**v0.1 (2026-11-21)**

- First draft, written from the project brief.
