# LogLens

LogLens is an IntelliJ IDEA plugin that brings a dedicated log viewer into the IDE.

Open `.log`, `.out` and `.txt` files in a dedicated tool window with level filtering, plain-text and regex search. No network access, no external services — the whole experience is local-first.

The technical specification lives in [SPEC.md](SPEC.md) and the iteration plan in [ROADMAP.md](ROADMAP.md). The current source tree implements the **0.1 MVP** described in those documents.

## Features (0.1 MVP)

- Dedicated **LogLens tool window** anchored to the bottom of the IDE.
- **Open Log File…** action that loads `.log`, `.out`, `.txt` files into the viewer.
- **Buffered file reading** to keep large files responsive.
- Parsing pipeline built around a small `LogParser` interface with a `ParserRegistry`:
  - `SpringBootLogParser` — Spring Boot / Logback default pattern (timestamp, level, thread, logger).
  - `PlainTextLogParser` — fallback for arbitrary text files with optional level detection.
- `LogEntry`, `LogLevel` and `ThrowableInfo` domain types shared by every parser.
- **Level filter** toggles for `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` (UNKNOWN lines are always shown).
- **Search** with plain text, case-sensitive option, and **regular expression** mode; composable with the level filter.
- **Stack-trace header detection** (used by 0.2 navigation — see ROADMAP).
- **Persistent preferences** (filter state, search options) via `LogLensSettings`.
- Graceful handling of malformed lines; raw text is always preserved.
- Unit tests for parsers, level filter, search and file reading.

## Tech Stack

- **Language:** Kotlin 2.0.21 (JVM 21 toolchain).
- **Platform:** IntelliJ Platform 2024.3 (`com.jetbrains.intellij.platform` Gradle plugin v2.3.0).
- **Build:** Gradle 9.5.1 with Kotlin DSL (`build.gradle.kts`).
- **Testing:** JUnit 5 + `kotlin.test`.

## Requirements

- JDK 21
- Gradle 8.10 or newer (the project also works with the bundled wrapper `./gradlew`).

## Installation

Clone the repository and build the plugin locally:

```bash
git clone <repository-url>
cd loglens
./gradlew build
```

The resulting distribution ZIP is written to `build/distributions/`. Install it in IntelliJ IDEA via **Settings → Plugins → Install Plugin from Disk…** and choose the ZIP.

## Running

After the plugin is installed and the IDE restarted, open any `.log`, `.out` or `.txt` file via **File → Open Log File…**. The LogLens tool window appears at the bottom of the IDE, showing the parsed entries. Use the level toggles and the search box to narrow the view.

To open the tool window without a file, use the **Window → LogLens** menu entry.

## Development

```bash
# Compile main + test sources.
./gradlew compileKotlin compileTestKotlin

# Run unit tests.
./gradlew test

# Build the plugin distribution.
./gradlew build

# Run the sandboxed IDE with the plugin loaded for manual smoke testing.
./gradlew runIde
```

### Project layout

```
src/
├── main/
│   ├── kotlin/io/loglens/
│   │   ├── exception/   # StackTraceDetector (used in 0.2 navigation)
│   │   ├── filter/      # LevelFilter
│   │   ├── icons/       # Plugin icon loader
│   │   ├── model/       # LogEntry, LogLevel, ThrowableInfo, StackFrame
│   │   ├── parser/      # LogParser + SpringBootLogParser + PlainTextLogParser + ParserRegistry
│   │   ├── search/      # LogSearch (plain / regex)
│   │   ├── service/     # LogLensProjectService, FileReadingService
│   │   ├── settings/    # PersistentStateComponent + Settings Configurable
│   │   ├── ui/          # Tool Window factory, toolbar, viewer
│   │   └── util/        # RawText helpers (searchable text, summary)
│   └── resources/
│       ├── META-INF/plugin.xml
│       └── icons/loglens.svg
└── test/
    ├── kotlin/io/loglens/   # JUnit 5 + kotlin.test specs
    └── resources/
```

The application logic stays parser-agnostic: every line of input is normalised into a `LogEntry` before the viewer, filter and search see it.

## Architecture

LogLens uses a thin layered structure on top of the IntelliJ Platform:

- **`LogParser`** — pure parsing abstraction. Implementations never touch UI code.
- **`ParserRegistry`** — selects the right `LogParser` per file and keeps parser affinity so a single file is not flipped between formats mid-stream.
- **`LogLensProjectService`** — project-scoped state holder that owns the most recently opened log and broadcasts snapshots to listeners.
- **`LogViewerPanel`** + **`LogViewerToolbar`** — Swing UI components inside the Tool Window. They consume `LogEntry`s, a `LevelFilter` and a `LogSearch` and never know which parser produced them.
- **`LogLensSettings`** — `PersistentStateComponent` storing the user's filter/search preferences across IDE restarts.

This honours SPEC §13: features depend on abstractions (parser interface, snapshot model) rather than concrete implementations.

## Security & Privacy

Per SPEC §12:

- Logs never leave the IDE.
- No network calls, no API keys, no accounts.
- No telemetry.

## Contributing

1. Fork the repository.
2. Create a feature branch.
3. `./gradlew test` must pass before submitting a PR.
4. Submit a Pull Request targeting the appropriate milestone in `ROADMAP.md`.

## License

MIT — see `LICENSE`.