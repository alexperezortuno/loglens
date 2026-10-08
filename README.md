# LogLens

LogLens is an IntelliJ IDEA plugin that brings a dedicated log viewer into the IDE.

Open `.log`, `.out` and `.txt` files in LogLens with level filtering, plain-text and regex search. Spring Boot, Logback, Log4j, compact timestamp-level, ANSI-formatted and newline-delimited JSON logs are supported. No network access, no external services — the whole experience is local-first.

The technical specification lives in [SPEC.md](SPEC.md) and the iteration plan in [ROADMAP.md](ROADMAP.md). The current source tree implements the **0.1 MVP**, **0.2 developer navigation**, and selected **0.3 advanced viewer** capabilities.

## Features

- Dedicated **LogLens tool window** anchored to the bottom of the IDE.
- Opens `.log` and `.out` files directly in an ANSI-aware LogLens file editor.
- **Open Log File…** action that loads `.log`, `.out`, `.txt` files into the viewer.
- ANSI SGR foreground/background colors, bold, italic and underline rendering in the viewer.
- Newline-delimited JSON parsing with structured metadata and a compact feed/detail layout.
- **Bounded background file loading** with a 10 MiB/10,000-record initial page, 4 MiB/5,000-record subsequent pages, a 20,000-entry/32 MiB retention cap, and a 1 MiB per-record limit.
- **Tail mode** with Follow/Pause controls, rotation restart, automatic scrolling, and a new-entry indicator. Tailing evicts the oldest records at the retention cap.
- Parsing pipeline built around a small `LogParser` interface with a `ParserRegistry`:
  - `SpringBootLogParser` — Spring Boot / Logback default pattern (timestamp, level, thread, logger).
  - `LogbackLogParser` — common timestamp/thread/level/logger Logback layouts.
  - `Log4jLogParser` — common Log4j2 time-first and Log4j 1.x date-first layouts.
  - `TimestampLevelLogParser` — compact records such as `21:00:27.261 [ERROR] message`.
  - `JsonLinesLogParser` — one JSON object per line, including structured app/module/function fields.
  - `PlainTextLogParser` — fallback for arbitrary text files with optional level detection.
- `LogEntry`, `LogLevel` and `ThrowableInfo` domain types shared by every parser.
- **Level filter** toggles for `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, and `UNKNOWN` entries.
- **Search** with plain text, case-sensitive option, and **regular expression** mode; composable with the level filter.
- **Grouped Java/Kotlin exceptions** with parsed causes, suppressed exceptions, clickable source frames, and source navigation.
- Copy complete exceptions or individual frames from the exception detail pane.
- **Persistent preferences** (filter state, search options) via `LogLensSettings`.
- Graceful handling of malformed lines; raw text is always preserved.
- Unit tests for parsers, stack-trace grouping/navigation, filters, search and bounded file reading.

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

After the plugin is installed and the IDE restarted, open `.log`, `.out`, or `.txt` files normally; LogLens starts at the beginning and loads a bounded page in the background. Choose **Load more** to continue, or **Cancel** to stop the current read. Use **Follow** to watch appended records, **Pause** to stop polling, and **Resume** to continue. Oversized records show a truncated preview while their remaining bytes are skipped in cancellable chunks. The viewer keeps a bounded in-memory record window and reports when its retention limit is reached. Search and filters apply to records currently loaded. ANSI colors and JSON metadata are preserved. Select an exception to inspect its frames; click a source frame to navigate, or use the copy buttons. **File → Open Log File…** and **Open in LogLens** remain available for explicit loading.

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
│   │   ├── parser/      # LogParser + JSON-lines, Spring Boot and plain-text parsers
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
