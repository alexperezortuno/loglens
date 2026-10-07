package io.loglens.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.util.AnsiCodes

/** Parser for newline-delimited JSON log records. */
class JsonLinesLogParser : LogParser {

    override fun supports(line: String): Boolean = prepared(line).startsWith('{')

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val json = runCatching { JsonParser.parseString(prepared(line)) }.getOrNull()
        val record = json?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return malformedEntry(line, lineNumber)

        return runCatching { parseObject(record, line, lineNumber) }
            .getOrElse { malformedEntry(line, lineNumber) }
    }

    private fun parseObject(record: JsonObject, raw: String, lineNumber: Int?): LogEntry {
        val message = record.stringValue("message") ?: AnsiCodes.strip(raw)
        val metadata = linkedMapOf<String, String>()
        for ((key, value) in record.entrySet()) {
            if (key in CORE_FIELDS || value.isJsonNull) continue
            val metadataKey = if (key == "line") SOURCE_LINE_KEY else key
            metadata[metadataKey] = value.asLogText()
        }

        return LogEntry(
            timestamp = record.stringValue("timestamp"),
            level = LogLevel.fromToken(record.stringValue("level")),
            message = message,
            raw = raw,
            lineNumber = lineNumber,
            metadata = metadata,
        )
    }

    private fun JsonObject.stringValue(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return value.asLogText()
    }

    private fun JsonElement.asLogText(): String =
        if (isJsonPrimitive && asJsonPrimitive.isString) asString else toString()

    private fun malformedEntry(line: String, lineNumber: Int?): LogEntry = LogEntry(
        message = AnsiCodes.strip(line),
        raw = line,
        lineNumber = lineNumber,
        level = LogLevel.UNKNOWN,
    )

    private fun prepared(line: String): String = line.trimStart { it.isWhitespace() || it == '\uFEFF' }

    private companion object {
        const val SOURCE_LINE_KEY = "sourceLine"
        val CORE_FIELDS = setOf("timestamp", "level", "message")
    }
}
