package com.phase3.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramSync {

    companion object {
        // Read from BuildConfig (injected via CI / GitHub Secrets)
        val BOT_TOKEN = com.phase3.tracker.BuildConfig.BOT_TOKEN
        val CHAT_ID = com.phase3.tracker.BuildConfig.CHAT_ID

        private const val BASE_URL = "https://api.telegram.org/bot"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(file: File, deviceName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("caption", "sent from $deviceName")
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("${BASE_URL}${BOT_TOKEN}/sendDocument")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.contains("\"ok\":true")) {
                Result.success("Upload successful!")
            } else {
                Result.failure(Exception("Upload failed: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadLatestFile(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get updates to find latest document
            val updatesRequest = Request.Builder()
                .url("${BASE_URL}${BOT_TOKEN}/getUpdates?limit=100")
                .get()
                .build()

            val updatesResponse = client.newCall(updatesRequest).execute()
            val updatesBody = updatesResponse.body?.string() ?: ""

            if (!updatesResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get updates"))
            }

            // Parse file_id from latest document message
            val fileIdRegex = """"file_id"\s*:\s*"([^"]+)"""".toRegex()
            val fileNameRegex = """"file_name"\s*:\s*"([^"]*\.xlsx)"""".toRegex()

            // Find all xlsx file_ids - we want the latest one
            val fileNameMatches = fileNameRegex.findAll(updatesBody).toList()
            if (fileNameMatches.isEmpty()) {
                return@withContext Result.failure(Exception("No Excel files found in chat"))
            }

            // Get the file_id that appears closest to the last xlsx filename match
            val lastXlsxPos = fileNameMatches.last().range.first
            val allFileIds = fileIdRegex.findAll(updatesBody).toList()

            // Find the file_id right before the xlsx filename
            val relevantFileId = allFileIds
                .filter { it.range.first < lastXlsxPos }
                .maxByOrNull { it.range.first }
                ?.groupValues?.get(1)
                ?: return@withContext Result.failure(Exception("Could not find file ID"))

            // Step 2: Get file path
            val fileRequest = Request.Builder()
                .url("${BASE_URL}${BOT_TOKEN}/getFile?file_id=$relevantFileId")
                .get()
                .build()

            val fileResponse = client.newCall(fileRequest).execute()
            val fileBody = fileResponse.body?.string() ?: ""

            val filePathRegex = """"file_path"\s*:\s*"([^"]+)"""".toRegex()
            val filePath = filePathRegex.find(fileBody)?.groupValues?.get(1)
                ?: return@withContext Result.failure(Exception("Could not get file path"))

            // Step 3: Download the file
            val downloadRequest = Request.Builder()
                .url("https://api.telegram.org/file/bot${BOT_TOKEN}/$filePath")
                .get()
                .build()

            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed"))
            }

            val bytes = downloadResponse.body?.bytes()
                ?: return@withContext Result.failure(Exception("Empty response"))

            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
