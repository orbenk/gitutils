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
    private lateinit var groqApiKeyLabel: JBLabel
    private lateinit var geminiApiKeyField: JBPasswordField
    private lateinit var geminiApiKeyLabel: JBLabel
    private lateinit var hint: JTextArea
    private lateinit var panel: JPanel

    override fun getDisplayName(): String = "Git Utils AI"

    override fun createComponent(): JComponent {
        providerCombo = ComboBox(DefaultComboBoxModel(arrayOf("Groq", "Gemini")))

        groqApiKeyLabel = JBLabel("Groq API Key:")
        groqApiKeyField = JBPasswordField().apply { columns = 40 }

        geminiApiKeyLabel = JBLabel("Gemini API Key:")
        geminiApiKeyField = JBPasswordField().apply { columns = 40 }

        hint = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Provedor de IA:"), providerCombo, true)
            .addLabeledComponent(groqApiKeyLabel, groqApiKeyField, true)
            .addLabeledComponent(geminiApiKeyLabel, geminiApiKeyField, true)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        providerCombo.addActionListener { updateVisibility() }

        reset()
        return panel
    }

    private fun updateVisibility() {
        val isGroq = providerCombo.selectedItem == "Groq"

        groqApiKeyLabel.isVisible   = isGroq
        groqApiKeyField.isVisible   = isGroq
        geminiApiKeyLabel.isVisible = !isGroq
        geminiApiKeyField.isVisible = !isGroq

        hint.text = if (isGroq)
            "Chave gratuita em https://console.groq.com\nOu defina a variável de ambiente GROQ_API_KEY."
        else
            "Chave gratuita em https://aistudio.google.com/app/apikey\nOu defina a variável de ambiente GEMINI_API_KEY."

        panel.revalidate()
        panel.repaint()
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
        updateVisibility()
    }
}
