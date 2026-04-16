package com.orbenk

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import git4idea.repo.GitRepositoryManager
import java.io.File

class GenerateCommitMessageAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return

        val settings = GitUtilsSettings.getInstance()

        // Script path not configured → offer to open settings
        if (settings.scriptPath.isBlank()) {
            val choice = Messages.showOkCancelDialog(
                project,
                "Caminho do script Get-CommitMessage.ps1 não configurado.\n\nDeseja abrir as configurações agora?",
                "Git Utils AI",
                "Abrir Configurações",
                "Cancelar",
                Messages.getWarningIcon()
            )
            if (choice == Messages.OK) {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, GitUtilsSettingsConfigurable::class.java)
            }
            return
        }

        val apiKey = settings.activeApiKey()
        if (apiKey.isBlank()) {
            val envVar = if (settings.provider == "Groq") "GROQ_API_KEY" else "GEMINI_API_KEY"
            Messages.showErrorDialog(
                project,
                "${settings.provider} API Key não encontrada.\n\n" +
                "Defina-a em Settings → Tools → Git Utils AI\n" +
                "ou na variável de ambiente $envVar.",
                "Git Utils AI"
            )
            return
        }

        val scriptFile = File(settings.scriptPath)
        if (!scriptFile.exists()) {
            Messages.showErrorDialog(
                project,
                "Script não encontrado:\n${settings.scriptPath}\n\n" +
                "Verifique o caminho em Settings → Tools → Git Utils AI.",
                "Git Utils AI"
            )
            return
        }

        // Working directory must be the project's git root so the diff is collected correctly
        val workingDir = resolveGitRoot(project)

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Gerando mensagem de commit com IA...", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Enviando diff para o ${settings.provider}..."
                    indicator.isIndeterminate = true

                    try {
                        val process = ProcessBuilder(
                            "powershell.exe",
                            "-NonInteractive",
                            "-ExecutionPolicy", "Bypass",
                            "-File", scriptFile.absolutePath,
                            "-OutputOnly",
                            "-Provider", settings.provider,
                            "-ApiKey", apiKey
                        )
                            .directory(File(workingDir))
                            .start()

                        val stdout = process.inputStream.bufferedReader().readText().trim()
                        val stderr = process.errorStream.bufferedReader().readText().trim()
                        val exitCode = process.waitFor()

                        if (indicator.isCanceled) return

                        if (exitCode != 0 || stdout.isBlank()) {
                            val detail = stderr.ifBlank { stdout.ifBlank { "Código de saída: $exitCode" } }
                            throw RuntimeException(detail)
                        }

                        ApplicationManager.getApplication().invokeLater {
                            commitMessageControl.setCommitMessage(stdout)
                        }

                    } catch (ex: Exception) {
                        if (!indicator.isCanceled) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    ex.message ?: "Erro desconhecido",
                                    "Git Utils AI — Erro"
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    /** Returns the git root of the first repository in the project, or falls back to the project base path. */
    private fun resolveGitRoot(project: Project): String {
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isNotEmpty()) return repos.first().root.path
        return project.basePath ?: "."
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
