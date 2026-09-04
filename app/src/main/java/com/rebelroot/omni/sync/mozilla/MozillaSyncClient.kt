package com.rebelroot.omni.sync.mozilla

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class TokenServerResponse(
    val id: String,
    val key: String,
    val apiEndpoint: String,
    val durationSeconds: Long,
    val hashAlgorithm: String = "sha256"
)

data class BsoRecord(
    val id: String,
    val modified: Double = System.currentTimeMillis() / 1000.0,
    val payload: String,
    val sortindex: Int? = null,
    val ttl: Int? = null
)

sealed class SyncClientResult<out T> {
    data class Success<out T>(
        val data: T,
        val serverTimestamp: Double = 0.0,
        val backoffSeconds: Long = 0L
    ) : SyncClientResult<T>()

    data class Failure(
        val statusCode: Int,
        val errorMessage: String,
        val isAuthError: Boolean = (statusCode == 401 || statusCode == 403),
        val backoffSeconds: Long = 0L
    ) : SyncClientResult<Nothing>()
}

class MozillaSyncClient(
    private val tokenServerUrl: String = "https://token.services.mozilla.com/1.0/sync/1.5"
) {

    /**
     * Exchanges FxA OAuth access token for storage node endpoint and authentication keys.
     */
    fun fetchStorageCredentials(accessToken: String, syncKey: String? = null): SyncClientResult<TokenServerResponse> {
        return try {
            val url = URL(tokenServerUrl)
            val authHeader = if (!syncKey.isNullOrBlank()) {
                generateHawkHeader(accessToken, syncKey, "GET", url)
            } else {
                "Bearer $accessToken"
            }

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val response = TokenServerResponse(
                    id = json.optString("id", json.optString("uid", "user_storage")),
                    key = json.optString("key", "sync_master_key"),
                    apiEndpoint = json.optString("api_endpoint", "https://sync-1-5.sync.services.mozilla.com/1.5/"),
                    durationSeconds = json.optLong("duration", 3600L),
                    hashAlgorithm = json.optString("hashalg", "sha256")
                )
                SyncClientResult.Success(response, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "TokenServer returned status $statusCode"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(
                statusCode = -1,
                errorMessage = e.message ?: "Network error connecting to TokenServer"
            )
        }
    }

    /**
     * Queries last modified timestamps for each collection (bookmarks, tabs, history, passwords).
     */
    fun fetchCollectionTimestamps(
        apiEndpoint: String,
        authToken: String
    ): SyncClientResult<Map<String, Double>> {
        return try {
            val endpoint = if (apiEndpoint.endsWith("/")) "${apiEndpoint}info/collections" else "$apiEndpoint/info/collections"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)
            val serverTimestamp = extractServerTimestamp(conn)

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val map = mutableMapOf<String, Double>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = json.optDouble(k, 0.0)
                }
                SyncClientResult.Success(map, serverTimestamp = serverTimestamp, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode from /info/collections"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Failed to fetch collection timestamps")
        }
    }

    /**
     * Fetches BSO records from a collection (e.g. bookmarks, tabs, history).
     */
    fun fetchCollectionRecords(
        apiEndpoint: String,
        collection: String,
        authToken: String,
        newerThan: Double = 0.0,
        limit: Int = 500
    ): SyncClientResult<List<BsoRecord>> {
        return try {
            val base = if (apiEndpoint.endsWith("/")) apiEndpoint else "$apiEndpoint/"
            val queryParams = buildString {
                append("full=1&limit=").append(limit)
                if (newerThan > 0.0) {
                    append("&newer=").append(String.format(java.util.Locale.US, "%.2f", newerThan))
                }
            }
            val endpoint = "${base}storage/$collection?$queryParams"

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)
            val serverTimestamp = extractServerTimestamp(conn)

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val list = mutableListOf<BsoRecord>()
                val array = JSONArray(responseText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BsoRecord(
                            id = obj.getString("id"),
                            modified = obj.optDouble("modified", serverTimestamp),
                            payload = obj.optString("payload", "{}"),
                            sortindex = if (obj.has("sortindex")) obj.optInt("sortindex") else null
                        )
                    )
                }
                SyncClientResult.Success(list, serverTimestamp = serverTimestamp, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode from /storage/$collection"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Failed to fetch records for $collection")
        }
    }

    /**
     * Posts BSO records to a collection.
     */
    fun postCollectionRecords(
        apiEndpoint: String,
        collection: String,
        authToken: String,
        records: List<BsoRecord>
    ): SyncClientResult<Boolean> {
        if (records.isEmpty()) {
            return SyncClientResult.Success(true)
        }

        return try {
            val base = if (apiEndpoint.endsWith("/")) apiEndpoint else "$apiEndpoint/"
            val endpoint = "${base}storage/$collection"

            val jsonArray = JSONArray()
            records.forEach { r ->
                jsonArray.put(JSONObject().apply {
                    put("id", r.id)
                    put("payload", r.payload)
                    r.sortindex?.let { put("sortindex", it) }
                    r.ttl?.let { put("ttl", it) }
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonArray.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)
            val serverTimestamp = extractServerTimestamp(conn)

            if (statusCode in 200..299) {
                SyncClientResult.Success(true, serverTimestamp = serverTimestamp, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode writing to /storage/$collection"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Failed to post records to $collection")
        }
    }

    fun generateHawkHeader(
        id: String,
        key: String,
        method: String,
        url: URL,
        hashAlgorithm: String = "sha256"
    ): String {
        return try {
            val ts = System.currentTimeMillis() / 1000L
            val nonce = "hawk_" + java.util.UUID.randomUUID().toString().take(6)
            val path = if (url.query != null) "${url.path}?${url.query}" else url.path
            val host = url.host
            val port = if (url.port != -1) url.port else (if (url.protocol.equals("https", ignoreCase = true)) 443 else 80)

            val normalized = "hawk.1.header\n$ts\n$nonce\n$method\n$path\n$host\n$port\n\n\n"
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val macBytes = mac.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
            val macBase64 = java.util.Base64.getEncoder().encodeToString(macBytes)

            """Hawk id="$id", ts="$ts", nonce="$nonce", mac="$macBase64""""
        } catch (_: Exception) {
            "Bearer $id"
        }
    }

    private fun extractBackoff(conn: HttpURLConnection): Long {
        val backoffHeader = conn.getHeaderField("X-Weave-Backoff") ?: conn.getHeaderField("Retry-After")
        return backoffHeader?.toLongOrNull() ?: 0L
    }

    private fun extractServerTimestamp(conn: HttpURLConnection): Double {
        val tsHeader = conn.getHeaderField("X-Weave-Timestamp")
        return tsHeader?.toDoubleOrNull() ?: (System.currentTimeMillis() / 1000.0)
    }

    private fun extractErrorStream(conn: HttpURLConnection): String? {
        return try {
            val errorStream = conn.errorStream ?: return null
            val reader = BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8))
            val text = reader.readText()
            reader.close()
            text.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
