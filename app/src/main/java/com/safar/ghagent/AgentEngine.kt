package com.safar.ghagent

import org.json.JSONArray
import org.json.JSONObject

class AgentEngine(
    private val gemini: GeminiClient,
    private val github: GitHubClient
) {
    private val history = JSONArray()

    private val systemInstruction = """
        Siz foydalanuvchining GitHub akkauntini boshqaradigan AI agentsiz.
        Har doim mavjud funksiyalardan (tools) foydalanib amal bajaring, taxmin qilmang.
        Javobni foydalanuvchi tushunadigan tilda (o'zbekcha) qisqa va aniq bering.
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
        fun boolProp(desc: String) = JSONObject().put("type", "BOOLEAN").put("description", desc)

        val list = JSONArray()

        list.put(fn("list_repos", "Foydalanuvchining barcha repositoriyalarini ro'yxatlaydi", JSONObject()))

        list.put(fn("get_repo", "Bitta repo haqida ma'lumot oladi",
            JSONObject().put("full_name", strProp("owner/repo formatida")),
            JSONArray().put("full_name")))

        list.put(fn("create_repo", "Yangi repository yaratadi",
            JSONObject()
                .put("name", strProp("Repo nomi"))
                .put("description", strProp("Repo tavsifi"))
                .put("is_private", boolProp("true bo'lsa private repo")),
            JSONArray().put("name")))

        list.put(fn("list_issues", "Repodagi ochiq issue'larni ro'yxatlaydi",
            JSONObject().put("full_name", strProp("owner/repo formatida")),
            JSONArray().put("full_name")))

        list.put(fn("create_issue", "Repoda yangi issue ochadi",
            JSONObject()
                .put("full_name", strProp("owner/repo formatida"))
                .put("title", strProp("Issue sarlavhasi"))
                .put("body", strProp("Issue matni")),
            JSONArray().put("full_name").put("title")))

        list.put(fn("list_commits", "Repodagi so'nggi commitlarni ro'yxatlaydi",
            JSONObject().put("full_name", strProp("owner/repo formatida")),
            JSONArray().put("full_name")))

        list.put(fn("get_file_content", "Repodagi faylning mazmunini o'qiydi",
            JSONObject()
                .put("full_name", strProp("owner/repo formatida"))
                .put("path", strProp("Fayl yo'li, masalan README.md")),
            JSONArray().put("full_name").put("path")))

        list.put(fn("create_or_update_file", "Repoda fayl yaratadi yoki mavjudini yangilaydi (commit qiladi)",
            JSONObject()
                .put("full_name", strProp("owner/repo formatida"))
                .put("path", strProp("Fayl yo'li"))
                .put("content", strProp("Faylning to'liq matni"))
                .put("message", strProp("Commit xabari")),
            JSONArray().put("full_name").put("path").put("content").put("message")))

        list.put(fn("star_repo", "Repoga star qo'yadi",
            JSONObject().put("full_name", strProp("owner/repo formatida")),
            JSONArray().put("full_name")))

        list.put(fn("search_code", "GitHub bo'ylab kod qidiradi",
            JSONObject().put("query", strProp("Qidiruv so'rovi")),
            JSONArray().put("query")))

        list.put(fn("list_notifications", "Foydalanuvchining GitHub bildirishnomalarini oladi", JSONObject()))

        return list
    }

    private fun executeFunction(call: FunctionCall): String {
        val a = call.args
        return try {
            when (call.name) {
                "list_repos" -> github.listRepos()
                "get_repo" -> github.getRepo(a.getString("full_name"))
                "create_repo" -> github.createRepo(
                    a.getString("name"),
                    a.optString("description", ""),
                    a.optBoolean("is_private", false)
                )
                "list_issues" -> github.listIssues(a.getString("full_name"))
                "create_issue" -> github.createIssue(
                    a.getString("full_name"), a.getString("title"), a.optString("body", "")
                )
                "list_commits" -> github.listCommits(a.getString("full_name"))
                "get_file_content" -> github.getFileContent(a.getString("full_name"), a.getString("path"))
                "create_or_update_file" -> github.createOrUpdateFile(
                    a.getString("full_name"), a.getString("path"),
                    a.getString("content"), a.getString("message")
                )
                "star_repo" -> github.starRepo(a.getString("full_name"))
                "search_code" -> github.searchCode(a.getString("query"))
                "list_notifications" -> github.listNotifications()
                else -> "Noma'lum funksiya: ${call.name}"
            }
        } catch (e: Exception) {
            "Bajarishda xatolik: ${e.message}"
        }
    }

    /** Foydalanuvchi xabarini qabul qilib, agent tsiklini ishga tushiradi, yakuniy matnni qaytaradi */
    fun handleUserMessage(userText: String): String {
        history.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        })

        // Cheksiz tsikldan saqlanish uchun limit
        repeat(8) {
            val turn = gemini.sendMessage(history, tools(), systemInstruction)

            if (turn.functionCalls.isEmpty()) {
                val finalText = turn.text ?: "(bo'sh javob)"
                history.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", finalText)))
                })
                return finalText
            }

            // Model funksiya(lar) chaqirmoqchi — bularni tarixga qo'shamiz
            val modelParts = JSONArray()
            for (fc in turn.functionCalls) {
                modelParts.put(JSONObject().apply {
                    put("functionCall", JSONObject().apply {
                        put("name", fc.name)
                        put("args", fc.args)
                    })
                })
            }
            history.put(JSONObject().apply {
                put("role", "model")
                put("parts", modelParts)
            })

            // Har bir funksiyani bajarib, natijani yuboramiz
            val responseParts = JSONArray()
            for (fc in turn.functionCalls) {
                val result = executeFunction(fc)
                responseParts.put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", fc.name)
                        put("response", JSONObject().put("result", result))
                    })
                })
            }
            history.put(JSONObject().apply {
                put("role", "user")
                put("parts", responseParts)
            })
        }

        return "Amal juda ko'p qadamga cho'zildi, to'xtatildi."
    }
}
