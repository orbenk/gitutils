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
        var scriptPath: String = "",
        var apiKey: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var scriptPath: String
        get() = myState.scriptPath
        set(value) { myState.scriptPath = value }

    var apiKey: String
        get() = myState.apiKey
        set(value) { myState.apiKey = value }

    companion object {
        fun getInstance(): GitUtilsSettings =
            ApplicationManager.getApplication().getService(GitUtilsSettings::class.java)
    }
}
