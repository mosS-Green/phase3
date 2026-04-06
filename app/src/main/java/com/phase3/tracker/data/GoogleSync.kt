package com.phase3.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GoogleSync {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadExcel(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "https://docs.google.com/spreadsheets/d/1nDPPHSnNkp752_-f8dO1qMSjItwxyZg5OlNs_T1wvcc/export?format=xlsx"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("Empty file content"))
                }
            } else {
                Result.failure(Exception("HTTP error ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCell(sheetName: String, row: Int, col: Int, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val webhookUrl = "https://script.google.com/macros/s/AKfycbwvGJpoGHqqeB1Bwkbcoj_EDz-TmF__tVmd1iRX6OLKOZx5Uhz3uxwt0FQ3YXBR9qEKPA/exec"
            val payload = JSONObject().apply {
                put("action", "updateCell")
                put("sheetName", sheetName)
                put("row", row)
                put("col", col)
                put("value", value)
            }
            
            val body = payload.toString().toRequestBody("text/plain;charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()
                
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Webhook returned ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
