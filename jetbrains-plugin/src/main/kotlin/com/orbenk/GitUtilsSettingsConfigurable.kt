package com.orbenk

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class GitUtilsSettingsConfigurable : Configurable {

    private lateinit var scriptPathField: TextFieldWithBrowseButton
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var panel: JPanel

    override fun getDisplayName(): String = "Git Utils AI"

    override fun createComponent(): JComponent {
        scriptPathField = TextFieldWithBrowseButton()
        scriptPathField.addBrowseFolderListener(
            "Selecionar Script",
            "Selecione o arquivo Get-CommitMessage.ps1",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("ps1")
        )

        apiKeyField = JBPasswordField()
        apiKeyField.columns = 40

        val hint = JTextArea(
            "A API Key pode ser omitida aqui se a variável de ambiente GEMINI_API_KEY estiver definida.\n" +
            "Obtenha uma chave gratuita em: https://aistudio.google.com/app/apikey"
        ).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Caminho do script (Get-CommitMessage.ps1):"), scriptPathField, true)
            .addLabeledComponent(JBLabel("Gemini API Key:"), apiKeyField, true)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = GitUtilsSettings.getInstance()
        return scriptPathField.text != settings.scriptPath ||
               String(apiKeyField.password) != settings.apiKey
    }

    override fun apply() {
        val settings = GitUtilsSettings.getInstance()
        settings.scriptPath = scriptPathField.text.trim()
        settings.apiKey = String(apiKeyField.password)
    }

    override fun reset() {
        val settings = GitUtilsSettings.getInstance()
        scriptPathField.text = settings.scriptPath
        apiKeyField.text = settings.apiKey
    }
}
