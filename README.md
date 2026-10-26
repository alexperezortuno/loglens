# LogLens

A fast, offline-first log viewer for IntelliJ-based IDEs.

Open `.log`, `.out`, and `.txt` files in a dedicated ANSI-aware viewer with level filtering, full-text/regex search, tail mode, and stack-trace navigation — all without leaving the IDE.

![LogLens Tool Window](docs/images/tool-window.png)

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
2. Search for **LogLens**
3. Click **Install**
4. Restart IDE when prompted

### From Disk

1. Download the latest `loglens-<version>.zip` from [GitHub Releases](https://github.com/loglens/loglens/releases)
2. Open **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the downloaded ZIP
4. Restart IDE when prompted

### From Source (Development)

```bash
git clone https://github.com/loglens/loglens.git
cd loglens
./gradlew buildPlugin
# Plugin ZIP at build/distributions/loglens-<version>.zip
```

## Usage

### Opening Logs

- **Tool Window**: View → Tool Windows → LogLens, then click **Open Log File…** or drag a `.log`/`.out`/`.txt` file into the tool window
- **File Editor**: Double-click any `.log` or `.out` file in the Project view — it opens in the LogLens editor
- **Context Menu**: Right-click a log file → **Open in LogLens**
- **Docker**: Tools → LogLens → Open Docker Log… / Open Docker Compose Log…
- **Process**: Tools → LogLens → Open Process Log… (follow a local process by PID or command)

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

Open **Settings → Tools → LogLens** to configure:

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

See [PARSER_API.md](docs/PARSER_API.md) for extending LogLens with custom parsers.

## License

Apache-2.0 — see [LICENSE](LICENSE) for details.

## Links

- [User Guide](docs/USER_GUIDE.md)
- [Parser API](docs/PARSER_API.md)
- [Development Guide](docs/DEVELOPMENT.md)
- [Changelog](CHANGELOG.md)
- [Issues](https://github.com/loglens/loglens/issues)
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/XXXXXX-loglens)