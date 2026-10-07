# LogLens Roadmap

## 0.1 — MVP

Goal:

> Open a log file inside IntelliJ and provide a significantly better reading and filtering experience than a plain text editor.

### Plugin foundation

- [ ] IntelliJ Platform project
- [ ] Kotlin
- [ ] Gradle Kotlin DSL
- [ ] `plugin.xml`
- [ ] LogLens Tool Window
- [ ] Basic plugin icon
- [ ] Plugin settings skeleton

### Log input

- [ ] Open local log file
- [ ] `.log` support
- [ ] `.out` support
- [ ] `.txt` support
- [ ] Buffered file reading

### Domain

- [ ] `LogEntry`
- [ ] `LogLevel`
- [ ] `ThrowableInfo`
- [ ] Common parser interface
- [ ] `ParserRegistry`

### Parsing

- [ ] Plain-text parser
- [ ] Spring Boot parser
- [ ] Timestamp detection
- [ ] Log-level detection
- [ ] Thread detection
- [ ] Logger/class detection
- [ ] Message extraction

### Viewer

- [ ] Display parsed entries
- [ ] Visually distinguish log levels
- [ ] Scroll through logs
- [ ] Preserve raw message
- [ ] Handle malformed lines gracefully

### Filters

- [ ] TRACE
- [ ] DEBUG
- [ ] INFO
- [ ] WARN
- [ ] ERROR
- [ ] FATAL

### Search

- [ ] Plain-text search
- [ ] Case-sensitive option
- [ ] Regex search
- [ ] Combine search + level filters

### Testing

- [ ] Parser unit tests
- [ ] Filter unit tests
- [ ] Malformed input tests

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

- [ ] Detect Java exceptions
- [ ] Detect `Caused by`
- [ ] Parse stack frames
- [ ] Group multiline stack traces

### Navigation

- [ ] Recognize `File.java:line`
- [ ] Find source in project
- [ ] Open referenced source
- [ ] Navigate to exact line
- [ ] Support nested modules

### UX

- [ ] Clickable stack frames
- [ ] Exception visualization
- [ ] Copy complete exception
- [ ] Copy individual stack frame

---

# 0.3 — Advanced Log Viewer

Goal:

> Make LogLens practical for daily development.

### Formats

- [x] Newline-delimited JSON logs
- [ ] Logback
- [ ] Log4j

### Large files

- [x] Incremental background parsing and paging
- [x] Lazy list rendering
- [x] Bounded in-memory cache
- [ ] Background indexing

Target:

```text
100 MB comfortably
500 MB usable
```

### Tail mode

- [ ] Follow active files
- [ ] Detect appended content
- [ ] Pause/resume
- [ ] Auto-scroll
- [ ] New-entry indicator

### Filters

- [ ] Logger filter
- [ ] Thread filter
- [ ] Date/time range
- [ ] Exclusion filters
- [ ] Saved filters

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
- [ ] Stack-trace navigation
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
