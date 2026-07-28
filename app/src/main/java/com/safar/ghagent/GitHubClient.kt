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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()
    private val base = "https://api.github.com"

    private fun req(method: String, path: String, body: JSONObject? = null): String {
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

    // --- Repositories ---
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
    fun updateRepo(fullName: String, body: JSONObject): String = req("PATCH", "/repos/$fullName", body)
    fun deleteRepo(fullName: String): String = req("DELETE", "/repos/$fullName")

    // --- Pull Requests ---
    fun listPullRequests(fullName: String): String = req("GET", "/repos/$fullName/pulls")
    fun createPullRequest(fullName: String, title: String, head: String, base: String, bodyText: String): String {
        val body = JSONObject().apply {
            put("title", title)
            put("head", head)
            put("base", base)
            put("body", bodyText)
        }
        return req("POST", "/repos/$fullName/pulls", body)
    }
    fun mergePullRequest(fullName: String, prNumber: Int): String = req("PUT", "/repos/$fullName/pulls/$prNumber/merge")

    // --- Issues ---
    fun listIssues(fullName: String): String = req("GET", "/repos/$fullName/issues?state=all")
    fun createIssue(fullName: String, title: String, bodyText: String): String {
        val body = JSONObject().apply { put("title", title); put("body", bodyText) }
        return req("POST", "/repos/$fullName/issues", body)
    }
    fun closeIssue(fullName: String, issueNumber: Int): String {
        val body = JSONObject().put("state", "closed")
        return req("PATCH", "/repos/$fullName/issues/$issueNumber", body)
    }

    // --- Contents & Commits ---
    fun listCommits(fullName: String): String = req("GET", "/repos/$fullName/commits?per_page=20")
    fun getFileContent(fullName: String, path: String): String = req("GET", "/repos/$fullName/contents/$path")
    fun createOrUpdateFile(fullName: String, path: String, content: String, message: String): String {
        var sha: String? = null
        val existing = req("GET", "/repos/$fullName/contents/$path")
        if (!existing.startsWith("XATOLIK")) {
            try { sha = JSONObject(existing).getString("sha") } catch (_: Exception) { }
        }
        val body = JSONObject().apply {
            put("message", message)
            put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
            if (sha != null) put("sha", sha)
        }
        return req("PUT", "/repos/$fullName/contents/$path", body)
    }

    // --- Releases ---
    fun listReleases(fullName: String): String = req("GET", "/repos/$fullName/releases")
    fun createRelease(fullName: String, tagName: String, name: String, bodyText: String): String {
        val body = JSONObject().apply {
            put("tag_name", tagName)
            put("name", name)
            put("body", bodyText)
        }
        return req("POST", "/repos/$fullName/releases", body)
    }

    // --- Collaborators & Teams ---
    fun listCollaborators(fullName: String): String = req("GET", "/repos/$fullName/collaborators")
    fun addCollaborator(fullName: String, username: String, permission: String): String {
        val body = JSONObject().put("permission", permission)
        return req("PUT", "/repos/$fullName/collaborators/$username", body)
    }

    // --- Actions ---
    fun listActionsRuns(fullName: String): String = req("GET", "/repos/$fullName/actions/runs?per_page=10")
    fun getActionRun(fullName: String, runId: Long): String = req("GET", "/repos/$fullName/actions/runs/$runId")
    fun rerunWorkflow(fullName: String, runId: Long): String = req("POST", "/repos/$fullName/actions/runs/$runId/rerun")

    // --- Search & Notifications ---
    fun searchRepos(query: String): String = req("GET", "/search/repositories?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
    fun searchCode(query: String): String = req("GET", "/search/code?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
    fun listNotifications(): String = req("GET", "/notifications")
    fun starRepo(fullName: String): String = req("PUT", "/user/starred/$fullName")
    fun unstarRepo(fullName: String): String = req("DELETE", "/user/starred/$fullName")

    // --- User Info ---
    fun getAuthenticatedUser(): String = req("GET", "/user")
}
