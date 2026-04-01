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
                // Try to pin the uploaded file so it can be reliably found by bots
                try {
                    val jsonResponse = org.json.JSONObject(body)
                    val messageId = jsonResponse.optJSONObject("result")?.optInt("message_id")
                    if (messageId != null && messageId > 0) {
                        val pinRequest = Request.Builder()
                            .url("${BASE_URL}${BOT_TOKEN}/pinChatMessage")
                            .post(
                                okhttp3.FormBody.Builder()
                                    .add("chat_id", CHAT_ID)
                                    .add("message_id", messageId.toString())
                                    .build()
                            )
                            .build()
                        client.newCall(pinRequest).execute()
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // pinning is best-effort
                }
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
            var relevantFileId: String? = null

            // First try to find it in the pinned message of the chat
            try {
                val chatRequest = Request.Builder()
                    .url("${BASE_URL}${BOT_TOKEN}/getChat?chat_id=$CHAT_ID")
                    .get()
                    .build()
                val chatResponse = client.newCall(chatRequest).execute()
                if (chatResponse.isSuccessful) {
                    val chatBody = chatResponse.body?.string() ?: ""
                    val pinnedContent = org.json.JSONObject(chatBody)
                        .optJSONObject("result")?.optJSONObject("pinned_message")
                    val doc = pinnedContent?.optJSONObject("document")
                    if (doc != null) {
                        val fileId = doc.optString("file_id")
                        if (!fileId.isNullOrEmpty()) {
                            relevantFileId = fileId
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback to getUpdates
            if (relevantFileId == null) {
                val updatesRequest = Request.Builder()
                    .url("${BASE_URL}${BOT_TOKEN}/getUpdates?limit=100")
                    .get()
                    .build()

                val updatesResponse = client.newCall(updatesRequest).execute()
                val updatesBody = updatesResponse.body?.string() ?: ""

                if (!updatesResponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to get updates"))
                }

                try {
                    val jsonBody = org.json.JSONObject(updatesBody)
                    val results = jsonBody.optJSONArray("result")
                    if (results != null) {
                        for (i in results.length() - 1 downTo 0) {
                            val update = results.optJSONObject(i) ?: continue
                            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post")
                            val doc = msg?.optJSONObject("document")
                            if (doc != null) {
                                val fileName = doc.optString("file_name", "").lowercase()
                                val mimeType = doc.optString("mime_type", "").lowercase()
                                if (fileName.endsWith(".xlsx") || mimeType.contains("spreadsheet")) {
                                    relevantFileId = doc.optString("file_id")
                                    if (!relevantFileId.isNullOrEmpty()) {
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (relevantFileId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No Excel files found in chat"))
            }

            // Step 2: Get file path
            val fileRequest = Request.Builder()
                .url("${BASE_URL}${BOT_TOKEN}/getFile?file_id=$relevantFileId")
                .get()
                .build()

            val fileResponse = client.newCall(fileRequest).execute()
            val fileBody = fileResponse.body?.string() ?: ""

            // We can parse the file_path safely now using JSON
            val filePath = try {
                org.json.JSONObject(fileBody).optJSONObject("result")?.optString("file_path")
            } catch (e: Exception) { null }

            if (filePath.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Could not get file path"))
            }

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
