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
import com.intellij.openapi.vcs.changes.Change
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

        // Collect selected changes from the commit panel
        val changes = e.getData(VcsDataKeys.CHANGES)?.toList() ?: emptyList()
        if (changes.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Nenhum arquivo selecionado na tela de commit.\n\nSelecione ao menos um arquivo e tente novamente.",
                "Git Utils AI"
            )
            return
        }

        val gitRoot = resolveGitRoot(project)
        val filePaths = collectFilePaths(changes, gitRoot)

        if (filePaths.isEmpty()) {
            Messages.showWarningDialog(project, "Não foi possível determinar os caminhos dos arquivos selecionados.", "Git Utils AI")
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Gerando mensagem de commit com IA...", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Gerando diff dos arquivos selecionados..."
                    indicator.isIndeterminate = true

                    try {
                        val diff = generateDiff(gitRoot, filePaths)
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

                        indicator.text = "Enviando diff para o Gemini..."

                        val branch = resolveBranchName(gitRoot)
                        val relPaths = filePaths.map { it.removePrefix("$gitRoot/").removePrefix("$gitRoot\\") }
                        val commitMessage = LlmClient.generateCommitMessage(diff, branch, relPaths, apiKey, settings.provider)

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

    // ── Git helpers ──────────────────────────────────────────────────────────

    /** Generates `git diff HEAD -- <files>` for the given paths. */
    private fun generateDiff(gitRoot: String, filePaths: List<String>): String {
        val args = mutableListOf("git", "diff", "HEAD", "--")
        args.addAll(filePaths)

        val process = ProcessBuilder(args)
            .directory(File(gitRoot))
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        // Truncate very large diffs to avoid exceeding Gemini's token limit
        val lines = output.lines()
        return if (lines.size > 600) {
            lines.take(600).joinToString("\n") + "\n... [diff truncado — ${lines.size - 600} linhas omitidas]"
        } else {
            output
        }
    }

    /** Runs `git add -- <files>` followed by `git commit -m <message>`. */
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
                        // git add
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

                        // git commit
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

                        // Refresh VCS state
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

    private fun resolveGitRoot(project: Project): String {
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isNotEmpty()) return repos.first().root.path
        return project.basePath ?: "."
    }

    private fun resolveBranchName(gitRoot: String): String {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(File(gitRoot))
            .start()
        val name = process.inputStream.bufferedReader().readText().trim()
        return if (process.waitFor() == 0 && name.isNotBlank()) name else "unknown"
    }

    /** Resolves absolute file paths from the Change objects. */
    private fun collectFilePaths(changes: List<Change>, gitRoot: String): List<String> {
        return changes.flatMap { change ->
            listOfNotNull(
                change.afterRevision?.file?.path,
                change.beforeRevision?.file?.path
            )
        }.distinct().filter { File(it).exists() || it.startsWith(gitRoot) }
    }

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        val hasChanges = (e.getData(VcsDataKeys.CHANGES)?.size ?: 0) > 0
        e.presentation.isEnabled = hasProject && hasChanges
        e.presentation.isVisible = hasProject
    }
}
