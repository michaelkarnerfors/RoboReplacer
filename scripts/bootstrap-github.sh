#!/usr/bin/env bash
#
# One-time bootstrap of GitHub labels, milestones and the seed backlog.
#
# Requires the GitHub CLI (https://cli.github.com/), authenticated with
# `gh auth login`, and must be run from inside a clone of the repository.
#
# Usage:
#   scripts/bootstrap-github.sh --dry-run   print what would be done
#   scripts/bootstrap-github.sh             do it
#   FORCE=1 scripts/bootstrap-github.sh     seed even if the repo already has issues
#
set -euo pipefail

DRY_RUN=0
case "${1:-}" in
  "") ;;
  --dry-run) DRY_RUN=1 ;;
  -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
  *) echo "Unknown argument: $1" >&2; exit 2 ;;
esac

run() {
  if (( DRY_RUN )); then
    printf 'DRY RUN:'
    printf ' %q' "$@"
    printf '\n'
  else
    "$@"
  fi
}

if (( ! DRY_RUN )); then
  command -v gh >/dev/null 2>&1 || { echo "GitHub CLI (gh) not found. Install it from https://cli.github.com/" >&2; exit 1; }
  gh auth status >/dev/null 2>&1 || { echo "Not logged in. Run: gh auth login" >&2; exit 1; }
  existing="$(gh issue list --state all --limit 1 --json number --jq 'length')"
  if [[ "$existing" != "0" && "${FORCE:-0}" != "1" ]]; then
    echo "This repository already has issues. Refusing to seed duplicates (set FORCE=1 to override)." >&2
    exit 1
  fi
fi

label() { run gh label create "$1" --color "$2" --description "$3" --force; }

milestone() {
  run gh api "repos/{owner}/{repo}/milestones" --silent -f title="$1" -f description="$2" \
    || echo "  (milestone '$1' may already exist, continuing)"
}

# issue "title" "milestone" "label1,label2" <<'EOF' ... EOF   (body on stdin)
issue() {
  local title="$1" milestone="$2" labels="$3" body
  body="$(cat)"
  run gh issue create --title "$title" --milestone "$milestone" --label "$labels" --body "$body"
}

echo "== Labels"
label "enhancement"        "a2eeef" "New feature or requirement"
label "bug"                "d73a4a" "Something is not working"
label "documentation"      "0075ca" "Improvements or additions to documentation"
label "task"               "5319e7" "Technical work, spike or decision"
label "privacy"            "b60205" "Touches clipboard content, logging or network access"
label "blocked"            "d93f0b" "Waiting on something else"
label "priority: high"     "d93f0b" "Do this before other work"
label "priority: low"      "c5def5" "Nice to have, pick up when convenient"
label "area: core"         "1d76db" "Rule model and rule engine"
label "area: clipboard"    "0e8a16" "Clipboard monitoring and write-back"
label "area: ui"           "fbca04" "User interface"
label "area: build"        "6f42c1" "Build, CI and packaging"
label "dependencies"       "0366d6" "Dependency updates"
label "ignore-for-release" "ededed" "Excluded from generated release notes"

echo "== Milestones"
M1="v0.1.0 - Walking skeleton"
M2="v0.2.0 - Clipboard watcher"
M3="v0.3.0 - Rule editor"
M4="v0.4.0 - Packaging and releases"
milestone "$M1" "Build and CI in place, plus a headless rule engine with tests."
milestone "$M2" "Watch the clipboard, transform and write back safely, with a tray icon."
milestone "$M3" "Create, edit, test, import and export rules."
milestone "$M4" "Windows installer and an automated release pipeline."

echo "== Issues: $M1"
issue "Verify the skeleton and harden CI" "$M1" "task,area: build" <<'EOF'
## Goal
The Maven skeleton, the CI workflow and the Swing + FlatLaf stack were set up (ADR-0004) without being built on a machine with JDK 25. Prove that they work, then make CI a quality gate.

## Done when
- [x] `mvn verify` passes locally on Windows with JDK 25
- [ ] The placeholder window opens, both via `mvn compile exec:java` and via `java -jar` on the packaged jar
- [ ] The CI workflow is green on Windows and Linux
- [ ] Both CI jobs are required status checks in the branch ruleset
- [ ] CodeQL code scanning (default setup) is enabled
- [ ] The Maven wrapper is added (`mvn wrapper:wrapper`) and used by CI
- [ ] The README build instructions are confirmed accurate
EOF

issue "Core: rule model and ordered rule engine" "$M1" "enhancement,area: core" <<'EOF'
## Goal
A pure Java library (no UI, no clipboard dependency) that applies an ordered list of rules to a string. Covers FR-002, FR-003 and the ordering part of FR-004.

## Done when
- [ ] Literal search and replace, with case options
- [ ] Regular expressions with capture groups in the replacement
- [ ] Rules apply in order, and can be individually enabled or disabled
- [ ] Unit tests cover each behaviour using synthetic data
EOF

issue "Core: safe regex evaluation with a time limit" "$M1" "enhancement,area: core" <<'EOF'
## Goal
A user-defined regular expression must never be able to hang the application (NFR-004), and a failing rule must leave the text unchanged (NFR-006).

## Done when
- [ ] Rule evaluation has a time limit, and a timeout aborts the whole transformation
- [ ] A test with a known catastrophic pattern proves it returns in bounded time
- [ ] The outcome (unchanged text plus a reportable error) is part of the engine's API
EOF

issue "Core: synthetic golden-file test suite for common formats" "$M1" "task,area: core" <<'EOF'
## Goal
A data-driven test suite built from the example scenarios S1 to S4 in the requirements (number formats, date formats, tidy-up, reference numbers), which also serves as living documentation.

## Done when
- [ ] Each case is input text, rules and expected output, stored as plain files
- [ ] Only synthetic data is used (NFR-010)
- [ ] Adding a new case requires no Java code
EOF

issue "Logging policy: never log clipboard content" "$M1" "task,privacy" <<'EOF'
## Goal
Make NFR-002 enforceable rather than aspirational.

## Done when
- [ ] A logging approach is chosen and documented (what may be logged: rule IDs, counts, timings)
- [ ] An automated guard in CI helps catch clipboard content being logged (approach to be decided, for example a test or a static check)
- [ ] CONTRIBUTING.md points to the policy
EOF

echo "== Issues: $M2"
issue "Spike: clipboard access on Windows from Java" "$M2" "task,area: clipboard" <<'EOF'
## Goal
Answer how a Java application should detect clipboard changes and rewrite content reliably on Windows. Time box: one day.

## Questions
- Is the standard Java clipboard API (`java.awt.datatransfer`, with a `FlavorListener`) enough on Windows and notified reliably on every copy, or do we need polling or a native listener (JNA or the Foreign Function API)?
- What does a spreadsheet put on the clipboard (formats), and what happens to the other formats when text is rewritten? (OQ-1)
- How do we recognise our own write-back so we never loop? (NFR-003)

## Done when
- [ ] Findings and a recommendation are recorded in an ADR
- [ ] A minimal proof of concept exists, or a clear reason why not
EOF

issue "Clipboard watcher with loop prevention" "$M2" "enhancement,area: clipboard" <<'EOF'
## Goal
While enabled, detect text changes on the clipboard, run the rule engine, and write the result back without re-processing our own output. Covers FR-001, FR-002, FR-010, NFR-003 and NFR-006.

## Done when
- [ ] Text changes trigger the rule engine and the result is written back
- [ ] Our own write-back is never re-processed
- [ ] Non-text content is left untouched
- [ ] If a rule or the write-back fails, the original clipboard content stays intact and the failure is reported
EOF

issue "Handle multi-format clipboard content" "$M2" "enhancement,area: clipboard" <<'EOF'
## Goal
Resolve open question OQ-1: when text is rewritten, what happens to the other formats (such as HTML) that a source application placed on the clipboard? Decide and implement the behaviour so that pasting into rich-text targets does not bring back the old content.

## Done when
- [ ] Decision recorded (rewrite all text flavours, drop rich ones, or make it configurable)
- [ ] Implemented and covered by tests where practical
- [ ] Documented for users

Depends on the clipboard spike.
EOF

issue "System tray icon with pause, resume and status" "$M2" "enhancement,area: ui" <<'EOF'
## Goal
The application runs quietly in the system tray. The user can always see whether it is active and can pause it (FR-006).

## Done when
- [ ] Tray icon shows active or paused state
- [ ] Menu offers pause and resume, and exit
- [ ] Paused means the clipboard is never touched
EOF

echo "== Issues: $M3"
issue "Rule persistence and versioned import and export" "$M3" "enhancement,area: core" <<'EOF'
## Goal
Rules are saved between runs and can be shared as a file (FR-009).

## Done when
- [ ] Rules are stored in a documented, versioned plain-text format
- [ ] Export and import work, including handling of files from a newer or older version
- [ ] A malformed file is rejected with a clear message and never crashes the application (see SECURITY.md)
EOF

issue "Profiles and trigger patterns" "$M3" "enhancement,area: core" <<'EOF'
## Goal
Group rules into named profiles, and optionally limit a rule or profile to clipboard content that matches a trigger pattern (FR-004 and FR-005).

## Done when
- [ ] Profiles can be created, enabled and disabled
- [ ] A trigger pattern prevents a rule from running unless the clipboard content matches
- [ ] Covered by golden-file tests
EOF

issue "Rule editor UI" "$M3" "enhancement,area: ui" <<'EOF'
## Goal
A user without regular-expression knowledge can create and edit rules (FR-003, FR-004, NFR-007 usability intent). Literal mode is the default; regular expressions are an opt-in.

## Done when
- [ ] Create, edit, delete, enable, disable and reorder rules
- [ ] Clear validation messages, including for invalid regular expressions
- [ ] Reviewed against a realistic scenario with synthetic data
EOF

issue "Test panel with live preview" "$M3" "enhancement,area: ui" <<'EOF'
## Goal
Paste sample text, see what the rules would produce, before enabling them (FR-007).

## Done when
- [ ] Output updates as rules or sample text change
- [ ] The test panel never reads or writes the real clipboard unless the user asks
EOF

issue "Restore the original clipboard content" "$M3" "enhancement,area: clipboard" <<'EOF'
## Goal
After a transformation, the user can get the original content back (FR-008).

## Done when
- [ ] The most recent original is kept in memory only (never written to disk or logs)
- [ ] A tray action restores it
- [ ] Original content is discarded when the application exits
EOF

echo "== Issues: $M4"
issue "Package a Windows installer with jpackage" "$M4" "task,area: build" <<'EOF'
## Goal
A single installer with a bundled Java runtime, installable without administrator rights where possible (NFR-008).

## Done when
- [ ] The installer builds reproducibly from a clean checkout
- [ ] Install, upgrade and uninstall work on a clean Windows machine
EOF

issue "Release workflow: build and attach installers on tag" "$M4" "task,area: build" <<'EOF'
## Goal
Pushing a version tag on `main` builds the installers in GitHub Actions and attaches them to the GitHub Release.

## Done when
- [ ] Tag push triggers the build, with artifacts attached to the release
- [ ] Checksums are published alongside the artifacts
- [ ] docs/process.md is updated to describe the release steps as they now are
EOF

issue "Publish test builds from dev to a rolling pre-release" "$M4" "task,area: build" <<'EOF'
## Goal
Finished work merged into `dev` can be tried by a tester without being a release (ADR-0005).

## Done when
- [ ] A successful build of `dev` updates a clearly marked pre-release (for example a rolling `dev-latest`) with the jar attached
- [ ] That pre-release is never marked as the latest release, and shows which commit it was built from
- [ ] Releases from `main` are unaffected, and docs/process.md explains where a tester finds the build
EOF

issue "Decide on code signing for Windows installers" "$M4" "task,area: build" <<'EOF'
## Goal
Unsigned installers trigger Windows SmartScreen warnings, which may put off non-technical users. Decide what is reasonable for a hobby project.

## Done when
- [ ] Options compared (unsigned with clear instructions, a paid certificate, or a signing service for open-source projects) with cost and effort
- [ ] The decision is recorded in an ADR
EOF

issue "User guide with screenshots" "$M4" "documentation" <<'EOF'
## Goal
A short guide for non-technical users: install, create a first rule, test it, pause it.

## Done when
- [ ] Guide lives in docs/ and is linked from the README
- [ ] All screenshots and examples use synthetic data
EOF

echo
echo "Done. Next: run scripts/configure-github-repo.sh for repository settings and branch protection, then create the Project board (docs/github-setup.md, steps 5 and 6)."
