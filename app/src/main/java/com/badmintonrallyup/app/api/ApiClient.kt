// Suspend API client for the RallyUp backend: snake_case JSON, bearer auth,
// single-flight refresh-on-401 with rotation (mirrors the server's 60s
// network-retry grace — a lost refresh response is safe to retry).
// 1:1 with the iOS APIClient actor.

package com.badmintonrallyup.app.api

import com.badmintonrallyup.app.auth.TokenPair
import com.badmintonrallyup.app.auth.TokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("Your session expired — please log in again.")
    class Server(message: String) : ApiException(message)
    class Transport : ApiException("Can't reach RallyUp — check your connection.")
}

object ApiClient {
    /** Overridden by the debug dev hooks (RALLYUP_API intent extra). */
    @Volatile
    var baseUrl: String = "https://badmintonrallyup.com"

    @Volatile
    private var tokens: TokenPair? = null

    @Volatile
    private var onSessionExpired: (() -> Unit)? = null

    private val refreshMutex = Mutex()
    private var refreshInFlight: CompletableDeferred<Boolean>? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    fun loadPersistedTokens() {
        tokens = TokenStore.load()
    }

    val isAuthenticated: Boolean get() = tokens != null

    fun setSessionExpiredHandler(handler: () -> Unit) {
        onSessionExpired = handler
    }

    fun adopt(access: String, refresh: String) {
        val pair = TokenPair(access, refresh)
        tokens = pair
        TokenStore.save(pair)
    }

    fun signOutLocally() {
        tokens = null
        TokenStore.clear()
    }

    // MARK: Requests — reified helpers so call sites read like the iOS ones.

    suspend inline fun <reified T> get(path: String): T =
        send("GET", path, null)

    suspend inline fun <reified T, reified B> post(path: String, body: B?): T =
        send("POST", path, body?.let { json.encodeToString(serializer<B>(), it) })

    suspend inline fun <reified T, reified B> put(path: String, body: B?): T =
        send("PUT", path, body?.let { json.encodeToString(serializer<B>(), it) })

    suspend inline fun <reified T> delete(path: String): T =
        send("DELETE", path, null)

    suspend inline fun <reified T> send(method: String, path: String, jsonBody: String?): T {
        val data = rawSend(method, path, jsonBody)
        return decodePayload(data.first, data.second)
    }

    /** Executes with auth + refresh-on-401; returns (bodyBytes, status). */
    suspend fun rawSend(
        method: String, path: String, jsonBody: String?, isRetry: Boolean = false
    ): Pair<ByteArray, Int> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("X-Client", "native")
        tokens?.let { builder.header("Authorization", "Bearer ${it.accessToken}") }
        val requestBody = jsonBody?.toRequestBody("application/json".toMediaType())
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody
            ?: ByteArray(0).toRequestBody(null))
        if (method == "DELETE" && jsonBody != null) builder.method("DELETE", requestBody)

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException.Transport()
        }
        response.use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (resp.code == 401 && !isRetry && tokens != null) {
                if (refreshIfNeeded()) {
                    return@withContext rawSend(method, path, jsonBody, isRetry = true)
                }
                onSessionExpired?.invoke()
                throw ApiException.Unauthorized()
            }
            bytes to resp.code
        }
    }

    inline fun <reified T> decodePayload(bytes: ByteArray, status: Int): T {
        val envelope = try {
            json.decodeFromString<ApiEnvelope<T>>(bytes.decodeToString())
        } catch (e: Exception) {
            // Proxy/HTML error page or other non-API reply (e.g. 413 from nginx).
            throw ApiException.Server(
                "The server couldn't handle that request (HTTP $status). " +
                    "If you were uploading a photo, it may be too large."
            )
        }
        if (status !in 200..299 || !envelope.success) {
            throw ApiException.Server(envelope.message ?: "Something went wrong ($status).")
        }
        val payload = envelope.data
        if (payload != null) return payload
        // Endpoints that return only a message decode as EmptyData.
        if (T::class == EmptyData::class) return EmptyData as T
        throw ApiException.Server(envelope.message ?: "Empty response.")
    }

    // MARK: Multipart upload (OCR endpoints — field name "image")

    suspend inline fun <reified T> upload(
        path: String, imageData: ByteArray,
        filename: String = "photo.jpg", mime: String = "image/jpeg",
    ): T {
        val data = rawUpload(path, imageData, filename, mime)
        return try {
            decodePayload(data.first, data.second)
        } catch (e: ApiException.Server) {
            if (e.message?.startsWith("The server couldn't handle") == true) {
                throw ApiException.Server(
                    "The server couldn't handle that upload (HTTP ${data.second}) — it may be too large."
                )
            }
            throw e
        }
    }

    suspend fun rawUpload(
        path: String, imageData: ByteArray, filename: String, mime: String,
        isRetry: Boolean = false,
    ): Pair<ByteArray, Int> = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", filename, imageData.toRequestBody(mime.toMediaType()))
            .build()
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("X-Client", "native")
            .post(body)
        tokens?.let { builder.header("Authorization", "Bearer ${it.accessToken}") }
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException.Transport()
        }
        response.use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (resp.code == 401 && !isRetry && tokens != null) {
                if (refreshIfNeeded()) {
                    return@withContext rawUpload(path, imageData, filename, mime, isRetry = true)
                }
                onSessionExpired?.invoke()
                throw ApiException.Unauthorized()
            }
            bytes to resp.code
        }
    }

    // MARK: Refresh (single-flight)

    suspend fun refreshIfNeeded(): Boolean {
        val waiter: CompletableDeferred<Boolean>
        var isLeader = false
        refreshMutex.withLock {
            val running = refreshInFlight
            if (running != null) {
                waiter = running
            } else {
                waiter = CompletableDeferred()
                refreshInFlight = waiter
                isLeader = true
            }
        }
        if (!isLeader) return waiter.await()

        val ok = doRefresh()
        refreshMutex.withLock { refreshInFlight = null }
        if (!ok) signOutLocally()
        waiter.complete(ok)
        return ok
    }

    private suspend fun doRefresh(): Boolean = withContext(Dispatchers.IO) {
        val refresh = tokens?.refreshToken ?: return@withContext false
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("refresh_token", kotlinx.serialization.json.JsonPrimitive(refresh))
            }
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/auth/token/refresh")
            .header("X-Client", "native")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext false
        }
        response.use { resp ->
            if (resp.code != 200) return@withContext false
            val envelope = try {
                json.decodeFromString<ApiEnvelope<RefreshResult>>(resp.body?.string() ?: "")
            } catch (e: Exception) {
                return@withContext false
            }
            val result = envelope.data ?: return@withContext false
            adopt(result.accessToken, result.refreshToken)
            true
        }
    }
}
