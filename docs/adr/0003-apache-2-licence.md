# ADR-0003: License the project under Apache-2.0

- **Status:** Proposed (to be accepted by the maintainer before the first public push)
- **Date:** 2026-11-21

## Context

The project is open source and non-commercial for now, written in Java, and its repository is public. A licence must be chosen before others can legally use or contribute to it.

## Decision

Use the Apache License, Version 2.0. Contributions are accepted under the same licence, as described in section 5 of that licence.

## Consequences

Permissive: anyone may use, modify and redistribute the software, including in closed-source or commercial products, provided they keep the notices. It includes an explicit patent grant, and it is the common choice in the Java ecosystem, so dependency licences are easy to check against it.

## Alternatives considered

- **MIT.** Shorter and equally permissive, but without an explicit patent grant.
- **GPL-3.0 or AGPL-3.0.** Copyleft would keep derivative works open, at the cost of discouraging some users and contributors. A reasonable choice if keeping derivatives open matters more than adoption.
