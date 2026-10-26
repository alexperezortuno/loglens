# LogLens Parser API

## Overview

LogLens uses a parser registry to route log lines to format-specific parsers. The API is designed for extensibility — plugins and applications can register custom parsers without modifying core code.

## Versioning

```kotlin
object ParserApi {
    const val MAJOR = 1
    const val MINOR = 0
    const val PATCH = 0
    val version = "$MAJOR.$MINOR.$PATCH" // "1.0.0"
}
```

- **MAJOR** — Incremented for backwards-incompatible changes
- **MINOR** — Incremented for backwards-compatible additions
- **PATCH** — Incremented for bug fixes

Current stable API: **1.0.0**

## Stability Annotations

| Annotation | Meaning |
|------------|---------|
| `@StableApi` | Will not change in a backwards-incompatible way within the same major version |
| `@ExperimentalApi` | May change or be removed in future minor releases |
| `@DeprecatedApi(since, replacement)` | Deprecated; will be removed in a future major release |

All public parser API surfaces are annotated with `@StableApi` unless otherwise noted.

## Core Interfaces

### LogParser

```kotlin
@StableApi
interface LogParser {

    /**
     * Returns true if this parser can handle the given line.
     * Called on ANSI-stripped, normalized input.
     */
    fun supports(line: String): Boolean

    /**
     * Parses a single log line into a [LogEntry].
     * Returns null if the line cannot be parsed (should not happen if [supports] returned true).
     *
     * @param line The normalized (ANSI-stripped) line to parse
     * @param lineNumber One-based line number in the source file, or null if unknown
     */
    fun parse(line: String, lineNumber: Int? = null): LogEntry?
}
```

### LogEntry

```kotlin
@StableApi
data class LogEntry(
    val timestamp: String? = null,
    val level: LogLevel = LogLevel.UNKNOWN,
    val thread: String? = null,
    val logger: String? = null,
    val message: String,
    val raw: String,
    val lineNumber: Int? = null,
    val throwable: ThrowableInfo? = null,
    val metadata: Map<String, String> = emptyMap(),
    val isTruncated: Boolean = false,
) {
    fun identity(): String = "${lineNumber ?: 0}#${raw.hashCode()}"
}
```

### LogLevel

```kotlin
@StableApi
enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN;

    fun display(): String = name

    companion object {
        fun fromToken(token: String?): LogLevel
        val FILTERABLE: List<LogLevel> = listOf(TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN)
    }
}
```

### ThrowableInfo

```kotlin
@StableApi
data class ThrowableInfo(
    val className: String?,
    val message: String?,
    val frames: List<StackFrame>,
    val causes: List<ThrowableInfo> = emptyList(),
    val suppressed: List<ThrowableInfo> = emptyList(),
    val omittedFrameCount: Int = 0,
    val isTruncated: Boolean = false,
    val raw: String? = null,
)
```

### StackFrame

```kotlin
@StableApi
data class StackFrame(
    val declaringClass: String?,
    val methodName: String?,
    val fileName: String?,
    val lineNumber: Int?,
    val isNative: Boolean = false,
)
```

### ObservabilityMetadata

```kotlin
@StableApi
object ObservabilityMetadata {
    /**
     * Enriches a [LogEntry] with canonical observability fields extracted from
     * the message and metadata. Known aliases are normalized to standard keys:
     * traceId, spanId, requestId, service, host, container.
     */
    fun enrich(entry: LogEntry): LogEntry
}
```

### ParserRegistry

```kotlin
@StableApi
class ParserRegistry(private val parsers: List<LogParser>) {

    init {
        require(parsers.isNotEmpty()) { "at least one parser is required" }
        require(parsers.last() is PlainTextLogParser) {
            "the last parser in the registry must be the PlainTextLogParser fallback"
        }
    }

    /**
     * Selects the appropriate parser for [line].
     * The first parser whose [supports] returns true wins.
     * Per-file affinity: once a non-fallback parser wins, it is preferred for
     * subsequent lines in the same file.
     */
    @StableApi
    fun selectParser(line: String): LogParser

    /**
     * Convenience: selects parser and returns the parsed [LogEntry].
     * Normalizes ANSI input for parsing, restores original raw line,
     * and enriches with observability metadata.
     */
    @StableApi
    fun parse(line: String, lineNumber: Int? = null): LogEntry

    /** Clears per-file parser affinity. Call when switching files. */
    @StableApi
    fun reset()

    companion object {
        /** Default registry with built-in parsers in specificity order. */
        @StableApi
        fun defaults(): ParserRegistry = ParserRegistry(
            listOf(
                JsonLinesLogParser(),
                SpringBootLogParser(),
                LogbackLogParser(),
                Log4jLogParser(),
                TimestampLevelLogParser(),
                PlainTextLogParser(),
            ),
        )
    }
}
```

## Built-in Parsers

| Parser | Format | Key Patterns |
|--------|--------|--------------|
| `JsonLinesLogParser` | NDJSON | `{"timestamp": "...", "level": "...", ...}` |
| `SpringBootLogParser` | Spring Boot | `2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : msg` |
| `LogbackLogParser` | Logback classic | `2026-10-01 10:30:25,123 [main] INFO  c.e.App - msg` |
| `Log4jLogParser` | Log4j 1.x/2.x | `10:30:25.123 [main] INFO  c.e.App - msg` |
| `TimestampLevelLogParser` | Compact bracket | `21:00:27.261 [ERROR] Handshake failed` |
| `PlainTextLogParser` | Fallback | Any non-empty line |

## Extending with Custom Parsers

### 1. Implement `LogParser`

```kotlin
import io.loglens.parser.LogParser
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.parser.StableApi

@StableApi
class NginxLogParser : LogParser {

    private val pattern = """^(\S+) - - \[(.*?)\] "(\S+) (\S+) HTTP/\d\.\d" (\d+) (\d+)""".toRegex()

    override fun supports(line: String): Boolean {
        return pattern.matches(line)
    }

    override fun parse(line: String, lineNumber: Int? = null): LogEntry? {
        val match = pattern.matchEntire(line) ?: return null
        val (remote, timestamp, method, url, status, bytes) = match.destructured
        return LogEntry(
            timestamp = timestamp,
            level = when { status.startsWith("5") -> LogLevel.ERROR; status.startsWith("4") -> LogLevel.WARN; else -> LogLevel.INFO },
            logger = "nginx",
            message = "$method $url $status $bytes",
            raw = line,
            lineNumber = lineNumber,
            metadata = mapOf(
                "remote" to remote,
                "method" to method,
                "url" to url,
                "status" to status,
                "bytes" to bytes,
            ),
        )
    }
}
```

### 2. Register with ParserRegistry

```kotlin
import io.loglens.parser.ParserRegistry
import io.loglens.parser.PlainTextLogParser

val customRegistry = ParserRegistry(
    listOf(
        NginxLogParser(),           // Most specific first
        JsonLinesLogParser(),
        SpringBootLogParser(),
        LogbackLogParser(),
        Log4jLogParser(),
        TimestampLevelLogParser(),
        PlainTextLogParser(),       // Fallback MUST be last
    ),
)
```

### 3. Use in Your Code

```kotlin
val registry = ParserRegistry.defaults() // or customRegistry
registry.reset() // Call when switching files

val entry = registry.parse("2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : started")
println(entry.level) // INFO
```

## Compatibility Guarantees

1. **Parser Interface Stability** — `LogParser`, `LogEntry`, `LogLevel`, `ThrowableInfo`, `StackFrame` are `@StableApi` and will not change incompatibly within major version 1.x.

2. **ParserRegistry Behavior** — Per-file affinity, ANSI normalization, observability enrichment, and fallback semantics are guaranteed.

3. **Built-in Parsers** — Existing parsers will not be removed or have their `supports`/`parse` contracts changed in 1.x.

4. **Extension Safety** — Adding a parser to the registry (before the fallback) cannot break parsing of formats handled by existing parsers.

5. **Metadata Preservation** — `LogEntry.raw` always preserves the original source line. `metadata` map is extensible.

## Version Compatibility Matrix

| LogLens Version | ParserApi Version | Compatible With |
|-----------------|-------------------|-----------------|
| 1.0.x | 1.0 | 1.0.x |
| 1.1.x | 1.0 | 1.0.x, 1.1.x |
| 2.0.x | 2.0 | 2.0.x only (breaking) |

## Migration Guide (Future)

When upgrading to a new major version:
1. Check `@DeprecatedApi` annotations for replacements
2. Recompile custom parsers against the new API
3. Test with your log formats
4. Update `ParserApi.MAJOR` reference if accessing programmatically

## Testing Custom Parsers

```kotlin
class NginxLogParserTest {

    private val parser = NginxLogParser()

    @Test
    fun `parses common log format`() {
        val line = """192.168.1.1 - - [01/Oct/2026:10:30:25 +0000] "GET /api/health HTTP/1.1" 200 512"""
        val entry = parser.parse(line)
        
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("nginx", entry.logger)
        assertEquals("GET /api/health 200 512", entry.message)
        assertEquals("192.168.1.1", entry.metadata["remote"])
    }

    @Test
    fun `rejects non-nginx lines`() {
        assertFalse(parser.supports("2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : msg"))
    }
}
```

## Best Practices

1. **Specificity First** — Register custom parsers before generic ones in the registry
2. **Fail Fast** — `supports()` should be cheap; return `false` quickly for non-matching lines
3. **Preserve Raw** — Always set `LogEntry.raw` to the original unmodified line
4. **Use Metadata** — Put structured fields (JSON keys, custom attributes) in `metadata` map
5. **Handle Truncation** — Set `isTruncated = true` if the source record exceeded reader limits
6. **Test Edge Cases** — Malformed lines, missing fields, unknown levels, multiline records