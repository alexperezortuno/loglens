# LogLens Roadmap

## 0.1 — MVP

Goal:

> Open a log file inside IntelliJ and provide a significantly better reading and filtering experience than a plain text editor.

### Plugin foundation

- [x] IntelliJ Platform project
- [x] Kotlin
- [x] Gradle Kotlin DSL
- [x] `plugin.xml`
- [x] LogLens Tool Window
- [x] Basic plugin icon
- [x] Plugin settings skeleton

### Log input

- [x] Open local log file
- [x] `.log` support
- [x] `.out` support
- [x] `.txt` support
- [x] Bounded buffered file reading

### Domain

- [x] `LogEntry`
- [x] `LogLevel`
- [x] `ThrowableInfo`
- [x] Common parser interface
- [x] `ParserRegistry`

### Parsing

- [x] Plain-text parser
- [x] Spring Boot parser
- [x] Timestamp detection
- [x] Log-level detection
- [x] Thread detection
- [x] Logger/class detection
- [x] Message extraction

### Viewer

- [x] Display parsed entries
- [x] Visually distinguish log levels
- [x] Scroll through logs
- [x] Preserve raw message
- [x] Handle malformed lines gracefully

### Filters

- [x] TRACE
- [x] DEBUG
- [x] INFO
- [x] WARN
- [x] ERROR
- [x] FATAL

### Search

- [x] Plain-text search
- [x] Case-sensitive option
- [x] Regex search
- [x] Combine search + level filters

### Testing

- [x] Parser unit tests
- [x] Filter unit tests
- [x] Malformed input tests

### 0.1 completion criteria

Given:

```text
application.log
```

the user can open LogLens and:

```text
view logs
search logs
filter by level
identify ERROR/WARN records quickly
```

without external services.

---

# 0.2 — Developer Navigation

Goal:

> Connect logs with source code.

### Stack traces

- [x] Detect Java exceptions
- [x] Detect `Caused by`
- [x] Parse stack frames
- [x] Group multiline stack traces across bounded pages

### Navigation

- [x] Recognize `File.java:line`
- [x] Find source in project
- [x] Open referenced source
- [x] Navigate to exact line
- [x] Support nested modules

### UX

- [x] Clickable stack frames
- [x] Exception visualization
- [x] Copy complete exception
- [x] Copy individual stack frame

---

# 0.3 — Advanced Log Viewer

Goal:

> Make LogLens practical for daily development.

### Formats

- [x] Newline-delimited JSON logs
- [x] Logback
- [x] Log4j

### Large files

- [x] Incremental background parsing and paging
- [x] Lazy list rendering
- [x] Bounded in-memory cache
- [x] Background indexing

Target:

```text
100 MB comfortably
500 MB usable
```

### Tail mode

- [x] Follow active files
- [x] Detect appended content
- [x] Pause/resume
- [x] Auto-scroll
- [x] New-entry indicator

### Filters

- [x] Logger filter
- [x] Thread filter
- [x] Date/time range
- [x] Exclusion filters
- [x] Saved filters

---

# 0.4 — Observability

Goal:

> Move from viewing individual lines to understanding application behavior.

### Structured metadata

- [ ] traceId
- [ ] spanId
- [ ] requestId
- [ ] service
- [ ] host
- [ ] container

### Correlation

Example:

```text
traceId = f84a91

API Gateway
    ↓
PaymentService
    ↓
Database
```

- [ ] Group by trace
- [ ] Search by correlation ID
- [ ] Visualize related events
- [ ] Time delta between events

### Statistics

- [ ] Log levels over time
- [ ] Error frequency
- [ ] Most frequent exceptions
- [ ] Most active loggers

---

# 0.5 — Multiple Sources

Goal:

> Analyze logs coming from different local sources.

Potential sources:

- [ ] Local files
- [ ] Docker containers
- [ ] Docker Compose
- [ ] Local processes

Future candidates:

- Kubernetes
- SSH
- systemd/journald

Remote integrations should remain modular.

---

# 0.6 — Local AI

Goal:

> Add optional AI-assisted log analysis without making AI a core dependency.

Potential providers:

```text
Ollama
llama.cpp
OpenAI-compatible local endpoint
```

Possible actions:

```text
Explain error
Summarize exception
Find probable cause
Explain stack trace
Find related events
Suggest debugging steps
```

Example:

```text
ERROR PaymentService
SocketTimeoutException
        ↓
Explain error
        ↓
Local LLM
        ↓
Possible cause:
PaymentService waited longer than the configured
timeout for the remote payment provider.
```

AI must be:

- optional
- disabled by default
- provider-independent
- isolated from core parsing

---

# 1.0 — Stable Release

Requirements:

- [ ] Stable parser API
- [ ] Reliable large-file support
- [x] Stack-trace navigation
- [ ] Search and advanced filtering
- [ ] Tail mode
- [ ] Spring Boot
- [ ] Logback
- [ ] Log4j
- [ ] JSON logs
- [ ] Documentation
- [ ] Automated tests
- [ ] JetBrains Marketplace publication pipeline

Core functionality must remain free and open source.
