package com.safar.ghagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject

class RepoMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repoFullName = inputData.getString("repo_full_name") ?: return Result.failure()
        val githubToken = Prefs.getGithubToken(applicationContext)
        val github = GitHubClient(githubToken)

        try {
            val runsInfo = github.listActionsRuns(repoFullName)
            if (runsInfo.startsWith("XATOLIK")) return Result.retry()

            val json = JSONObject(runsInfo)
            val runs = json.getJSONArray("workflow_runs")
            
            if (runs.length() > 0) {
                val latestRun = runs.getJSONObject(0)
                val status = latestRun.getString("status")
                val conclusion = latestRun.optString("conclusion", "running")

                sendNotification("Repo Monitoring", "Repo: $repoFullName\nStatus: $status\nConclusion: $conclusion")
            }
        } catch (e: Exception) {
            // Log error but don't fail the worker
        }

        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "repo_monitor_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Repo Monitoring", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
