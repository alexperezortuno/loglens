# LogLens — Technical Specification

## 1. Purpose

LogLens provides a dedicated log exploration experience inside IntelliJ-based IDEs.

The plugin must allow developers to inspect application logs without leaving the IDE.

The primary design principles are:

1. Fast
2. Offline-first
3. Extensible
4. Low memory usage
5. Native IntelliJ experience
6. No vendor lock-in

---

# 2. Core domain

## LogEntry

Every recognized log record should eventually be represented by a common model.

```kotlin
data class LogEntry(
    val timestamp: String?,
    val level: LogLevel?,
    val thread: String?,
    val logger: String?,
    val message: String,
    val raw: String,
    val throwable: ThrowableInfo? = null
)
```

The exact representation may evolve as implementation requirements become clearer.

## LogLevel

```kotlin
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
    UNKNOWN
}
```

The parser must not assume that every format supports every level.

---

# 3. Parser system

Log parsing must be extensible.

Base abstraction:

```kotlin
interface LogParser {

    fun supports(line: String): Boolean

    fun parse(line: String): LogEntry?
}
```

Parser implementations must remain independent from the UI.

Initial parsers:

```text
PlainTextLogParser
SpringBootLogParser
```

Planned:

```text
LogbackLogParser
Log4jLogParser
JsonLogParser
DockerLogParser
NginxLogParser
```

A parser registry should be responsible for selecting the appropriate parser.

Conceptually:

```text
line
 │
 ▼
ParserRegistry
 │
 ├─ SpringBootLogParser
 ├─ LogbackLogParser
 ├─ JsonLogParser
 └─ PlainTextLogParser
```

`PlainTextLogParser` should act as the fallback.

---

# 4. Log viewer

Logs should be displayed in a dedicated IntelliJ Tool Window.

Initial layout:

```text
┌──────────────────────────────────────────────────────────┐
│ LogLens                                                  │
├──────────────────────────────────────────────────────────┤
│ Search: [_________________________]  Regex [ ]            │
│                                                          │
│ [TRACE] [DEBUG] [INFO] [WARN] [ERROR] [FATAL]            │
├──────────────────────────────────────────────────────────┤
│ 10:30:21 INFO  Application started                       │
│ 10:30:23 DEBUG Loading configuration                     │
│ 10:30:25 WARN  Slow database query                       │
│ 10:30:30 ERROR Payment processing failed                 │
│                                                          │
│ java.net.SocketTimeoutException                          │
│   at PaymentClient.send(PaymentClient.java:87)           │
└──────────────────────────────────────────────────────────┘
```

---

# 5. Filtering

Users must be able to enable and disable individual log levels.

Example:

```text
TRACE  OFF
DEBUG  OFF
INFO   ON
WARN   ON
ERROR  ON
FATAL  ON
```

Filtering must not modify the source file.

Multiple active filters are combined using OR semantics.

---

# 6. Search

Search must support:

- Plain text
- Case-sensitive mode
- Case-insensitive mode
- Regular expressions

Search and level filtering must be composable.

Example:

```text
level = ERROR
search = PaymentService
```

Only matching ERROR records should be displayed.

---

# 7. Stack traces

Java stack traces should be recognized.

Example:

```text
java.lang.IllegalStateException: Invalid state
    at com.example.PaymentService.process(PaymentService.java:143)
    at com.example.PaymentController.pay(PaymentController.java:72)
```

The plugin should extract:

```text
class
method
filename
line
```

Example:

```text
com.example.PaymentService
process
PaymentService.java
143
```

---

# 8. Source navigation

When the referenced source belongs to the current IntelliJ project:

```text
PaymentService.java:143
```

should become navigable.

Activating it should open:

```text
PaymentService.java
```

and move the editor caret to line:

```text
143
```

Navigation should use IntelliJ APIs rather than manually searching the filesystem whenever possible.

---

# 9. Performance

The plugin must avoid loading arbitrarily large log files into UI components in a single operation.

Target files:

```text
10 MB     normal
100 MB    expected
500 MB    supported eventually
1+ GB     future optimization target
```

Large-file handling should eventually use techniques such as:

- buffered reading
- incremental parsing
- lazy rendering
- pagination/windowing
- bounded caches

The UI thread must never perform expensive parsing.

The current loader starts at the beginning of the file and reads asynchronously in
bounded pages: up to 10 MiB or 10,000 records initially, then 4 MiB or 5,000
records per page. It retains at most 20,000 records or an estimated 32 MiB of
record text, and caps an individual record at 1 MiB. An oversized record is
shown as a truncated preview while its remainder is skipped in cancellable
chunks. Search and filtering apply to the records currently loaded.

---

# 10. Tail mode

Future versions should support following files being actively written.

Equivalent concept:

```bash
tail -f application.log
```

The viewer should append new records without re-reading the complete file.

---

# 11. Structured logs

JSON log support is planned.

Example:

```json
{
  "timestamp": "2026-10-01T10:30:25Z",
  "level": "ERROR",
  "service": "payments",
  "traceId": "abc123",
  "message": "Payment failed"
}
```

Known fields should become searchable/filterable attributes.

Unknown fields must not cause parsing failures.

---

# 12. Security and privacy

Core LogLens functionality must be local.

The plugin must not:

- upload logs
- collect log contents
- require accounts
- require API keys
- silently contact external services

Any future external integration must require explicit configuration and user action.

---

# 13. Extensibility

Features should depend on abstractions instead of concrete parser implementations.

Avoid:

```kotlin
if (springLog) {
    ...
} else if (jsonLog) {
    ...
}
```

Prefer:

```text
ParserRegistry
      │
      ▼
  LogParser
      │
 ┌────┼────┐
 ▼    ▼    ▼
Spring JSON Custom
```

This rule applies throughout the project.

---

# 14. Testing

Parser behavior must be covered by unit tests.

Every parser should include:

- valid input
- malformed input
- missing fields
- unknown level
- multiline input where applicable
- edge cases

Stack-trace parsing must also have dedicated tests.

UI tests should be introduced only where they provide meaningful value.
