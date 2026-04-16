package com.orbenk

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class CommitConfirmationDialog(
    project: Project,
    generatedMessage: String,
    private val filePaths: List<String>
) : DialogWrapper(project) {

    private val messageArea = JTextArea(generatedMessage).apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 6
        font = JBUI.Fonts.create("JetBrains Mono", 12)
    }

    init {
        title = "Git Utils AI — Confirmar Commit"
        setOKButtonText("Adicionar e Commitar")
        setCancelButtonText("Cancelar")
        init()
    }

    fun getCommitMessage(): String = messageArea.text.trim()

    override fun createCenterPanel(): JComponent {
        val filesLabel = JTextArea(
            filePaths.joinToString("\n") { "  • $it" }
        ).apply {
            isEditable = false
            isOpaque = false
            font = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        val messageScroll = JBScrollPane(messageArea).apply {
            preferredSize = Dimension(600, 130)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("Arquivos selecionados (${filePaths.size}):"), filesLabel, true
            )
            .addLabeledComponent(
                JBLabel("Mensagem de commit (editável):"), messageScroll, true
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { it.border = JBUI.Borders.empty(8) }
    }

    override fun getPreferredFocusedComponent() = messageArea
}
