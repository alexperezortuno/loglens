package io.loglens.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/** Normalizes the timestamp formats emitted by the supported log parsers. */
object Timestamps {

    private val spaceDateTime = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalEnd()
        .toFormatter()

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        val normalized = text.replace(',', '.')
        return runCatching { Instant.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(normalized, spaceDateTime)
                    .atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
    }

    fun parseBoundary(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        return runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: parse(text)
    }
}
