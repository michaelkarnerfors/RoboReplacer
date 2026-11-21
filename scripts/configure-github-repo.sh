#!/usr/bin/env bash
#
# Applies repository settings and branch protection through the GitHub CLI,
# following ADR-0005: `dev` is the integration (and default) branch, `main`
# holds releases.
#
# Run once after the repository exists, `dev` has been pushed and `gh auth login`
# has been done, from inside a clone. Read what it will do first with --dry-run.
#
# Usage:
#   scripts/configure-github-repo.sh --dry-run
#   scripts/configure-github-repo.sh
#
# What it does:
#   1. Makes dev the default branch
#   2. Merge settings: squash and merge commits allowed (each ruleset narrows it
#      to one), PR title and description become the commit message, head branches
#      are deleted after merge; repository topics
#   3. Security: private vulnerability reporting, Dependabot alerts and security updates
#   4. One ruleset per branch: pull request required (0 approvals, since you are the
#      only maintainer), both CI checks must pass, no force pushes, no deletion.
#      dev: squash merges only, linear history. main: merge commits only.
#
# Override the branch names with INTEGRATION_BRANCH and RELEASE_BRANCH.

set -euo pipefail

DRY_RUN=0
case "${1:-}" in
  "") ;;
  --dry-run) DRY_RUN=1 ;;
  -h|--help) sed -n '2,/^$/p' "$0"; exit 0 ;;
  *) echo "Unknown argument: $1" >&2; exit 2 ;;
esac

INTEGRATION_BRANCH="${INTEGRATION_BRANCH:-dev}"
RELEASE_BRANCH="${RELEASE_BRANCH:-main}"
CHECKS=("Build and test (ubuntu-latest)" "Build and test (windows-latest)")

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
fi

checks_json() {
  local out="" c
  for c in "${CHECKS[@]}"; do
    out+="${out:+, }{ \"context\": \"$c\" }"
  done
  printf '%s' "$out"
}

# ruleset_json NAME BRANCH LINEAR_HISTORY(yes|no) MERGE_METHOD(squash|merge)
ruleset_json() {
  local name="$1" branch="$2" linear="$3" method="$4" linear_rule=""
  [[ "$linear" == "yes" ]] && linear_rule='{ "type": "required_linear_history" },'
  cat <<JSON
{
  "name": "$name",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": [ "refs/heads/$branch" ], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    $linear_rule
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false,
        "allowed_merge_methods": [ "$method" ]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [ $(checks_json) ]
      }
    }
  ]
}
JSON
}

create_ruleset() {
  local name="$1"
  if (( DRY_RUN )); then
    echo "DRY RUN: would POST this to repos/{owner}/{repo}/rulesets"
    echo "--- ruleset JSON ---"
    ruleset_json "$@"
    echo "--- end ---"
  elif gh api "repos/{owner}/{repo}/rulesets" --jq '.[].name' | grep -qxF "$name"; then
    echo "  A ruleset named '$name' already exists; leaving it alone (edit it under Settings -> Rules -> Rulesets)."
  else
    ruleset_json "$@" | gh api --method POST "repos/{owner}/{repo}/rulesets" --input - --silent
    echo "  Created '$name'."
  fi
}

echo "== Default branch: $INTEGRATION_BRANCH"
if (( DRY_RUN )) || gh api "repos/{owner}/{repo}/branches/$INTEGRATION_BRANCH" --silent 2>/dev/null; then
  run gh repo edit --default-branch "$INTEGRATION_BRANCH"
else
  echo "  Branch '$INTEGRATION_BRANCH' is not on GitHub yet. Push it first: git push -u origin $INTEGRATION_BRANCH" >&2
  exit 1
fi

echo "== Merge settings and topics"
run gh repo edit --enable-squash-merge --enable-merge-commit --enable-rebase-merge=false \
  --delete-branch-on-merge --add-topic java --add-topic swing --add-topic clipboard
run gh api --method PATCH "repos/{owner}/{repo}" --silent \
  -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=PR_BODY \
  -f merge_commit_title=PR_TITLE -f merge_commit_message=PR_BODY

echo "== Security features"
run gh api --method PUT "repos/{owner}/{repo}/private-vulnerability-reporting" --silent \
  || echo "  (could not enable private vulnerability reporting; do it under Settings -> Security)"
run gh api --method PUT "repos/{owner}/{repo}/vulnerability-alerts" --silent \
  || echo "  (could not enable Dependabot alerts; do it under Settings -> Security)"
run gh api --method PUT "repos/{owner}/{repo}/automated-security-fixes" --silent \
  || echo "  (could not enable Dependabot security updates; do it under Settings -> Security)"

echo "== Branch rulesets"
create_ruleset "Protect $INTEGRATION_BRANCH" "$INTEGRATION_BRANCH" yes squash
create_ruleset "Protect $RELEASE_BRANCH" "$RELEASE_BRANCH" no merge

echo
echo "Done. Still manual: create the Project board and enable CodeQL (docs/github-setup.md)."
echo "From now on both branches only accept pull requests with both CI checks green; features go to $INTEGRATION_BRANCH, releases to $RELEASE_BRANCH."
