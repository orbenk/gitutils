package com.orbenk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import git4idea.repo.GitRepositoryManager
import java.io.File

object GitHelper {

    fun resolveGitRoot(project: Project): String {
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isNotEmpty()) return repos.first().root.path
        return project.basePath ?: "."
    }

    fun resolveBranchName(gitRoot: String): String {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(File(gitRoot))
            .start()
        val name = process.inputStream.bufferedReader().readText().trim()
        return if (process.waitFor() == 0 && name.isNotBlank()) name else "unknown"
    }

    fun collectFilePaths(changes: List<Change>, gitRoot: String): List<String> {
        return changes.flatMap { change ->
            listOfNotNull(
                change.afterRevision?.file?.path,
                change.beforeRevision?.file?.path
            )
        }.distinct().filter { File(it).exists() || it.startsWith(gitRoot) }
    }

    /** Runs `git diff HEAD -- <files>`, truncating at [maxLines] if needed. */
    fun generateDiff(gitRoot: String, filePaths: List<String>, maxLines: Int = 600): String {
        val args = mutableListOf("git", "diff", "HEAD", "--")
        args.addAll(filePaths)

        val process = ProcessBuilder(args)
            .directory(File(gitRoot))
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        val lines = output.lines()
        return if (lines.size > maxLines) {
            lines.take(maxLines).joinToString("\n") +
                "\n... [diff truncado — ${lines.size - maxLines} linhas omitidas]"
        } else {
            output
        }
    }
}
