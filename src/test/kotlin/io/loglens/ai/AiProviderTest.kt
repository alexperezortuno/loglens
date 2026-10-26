package io.loglens.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiProviderTest {

    @Test
    fun `ollama provider has defaults`() {
        val provider = AiProvider.Ollama()
        assertEquals("ollama", provider.id)
        assertEquals("Ollama", provider.displayName)
        assertEquals("http://localhost:11434", provider.baseUrl)
        assertEquals("llama3.1", provider.model)
    }

    @Test
    fun `openai compatible provider requires base url and model`() {
        AiProvider.OpenAiCompatible(baseUrl = "http://localhost:8080", model = "llama3.1")
        // Should not throw
    }

    @Test
    fun `openai compatible provider rejects empty base url`() {
        assertTrue(
            try {
                AiProvider.OpenAiCompatible(baseUrl = "", model = "llama3.1")
                false
            } catch (e: IllegalArgumentException) {
                true
            }
        )
    }

    @Test
    fun `openai compatible provider rejects empty model`() {
        assertTrue(
            try {
                AiProvider.OpenAiCompatible(baseUrl = "http://localhost:8080", model = "")
                false
            } catch (e: IllegalArgumentException) {
                true
            }
        )
    }

    @Test
    fun `ai actions have labels and prompts`() {
        AiAction.all.forEach { action ->
            assertTrue(action.label.isNotBlank())
            assertTrue(action.promptTemplate.isNotBlank())
            assertTrue(action.promptTemplate.contains("{entry}"))
        }
    }

    @Test
    fun `ai request and response classes`() {
        val provider = AiProvider.Ollama()
        val request = AiRequest(
            action = AiAction.EXPLAIN_ERROR,
            entry = "test entry",
            context = "test context",
            provider = provider,
        )
        assertEquals(AiAction.EXPLAIN_ERROR, request.action)
        assertEquals("test entry", request.entry)
        assertEquals("test context", request.context)
        assertEquals(provider, request.provider)

        val success = AiResponse.Success("test content")
        val error = AiResponse.Error("test error")
        assertTrue(success is AiResponse)
        assertTrue(error is AiResponse)
    }
}