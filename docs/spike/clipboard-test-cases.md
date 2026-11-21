# Clipboard spike: test cases

**Status:** throw-away spike, lives on a branch that is never merged. What we learn goes into an ADR (written on a clean branch).
**Question:** what do real Windows applications put on the clipboard, and what does Java's AWT make of it? (OQ-1, and the first half of the clipboard spike issue.)

## Safety

- **Synthetic data only** (NFR-010). While the spike runs, every copy you make is excerpted (160 characters per format) in its window and appended to `target/spike/flavours.log`.
- Do not copy real data, and no real passwords, while it runs. Close the window when you are done.
- `target/` is git-ignored, so the log is never committed.

## Run it (on the Windows JVM, not in WSL)

- **Eclipse:** `ClipboardSpike` in package `se.karnerfors.roboreplacer.spike` > Run As > Java Application. (Eclipse needs Lombok installed into the IDE first, see the notes in the hand-over.)
- **Maven, Git Bash:** `./mvnw compile exec:java -Dexec.mainClass=se.karnerfors.roboreplacer.spike.ClipboardSpike`
- **Built jar:** `./mvnw package`, then `java -cp target/roboreplacer-0.1.0-SNAPSHOT.jar se.karnerfors.roboreplacer.spike.ClipboardSpike`

Before each case, type its ID (T1, T2 ...) in the marker box and press **Add marker**, so the log can be read afterwards. If you copy and no dump appears, press **Dump clipboard now**: when that shows new content, the clipboard changed without a notification.

## Test data to prepare

An Excel workbook with Swedish regional settings, sheet `Spike`:

| Cell | Content | Notes |
|---|---|---|
| A1 | `1234.56` | Number format, 2 decimals, thousands separator. Shows `1 234,56`; the separator is a no-break space. |
| A2 | `5678.9` | Same format. |
| A3 | `2026-11-21` | Custom format `dd/mm/yyyy`. |
| A4 | `1234 5678-90 ` | Text, with a trailing space (S3 and S4). |
| A5 | `Line one`, Alt+Enter, `Line two` | A line break inside one cell (S3). |
| B1:B3 | any three numbers | Together with A1:A3 this is a 2 x 3 range. |
| big range | `=ROW()*COLUMN()/7` in A1:E20000 on a second sheet `Big` | For the timing case. |

For the plain-text cases: a Notepad document containing `1 234,56` where the space is typed with Alt+0160 (no-break space).

## Hypotheses

| ID | Hypothesis | Cases |
|---|---|---|
| H1 | `FlavorListener` is silent when a new copy has the same set of formats as the previous one (the JDK compares format sets). A live run on Linux already showed this; Windows is unconfirmed. | T2, T3, T5 |
| H2 | AWT shows only the formats the JDK knows. Excel's private formats and anything exotic are invisible. | T4, T9 |
| H3 | A few native formats fan out into many Java flavours (String, Reader, InputStream, byte[] per charset). | all |
| H4 | Excel text ends with CRLF, and a cell with an embedded line break arrives wrapped in double quotes. | T4, T8 |
| H5 | The HTML flavour carries the Windows CF_HTML header (`Version:`, `StartHTML:` ...), or the JDK strips it. | T4, T11 |
| H6 | Reading every flavour of a big range is slow, and shows how much re-reading the fan-out costs. | T10 |
| H7 | Password managers mark their copies with exclusion flags that AWT does not show. | T15 |

## Cases

| ID | Copy this | From | Look for |
|---|---|---|---|
| T1 | `1 234,56` (no-break space) | Notepad | Baseline. Which flavours, how many, NBSP and CRLF counts. |
| T2 | Change the Notepad text to `abc`, copy again | Notepad | H1: is there a second event? If not, press Dump now. |
| T3 | Copy the same text twice in a row | Notepad | H1 again, with identical content. |
| T4 | A1 | Excel | H2 to H5: the full flavour list, trailing CRLF, HTML header. |
| T5 | A2, straight after T4 | Excel | H1 with the case that matters most. |
| T6 | A3 (date) | Excel | What the date looks like in each flavour. |
| T7 | A4 (trailing space) | Excel | Is the space kept? |
| T8 | A5 (line break in cell) | Excel | H4: quoting. |
| T9 | A1:B3 | Excel | Tab and CRLF structure; any extra flavours. |
| T10 | Sheet `Big`, A1:E20000 | Excel | H6: dump time, truncation marker, attempts. |
| T11 | A paragraph and a small table from a web page | Edge or Chrome | HTML and text flavours; H5. |
| T12 | A paragraph with some bold text | Word | RTF and HTML flavours. |
| T13 | A file (not its content) | Explorer | The file-list flavour. |
| T14 | A region (Win+Shift+S) | Snipping tool | The image flavour and its size. |
| T15 | A dummy password entry. Skip if you have no password manager. | KeePass, 1Password or similar | H7: anything unusual. |
| T16 | Open Windows clipboard history (Win+V) and paste an older item | Windows | Does picking from history produce an event? (OQ-2) |

## What happens next

If H1 holds on Windows, `FlavorListener` is ruled out as the only trigger for FR-001, and the next spike step compares it with polling `GetClipboardSequenceNumber` through the Foreign Function API. The findings go into the ADR for the clipboard approach.
