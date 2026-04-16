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
import com.intellij.openapi.vfs.VfsUtil
import git4idea.repo.GitRepositoryManager
import java.io.File

class GenerateAndCommitAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = GitUtilsSettings.getInstance()
        val apiKey = settings.activeApiKey()

        if (apiKey.isBlank()) {
            val envVar = if (settings.provider == "Groq") "GROQ_API_KEY" else "GEMINI_API_KEY"
            val choice = Messages.showOkCancelDialog(
                project,
                "${settings.provider} API Key não encontrada ($envVar).\n\nDeseja abrir as configurações agora?",
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

        val changes = e.getData(VcsDataKeys.CHANGES)?.toList() ?: emptyList()
        if (changes.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Nenhum arquivo selecionado na tela de commit.\n\nSelecione ao menos um arquivo e tente novamente.",
                "Git Utils AI"
            )
            return
        }

        val gitRoot = GitHelper.resolveGitRoot(project)
        val filePaths = GitHelper.collectFilePaths(changes, gitRoot)

        if (filePaths.isEmpty()) {
            Messages.showWarningDialog(project, "Não foi possível determinar os caminhos dos arquivos selecionados.", "Git Utils AI")
            return
        }

        val client = LlmClient.from(settings)

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Gerando mensagem de commit com IA...", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Gerando diff dos arquivos selecionados..."
                    indicator.isIndeterminate = true

                    try {
                        val diff = GitHelper.generateDiff(gitRoot, filePaths)
                        if (diff.isBlank()) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showWarningDialog(
                                    project,
                                    "Os arquivos selecionados não possuem alterações em relação ao HEAD.",
                                    "Git Utils AI"
                                )
                            }
                            return
                        }

                        indicator.text = "Enviando diff para o ${settings.provider}..."

                        val branch = GitHelper.resolveBranchName(gitRoot)
                        val relPaths = filePaths.map { it.removePrefix("$gitRoot/").removePrefix("$gitRoot\\") }
                        val commitMessage = client.generateCommitMessage(diff, branch, relPaths)

                        if (indicator.isCanceled) return

                        ApplicationManager.getApplication().invokeLater {
                            val dialog = CommitConfirmationDialog(project, commitMessage, relPaths)
                            if (dialog.showAndGet()) {
                                performCommit(project, gitRoot, filePaths, dialog.getCommitMessage(), indicator)
                            }
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

    private fun performCommit(
        project: Project,
        gitRoot: String,
        filePaths: List<String>,
        message: String,
        indicator: ProgressIndicator
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Realizando commit...", false) {
                override fun run(ind: ProgressIndicator) {
                    try {
                        ind.text = "Adicionando arquivos ao stage..."
                        val addArgs = mutableListOf("git", "add", "--")
                        addArgs.addAll(filePaths)
                        val addProcess = ProcessBuilder(addArgs)
                            .directory(File(gitRoot))
                            .start()
                        val addExit = addProcess.waitFor()
                        if (addExit != 0) {
                            val err = addProcess.errorStream.bufferedReader().readText()
                            throw RuntimeException("Falha no git add: $err")
                        }

                        ind.text = "Realizando commit..."
                        val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                            .directory(File(gitRoot))
                            .start()
                        val commitOut = commitProcess.inputStream.bufferedReader().readText()
                        val commitErr = commitProcess.errorStream.bufferedReader().readText()
                        val commitExit = commitProcess.waitFor()

                        if (commitExit != 0) {
                            throw RuntimeException("Falha no git commit:\n${commitErr.ifBlank { commitOut }}")
                        }

                        ApplicationManager.getApplication().invokeLater {
                            VfsUtil.markDirtyAndRefresh(true, true, true, *emptyArray<com.intellij.openapi.vfs.VirtualFile>())
                            GitRepositoryManager.getInstance(project).repositories.forEach { it.update() }
                        }

                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, ex.message ?: "Erro desconhecido", "Git Utils AI — Erro")
                        }
                    }
                }
            }
        )
    }

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        val hasChanges = (e.getData(VcsDataKeys.CHANGES)?.size ?: 0) > 0
        e.presentation.isEnabled = hasProject && hasChanges
        e.presentation.isVisible = hasProject
    }
}
