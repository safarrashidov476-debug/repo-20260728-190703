package com.safar.ghagent

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var agent: AgentEngine? = null
    private lateinit var txtLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var edtInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtLog = findViewById(R.id.txtLog)
        scrollLog = findViewById(R.id.scrollLog)
        edtInput = findViewById(R.id.edtInput)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSend.setOnClickListener {
            if (!Prefs.isConfigured(this)) {
                appendLog("\n\nAvval Sozlamalar bo'limida GitHub token va Gemini API kalitini kiriting.")
                return@setOnClickListener
            }

            val text = edtInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            appendLog("\n\nSiz: $text")
            edtInput.setText("")

            val currentAgent = getOrCreateAgent()

            CoroutineScope(Dispatchers.Main).launch {
                appendLog("\nAgent o'ylayapti...")
                val reply = withContext(Dispatchers.IO) {
                    currentAgent.handleUserMessage(text)
                }
                appendLog("\nAgent: $reply")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sozlamalar o'zgargan bo'lishi mumkin (masalan Settings'dan qaytganda) — agentni qayta yaratamiz
        agent = null
    }

    private fun getOrCreateAgent(): AgentEngine {
        val existing = agent
        if (existing != null) return existing

        val gemini = GeminiClient(Prefs.getGeminiKey(this), Prefs.getGeminiModel(this))
        val github = GitHubClient(Prefs.getGithubToken(this))
        val created = AgentEngine(gemini, github, this)
        agent = created
        return created
    }

    private fun appendLog(text: String) {
        txtLog.append(text)
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }
}
