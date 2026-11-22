# Clipboard spike, round 2: which trigger?

**Status:** throw-away spike, same branch as round 1 (`chore/13-clipboard-spike`), never merged. What we learn goes into the ADR for the clipboard approach (written on a clean branch).
**Question:** can `FlavorListener` be the trigger for FR-001, or do we poll `GetClipboardSequenceNumber`? Round 1 showed that the listener is silent when a copy has the same set of formats as the one before (a Notepad copy with other text gave no event), and that one Excel copy gives two events, the first often with an empty clipboard.
**Decision already taken (2026-11-22):** the MVP reads and writes plain text only, so this spike reads only the plain-text flavour.

## Safety

- The log, `target/spike/triggers.log`, holds **no clipboard content**: only sequence numbers, text lengths, a short fingerprint (4 bytes of a SHA-256, enough to tell two copies apart, still guessable for short texts) and traits such as `LF=2 TAB=3`. `target/` is git-ignored.
- Copy **synthetic data only** all the same (NFR-010). The "Write synthetic text" button copies `ROBO-SPIKE-1`, `ROBO-SPIKE-2` ... onto the clipboard, replacing whatever was there.

## Run it (Windows JVM, not WSL)

The program calls `user32` through the Foreign Function API, which is a restricted operation. Start the JVM with `--enable-native-access=ALL-UNNAMED`, or JDK 24 and later prints a warning (the jar manifest's `Enable-Native-Access` only counts for `java -jar`).

- **Eclipse:** `TriggerCompareSpike` in `se.karnerfors.roboreplacer.spike` > Run As > Java Application; then Run Configurations > Arguments > VM arguments: `--enable-native-access=ALL-UNNAMED`.
- **Built jar:** `./mvnw package`, then `java --enable-native-access=ALL-UNNAMED -cp target/roboreplacer-0.1.0-SNAPSHOT.jar se.karnerfors.roboreplacer.spike.TriggerCompareSpike`
- **Tuning:** `-Dspike.pollMillis=20` (how often the sequence number is looked at) and `-Dspike.quietMillis=100` (how long it must stay still before the one read).

Before each case type its ID (R1, R2 ...) in the marker box and press **Add marker**. The window has three more buttons: **Write synthetic text** (loop-guard test), **Read text now** (the plain-text read, on demand) and **Clear view**.

## Reading the log

Every line starts with the time of day and a tag, so the two triggers can be compared line by line.

| Tag | Meaning |
|---|---|
| `LISTENER` | AWT's `FlavorListener` fired. Shows the sequence number at that moment and how far it moved since the previous listener event. |
| `POLL` | The poller saw the sequence number change (`seq 100 -> 103 (+3)`), and how many ms after the previous change. |
| `SETTLED` | No further change for the quiet period: the burst is complete. Says how many changes it held. This is the moment the product would read. |
| `READ` | The plain-text read: number of native formats (from `CountClipboardFormats`), exclusion flags found, the outcome (`TEXT`, `EMPTY`, `NO_TEXT`, `BUSY`), length, fingerprint, traits, attempts and time, and whether the content equals the previous text read. |
| `SKIP` | The copy carries `ExcludeClipboardContentFromMonitorProcessing`, so it is not read. |
| `WRITE` / `OWNER` | Our own write (sequence number before and after), and Java telling us that our contents were replaced (`lostOwnership`), by another application or by our own next write. |
| `ERROR` | Something threw. Reported once per kind. |

## Hypotheses

| ID | Hypothesis | Cases |
|---|---|---|
| H9 | The sequence number changes on every copy, also when the set of formats is the same, so polling sees the copies that `FlavorListener` misses. | R1, R2 |
| H10 | One Excel copy is one burst of a few changes, a few milliseconds apart. With 100 ms of quiet the single read always finds the text, never an empty clipboard. | R3, R4, R5 |
| H11 | Copying the same text twice moves the sequence number but not the content (same fingerprint). The product cannot tell "copied again" from "unchanged" except by the sequence number. | R2 |
| H12 | Our own write is recognised: the burst it causes ends at the sequence number we noted straight after writing, so it is not read. Open: does our own write fire the listener, and does `lostOwnership` fire? | R6 |
| H13 | A copy with an exclusion flag is skipped. The native format count is higher than what AWT shows (H2 of round 1). | R9, R3 |
| H14 | Picking an older item in the Windows clipboard history (Win+V) changes the sequence number (OQ-2). | R8 |

## Cases

Use the same synthetic test data as in round 1 (`docs/spike/clipboard-test-cases.md`).

| ID | Do this | In | Look for |
|---|---|---|---|
| R1 | Type `abc`, select it, copy. Change it to `abd`, copy again. | Notepad | The second copy: `POLL` and `SETTLED` with a different fingerprint, and no `LISTENER` line (H9). |
| R2 | Copy the same text twice, with a few seconds between. | Notepad | Two bursts, same fingerprint (H9, H11). Does `LISTENER` fire for either? |
| R3 | Copy A1, then A2 straight after. | Excel | Number of changes in the burst and the `+Δ`; whether the `READ` ever says `EMPTY` (H10). Compare `LISTENER` lines. |
| R4 | Copy A1:B3. | Excel | The text read: `TAB` and `LF` traits, trailing newline. Native format count (H13). |
| R5 | Copy the `Big` range, A1:E20000. | Excel | Read time of the plain-text read, against 3.7 s for reading every flavour in round 1 (H10). |
| R6 | Press **Write synthetic text** once, wait, press it again. Then copy something in Excel a few seconds later. | the spike window | `WRITE`, then `POLL ... [our own write]`, then `SETTLED ... [ends at our own write: not read]` (H12). Any `LISTENER` line? Any `OWNER` line? The later Excel copy must be read normally. |
| R7 | Copy an image (Win+Shift+S) and a file (Explorer). | Snipping tool, Explorer | `READ` says `NO_TEXT`; nothing else is read. |
| R8 | Open the clipboard history (Win+V) and pick an older item. | Windows | `POLL` and `SETTLED`: does picking from history count as a copy (H14)? |
| R9 | Copy a dummy entry from a password manager. Skip if you have none. | KeePass, 1Password or similar | `SKIP` or the flags in `READ` (H13). |

## What happens next

The log decides the trigger for FR-001. If `POLL` sees every copy and `LISTENER` does not (H9), polling with a quiet period is the trigger, the loop guard works as in H12, and the findings go into the ADR for the clipboard approach. Whatever is left (a read that finds `EMPTY` even after the quiet period, a self-made change that is not recognised) becomes a requirement or a spike item of its own.
