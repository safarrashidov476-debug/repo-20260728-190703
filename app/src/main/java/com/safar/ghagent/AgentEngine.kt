package com.safar.ghagent

import org.json.JSONArray
import org.json.JSONObject
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.content.Context

class AgentEngine(
    private val gemini: GeminiClient,
    private val github: GitHubClient,
    private val context: Context? = null
) {
    private val history = JSONArray()

    private val systemInstruction = """
        Siz foydalanuvchining GitHub akkauntini TO'LIQ boshqaradigan super AI agentsiz.
        Sizda barcha huquqlar bor: Repos, PRs, Issues, Actions, Releases, Collaborators, ZIP upload va Monitoring.
        
        MUHIM: Agar foydalanuvchi GitHub URL yuborsa (masalan, .../actions), uni tahlil qiling va tegishli funksiyani chaqiring.
        Masalan, URL /actions bilan tugasa, 'list_runs' funksiyasini ishlating.
        
        Har doim mavjud funksiyalardan (tools) foydalaning. Taxmin qilmang.
        Javobni o'zbek tilida, aniq va professional bering.
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

        // Repositories
        list.put(fn("list_repos", "Foydalanuvchining barcha repositoriyalarini ro'yxatlaydi", JSONObject()))
        list.put(fn("get_repo", "Repo haqida ma'lumot", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("create_repo", "Yangi repo yaratadi", JSONObject().put("name", strProp("Nomi")).put("description", strProp("Tavsif")), JSONArray().put("name")))
        list.put(fn("delete_repo", "Reponi o'chiradi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))

        // Actions
        list.put(fn("list_runs", "GitHub Actions buildlarini (runs) ko'radi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("get_run_log", "Build xatolarini ko'rish uchun loglarni oladi", JSONObject().put("full_name", strProp("owner/repo")).put("run_id", intProp("Run ID")), JSONArray().put("full_name").put("run_id")))
        list.put(fn("rerun_workflow", "Buildni qayta ishga tushiradi", JSONObject().put("full_name", strProp("owner/repo")).put("run_id", intProp("Run ID")), JSONArray().put("full_name").put("run_id")))

        // Pull Requests & Issues
        list.put(fn("list_prs", "Pull requestlarni ko'radi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("list_issues", "Issue'larni ko'radi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))

        // Files
        list.put(fn("get_file", "Faylni o'qiydi", JSONObject().put("full_name", strProp("owner/repo")).put("path", strProp("Yo'l")), JSONArray().put("full_name").put("path")))
        list.put(fn("write_file", "Fayl yozadi", JSONObject().put("full_name", strProp("owner/repo")).put("path", strProp("Yo'l")).put("content", strProp("Matn")).put("message", strProp("Xabar")), JSONArray().put("full_name").put("path").put("content").put("message")))

        // Monitoring
        list.put(fn("start_monitoring", "Reponi monitoring qilishni boshlaydi", JSONObject().put("full_name", strProp("owner/repo")).put("interval_minutes", intProp("Daqiqa")), JSONArray().put("full_name").put("interval_minutes")))
        list.put(fn("stop_monitoring", "Monitoringni to'xtatadi", JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))

        return list
    }

    private fun executeFunction(call: FunctionCall): String {
        val a = call.args
        return try {
            when (call.name) {
                "list_repos" -> github.listRepos()
                "get_repo" -> github.getRepo(a.getString("full_name"))
                "create_repo" -> github.createRepo(a.getString("name"), a.optString("description", ""), a.optBoolean("is_private", false))
                "delete_repo" -> github.deleteRepo(a.getString("full_name"))
                "list_runs" -> github.listActionsRuns(a.getString("full_name"))
                "get_run_log" -> github.getActionRunLog(a.getString("full_name"), a.getLong("run_id"))
                "rerun_workflow" -> github.rerunWorkflow(a.getString("full_name"), a.getLong("run_id"))
                "list_prs" -> github.listPullRequests(a.getString("full_name"))
                "list_issues" -> github.listIssues(a.getString("full_name"))
                "get_file" -> github.getFileContent(a.getString("full_name"), a.getString("path"))
                "write_file" -> github.createOrUpdateFile(a.getString("full_name"), a.getString("path"), a.getString("content"), a.getString("message"))
                "start_monitoring" -> startMonitoring(a.getString("full_name"), a.getInt("interval_minutes"))
                "stop_monitoring" -> stopMonitoring(a.getString("full_name"))
                else -> "Noma'lum funksiya"
            }
        } catch (e: Exception) { "Xatolik: ${e.message}" }
    }

    private fun startMonitoring(repoFullName: String, interval: Int): String {
        if (context == null) return "Xatolik: Context topilmadi."
        val workRequest = PeriodicWorkRequestBuilder<RepoMonitorWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf("repo_full_name" to repoFullName))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("monitor_$repoFullName", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
        return "$repoFullName monitoringi har $interval daqiqada ishga tushirildi."
    }

    private fun stopMonitoring(repoFullName: String): String {
        if (context == null) return "Xatolik: Context topilmadi."
        WorkManager.getInstance(context).cancelUniqueWork("monitor_$repoFullName")
        return "$repoFullName monitoringi to'xtatildi."
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
        return "Amal bajarilmadi."
    }
}
