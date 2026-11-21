# ADR-0005: Use `dev` as the integration branch; a release is a deliberate merge to `main`

- **Status:** Accepted
- **Date:** 2026-11-21
- **Supersedes:** the branching, merging and release parts of [ADR-0002](0002-project-tooling-and-workflow.md) (GitHub Flow with an always-releasable `main`). The rest of ADR-0002 stands.

## Context

ADR-0002 chose GitHub Flow, where every merged change lands on a branch that is always releasable. The maintainer wants a clear gap between "finished and merged" and "released": finished features and slices must be buildable and reachable by a tester without that counting as a release, and a release must be a deliberate act.

## Decision

- **Two long-lived branches.** `main` holds released code only. `dev` is the integration branch: always buildable, never a release.
- **Features** are developed on short-lived branches (`feature/123-name`, `fix/123-name`, `docs/123-name`) cut from `dev`, and merged into `dev` by pull request with a squash merge and green CI.
- **Test builds.** A build of `dev` may be published as a clearly marked pre-release (for example a rolling `dev-latest`) that testers can download. It is never a release and never marked as the latest release.
- **Releases.** A release is a pull request from `dev` into `main`, merged with a merge commit (not squashed, so the two branches do not drift apart). The merge is tagged `vX.Y.Z` on `main` and published as a GitHub Release. Nothing else is merged into `main`, apart from hotfixes.
- **Hotfixes** to a released version branch from `main`, go into `main` by pull request, and are then merged back into `dev`.
- **Protection.** Both branches are protected by rulesets: pull request required, CI checks required, no force pushes, no deletion. `dev` also requires linear history and allows only squash merges. `main` allows only merge commits.
- **Default branch.** `dev` is the repository's default branch, so new pull requests target it by default, Dependabot follows it, and "Closes #123" closes an issue when the feature merges.

## Consequences

- One more long-lived branch to look after, and every change reaches `main` through `dev`.
- Issues close when a feature merges into `dev`, not at release. That matches the board's definition of Done. Milestones stay open until the release.
- The ruleset's deletion rule matters on `dev`: it stops the "delete head branch after merge" setting from removing `dev` when a release pull request merges.
- Visitors land on `dev`; the Releases page and `main` show what has actually shipped.
- The CI workflow runs on pushes to both branches, and on every pull request.
- Releasing needs a few manual steps (version bump, release pull request, tag). The release workflow in v0.4.0 can automate the tail end.

## Alternatives considered

- **Plain GitHub Flow (ADR-0002).** Simpler, but it leaves no place for merged-but-unreleased work other than feature branches.
- **Full Gitflow with `release/*` branches.** Release preparation here is a version bump and the release pull request, so the extra branch type buys little for a project this size.
- **Keeping `main` as the default branch.** Closing keywords would then only fire at release time, and pull requests would default to the wrong base.
