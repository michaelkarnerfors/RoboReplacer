# GitHub setup checklist

One-time steps to bring the repository online. Replace `<you>` with your GitHub username.

## 1. Create the repository

On GitHub: **New repository**, name `RoboReplacer`, **Public**. Leave *README*, *.gitignore* and *license* **unticked**, because this scaffold already provides them (except the licence, see step 2).

## 2. Add the licence

The project is intended to be Apache-2.0 (see [ADR-0003](adr/0003-apache-2-licence.md)). Fetch the official text from the project root:

```
curl.exe -fsSL -o LICENSE https://www.apache.org/licenses/LICENSE-2.0.txt
```

## 3. Initialise and push

```
cd E:\Development\Repositories\RoboReplacer
git init -b main
git add .
git commit -m "chore: initial project scaffold"
git remote add origin https://github.com/<you>/RoboReplacer.git
git push -u origin main
```

## 4. Seed labels, milestones and the first backlog

Needs the [GitHub CLI](https://cli.github.com/), authenticated once with `gh auth login`.

```
bash scripts/bootstrap-github.sh --dry-run    # shows what it would do
bash scripts/bootstrap-github.sh              # does it
```

Run it once. It refuses to seed issues into a repository that already has issues, unless `FORCE=1` is set.

## 5. Create the Project board

1. On your profile or the repo: **Projects -> New project -> Board**, named *RoboReplacer*, and link it to the repository.
2. Edit the **Status** field so its options are `Backlog`, `Ready`, `In progress`, `In review`, `Done`.
3. In **Workflows**, enable *Auto-add to project* for the repository, *Item closed* -> `Done`, and *Pull request merged* -> `Done`.
4. Optionally add a Table view grouped by Milestone, and a Roadmap view.
5. Add the existing issues to the project (the auto-add workflow can do this for new ones).

## 6. Repository settings

- **General -> Pull Requests:** allow *squash merging* only, default the commit message to the *pull request title*, and enable *Automatically delete head branches*.
- **Rules -> Rulesets -> New branch ruleset** targeting the default branch:
  - Require a pull request. Use **0 required approvals** while you are the only maintainer (you cannot approve your own PRs), and raise it to 1 when others join.
  - Require status checks to pass: `Build and test (ubuntu-latest)` and `Build and test (windows-latest)`. They only show up in the picker after the CI workflow has run once.
  - Block force pushes and restrict deletions.
  - Require linear history.
- **Settings -> Security:**
  - Enable *private vulnerability reporting* (SECURITY.md relies on it).
  - Enable Dependabot alerts and security updates. Secret scanning and push protection are on by default for public repositories.
  - Enable CodeQL code scanning (default setup) once there is Java code to scan.

## 7. Sanity check

- *New issue* shows the three forms and no blank-issue option.
- Opening a PR pre-fills the template.
- The first Dependabot run (monthly) will propose GitHub Actions updates once workflows exist.
