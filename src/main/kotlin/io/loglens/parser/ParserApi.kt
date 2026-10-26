package io.loglens.parser

/**
 * Parser API version — incremented when the public contract changes in a
 * backwards-incompatible way.
 *
 * Current stable API: 1.0
 */
object ParserApi {
    const val MAJOR = 1
    const val MINOR = 0
    const val PATCH = 0

    val version = "$MAJOR.$MINOR.$PATCH"
}

/**
 * Marker annotation for parser API that is considered stable and will not
 * change in a backwards-incompatible way within the same major version.
 */
@kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
annotation class StableApi

/**
 * Marker annotation for parser API that is experimental and may change or be
 * removed in future minor releases.
 */
@kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
annotation class ExperimentalApi

/**
 * Marker annotation for parser API that is deprecated and will be removed in
 * a future major release.
 */
@kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
annotation class DeprecatedApi(
    val since: String,
    val replacement: String = "",
)