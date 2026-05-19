package com.phase3.tracker.data

import com.phase3.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight Supabase REST client using OkHttp.
 * Talks directly to the PostgREST API — no Supabase SDK needed.
 */
class SupabaseClient {

    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_ANON_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private fun baseHeaders(): Map<String, String> = mapOf(
        "apikey" to apiKey,
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json"
    )

    // ── GET ──────────────────────────────────────────────────────────

    suspend fun fetchTowers(): Result<JSONArray> = get("towers?select=*")

    suspend fun fetchActivities(towerId: Int): Result<JSONArray> =
        get("activities?tower_id=eq.$towerId&select=*&order=sort_order.asc")

    suspend fun fetchFlatStatuses(activityId: Int): Result<JSONArray> =
        get("flat_statuses?activity_id=eq.$activityId&select=*")

    /** Fetch ALL flat statuses for a tower in one call (join through activities) */
    suspend fun fetchAllFlatStatusesForTower(towerId: Int): Result<JSONArray> =
        get("flat_statuses?select=*,activities!inner(tower_id)&activities.tower_id=eq.$towerId")

    // ── POST / UPSERT ───────────────────────────────────────────────

    /** Insert a new activity, returns the created row */
    suspend fun insertActivity(payload: JSONObject): Result<JSONArray> =
        post("activities", payload.toString(), prefer = "return=representation")

    /** Batch upsert flat statuses (uses composite PK for conflict resolution) */
    suspend fun upsertFlatStatuses(statuses: JSONArray): Result<JSONArray> =
        post(
            "flat_statuses",
            statuses.toString(),
            prefer = "return=minimal,resolution=merge-duplicates"
        )

    // ── PATCH ───────────────────────────────────────────────────────

    /** Update an existing activity by ID */
    suspend fun updateActivity(activityId: Int, payload: JSONObject): Result<JSONArray> =
        patch("activities?id=eq.$activityId", payload.toString())

    // ── DELETE ───────────────────────────────────────────────────────

    /** Delete an activity (cascade deletes its flat_statuses) */
    suspend fun deleteActivity(activityId: Int): Result<Unit> =
        delete("activities?id=eq.$activityId")

    // ══════════════════════════════════════════════════════════════════
    // Aluminium Doors & Windows
    // ══════════════════════════════════════════════════════════════════

    // ── DW Types ────────────────────────────────────────────────────
    suspend fun fetchDWTypes(): Result<JSONArray> = get("dw_types?select=*&order=name.asc")

    suspend fun insertDWType(payload: JSONObject): Result<JSONArray> =
        post("dw_types", payload.toString())

    suspend fun updateDWType(id: Int, payload: JSONObject): Result<JSONArray> =
        patch("dw_types?id=eq.$id", payload.toString())

    suspend fun deleteDWType(id: Int): Result<Unit> = delete("dw_types?id=eq.$id")

    // ── DW Rooms ────────────────────────────────────────────────────
    suspend fun fetchDWRooms(towerId: Int, columnType: String): Result<JSONArray> =
        get("dw_rooms?tower_id=eq.$towerId&column_type=eq.$columnType&select=*&order=sort_order.asc")

    suspend fun fetchAllDWRooms(towerId: Int): Result<JSONArray> =
        get("dw_rooms?tower_id=eq.$towerId&select=*&order=sort_order.asc")

    suspend fun insertDWRoom(payload: JSONObject): Result<JSONArray> =
        post("dw_rooms", payload.toString())

    suspend fun updateDWRoom(id: Int, payload: JSONObject): Result<JSONArray> =
        patch("dw_rooms?id=eq.$id", payload.toString())

    suspend fun deleteDWRoom(id: Int): Result<Unit> = delete("dw_rooms?id=eq.$id")

    // ── DW Room Types (junction) ────────────────────────────────────
    suspend fun fetchDWRoomTypes(roomId: Int): Result<JSONArray> =
        get("dw_room_types?room_id=eq.$roomId&select=*,dw_types(*)")

    suspend fun insertDWRoomType(payload: JSONObject): Result<JSONArray> =
        post("dw_room_types", payload.toString())

    suspend fun deleteDWRoomType(roomId: Int, typeId: Int): Result<Unit> =
        delete("dw_room_types?room_id=eq.$roomId&type_id=eq.$typeId")

    /** Replace all type assignments for a room */
    suspend fun replaceDWRoomTypes(roomId: Int, typeIds: List<Int>): Result<Unit> {
        // Delete existing
        delete("dw_room_types?room_id=eq.$roomId")
        // Insert new
        if (typeIds.isNotEmpty()) {
            val arr = JSONArray()
            typeIds.forEach { typeId ->
                arr.put(JSONObject().apply {
                    put("room_id", roomId)
                    put("type_id", typeId)
                })
            }
            post("dw_room_types", arr.toString(), prefer = "return=minimal,resolution=merge-duplicates")
        }
        return Result.success(Unit)
    }

    // ── DW Statuses ─────────────────────────────────────────────────
    suspend fun fetchDWStatuses(roomId: Int): Result<JSONArray> =
        get("dw_statuses?room_id=eq.$roomId&select=*")

    suspend fun deleteDWStatusesForRoom(roomId: Int): Result<Unit> =
        delete("dw_statuses?room_id=eq.$roomId")

    suspend fun upsertDWStatuses(statuses: JSONArray): Result<JSONArray> =
        post("dw_statuses", statuses.toString(), prefer = "return=minimal,resolution=merge-duplicates")

    // ── Core HTTP helpers ────────────────────────────────────────────

    private suspend fun get(path: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/$path")
                .apply { baseHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"

            if (response.isSuccessful) {
                Result.success(JSONArray(body))
            } else {
                Result.failure(Exception("GET /$path failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun post(
        path: String,
        json: String,
        prefer: String = "return=representation"
    ): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/$path")
                .apply { baseHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .addHeader("Prefer", prefer)
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"

            if (response.isSuccessful) {
                // "return=minimal" produces empty body
                val arr = if (body.isBlank() || body == "null") JSONArray() else {
                    try { JSONArray(body) } catch (_: Exception) { JSONArray().put(JSONObject(body)) }
                }
                Result.success(arr)
            } else {
                Result.failure(Exception("POST /$path failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun patch(path: String, json: String): Result<JSONArray> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/$path")
                    .apply { baseHeaders().forEach { (k, v) -> addHeader(k, v) } }
                    .addHeader("Prefer", "return=representation")
                    .patch(json.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "[]"

                if (response.isSuccessful) {
                    val arr = try { JSONArray(body) } catch (_: Exception) { JSONArray().put(JSONObject(body)) }
                    Result.success(arr)
                } else {
                    Result.failure(Exception("PATCH /$path failed: ${response.code} $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/$path")
                .apply { baseHeaders().forEach { (k, v) -> addHeader(k, v) } }
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("DELETE /$path failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
