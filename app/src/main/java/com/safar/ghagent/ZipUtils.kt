package com.safar.ghagent

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ZipUtils {
    fun unzipAndUpload(context: Context, zipUri: Uri, repoFullName: String, github: GitHubClient, callback: (String) -> Unit) {
        val inputStream = context.contentResolver.openInputStream(zipUri) ?: return
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        
        val results = mutableListOf<String>()
        
        while (entry != null) {
            if (!entry.isDirectory) {
                val fileName = entry.name
                val content = zipInputStream.readBytes()
                val base64Content = android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP)
                
                // GitHub-ga yuklash
                val result = github.createOrUpdateFile(
                    repoFullName,
                    fileName,
                    String(content), // Oddiy matn sifatida (rasmlar uchun GitHubClient-ni yangilash kerak bo'lishi mumkin)
                    "Auto-upload from ZIP: $fileName"
                )
                results.add("$fileName: $result")
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        callback(results.joinToString("\n"))
    }
}
