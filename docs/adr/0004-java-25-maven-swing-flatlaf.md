# ADR-0004: Build with Maven on Java 25, with a Swing and FlatLaf user interface

- **Status:** Accepted
- **Date:** 2026-11-21

## Context

[ADR-0002](0002-project-tooling-and-workflow.md) chose Java for the application and deferred the build tool, Java version and UI toolkit. The requirements call for Windows first, an easy installer with a bundled runtime, a small footprint, and a tray-resident application used by non-developers.

## Decision

- **Build tool:** Maven, run through the Maven Wrapper (`mvnw`, `mvnw.cmd`, only-script type). The Maven version is pinned in `.mvn/wrapper/maven-wrapper.properties` (3.9.16 when added) and used by both contributors and CI, so nobody needs a system Maven. The build also works with a self-installed Maven 3.8 or newer (verified with 3.8.7).
- **Java version:** Java 25, the current long-term-support release (September 2025). The installer will bundle a runtime (with `jpackage`), so end users do not need to install Java.
- **User interface:** Swing with the FlatLaf look and feel (Apache-2.0 licensed).
- **Tests:** JUnit Jupiter.
- **Base package:** `se.karnerfors.roboreplacer`, with Maven groupId `se.karnerfors`.
- **Packaging:** `mvn package` produces one executable jar (built with the Maven Shade plugin) that runs with `java -jar`. The Windows installer later wraps it with `jpackage`.
- **Not decided here:** how the clipboard is accessed. The standard `java.awt.datatransfer` API is the natural starting point with Swing, and the v0.2.0 clipboard spike validates it on Windows.

## Consequences

- Building requires JDK 25, for contributors and for CI.
- Swing ships with the JDK, so there is no separate UI runtime to bundle, and `java.awt.SystemTray` covers the tray icon. FlatLaf gives Swing a modern flat appearance and HiDPI handling.
- Swing is mature rather than fashionable; richer visual effects are harder than in JavaFX. For a tray-resident rule editor that is an acceptable trade.
- Swing and Maven are both cross-platform, so macOS and Linux remain possible later, although tray behaviour varies by platform.

## Alternatives considered

- **Gradle.** Equally capable. Maven was chosen by the maintainer; it is conventional and well supported by CI, Dependabot and `jpackage` tooling.
- **JavaFX.** More modern visuals, but it is a separate runtime to bundle and adds packaging weight.
- **Java 21 (previous LTS).** Works, but there is no reason to start a new project on an older LTS.
