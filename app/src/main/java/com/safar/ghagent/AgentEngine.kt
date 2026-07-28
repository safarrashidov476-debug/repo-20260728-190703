package com.safar.ghagent

import org.json.JSONArray
import org.json.JSONObject
import androidx.work.*
import java.util.concurrent.TimeUnit

class AgentEngine(
    private val gemini: GeminiClient,
    private val github: GitHubClient,
    private val context: android.content.Context? = null
) {
    private val history = JSONArray()

    private val systemInstruction = """
        Siz foydalanuvchining GitHub akkauntini TO'LIQ boshqaradigan super AI agentsiz.
        Sizda repozitoriyalar yaratish, o'chirish, PRlar bilan ishlash, issue'larni boshqarish, release'lar yaratish,
        collaborator'larni qo'shish va Actions'larni boshqarish huquqi bor.
        Shuningdek, siz ZIP fayllarni avtomatik ochib yuklay olasiz va loyihalarni vaqt bo'yicha monitoring qila olasiz.
        Monitoring o'rnatilganda, foydalanuvchi ilovadan tashqarida bo'lsa ham push-bildirishnoma yuboriladi.
        Har doim mavjud funksiyalardan (tools) foydalanib amal bajaring.
        Javobni foydalanuvchi tushunadigan tilda (o'zbekcha) aniq va professional tarzda bering.
    """.trimIndent()

    private fun tools(): JSONArray {
        fun fn(name: String, desc: String, props: JSONObject, required: JSONArray = JSONArray()) =
            JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", props)
                    put("required", required)
                })
            }

        fun strProp(desc: String) = JSONObject().put("type", "STRING").put("description", desc)
        fun intProp(desc: String) = JSONObject().put("type", "INTEGER").put("description", desc)
        fun boolProp(desc: String) = JSONObject().put("type", "BOOLEAN").put("description", desc)

        val list = JSONArray()

        // Repos
        list.put(fn("list_repos", "Foydalanuvchining barcha repositoriyalarini ro'yxatlaydi", JSONObject()))
        list.put(fn("create_repo", "Yangi repository yaratadi",
            JSONObject().put("name", strProp("Repo nomi")).put("description", strProp("Tavsif")).put("is_private", boolProp("Private?")),
            JSONArray().put("name")))
        list.put(fn("delete_repo", "Repositoryni butunlay o'chiradi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))

        // Monitoring
        list.put(fn("start_monitoring", "Repozitoriyani vaqt bo'yicha monitoring qilishni boshlaydi",
            JSONObject()
                .put("full_name", strProp("owner/repo"))
                .put("interval_minutes", intProp("Tekshirish oralig'i (daqiqa)")),
            JSONArray().put("full_name").put("interval_minutes")))
        
        list.put(fn("stop_monitoring", "Monitoringni to'xtatadi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))

        // PRs, Issues, Files, etc. (Existing tools...)
        list.put(fn("list_prs", "Pull requestlarni ro'yxatlaydi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("create_issue", "Yangi issue yaratadi", JSONObject().put("full_name", strProp("owner/repo")).put("title", strProp("Sarlavha")), JSONArray().put("full_name").put("title")))
        list.put(fn("write_file", "Fayl yaratadi yoki yangilaydi", JSONObject().put("full_name", strProp("owner/repo")).put("path", strProp("Yo'l")).put("content", strProp("Matn")).put("message", strProp("Commit xabari")), JSONArray().put("full_name").put("path").put("content").put("message")))
        list.put(fn("get_my_info", "O'zim haqimda ma'lumot olaman", JSONObject()))

        return list
    }

    private fun executeFunction(call: FunctionCall): String {
        val a = call.args
        return try {
            when (call.name) {
                "list_repos" -> github.listRepos()
                "create_repo" -> github.createRepo(a.getString("name"), a.optString("description", ""), a.optBoolean("is_private", false))
                "delete_repo" -> github.deleteRepo(a.getString("full_name"))
                "start_monitoring" -> startMonitoring(a.getString("full_name"), a.getInt("interval_minutes"))
                "stop_monitoring" -> stopMonitoring(a.getString("full_name"))
                "get_my_info" -> github.getAuthenticatedUser()
                // Other functions...
                else -> "Noma'lum funksiya"
            }
        } catch (e: Exception) { "Xatolik: ${e.message}" }
    }

    private fun startMonitoring(repoFullName: String, interval: Int): String {
        if (context == null) return "Context mavjud emas, monitoringni boshlab bo'lmaydi."
        
        val workRequest = PeriodicWorkRequestBuilder<RepoMonitorWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf("repo_full_name" to repoFullName))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "monitor_$repoFullName",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        return "$repoFullName uchun monitoring $interval daqiqada bir marta o'rnatildi."
    }

    private fun stopMonitoring(repoFullName: String): String {
        if (context == null) return "Context mavjud emas."
        WorkManager.getInstance(context).cancelUniqueWork("monitor_$repoFullName")
        return "$repoFullName uchun monitoring to'xtatildi."
    }

    fun handleUserMessage(userText: String): String {
        history.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        })
        repeat(10) {
            val turn = gemini.sendMessage(history, tools(), systemInstruction)
            if (turn.functionCalls.isEmpty()) {
                val finalText = turn.text ?: ""
                history.put(JSONObject().apply { put("role", "model"); put("parts", JSONArray().put(JSONObject().put("text", finalText))) })
                return finalText
            }
            val modelParts = JSONArray()
            for (fc in turn.functionCalls) {
                modelParts.put(JSONObject().apply { put("functionCall", JSONObject().apply { put("name", fc.name); put("args", fc.args) }) })
            }
            history.put(JSONObject().apply { put("role", "model"); put("parts", modelParts) })
            val responseParts = JSONArray()
            for (fc in turn.functionCalls) {
                val result = executeFunction(fc)
                responseParts.put(JSONObject().apply { put("functionResponse", JSONObject().apply { put("name", fc.name); put("response", JSONObject().put("result", result)) }) })
            }
            history.put(JSONObject().apply { put("role", "user"); put("parts", responseParts) })
        }
        return "Juda ko'p qadam."
    }
}
