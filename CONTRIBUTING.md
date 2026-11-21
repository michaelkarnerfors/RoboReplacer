# Contributing to RoboReplacer

Thanks for your interest! This is a hobby project run when time permits, so replies may take a while. Issues and pull requests are welcome all the same.

## The short version

1. Pick or open an [issue](../../issues). For anything non-trivial, agree on the approach there before writing code.
2. Branch from `main`: `feature/123-short-name`, `fix/123-short-name` or `docs/123-short-name`.
3. Run `mvn verify` locally (JDK 25, Maven 3.9+), then open a pull request. Keep it focused and fill in the template.
4. CI must be green. PRs are squash-merged, so **the PR title becomes the commit message** and must follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `ci:`, `chore:`.

The full workflow, board columns and definitions of ready and done are in [docs/process.md](docs/process.md).

## House rules

- **No real data, ever.** RoboReplacer handles clipboard content that may include financial or personal data. Use synthetic data in tests, issues, screenshots and logs. Never paste real invoices, account numbers or names.
- **Never log or transmit clipboard content.** Not at debug level either. Log rule identifiers and timings instead.
- **AI-assisted work is welcome, but you own it.** Say in the PR how much AI help was involved. You must have read, understood and tested everything you submit, and be able to explain it.
- **Dependencies need a reason.** New libraries must have a licence compatible with Apache-2.0 and be justified in the PR.
- **Decisions get recorded.** Significant architecture or process decisions go in an [ADR](docs/adr/).

## Licensing of contributions

By submitting a contribution you agree that it is licensed under the Apache License 2.0, as described in section 5 of that licence.
