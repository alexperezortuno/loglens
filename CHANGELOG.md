# Changelog

All notable changes to TraceLens are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-10-27

### Fixed
- **Responsive Layout**: Fixed resizing of the TraceLens windows; the log viewer now uses a splitter for dynamic resizing of the details panel

### Changed
- Toggleable toolbar and reset-layout action in the tool window
- Details panel visibility toggle in the log viewer

## [1.0.0] - 2026-10-26

### Added
- **Parser System**: Extensible `ParserRegistry` with per-file parser affinity
- **Built-in Parsers**: Plain text, Spring Boot, Logback, Log4j, JSON Lines, compact timestamp-level
- **Log Entry Model**: `LogEntry` with timestamp, level, thread, logger, message, raw, line number, throwable, metadata
- **Log Levels**: TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN with framework-specific synonym mapping
- **ANSI Support**: SGR color/background/style parsing and rendering in viewer
- **Stack Trace Handling**: Detection, assembly across page boundaries, frame parsing with causes/suppressed
- **Source Navigation**: Clickable stack frames opening project sources at exact line
- **Tool Window**: Dedicated TraceLens tool window with compact record feed and detail pane
- **File Editor**: ANSI-aware editor for `.log` and `.out` files on double-click
- **Level Filtering**: Independent toggle for each level (OR semantics)
- **Search**: Plain text, case-sensitive, and regex modes; composable with filters
- **Tail Mode**: Follow active files with pause/resume, auto-scroll, new-entry indicator, rotation detection
- **Large File Support**: Bounded paging (10 MiB/10k initial, 4 MiB/5k subsequent), 20k/32 MiB retention
- **Advanced Filters**: Logger, thread, date/time range, exclusion terms
- **Observability**: Trace grouping by traceId/spanId/requestId, timeline view, log statistics
- **Multiple Sources**: Local files, Docker containers, Docker Compose services, local processes
- **Local AI (Optional)**: Explain errors, summarize exceptions, find causes via Ollama/OpenAI-compatible
- **Settings**: Persistent filter/search preferences, tail interval, retention limits, AI configuration
- **Parser API**: Versioned with `@StableApi`/`@ExperimentalApi`/`@DeprecatedApi` annotations
- **Plugin Verification**: Compatible with IC-2024.3 through IC-2025.2 (since-build 243, until-build 253.*)
- **Documentation**: README, User Guide, Parser API, Development Guide

### Changed
- N/A (initial release)

### Deprecated
- N/A (initial release)

### Removed
- N/A (initial release)

### Fixed
- N/A (initial release)

### Security
- Fully offline-first: no network calls unless AI explicitly enabled and configured
- No log data uploaded or collected
- No telemetry or analytics

## [0.1.0] - 2026-09-15 (Internal MVP)

### Added
- IntelliJ Platform project foundation
- TraceLens Tool Window with level filter toggles
- Plain-text, Spring Boot, timestamp-level, JSON Lines parsers
- ANSI SGR color rendering
- Compact log feed with message/raw details
- Stack trace grouping with source navigation
- Tail mode with pause/resume/auto-scroll
- File editor for `.log`/`.out`
- Plain-text and regex search
- Buffered file reader for `.log`/`.out`/`.txt`
- Unit tests for parsers, filters, search

---

## Release Notes Format

Each release includes:

- **Added** — New features
- **Changed** — Changes to existing functionality
- **Deprecated** — Soon-to-be-removed features
- **Removed** — Removed features
- **Fixed** — Bug fixes
- **Security** — Security-related changes

## Versioning

- **MAJOR** — Incompatible API changes
- **MINOR** — Backwards-compatible additions
- **PATCH** — Backwards-compatible bug fixes

Parser API version tracked separately in `ParserApi` object.

## Links

- [GitHub Releases](https://github.com/loglens/loglens/releases)
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/XXXXXX-tracelens)
- [Issues](https://github.com/loglens/loglens/issues)