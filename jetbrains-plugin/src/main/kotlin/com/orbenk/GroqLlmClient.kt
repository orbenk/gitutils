package com.orbenk

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GroqLlmClient(private val apiKey: String) : LlmClient {

    override fun callApi(prompt: String, maxOutputTokens: Int): String {
        val body = buildBody(prompt, maxOutputTokens)

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        when (response.statusCode()) {
            401 -> throw RuntimeException("Groq API Key inválida (401). Verifique em Settings → Tools → Git Utils AI.")
            429 -> throw RuntimeException("Limite de requisições atingido (429). Aguarde e tente novamente.")
            200 -> { /* ok */ }
            else -> throw RuntimeException("Erro na API do Groq (${response.statusCode()}): ${response.body()}")
        }

        return JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
            .trim()
    }

    private fun buildBody(prompt: String, maxTokens: Int): String {
        val escaped = escapeJson(prompt)
        return """
            {
                "model": "llama-3.3-70b-versatile",
                "messages": [{"role": "user", "content": "$escaped"}],
                "temperature": 0.2,
                "max_tokens": $maxTokens
            }
        """.trimIndent()
    }

    private fun escapeJson(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
