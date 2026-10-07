package io.loglens.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Placeholder settings panel. The 0.1 MVP persists preferences automatically
 * via [LogLensSettings]; this configurable exists so the entry point under
 * Settings → Tools → LogLens is reachable and discoverable.
 *
 * The actual checkbox grid will move here as we add more knobs in 0.2+ but
 * for now the panel just shows a friendly explanation to make the menu
 * entry visible and avoid surprising users with an empty screen.
 */
class LogLensSettingsConfigurable : Configurable {

    override fun getDisplayName(): String = "LogLens"

    override fun createComponent(): JComponent = JPanel().apply {
        add(
            javax.swing.JLabel(
                "<html><b>LogLens 0.1 MVP</b><br>" +
                    "Preferences are stored automatically.<br>" +
                    "More options will appear in upcoming releases.</html>",
            ),
        )
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        Messages.showInfoMessage(
            "LogLens preferences are saved automatically.",
            "LogLens",
        )
    }
}