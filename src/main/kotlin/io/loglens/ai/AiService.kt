package io.loglens.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Service for communicating with local LLM endpoints. */
class AiService private constructor(
    private val client: OkHttpClient,
    private val gson: Gson,
) {

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 60L

        @JvmStatic
        fun create(): AiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val gson = Gson()
            return AiService(client, gson)
        }
    }

    /** Sends a request to the configured AI provider and returns the response. */
    fun send(request: AiRequest): AiResponse = try {
        val (url, body) = buildRequest(request)
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                AiResponse.Error("HTTP ${response.code}: $responseBody")
            } else {
                parseResponse(responseBody, request.provider)
            }
        }
    } catch (e: Exception) {
        AiResponse.Error("Failed to communicate with AI provider: ${e.message}", e)
    }

    private fun buildRequest(request: AiRequest): Pair<String, okhttp3.RequestBody> {
        val prompt = buildPrompt(request)
        return when (request.provider) {
            is AiProvider.Ollama -> buildOllamaRequest(request.provider, prompt)
            is AiProvider.OpenAiCompatible -> buildOpenAiCompatibleRequest(request.provider, prompt)
        }
    }

    private fun buildPrompt(request: AiRequest): String {
        var template = request.action.promptTemplate
        template = template.replace("{entry}", request.entry)
        template = template.replace("{context}", request.context.ifBlank { "(no additional context)" })
        return template
    }

    private fun buildOllamaRequest(provider: AiProvider.Ollama, prompt: String): Pair<String, okhttp3.RequestBody> {
        val json = JsonObject().apply {
            addProperty("model", provider.model)
            addProperty("prompt", prompt)
            addProperty("stream", false)
            add("options", JsonObject().apply {
                addProperty("temperature", 0.3)
                addProperty("top_p", 0.9)
            })
        }
        val url = "${provider.baseUrl}/api/generate"
        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        return url to body
    }

    private fun buildOpenAiCompatibleRequest(provider: AiProvider.OpenAiCompatible, prompt: String): Pair<String, okhttp3.RequestBody> {
        val json = JsonObject().apply {
            addProperty("model", provider.model)
            add("messages", JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", prompt)
            })
            addProperty("temperature", 0.3)
            addProperty("max_tokens", 500)
        }
        val url = "${provider.baseUrl}/chat/completions"
        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url)
        if (provider.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${provider.apiKey}")
        }
        return url to body
    }

    private fun parseResponse(responseBody: String, provider: AiProvider): AiResponse {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val content = when (provider) {
                is AiProvider.Ollama -> json.get("response")?.asString ?: ""
                is AiProvider.OpenAiCompatible -> {
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        choices[0].asJsonObject.getAsJsonObject("message").get("content")?.asString ?: ""
                    } else ""
                }
            }
            if (content.isBlank()) {
                AiResponse.Error("Empty response from AI provider")
            } else {
                AiResponse.Success(content.trim())
            }
        } catch (e: Exception) {
            AiResponse.Error("Failed to parse AI response: ${e.message}", e)
        }
    }
}