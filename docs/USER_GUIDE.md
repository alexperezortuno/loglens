# LogLens User Guide

## Opening Log Files

### Tool Window

1. Open **View → Tool Windows → LogLens** (or press the LogLens icon in the bottom tool window bar)
2. Click **Open Log File…** in the toolbar or press `Ctrl+Shift+O` (Windows/Linux) / `Cmd+Shift+O` (macOS)
3. Select a `.log`, `.out`, or `.txt` file

### File Editor

Double-click any `.log` or `.out` file in the Project tool window — it opens in the LogLens editor instead of the plain text editor.

### Context Menu

Right-click a log file in the Project view → **Open in LogLens**.

### Docker Integration

- **Tools → LogLens → Open Docker Log…** — Select a running container
- **Tools → LogLens → Open Docker Compose Log…** — Select a service from a `docker-compose.yml`

### Process Integration

- **Tools → LogLens → Open Process Log…** — Enter a PID or command to follow a local process

## The Viewer Layout

```
┌─────────────────────────────────────────────────────────────┐
│ LogLens                                          [Toolbar]  │
├─────────────────────────────────────────────────────────────┤
│ Search: [_______________]  [Regex] [Match Case]             │
│ [TRACE] [DEBUG] [INFO] [WARN] [ERROR] [FATAL] [UNKNOWN]     │
├─────────────────────────────────────────────────────────────┤
│ 10:30:21  INFO   Application started                        │
│ 10:30:23  DEBUG  Loading configuration                      │
│ 10:30:25  WARN   Slow database query                        │
│ 10:30:30  ERROR  Payment failed  ▼                          │
│    java.lang.SocketTimeoutException                         │
│      at PaymentClient.send(PaymentClient.java:87)           │
├─────────────────────────────────────────────────────────────┤
│ Message | Raw Record | Exception | Trace Timeline | Stats   │
└─────────────────────────────────────────────────────────────┘
```

**Left pane** — Compact record feed with timestamp, level badge, and message preview. ANSI colors rendered inline.

**Right pane** — Detail tabs for the selected record:
- **Message** — Formatted message
- **Raw Record** — Original unparsed line (pretty-printed for JSON)
- **Exception** — Parsed stack frames with navigation
- **Trace Timeline** — Related events by correlation ID
- **Statistics** — Aggregated stats for loaded records

## Level Filtering

Click level badges in the toolbar to toggle visibility:
- **TRACE** — Verbose tracing
- **DEBUG** — Debug information
- **INFO** — General information
- **WARN** — Warning conditions
- **ERROR** — Error conditions
- **FATAL** — Fatal errors
- **UNKNOWN** — Unrecognized levels

Multiple active levels combine with **OR** semantics (show records matching ANY selected level).

Default: TRACE and DEBUG hidden.

## Search

| Mode | Description |
|------|-------------|
| Plain text | Case-insensitive substring match (default) |
| Match Case | Case-sensitive substring match |
| Regex | Regular expression (Java `Pattern` syntax) |

Search matches against: message, logger, thread, raw record, and structured metadata (JSON fields).

Search and level filters combine with **AND** semantics.

## Tail Mode

Click **Follow** to poll the file for new content:
- Polls from current byte offset every 2 seconds (configurable)
- Ignores incomplete final record until newline arrives
- Detects file rotation/truncation and restarts from beginning
- **Pause** — Stop polling without losing position
- **Auto-scroll** — When at bottom, new entries scroll into view
- **New Entries** button — Appears when scrolled up; click to jump to bottom

Retention limit: 20,000 records or ~32 MiB (oldest evicted first).

## Stack-Trace Navigation

When an exception is detected:

1. Select the record in the feed
2. Open the **Exception** tab
3. Frames with a file icon (📄) are clickable
4. Click to open the source file at the exact line
5. Use **Copy Exception** for the full trace, **Copy Frame** for a single frame

Supported formats:
- Java/Kotlin stack traces
- `Caused by:` chains
- `Suppressed:` exceptions
- Omitted frame markers (`... N more`)

## Advanced Filters

Click the funnel icon (🔍) for advanced filters:

| Filter | Description |
|--------|-------------|
| Logger | Include/exclude by logger name (substring) |
| Thread | Include/exclude by thread name (substring) |
| Date Range | From/To timestamps (ISO or log formats) |
| Exclusions | Terms that veto a record even if other filters match |

Exclusion terms apply **after** include filters — any match vetoes the record.

Time-only timestamps (e.g., `10:30:25`) do not match date ranges.

## Observability Features

### Trace Grouping

Enable **Group by Trace** (toolbar) to collapse records sharing a `traceId`, `spanId`, or `requestId` under a trace header. Expand to see individual events.

### Trace Timeline

The **Trace Timeline** tab shows related events chronologically with time deltas from the first event in the trace.

### Statistics

The **Statistics** tab shows:
- Total records, error count, exception count, trace count
- Level distribution
- Top 10 exceptions by frequency
- Top 10 loggers by volume
- Levels over time (UTC minute buckets)

## Local AI (Optional)

Enable in **Settings → Tools → LogLens → AI**:

| Provider | Configuration |
|----------|---------------|
| Ollama | Base URL (default `http://localhost:11434`), Model |
| OpenAI-compatible | Base URL, API Key, Model |

Actions (right-click a record with an exception → **LogLens AI**):
- **Explain Error** — Plain-language explanation
- **Summarize Exception** — Concise summary
- **Find Probable Cause** — Likely root cause
- **Explain Stack Trace** — Frame-by-frame analysis
- **Find Related Events** — Correlated log entries
- **Suggest Debugging Steps** — Actionable next steps

AI is **disabled by default** and runs locally — no data leaves your machine.

## Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Open Log File | `Ctrl+Shift+O` | `Cmd+Shift+O` |
| Toggle Tool Window | `Alt+Shift+L` | `Opt+Shift+L` |
| Search | `Ctrl+F` | `Cmd+F` |
| Next Match | `F3` | `F3` |
| Previous Match | `Shift+F3` | `Shift+F3` |
| Follow/Pause | `Alt+F` | `Opt+F` |
| Load More | `Ctrl+L` | `Cmd+L` |

## Large File Handling

LogLens uses bounded paging to handle large files:

| Phase | Byte Limit | Record Limit |
|-------|------------|--------------|
| Initial load | 10 MiB | 10,000 |
| Subsequent pages | 4 MiB | 5,000 |
| Retention | ~32 MiB | 20,000 |
| Max single record | 1 MiB | — |

Oversized records show a truncated preview; the remainder is skipped in cancellable chunks.

## Settings

**Settings → Tools → LogLens**

| Setting | Description |
|---------|-------------|
| Default Level Filter | Which levels are enabled on first open |
| Search Case Sensitivity | Default case-sensitive mode |
| Search Regex Default | Default regex mode |
| Tail Poll Interval | Milliseconds between tail polls (default 2000) |
| Retention Limit | Max records/bytes to keep in memory |
| AI Provider | Ollama / OpenAI-compatible / Disabled |
| AI Model | Model name for selected provider |
| AI Base URL | Endpoint for AI provider |

## Troubleshooting

| Issue | Resolution |
|-------|------------|
| File won't open | Ensure extension is `.log`, `.out`, or `.txt` |
| No ANSI colors | Verify file contains SGR escape sequences (`\u001B[...m`) |
| Stack trace not clickable | Source file must be in the current IntelliJ project |
| Tail not updating | Check file permissions; ensure file is growing |
| AI not working | Verify provider URL/model; check Settings → Tools → LogLens → AI |
| High memory usage | Reduce retention limit in Settings |

## File Associations

LogLens registers for:
- `*.log`
- `*.out`
- `*.txt` (opens in tool window, not editor)

To change associations: **Settings → Editor → File Types → LogLens Log File**.