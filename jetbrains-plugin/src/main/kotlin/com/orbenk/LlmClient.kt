package com.orbenk

interface LlmClient {

    /**
     * Sends [prompt] to the underlying AI provider and returns the raw text response.
     */
    fun callApi(prompt: String, maxOutputTokens: Int = 300): String

    /**
     * Builds the Conventional Commits prompt from the given diff context and delegates to [callApi].
     * Implementations do not need to override this.
     */
    fun generateCommitMessage(diff: String, branch: String, files: List<String>): String {
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
        return callApi(prompt, maxOutputTokens = 300)
    }

    companion object {
        /** Factory: creates the correct implementation based on [settings]. */
        fun from(settings: GitUtilsSettings): LlmClient {
            val apiKey = settings.activeApiKey()
            return if (settings.provider == "Groq") GroqLlmClient(apiKey) else GeminiLlmClient(apiKey)
        }
    }
}
