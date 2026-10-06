package io.loglens.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Centralised icon registry. The actual SVG/PNG lives in
 * `src/main/resources/icons/loglens.svg` and is referenced from
 * `plugin.xml` via the FQN of this object.
 */
object LogLensIcons {

    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/loglens.svg", javaClass)
}