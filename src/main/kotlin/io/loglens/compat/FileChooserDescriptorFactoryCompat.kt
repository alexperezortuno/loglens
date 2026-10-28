package io.loglens.compat

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory

/**
 * Compatibility layer for [FileChooserDescriptorFactory] to avoid deprecated API usage
 * in bytecode while maintaining compatibility with IntelliJ IDEA 2024.3+.
 */
object FileChooserDescriptorFactoryCompat {

    private const val SINGLE_FILE_METHOD = "singleFile"
    private const val CREATE_SINGLE_FILE_DESCRIPTOR_METHOD = "createSingleFileDescriptor"

    /**
     * Creates a single file descriptor using the modern API when available
     * (IntelliJ IDEA 2025.2+), falling back to the legacy API for older versions.
     * Both paths use reflection to avoid deprecated method references in bytecode.
     */
    fun createSingleFileDescriptor(): FileChooserDescriptor {
        val factoryClass = FileChooserDescriptorFactory::class.java
        return try {
            // Try modern API (added in 2025.2 / IC-252)
            val method = factoryClass.getMethod(SINGLE_FILE_METHOD)
            method.invoke(null) as FileChooserDescriptor
        } catch (_: NoSuchMethodException) {
            // Fallback to legacy API (deprecated in 2025.2, but only option for 2024.3)
            // Use reflection to avoid deprecated method in bytecode
            val method = factoryClass.getMethod(CREATE_SINGLE_FILE_DESCRIPTOR_METHOD)
            method.invoke(null) as FileChooserDescriptor
        }
    }
}