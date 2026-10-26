package io.loglens.ai

import java.nio.file.Path

/** Abstraction over local LLM endpoints. */
sealed interface AiProvider {
    val id: String
    val displayName: String

    data class Ollama(
        override val id: String = "ollama",
        override val displayName: String = "Ollama",
        val baseUrl: String = "http://localhost:11434",
        val model: String = "llama3.1",
    ) : AiProvider

    data class OpenAiCompatible(
        override val id: String = "openai-compatible",
        override val displayName: String = "OpenAI-compatible",
        val baseUrl: String,
        val apiKey: String = "",
        val model: String,
    ) : AiProvider {
        init {
            require(baseUrl.isNotBlank()) { "Base URL is required for OpenAI-compatible provider." }
            require(model.isNotBlank()) { "Model is required for OpenAI-compatible provider." }
        }
    }

    companion object {
        val defaults = listOf<AiProvider>(
            Ollama(),
            OpenAiCompatible(baseUrl = "", model = ""),
        )
    }
}

/** Available AI actions for log analysis. */
enum class AiAction(val label: String, val promptTemplate: String) {
    EXPLAIN_ERROR(
        "Explain error",
        """
            You are a senior software engineer analyzing application logs.
            The user selected an ERROR log entry and wants an explanation.
            
            Log entry:
            {entry}
            
            Provide a concise explanation of what this error means, what likely caused it,
            and what the user should investigate. Focus on actionable insights.
            Limit your response to 3-5 sentences.
        """.trimIndent()
    ),
    SUMMARIZE_EXCEPTION(
        "Summarize exception",
        """
            You are a senior software engineer analyzing application logs.
            The user selected a stack trace and wants a summary.
            
            Exception:
            {entry}
            
            Provide a concise summary of the exception including:
            1. Exception type and message
            2. Likely root cause
            3. Key stack frames to focus on
            Limit your response to 4-6 sentences.
        """.trimIndent()
    ),
    FIND_PROBABLE_CAUSE(
        "Find probable cause",
        """
            You are a senior software engineer analyzing application logs.
            The user selected an ERROR/WARN log entry and wants to find the probable cause.
            
            Log entry:
            {entry}
            
            Related context (nearby entries):
            {context}
            
            Analyze the log entry and surrounding context to identify the most probable cause.
            Consider timing, related services, error patterns, and resource constraints.
            Limit your response to 3-5 sentences.
        """.trimIndent()
    ),
    EXPLAIN_STACK_TRACE(
        "Explain stack trace",
        """
            You are a senior software engineer analyzing application logs.
            The user selected a stack trace and wants an explanation of the execution flow.
            
            Stack trace:
            {entry}
            
            Explain the stack trace in terms of:
            1. What the code was trying to do
            2. Where the failure occurred
            3. The call chain leading to the failure
            Limit your response to 4-6 sentences.
        """.trimIndent()
    ),
    FIND_RELATED_EVENTS(
        "Find related events",
        """
            You are a senior software engineer analyzing application logs.
            The user selected a log entry and wants to find related events.
            
            Selected entry:
            {entry}
            
            Available log entries (recent):
            {context}
            
            Identify log entries that are likely related to the selected entry based on:
            - Shared traceId, spanId, requestId, or correlation IDs
            - Same service/logger/thread
            - Temporal proximity
            - Causal relationships
            List the most relevant related entries with brief explanations.
            Limit your response to 5-7 entries.
        """.trimIndent()
    ),
    SUGGEST_DEBUGGING_STEPS(
        "Suggest debugging steps",
        """
            You are a senior software engineer analyzing application logs.
            The user selected an ERROR log entry and wants debugging suggestions.
            
            Log entry:
            {entry}
            
            Related context:
            {context}
            
            Provide 3-5 concrete debugging steps the user should take, ordered by priority.
            Each step should be specific and actionable (e.g., "Check X configuration",
            "Verify Y service is reachable", "Increase Z timeout").
            Format as a numbered list.
        """.trimIndent()
    );

    companion object {
        val all = values().toList()
    }
}

/** Request to the AI service. */
data class AiRequest(
    val action: AiAction,
    val entry: String,
    val context: String = "",
    val provider: AiProvider,
)

/** Response from the AI service. */
sealed interface AiResponse {
    data class Success(val content: String) : AiResponse
    data class Error(val message: String, val cause: Throwable? = null) : AiResponse
}