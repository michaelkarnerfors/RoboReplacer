# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

The project is worked on intermittently, by a human and an AI collaborator, with possible future contributors. Between work sessions the reasons behind decisions are easily lost, and a later reader cannot tell a deliberate choice from an accident.

## Decision

We record significant decisions as short Architecture Decision Records in `docs/adr/`, numbered sequentially, using `0000-template.md`. Accepted ADRs are not rewritten. A changed decision gets a new ADR that supersedes the old one.

## Consequences

A small amount of writing per decision, in return for a durable trail that is cheap to read. Pull requests that make a significant decision should include or link the ADR.

## Alternatives considered

Keeping decisions in issue comments or chat history. They are hard to find later and are not versioned alongside the code.
