package com.safar.ghagent

import org.json.JSONArray
import org.json.JSONObject

class AgentEngine(
    private val gemini: GeminiClient,
    private val github: GitHubClient
) {
    private val history = JSONArray()

    private val systemInstruction = """
        Siz foydalanuvchining GitHub akkauntini TO'LIQ boshqaradigan super AI agentsiz.
        Sizda repozitoriyalar yaratish, o'chirish, PRlar bilan ishlash, issue'larni boshqarish, release'lar yaratish,
        collaborator'larni qo'shish va Actions'larni boshqarish huquqi bor.
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

        // PRs
        list.put(fn("list_prs", "Pull requestlarni ro'yxatlaydi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("create_pr", "Yangi Pull Request yaratadi",
            JSONObject().put("full_name", strProp("owner/repo")).put("title", strProp("Sarlavha")).put("head", strProp("Head branch")).put("base", strProp("Base branch")).put("body", strProp("Tavsif")),
            JSONArray().put("full_name").put("title").put("head").put("base")))
        list.put(fn("merge_pr", "Pull Requestni merge qiladi",
            JSONObject().put("full_name", strProp("owner/repo")).put("pr_number", intProp("PR raqami")),
            JSONArray().put("full_name").put("pr_number")))

        // Issues
        list.put(fn("list_issues", "Issue'larni ro'yxatlaydi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("create_issue", "Yangi issue yaratadi",
            JSONObject().put("full_name", strProp("owner/repo")).put("title", strProp("Sarlavha")).put("body", strProp("Matn")),
            JSONArray().put("full_name").put("title")))
        list.put(fn("close_issue", "Issue'ni yopadi",
            JSONObject().put("full_name", strProp("owner/repo")).put("issue_number", intProp("Issue raqami")),
            JSONArray().put("full_name").put("issue_number")))

        // Files
        list.put(fn("get_file", "Fayl mazmunini o'qiydi",
            JSONObject().put("full_name", strProp("owner/repo")).put("path", strProp("Yo'l")),
            JSONArray().put("full_name").put("path")))
        list.put(fn("write_file", "Fayl yaratadi yoki yangilaydi",
            JSONObject().put("full_name", strProp("owner/repo")).put("path", strProp("Yo'l")).put("content", strProp("Matn")).put("message", strProp("Commit xabari")),
            JSONArray().put("full_name").put("path").put("content").put("message")))

        // Actions
        list.put(fn("list_runs", "Actions buildlarini ko'radi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("rerun_workflow", "Workflow'ni qayta ishga tushiradi",
            JSONObject().put("full_name", strProp("owner/repo")).put("run_id", intProp("Run ID")),
            JSONArray().put("full_name").put("run_id")))

        // Others
        list.put(fn("list_collaborators", "Collaboratorlarni ro'yxatlaydi",
            JSONObject().put("full_name", strProp("owner/repo")), JSONArray().put("full_name")))
        list.put(fn("add_collaborator", "Collaborator qo'shadi",
            JSONObject().put("full_name", strProp("owner/repo")).put("username", strProp("Username")).put("permission", strProp("Huquq: pull, push, admin")),
            JSONArray().put("full_name").put("username")))
        list.put(fn("search_repos", "Repo qidiradi", JSONObject().put("query", strProp("So'rov")), JSONArray().put("query")))
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
                "list_prs" -> github.listPullRequests(a.getString("full_name"))
                "create_pr" -> github.createPullRequest(a.getString("full_name"), a.getString("title"), a.getString("head"), a.getString("base"), a.optString("body", ""))
                "merge_pr" -> github.mergePullRequest(a.getString("full_name"), a.getInt("pr_number"))
                "list_issues" -> github.listIssues(a.getString("full_name"))
                "create_issue" -> github.createIssue(a.getString("full_name"), a.getString("title"), a.optString("body", ""))
                "close_issue" -> github.closeIssue(a.getString("full_name"), a.getInt("issue_number"))
                "get_file" -> github.getFileContent(a.getString("full_name"), a.getString("path"))
                "write_file" -> github.createOrUpdateFile(a.getString("full_name"), a.getString("path"), a.getString("content"), a.getString("message"))
                "list_runs" -> github.listActionsRuns(a.getString("full_name"))
                "rerun_workflow" -> github.rerunWorkflow(a.getString("full_name"), a.getLong("run_id"))
                "list_collaborators" -> github.listCollaborators(a.getString("full_name"))
                "add_collaborator" -> github.addCollaborator(a.getString("full_name"), a.getString("username"), a.optString("permission", "push"))
                "search_repos" -> github.searchRepos(a.getString("query"))
                "get_my_info" -> github.getAuthenticatedUser()
                else -> "Noma'lum funksiya"
            }
        } catch (e: Exception) { "Xatolik: ${e.message}" }
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
                history.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", finalText)))
                })
                return finalText
            }
            val modelParts = JSONArray()
            for (fc in turn.functionCalls) {
                modelParts.put(JSONObject().apply {
                    put("functionCall", JSONObject().apply { put("name", fc.name); put("args", fc.args) })
                })
            }
            history.put(JSONObject().apply { put("role", "model"); put("parts", modelParts) })
            val responseParts = JSONArray()
            for (fc in turn.functionCalls) {
                val result = executeFunction(fc)
                responseParts.put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply { put("name", fc.name); put("response", JSONObject().put("result", result)) })
                })
            }
            history.put(JSONObject().apply { put("role", "user"); put("parts", responseParts) })
        }
        return "Juda ko'p qadam."
    }
}
