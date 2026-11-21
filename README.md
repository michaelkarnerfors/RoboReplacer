# RoboReplacer

A small desktop tool that watches your clipboard and automatically applies your own search-and-replace rules to whatever passes through it.

> **Status: pre-alpha.** There is nothing to download yet. The project is being set up; see the [roadmap](#roadmap).

## Why

Anyone who moves data between systems by copy and paste knows the routine: copy from the source, fix the formatting, paste into the destination. Decimal commas become decimal points, dates change shape, stray spaces and line breaks sneak in, and the same fixes get made by hand dozens of times a day. RoboReplacer makes those fixes for you the moment you press Ctrl+C.

The target users are people who shuffle data between systems all day, for example accounts receivable and payable assistants who copy between bank exports, spreadsheets and ERP screens, where source and destination rarely agree on format.

## The idea (planned behaviour)

You define a rule once, and from then on it runs by itself:

| You copy | The rule | You paste |
|---|---|---|
| `1 234,56` | Swedish number format to plain decimal | `1234.56` |

Rules are user-defined, ordered, can be switched on and off, and can be limited to clipboard content that matches a pattern, so a rule only fires when it should.

## Privacy

Your clipboard is private. RoboReplacer is designed to process everything locally, make no network calls, and never log clipboard content. These are requirements, not afterthoughts: see NFR-001 and NFR-002 in the [requirements](docs/requirements/requirements.md).

## Roadmap

Work is tracked as GitHub issues, grouped into milestones that double as releases:

- **v0.1.0 Walking skeleton**: build and CI in place, a headless rule engine with tests
- **v0.2.0 Clipboard watcher**: watch, transform and write back safely, with a tray icon
- **v0.3.0 Rule editor**: create, edit, test, import and export rules
- **v0.4.0 Packaging and releases**: installers and an automated release pipeline

## Building from source

You need JDK 25. You do not need to install Maven: the repository includes the Maven Wrapper, which downloads the pinned Maven version (see `.mvn/wrapper/maven-wrapper.properties`) on first use.

```
./mvnw verify                                     # compile, test and package (Linux, macOS)
mvnw.cmd verify                                   # the same on Windows
java -jar target/roboreplacer-0.1.0-SNAPSHOT.jar  # run the packaged application
./mvnw compile exec:java                          # or run straight from the sources
```

A Maven you installed yourself (3.8 or newer) works too; the commands are then `mvn verify` and so on.

The jar's file name follows the version in `pom.xml`.

## Documentation

- [Documentation index](docs/README.md)
- [How we work](docs/process.md)
- [Requirements (draft)](docs/requirements/requirements.md)
- [Architecture decision records](docs/adr/)
- [Contributing](CONTRIBUTING.md) and [Security policy](SECURITY.md)

## License

Apache License 2.0, see [LICENSE](LICENSE).
