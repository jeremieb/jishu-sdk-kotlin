package io.jishu.sdk.network

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.contact.ContactMessage
import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import io.jishu.sdk.identity.collectDeviceMetaInfo
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.dto.AccessResultDto
import io.jishu.sdk.network.dto.CheckAccessRequest
import io.jishu.sdk.network.dto.ContactRequest
import io.jishu.sdk.network.dto.ProposalListResponse
import io.jishu.sdk.network.dto.ProposalResponse
import io.jishu.sdk.network.dto.ReviewEventRequest
import io.jishu.sdk.network.dto.ReviewFeedbackRequest
import io.jishu.sdk.network.dto.SubmitProposalRequest
import io.jishu.sdk.network.dto.VoteRequest
import io.jishu.sdk.network.dto.VoteResponse
import io.jishu.sdk.review.ReviewConfig
import io.jishu.sdk.review.ReviewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal class JishuClient(private val config: JishuConfig) {

    val appId: String get() = config.appId

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint = "${config.baseUrl}/api/v1/mobile/entitlements/check"
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendContactMessage(message: ContactMessage, displayUserId: String) {
        val meta = collectDeviceMetaInfo()
        val requestDto = ContactRequest(
            senderName = message.senderName?.trim()?.takeIf { it.isNotEmpty() },
            senderEmail = message.senderEmail.trim(),
            subject = message.subject?.trim()?.takeIf { it.isNotEmpty() },
            body = message.body.trim(),
            userId = message.userId ?: displayUserId,
            platform = "android",
            osName = meta.osName,
            osVersion = meta.osVersion,
            deviceName = meta.deviceName,
        )
        val bodyJson = json.encodeToString(requestDto).toRequestBody(mediaType)
        val url = "${config.baseUrl}/api/apps/${config.appId}/contact"
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.request("POST", url)
        executeContactWithRetry(request)
    }

    private suspend fun executeContactWithRetry(request: Request, attempt: Int = 0) {
        try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            response.use {
                JishuLogger.response(it.code, "POST", request.url.toString())
                when {
                    it.code in 200..299 -> return
                    it.code in 400..499 -> {
                        val body = it.body?.string()
                        JishuLogger.error("Contact HTTP error ${it.code} — ${request.url}")
                        throw JishuApiException("Server returned ${it.code}: $body")
                    }
                    attempt < 1 -> {
                        JishuLogger.retry("Contact server error ${it.code}, retrying…")
                    }
                    else -> {
                        val body = it.body?.string()
                        JishuLogger.error("Contact server error ${it.code} — no retries left")
                        throw JishuApiException("Server returned ${it.code} after retry: $body")
                    }
                }
            }
            executeContactWithRetry(request, attempt + 1)
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.retry("Contact transport error, retrying: ${e.message}")
                executeContactWithRetry(request, attempt + 1)
            } else {
                JishuLogger.error("Contact transport error: ${e.message}")
                throw e
            }
        }
    }

    suspend fun fetchProposals(
        sort: String = "votes",
        status: ProposalStatus = ProposalStatus.OPEN,
    ): List<Proposal> {
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("status", status.value)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        JishuLogger.request("GET", url.toString())
        return executeFeedbackWithRetry(request, method = "GET") { body ->
            json.decodeFromString<ProposalListResponse>(body).proposals.map { it.toProposal() }
        }
    }

    suspend fun submitProposal(title: String, description: String?, voterToken: String): Proposal {
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals"
        val meta = collectDeviceMetaInfo()
        val bodyJson = json.encodeToString(
            SubmitProposalRequest(
                title = title,
                description = description,
                voterToken = voterToken,
                osName = meta.osName,
                osVersion = meta.osVersion,
                deviceName = meta.deviceName,
            ),
        ).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.request("POST", url)
        return executeFeedbackWithRetry(request, method = "POST") { body ->
            json.decodeFromString<ProposalResponse>(body).proposal.toProposal()
        }
    }

    suspend fun vote(proposalId: String, voterToken: String): Int {
        val encodedProposalId = URLEncoder.encode(proposalId, Charsets.UTF_8.name())
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals/$encodedProposalId/vote"
        val meta = collectDeviceMetaInfo()
        val bodyJson = json.encodeToString(
            VoteRequest(
                voterToken = voterToken,
                osName = meta.osName,
                osVersion = meta.osVersion,
                deviceName = meta.deviceName,
            ),
        ).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.request("POST", url)
        return executeFeedbackWithRetry(request, method = "POST") { body ->
            json.decodeFromString<VoteResponse>(body).voteCount
        }
    }

    private suspend fun <T> executeFeedbackWithRetry(
        request: Request,
        attempt: Int = 0,
        method: String = request.method,
        parse: (String) -> T,
    ): T {
        return try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            val body = response.body?.string()
            JishuLogger.response(response.code, method, request.url.toString())
            JishuLogger.responseBody(body)

            when {
                response.isSuccessful && body != null -> parse(body)
                response.code in 400..499 -> {
                    JishuLogger.error("HTTP error ${response.code} — ${request.url}")
                    throw JishuApiException("Server returned ${response.code}: $body")
                }
                attempt < 1 -> {
                    JishuLogger.retry("Server error ${response.code}, retrying…")
                    executeFeedbackWithRetry(request, attempt + 1, method, parse)
                }
                else -> {
                    JishuLogger.error("Server error ${response.code} — no retries left")
                    throw JishuApiException("Server returned ${response.code} after retry: $body")
                }
            }
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.retry("Transport error, retrying: ${e.message}")
                executeFeedbackWithRetry(request, attempt + 1, method, parse)
            } else {
                JishuLogger.error("Transport error: ${e.message}")
                throw e
            }
        }
    }

    // ── Review ──────────────────────────────────────────────────────────────

    /** Fetch review config with a 1-hour TTL backed by ReviewStore. */
    suspend fun fetchReviewConfig(appId: String, store: ReviewStore): ReviewConfig {
        store.cachedConfig()?.let { return it }
        val url = "${config.baseUrl}/api/apps/${URLEncoder.encode(appId, Charsets.UTF_8.name())}/review/config"
        val request = Request.Builder().url(url).get().build()
        JishuLogger.request("GET", url)
        val result = executeFeedbackWithRetry(request, method = "GET") { body ->
            json.decodeFromString<ReviewConfig>(body)
        }
        store.cacheConfig(result)
        return result
    }

    /** Fire-and-forget event log. Swallows all exceptions. */
    suspend fun logReviewEvent(appId: String, eventType: String, platform: String, rating: Int?, feedback: String? = null) {
        try {
            val url = "${config.baseUrl}/api/apps/${URLEncoder.encode(appId, Charsets.UTF_8.name())}/review/events"
            val bodyJson = json.encodeToString(ReviewEventRequest(eventType = eventType, platform = platform, rating = rating, feedback = feedback))
                .toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(bodyJson).build()
            JishuLogger.request("POST", url)
            withContext(Dispatchers.IO) { http.newCall(request).execute() }.use { /* fire-and-forget */ }
        } catch (e: Exception) {
            JishuLogger.error("Review event error ($eventType): ${e.message}")
        }
    }

    /** Fire-and-forget feedback submission. Swallows all exceptions. */
    suspend fun sendReviewFeedback(appId: String, body: String) {
        try {
            val url = "${config.baseUrl}/api/apps/${URLEncoder.encode(appId, Charsets.UTF_8.name())}/review/feedback"
            val meta = collectDeviceMetaInfo()
            val bodyJson = json.encodeToString(
                ReviewFeedbackRequest(
                    body = body,
                    platform = "android",
                    osName = meta.osName,
                    osVersion = meta.osVersion,
                    deviceName = meta.deviceName,
                )
            ).toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(bodyJson).build()
            JishuLogger.request("POST", url)
            executeContactWithRetry(request)
        } catch (e: Exception) {
            JishuLogger.error("Review feedback error: ${e.message}")
        }
    }

    suspend fun checkAccess(deviceId: String, externalUserId: String?): AccessResult {
        val requestDto = CheckAccessRequest(
            appId = config.appId,
            platform = "android",
            externalUserId = externalUserId,
            deviceId = deviceId,
            environment = config.environment,
        )

        val body = json.encodeToString(requestDto).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${config.apiToken}")
            .post(body)
            .build()

        JishuLogger.request("POST", endpoint)

        return executeWithRetry(request)
    }

    private suspend fun executeWithRetry(request: Request, attempt: Int = 0): AccessResult {
        return try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            val responseBody = response.body?.string()
            JishuLogger.response(response.code, "POST", request.url.toString())
            JishuLogger.responseBody(responseBody)

            when {
                response.isSuccessful && responseBody != null -> {
                    val dto = json.decodeFromString<AccessResultDto>(responseBody)
                    dto.toAccessResult()
                }
                response.code in 400..499 -> {
                    JishuLogger.error("HTTP error ${response.code} — ${request.url}")
                    throw JishuApiException("Server returned ${response.code}: $responseBody")
                }
                attempt < 1 -> {
                    JishuLogger.retry("Server error ${response.code}, retrying…")
                    executeWithRetry(request, attempt + 1)
                }
                else -> {
                    JishuLogger.error("Server error ${response.code} — no retries left")
                    throw JishuApiException("Server returned ${response.code} after retry: $responseBody")
                }
            }
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.retry("Transport error, retrying: ${e.message}")
                executeWithRetry(request, attempt + 1)
            } else {
                JishuLogger.error("Transport error: ${e.message}")
                throw e
            }
        }
    }
}
