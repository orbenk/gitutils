package com.orbenk

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object LlmClient {

    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    fun generateCommitMessage(
        diff: String,
        branch: String,
        files: List<String>,
        apiKey: String,
        provider: String = "Groq"
    ): String {
        val changedFiles = files.joinToString("\n")

        val prompt = """
Você é um especialista em Git e Conventional Commits. Analise o diff abaixo e gere uma mensagem de commit semântico.

## Contexto
- Branch: $branch
- Arquivos modificados:
$changedFiles

## Diff
```diff
$diff
```

## Instruções
Gere UMA mensagem de commit seguindo o padrão Conventional Commits:
- Formato: <tipo>(<escopo opcional>): <descrição curta em português>
- Tipos válidos: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- A descrição deve ter no máximo 72 caracteres
- Use o imperativo: "adiciona", "corrige", "atualiza" (não "adicionado", "corrigido")
- Se necessário, adicione um corpo com explicação após uma linha em branco
- Se houver breaking change, adicione "BREAKING CHANGE:" no rodapé

Responda APENAS com a mensagem de commit, sem explicações adicionais, sem blocos de código markdown.
        """.trimIndent()

        return callApi(prompt, apiKey, provider = provider, maxOutputTokens = 300)
    }

    fun callApi(
        prompt: String,
        apiKey: String,
        provider: String = "Groq",
        maxOutputTokens: Int = 300
    ): String {
        return if (provider == "Groq") callGroq(prompt, apiKey, maxOutputTokens)
        else callGemini(prompt, apiKey, maxOutputTokens)
    }

    private fun callGroq(prompt: String, apiKey: String, maxTokens: Int): String {
        val escapedPrompt = escapeJson(prompt)

        val body = """
            {
                "model": "llama-3.3-70b-versatile",
                "messages": [{"role": "user", "content": "$escapedPrompt"}],
                "temperature": 0.2,
                "max_tokens": $maxTokens
            }
        """.trimIndent()

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(GROQ_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        when (response.statusCode()) {
            401 -> throw RuntimeException("API Key inválida (401). Verifique em Settings → Tools → Git Utils AI.")
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

    private fun callGemini(prompt: String, apiKey: String, maxTokens: Int): String {
        val escapedPrompt = escapeJson(prompt)

        val body = """
            {
                "contents": [{"parts": [{"text": "$escapedPrompt"}]}],
                "generationConfig": {"temperature": 0.2, "maxOutputTokens": $maxTokens}
            }
        """.trimIndent()

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$GEMINI_URL?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        when (response.statusCode()) {
            403 -> throw RuntimeException("API Key inválida (403). Verifique em Settings → Tools → Git Utils AI.")
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

    private fun escapeJson(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
