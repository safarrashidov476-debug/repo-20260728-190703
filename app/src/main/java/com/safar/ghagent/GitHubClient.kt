package com.safar.ghagent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()
    private val base = "https://api.github.com"

    private fun req(method: String, path: String, body: JSONObject? = null): String {
        val urlStr = if (path.startsWith("http")) path else "$base$path"
        val builder = Request.Builder()
            .url(urlStr)
            .addHeader("Authorization", "token $token")
            .addHeader("Accept", "application/vnd.github+json")

        val rb = if (body != null) body.toString().toRequestBody(jsonMedia) 
                 else if (method == "POST" || method == "PUT" || method == "PATCH") "{}".toRequestBody(jsonMedia)
                 else null

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(rb!!)
            "PUT" -> builder.put(rb!!)
            "PATCH" -> builder.patch(rb!!)
            "DELETE" -> builder.delete()
        }

        return try {
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) text else "XATOLIK ${resp.code}: $text"
            }
        } catch (e: Exception) {
            "ALOQA XATOSI: ${e.message}"
        }
    }

    // Repos
    fun listRepos() = req("GET", "/user/repos?per_page=100&sort=updated")
    fun getRepo(fullName: String) = req("GET", "/repos/$fullName")
    fun createRepo(name: String, desc: String, private: Boolean) = 
        req("POST", "/user/repos", JSONObject().put("name", name).put("description", desc).put("private", private))
    fun deleteRepo(fullName: String) = req("DELETE", "/repos/$fullName")

    // Actions
    fun listActionsRuns(fullName: String) = req("GET", "/repos/$fullName/actions/runs?per_page=20")
    fun getActionRunLog(fullName: String, runId: Long) = req("GET", "/repos/$fullName/actions/runs/$runId/logs")
    fun rerunWorkflow(fullName: String, runId: Long) = req("POST", "/repos/$fullName/actions/runs/$runId/rerun")

    // PRs & Issues
    fun listPullRequests(fullName: String) = req("GET", "/repos/$fullName/pulls")
    fun listIssues(fullName: String) = req("GET", "/repos/$fullName/issues")

    // Files
    fun getFileContent(fullName: String, path: String) = req("GET", "/repos/$fullName/contents/$path")
    fun createOrUpdateFile(fullName: String, path: String, content: String, message: String): String {
        var sha: String? = null
        val existing = req("GET", "/repos/$fullName/contents/$path")
        if (!existing.startsWith("XATOLIK")) {
            try { sha = JSONObject(existing).getString("sha") } catch (_: Exception) {}
        }
        val body = JSONObject().apply {
            put("message", message)
            put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
            if (sha != null) put("sha", sha)
        }
        return req("PUT", "/repos/$fullName/contents/$path", body)
    }

    fun getAuthenticatedUser() = req("GET", "/user")
}
