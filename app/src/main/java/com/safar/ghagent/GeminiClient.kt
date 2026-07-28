package com.safar.ghagent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Gemini javobidan chiqqan bitta funksiya chaqiruvi */
data class FunctionCall(val name: String, val args: JSONObject)

/** Gemini bir turdagi javobni qaytaradi: yoki matn, yoki funksiya chaqiruvlari ro'yxati */
data class GeminiTurn(val text: String?, val functionCalls: List<FunctionCall>)

class GeminiClient(private val apiKey: String, private val model: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    fun sendMessage(history: JSONArray, tools: JSONArray, systemInstruction: String): GeminiTurn {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val payload = JSONObject().apply {
            put("contents", history)
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return GeminiTurn("Gemini XATOLIK ${resp.code}: $text", emptyList())
            }

            val root = JSONObject(text)
            val candidate = root.getJSONArray("candidates").getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")

            var replyText: String? = null
            val calls = mutableListOf<FunctionCall>()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    replyText = (replyText ?: "") + part.getString("text")
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val args = if (fc.has("args")) fc.getJSONObject("args") else JSONObject()
                    calls.add(FunctionCall(fc.getString("name"), args))
                }
            }
            return GeminiTurn(replyText, calls)
        }
    }
}
