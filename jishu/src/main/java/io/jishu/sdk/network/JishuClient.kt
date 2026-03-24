package io.jishu.sdk.network

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.dto.AccessResultDto
import io.jishu.sdk.network.dto.CheckAccessRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class JishuClient(private val config: JishuConfig) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint = "${config.baseUrl}/api/v1/mobile/entitlements/check"
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkAccess(deviceId: String, externalUserId: String?): AccessResult {
        val requestDto = CheckAccessRequest(
            appId = config.appId,
            platform = "android",
            externalUserId = externalUserId,
            deviceId = deviceId,
            environment = config.environment
        )

        val body = json.encodeToString(requestDto).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${config.apiToken}")
            .post(body)
            .build()

        JishuLogger.d("POST $endpoint appId=${config.appId}")

        return executeWithRetry(request)
    }

    private suspend fun executeWithRetry(request: Request, attempt: Int = 0): AccessResult {
        return try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            val responseBody = response.body?.string()
            JishuLogger.d("Response ${response.code}")

            when {
                response.isSuccessful && responseBody != null -> {
                    val dto = json.decodeFromString<AccessResultDto>(responseBody)
                    dto.toAccessResult()
                }
                response.code in 400..499 -> {
                    throw JishuApiException("Server returned ${response.code}: $responseBody")
                }
                attempt < 1 -> {
                    JishuLogger.d("Transient error ${response.code}, retrying…")
                    executeWithRetry(request, attempt + 1)
                }
                else -> throw JishuApiException("Server returned ${response.code} after retry: $responseBody")
            }
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.d("Network error, retrying: ${e.message}")
                executeWithRetry(request, attempt + 1)
            } else {
                throw e
            }
        }
    }
}
