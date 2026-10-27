# TraceLens

A fast, offline-first log viewer for IntelliJ-based IDEs.

Open `.log`, `.out` and `.txt` files in TraceLens with level filtering, plain-text and regex search. TraceLens can also follow local processes, Docker containers, and Docker Compose services. Spring Boot, Logback, Log4j, compact timestamp-level, ANSI-formatted and newline-delimited JSON logs are supported. No network access, no external services — the whole experience is local-first.

![TraceLens Tool Window](docs/images/tool-window.png)

## Features

- **Multi-format parsing** — Plain text, Spring Boot, Logback, Log4j, JSON Lines, and compact timestamp-level logs
- **ANSI color support** — SGR colors, backgrounds, and text styles rendered in the viewer
- **Level filtering** — Toggle TRACE/DEBUG/INFO/WARN/ERROR/FATAL/UNKNOWN independently
- **Search** — Plain text, case-sensitive, and regular expression modes; composable with level filters
- **Stack-trace navigation** — Clickable frames open source files at the exact line
- **Tail mode** — Follow actively written files with pause/resume, auto-scroll, and new-entry indicator
- **Large-file support** — Bounded paging (10 MiB/10k records initial, 4 MiB/5k subsequent), 20k record/32 MiB retention
- **Observability** — Trace/group by traceId/spanId/requestId, timeline view, log statistics
- **Local AI (optional)** — Explain errors, summarize exceptions, find probable causes via Ollama or OpenAI-compatible endpoints
- **Multiple sources** — Local files, Docker containers, Docker Compose services, local processes
- Dedicated **TraceLens tool window** anchored to the bottom of the IDE.
- Opens `.log` and `.out` files directly in an ANSI-aware TraceLens file editor.
- **Open Log File…** action that loads `.log`, `.out`, `.txt` files into the viewer.
- **Open Process Log…** action that runs a local command and follows its combined stdout/stderr through the same parser and viewer pipeline.
- **Open Docker Log…** action that follows a local container with `docker logs --follow`.
- **Open Docker Compose Log…** action that follows one service with `docker compose logs --follow`.
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
- Advanced filters for logger, thread, inclusive date/time range, exclusions, and saved presets.
- **Trace grouping** with a **Group by trace** toggle for related events that share a `traceId`.
- **Trace timeline** showing related events, service/logger context, and elapsed time between events.
- **Statistics** for loaded records: levels over time, ERROR/FATAL frequency, common exceptions, and active loggers.
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

## Supported IDEs

| IDE | Since Build | Until Build |
|-----|-------------|-------------|
| IntelliJ IDEA Community | 2024.3 (243) | 2025.3 (253.*) |
| IntelliJ IDEA Ultimate | 2024.3 (243) | 2025.3 (253.*) |
| Android Studio | 2024.3 (243) | 2025.3 (253.*) |
| Other IntelliJ Platform IDEs | 2024.3 (243) | 2025.3 (253.*) |

Verified with IntelliJ Plugin Verifier against IC-2024.3, IC-2025.1, IC-2025.2.

## Installation

### From JetBrains Marketplace (recommended)

1. Open **Settings → Plugins → Marketplace**
2. Search for **TraceLens**
3. Click **Install**
4. Restart IDE when prompted
After the plugin is installed and the IDE restarted, open `.log`, `.out`, or `.txt` files normally; TraceLens starts at the beginning and loads a bounded page in the background. Choose **Load more** to continue, or **Cancel** to stop the current read. Use **Follow** to watch appended records, **Pause** to stop polling, and **Resume** to continue. Use **File → Open Process Log…**, **Open Docker Log…**, or **Open Docker Compose Log…** to follow live local sources; commands are started directly without a shell. Docker must be installed and available on `PATH`. Oversized records show a truncated preview while their remaining bytes are skipped in cancellable chunks. The viewer keeps a bounded in-memory record window and reports when its retention limit is reached. Search and filters apply to records currently loaded. ANSI colors and JSON metadata are preserved. Select an exception to inspect its frames; click a source frame to navigate, or use the copy buttons. **File → Open Log File…** and **Open in TraceLens** remain available for explicit loading.

### From Disk

1. Download the latest `tracelens-<version>.zip` from [GitHub Releases](https://github.com/loglens/loglens/releases)
2. Open **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the downloaded ZIP
4. Restart IDE when prompted

### From Source (Development)

```bash
git clone https://github.com/loglens/loglens.git
cd loglens
./gradlew buildPlugin
# Plugin ZIP at build/distributions/tracelens-<version>.zip
```

util commands

```bash
./gradlew --refresh-dependencies runIde
```

```bash
./gradlew clean runIde
```

```bash
./gradlew runIde
```

## Usage

### Opening Logs

- **Tool Window**: View → Tool Windows → TraceLens, then click **Open Log File…** or drag a `.log`/`.out`/`.txt` file into the tool window
- **File Editor**: Double-click any `.log` or `.out` file in the Project view — it opens in the TraceLens editor
- **Context Menu**: Right-click a log file → **Open in TraceLens**
- **Docker**: Tools → TraceLens → Open Docker Log… / Open Docker Compose Log…
- **Process**: Tools → TraceLens → Open Process Log… (follow a local process by PID or command)

### Filtering

Click the level badges (TRACE, DEBUG, INFO, WARN, ERROR, FATAL) in the toolbar to toggle visibility. Multiple levels are combined with OR semantics.

Advanced filters (logger, thread, date/time range, exclusion terms) are available via the funnel icon.

### Search

Type in the search box for plain-text search. Enable **Regex** for regular expressions. Toggle **Match Case** for case-sensitive matching. Search and level filters compose with AND semantics.

### Tail Mode

Click **Follow** to tail an actively written file. New entries appear automatically. Click **Pause** to stop polling without losing position. The **New Entries** button appears when new records arrive while scrolled up — click to jump to the bottom.

### Stack-Trace Navigation

Exceptions are grouped and displayed in the **Exception** tab. Click any frame with a file icon to open that source file at the exact line. Use **Copy Exception** / **Copy Frame** to copy stack traces.

### Observability

- **Trace Timeline** tab: Related events grouped by traceId/spanId/requestId with time deltas
- **Statistics** tab: Level counts, top exceptions, top loggers, levels over time
- **Group by Trace** toggle: Collapse related events under trace headers

## Configuration

Open **Settings → Tools → TraceLens** to configure:

- Default level filter state
- Search preferences (case sensitivity, regex default)
- Tail polling interval
- Retention limits
- AI provider (Ollama, OpenAI-compatible) and model

## Screenshots

| Tool Window | File Editor | Stack Trace |
|-------------|-------------|-------------|
| ![Tool Window](docs/images/tool-window.png) | ![File Editor](docs/images/file-editor.png) | ![Stack Trace](docs/images/stack-trace.png) |

## Development

See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for local setup, build commands, and testing.

## Parser API

See [PARSER_API.md](docs/PARSER_API.md) for extending TraceLens with custom parsers.

## License

Apache-2.0 — see [LICENSE](LICENSE) for details.

## Links

- [User Guide](docs/USER_GUIDE.md)
- [Parser API](docs/PARSER_API.md)
- [Development Guide](docs/DEVELOPMENT.md)
- [Changelog](CHANGELOG.md)
- [Issues](https://github.com/loglens/loglens/issues)
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/XXXXXX-tracelens)
