package io.loglens.util

import io.loglens.model.LogEntry

/** Normalizes common tracing and deployment fields across log formats. */
object ObservabilityMetadata {
    val FIELDS = listOf("traceId", "spanId", "requestId", "service", "host", "container")

    fun enrich(entry: LogEntry): LogEntry {
        val fields = entry.metadata.toMutableMap()
        for (field in FIELDS) {
            if (fields[field].isNullOrBlank()) {
                findValue(entry, field)?.let { fields[field] = it }
            }
        }
        return if (fields == entry.metadata) entry else entry.copy(metadata = fields)
    }

    private fun findValue(entry: LogEntry, field: String): String? {
        val aliases = ALIASES[field].orEmpty()
        entry.metadata.entries.firstOrNull { (key, value) ->
            aliases.any { it.equals(key, ignoreCase = true) } && value.isNotBlank()
        }?.value?.let { return it }

        val source = "${entry.message}\n${entry.raw}"
        val names = aliases.joinToString("|") { Regex.escape(it) }
        return Regex(
            "(?:^|[\\s,;\\[({])(?:$names)(?:\\s*[:=]\\s*)(?:\\\"([^\\\"]+)\\\"|([^\\s,;\\]}^)]+))",
            RegexOption.IGNORE_CASE,
        ).find(source)?.let { match -> match.groups[1]?.value ?: match.groups[2]?.value }
    }

    private val ALIASES = mapOf(
        "traceId" to listOf("traceId", "trace_id", "trace.id", "x-b3-traceid", "x-trace-id"),
        "spanId" to listOf("spanId", "span_id", "span.id", "x-b3-spanid"),
        "requestId" to listOf("requestId", "request_id", "request.id", "correlationId", "correlation_id", "x-request-id"),
        "service" to listOf("service", "serviceName", "service.name", "app", "application"),
        "host" to listOf("host", "hostname", "host.name"),
        "container" to listOf("container", "containerId", "container.id", "container_name"),
    )
}
