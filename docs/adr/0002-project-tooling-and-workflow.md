# ADR-0002: Use GitHub for project management, CI/CD and collaboration, with GitHub Flow and Kanban

- **Status:** Accepted. The branching, merging and release flow is superseded by [ADR-0005](0005-dev-integration-branch-and-release-flow.md); the rest stands.
- **Date:** 2026-11-21

## Context

RoboReplacer is a small, personal, open-source hobby project, worked on when time permits by one maintainer plus an AI collaborator, with perhaps a few contributors later. It needs requirement and task tracking, release management, a remote repository for collaboration, and a CI/CD pipeline. The maintainer knows Git workflows (including Gitflow), Scrum and Kanban well. The application will be written in Java.

## Decision

- **Hosting and tracking:** a public GitHub repository, with GitHub Issues (issue forms, labels, sub-issues), GitHub Projects (board) and milestones for planning. Longer-lived requirements live in `docs/requirements/` and decisions in `docs/adr/`.
- **CI/CD:** GitHub Actions. The pipeline is added together with the Java build in v0.1.0.
- **Branching:** GitHub Flow. `main` is always releasable, work happens on short-lived branches, and pull requests are squash-merged with Conventional Commit titles.
- **Process:** Kanban with a small WIP limit. A milestone is a release and has scope but no dates. No sprints.
- **Versioning:** SemVer, released as tags and GitHub Releases.
- **Technology:** Java for the application. Build tool, Java version and UI toolkit are decided separately in v0.1.0.

## Consequences

Requirements, code, reviews, CI and releases live in one system, and public repositories get free CI. There is no separate tracker to keep in sync. The cost is less sophisticated reporting than a dedicated tool such as Jira, which this project does not need. Branch protection must allow a solo maintainer to merge their own pull requests.

## Alternatives considered

- **Jira and Confluence.** Powerful, but heavy for a project of this size, and it splits the source of truth across two systems unless the GitHub integration is maintained.
- **Gitflow.** A long-lived `develop` branch pays off when several released versions are maintained in parallel or releases pass a slow gate. Neither applies, so it would add merge overhead. If it ever does apply, `release/x.y` branches cover it.
- **Scrum with sprints.** Fixed cadences and ceremonies do not fit work done when time permits.
