package com.safar.ghagent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub akkauntni boshqarish uchun oddiy REST wrapper.
 * Har bir metod natijani JSON string ko'rinishida qaytaradi (Gemini'ga qaytarish uchun qulay).
 */
class GitHubClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()
    private val base = "https://api.github.com"

    private fun req(
        method: String,
        path: String,
        body: JSONObject? = null
    ): String {
        val urlStr = if (path.startsWith("http")) path else "$base$path"
        val builder = Request.Builder()
            .url(urlStr)
            .addHeader("Authorization", "token $token")
            .addHeader("Accept", "application/vnd.github+json")

        val rb = body?.toString()?.toRequestBody(jsonMedia)

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(rb ?: JSONObject().toString().toRequestBody(jsonMedia))
            "PUT" -> builder.put(rb ?: JSONObject().toString().toRequestBody(jsonMedia))
            "PATCH" -> builder.patch(rb ?: JSONObject().toString().toRequestBody(jsonMedia))
            "DELETE" -> builder.delete(rb)
        }

        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            return if (resp.isSuccessful) text else "XATOLIK ${resp.code}: $text"
        }
    }

    fun listRepos(): String = req("GET", "/user/repos?per_page=100&sort=updated")

    fun getRepo(fullName: String): String = req("GET", "/repos/$fullName")

    fun createRepo(name: String, description: String, isPrivate: Boolean): String {
        val body = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
        }
        return req("POST", "/user/repos", body)
    }

    fun listIssues(fullName: String): String = req("GET", "/repos/$fullName/issues?state=open")

    fun createIssue(fullName: String, title: String, bodyText: String): String {
        val body = JSONObject().apply {
            put("title", title)
            put("body", bodyText)
        }
        return req("POST", "/repos/$fullName/issues", body)
    }

    fun listCommits(fullName: String): String = req("GET", "/repos/$fullName/commits?per_page=20")

    fun getFileContent(fullName: String, path: String): String =
        req("GET", "/repos/$fullName/contents/$path")

    /** Fayl yaratish yoki yangilash. Agar fayl mavjud bo'lsa, avval SHA olinadi. */
    fun createOrUpdateFile(fullName: String, path: String, content: String, message: String): String {
        var sha: String? = null
        val existing = req("GET", "/repos/$fullName/contents/$path")
        if (!existing.startsWith("XATOLIK")) {
            try {
                sha = JSONObject(existing).getString("sha")
            } catch (_: Exception) { }
        }
        val body = JSONObject().apply {
            put("message", message)
            put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
            if (sha != null) put("sha", sha)
        }
        return req("PUT", "/repos/$fullName/contents/$path", body)
    }

    fun starRepo(fullName: String): String = req("PUT", "/user/starred/$fullName")

    fun searchCode(query: String): String =
        req("GET", "/search/code?q=${java.net.URLEncoder.encode(query, "UTF-8")}")

    fun listNotifications(): String = req("GET", "/notifications")

    fun deleteRepo(fullName: String): String = req("DELETE", "/repos/$fullName")

    fun listActionsRuns(fullName: String): String = req("GET", "/repos/$fullName/actions/runs?per_page=10")

    fun getActionRunLog(fullName: String, runId: Long): String = req("GET", "/repos/$fullName/actions/runs/$runId/logs")
}
