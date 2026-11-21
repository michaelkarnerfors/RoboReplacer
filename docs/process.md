# How we work

A small team (currently one human and one AI collaborator) working at hobby pace. The process is deliberately light: enough structure to keep track of things, no ceremonies that demand attention.

## Principles

- **GitHub is the single source of truth.** Issues track work, the Project board shows flow, milestones are releases, and `docs/` holds durable knowledge.
- **Kanban, not sprints.** Work is pulled when time permits. A small work-in-progress limit keeps things finishing instead of piling up.
- **`dev` is always buildable, and `main` only ever holds releases.** Work reaches `dev` through pull requests with green CI. `main` only receives a deliberate release.

## The board

A GitHub Project named *RoboReplacer*, with a **Status** field:

`Backlog` -> `Ready` -> `In progress` -> `In review` -> `Done`

- **WIP limit:** at most 2 items in *In progress* per person.
- **Definition of Ready** (an item may move to *Ready*):
  - It states the goal and acceptance criteria. Features include a synthetic example of what is copied and what should be pasted.
  - It is small enough to finish in a few sessions; otherwise split it into sub-issues.
  - Any decision it depends on has been made, or the item is itself that decision.
- **Definition of Done** (an item may move to *Done*):
  - Acceptance criteria are met and CI is green.
  - Tests cover the behaviour. Docs and ADRs are updated where relevant.
  - The privacy checks in the PR template are ticked.
  - The PR is merged into `dev` and the issue is closed. Releasing is a separate, later step.

## Work items and labels

| Kind | Label | Notes |
|---|---|---|
| Feature or requirement | `enhancement` | Use the *Feature or requirement* issue form |
| Bug | `bug` | Use the *Bug report* issue form |
| Technical work, spike or decision | `task` | Use the *Task, spike or decision* form |
| Documentation | `documentation` | |

Extra labels: `area: core`, `area: clipboard`, `area: ui`, `area: build`, `privacy`, `blocked`, `priority: high`, `priority: low`. Larger features are parent issues with sub-issues.

## Milestones, test builds and releases

A **milestone is a release**. Versions follow [SemVer](https://semver.org/); before 1.0 anything may change. Milestones have scope, not dates.

**Test builds.** Finished work merged into `dev` can be built and published as a pre-release for testers (see the test-build issue in v0.4.0). A test build is never a release.

**Releasing** is a deliberate act:

1. All milestone issues are closed, or consciously moved to a later milestone.
2. Set the version in `pom.xml` to the release version (drop `-SNAPSHOT`), through a pull request into `dev`.
3. Open a pull request from `dev` into `main` titled `release: vX.Y.Z`, and merge it with a **merge commit** (not a squash).
4. Tag `vX.Y.Z` on `main` and create a GitHub Release. Use *Generate release notes*, which groups changes by the categories in `.github/release.yml`.
5. Close the milestone, and move `dev` to the next `-SNAPSHOT` version through a pull request.

Once the release pipeline exists (v0.4.0), pushing the tag builds the installers and attaches them to the release automatically.

## Branching and commits (`main` and `dev`)

[ADR-0005](adr/0005-dev-integration-branch-and-release-flow.md) describes the model and the reasons.

- **`main`** holds released code only. **`dev`** is the integration branch: always buildable, and the repository's default branch. Both are protected. Changes arrive by pull request with green CI, and nothing is pushed to them directly.
- Branch from `dev`, short-lived: `feature/123-short-name`, `fix/123-short-name`, `docs/123-short-name`, where `123` is the issue number.
- Open the pull request **into `dev`** and **squash-merge** it.
- The **PR title is the commit message**, in [Conventional Commits](https://www.conventionalcommits.org/) style: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`, `test: ...`, `build: ...`, `ci: ...`, `chore: ...`. Add `!` after the type for a breaking change.
- Use draft PRs for early feedback. Keep PRs small and focused.
- A hotfix for a released version branches from `main`, goes into `main` by pull request, and is then merged back into `dev`.

## Working with AI: the 4D framework

We use the Delegation, Description, Discernment and Diligence framework from the AI Fluency curriculum as a working agreement.

| D | In practice here |
|---|---|
| **Delegation** | Decide up front what the AI drafts and what a human owns. AI is well suited to boilerplate, test-data variety, first drafts of docs and exploring options. Humans own architecture decisions, anything touching clipboard content, and the meaning of the rules. |
| **Description** | Issues are the briefing: goal, context, constraints, examples and acceptance criteria. If an issue could not be handed to a stranger, it is not Ready. |
| **Discernment** | AI output gets reviewed like any other PR, and it gets run. Watch for plausible but wrong. Prefer tests written from the examples in the issue before the implementation. |
| **Diligence** | Privacy first (no clipboard content in logs, ever), licence and security checks on dependencies, and honest disclosure of AI assistance in each PR. |

## Decisions

Significant decisions are recorded as short ADRs in [docs/adr](adr/). Copy `0000-template.md`, number it, and set the status (`Proposed`, `Accepted`, `Superseded by ADR-NNNN`).
