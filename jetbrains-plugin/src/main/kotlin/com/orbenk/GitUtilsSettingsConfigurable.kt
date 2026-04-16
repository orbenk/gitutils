package com.orbenk

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class GitUtilsSettingsConfigurable : Configurable {

    private lateinit var providerCombo: ComboBox<String>
    private lateinit var groqApiKeyField: JBPasswordField
    private lateinit var geminiApiKeyField: JBPasswordField
    private lateinit var panel: JPanel

    override fun getDisplayName(): String = "Git Utils AI"

    override fun createComponent(): JComponent {
        providerCombo = ComboBox(DefaultComboBoxModel(arrayOf("Groq", "Gemini")))

        groqApiKeyField = JBPasswordField()
        groqApiKeyField.columns = 40

        geminiApiKeyField = JBPasswordField()
        geminiApiKeyField.columns = 40

        val hint = JTextArea(
            "Groq (padrão): chave gratuita em https://console.groq.com — GROQ_API_KEY\n" +
            "Gemini: chave gratuita em https://aistudio.google.com/app/apikey — GEMINI_API_KEY\n" +
            "As chaves podem ser omitidas se a variável de ambiente correspondente estiver definida."
        ).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Provedor de IA:"), providerCombo, true)
            .addLabeledComponent(JBLabel("Groq API Key:"), groqApiKeyField, true)
            .addLabeledComponent(JBLabel("Gemini API Key:"), geminiApiKeyField, true)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = GitUtilsSettings.getInstance()
        return providerCombo.selectedItem != s.provider ||
               String(groqApiKeyField.password) != s.groqApiKey ||
               String(geminiApiKeyField.password) != s.apiKey
    }

    override fun apply() {
        val s = GitUtilsSettings.getInstance()
        s.provider   = providerCombo.selectedItem as String
        s.groqApiKey = String(groqApiKeyField.password)
        s.apiKey     = String(geminiApiKeyField.password)
    }

    override fun reset() {
        val s = GitUtilsSettings.getInstance()
        providerCombo.selectedItem = s.provider
        groqApiKeyField.text       = s.groqApiKey
        geminiApiKeyField.text     = s.apiKey
    }
}
