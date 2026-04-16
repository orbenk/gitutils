package com.orbenk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "GitUtilsSettings",
    storages = [Storage("git-utils.xml")]
)
class GitUtilsSettings : PersistentStateComponent<GitUtilsSettings.State> {

    data class State(
        var apiKey: String = "",
        var groqApiKey: String = "",
        var provider: String = "Groq"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** Gemini API Key. */
    var apiKey: String
        get() = myState.apiKey
        set(value) { myState.apiKey = value }

    var groqApiKey: String
        get() = myState.groqApiKey
        set(value) { myState.groqApiKey = value }

    var provider: String
        get() = myState.provider
        set(value) { myState.provider = value }

    /** Returns the active API key for the currently selected provider. */
    fun activeApiKey(): String {
        val key = if (provider == "Groq") groqApiKey else apiKey
        if (key.isNotBlank()) return key
        return if (provider == "Groq")
            System.getenv("GROQ_API_KEY") ?: ""
        else
            System.getenv("GEMINI_API_KEY") ?: ""
    }

    companion object {
        fun getInstance(): GitUtilsSettings =
            ApplicationManager.getApplication().getService(GitUtilsSettings::class.java)
    }
}
