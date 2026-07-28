package com.safar.ghagent

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val edtGithubToken = findViewById<EditText>(R.id.edtGithubToken)
        val edtGeminiKey = findViewById<EditText>(R.id.edtGeminiKey)
        val spinnerModel = findViewById<Spinner>(R.id.spinnerModel)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val txtStatus = findViewById<TextView>(R.id.txtStatus)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Prefs.AVAILABLE_MODELS)
        spinnerModel.adapter = adapter

        // Mavjud saqlangan qiymatlarni yuklaymiz
        edtGithubToken.setText(Prefs.getGithubToken(this))
        edtGeminiKey.setText(Prefs.getGeminiKey(this))
        val currentModel = Prefs.getGeminiModel(this)
        val modelIndex = Prefs.AVAILABLE_MODELS.indexOf(currentModel)
        if (modelIndex >= 0) spinnerModel.setSelection(modelIndex)

        btnSave.setOnClickListener {
            Prefs.setGithubToken(this, edtGithubToken.text.toString().trim())
            Prefs.setGeminiKey(this, edtGeminiKey.text.toString().trim())
            Prefs.setGeminiModel(this, spinnerModel.selectedItem.toString())

            txtStatus.text = "Saqlandi."
            Toast.makeText(this, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show()
        }
    }
}
