package com.orbenk

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GeminiLlmClient(private val apiKey: String) : LlmClient {

    override fun callApi(prompt: String, maxOutputTokens: Int): String {
        val body = buildBody(prompt, maxOutputTokens)

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        when (response.statusCode()) {
            403 -> throw RuntimeException("Gemini API Key inválida (403). Verifique em Settings → Tools → Git Utils AI.")
            429 -> throw RuntimeException("Limite de requisições atingido (429). Aguarde e tente novamente.")
            200 -> { /* ok */ }
            else -> throw RuntimeException("Erro na API do Gemini (${response.statusCode()}): ${response.body()}")
        }

        return JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonArray("candidates")[0].asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")[0].asJsonObject
            .get("text").asString
            .trim()
    }

    private fun buildBody(prompt: String, maxTokens: Int): String {
        val escaped = escapeJson(prompt)
        return """
            {
                "contents": [{"parts": [{"text": "$escaped"}]}],
                "generationConfig": {"temperature": 0.2, "maxOutputTokens": $maxTokens}
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
