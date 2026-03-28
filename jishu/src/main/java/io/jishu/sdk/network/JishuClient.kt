package io.jishu.sdk.network

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.contact.ContactMessage
import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.dto.AccessResultDto
import io.jishu.sdk.network.dto.CheckAccessRequest
import io.jishu.sdk.network.dto.ContactRequest
import io.jishu.sdk.network.dto.ProposalListResponse
import io.jishu.sdk.network.dto.ProposalResponse
import io.jishu.sdk.network.dto.SubmitProposalRequest
import io.jishu.sdk.network.dto.VoteRequest
import io.jishu.sdk.network.dto.VoteResponse
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

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint = "${config.baseUrl}/api/v1/mobile/entitlements/check"
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendContactMessage(message: ContactMessage, displayUserId: String) {
        val requestDto = ContactRequest(
            senderName = message.senderName,
            senderEmail = message.senderEmail,
            subject = message.subject,
            body = message.body,
            userId = message.userId ?: displayUserId
        )
        val bodyJson = json.encodeToString(requestDto).toRequestBody(mediaType)
        val url = "${config.baseUrl}/api/apps/${config.appId}/contact"
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.d("POST $url")
        executeContactWithRetry(request)
    }

    private suspend fun executeContactWithRetry(request: Request, attempt: Int = 0) {
        try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            JishuLogger.d("Contact response ${response.code}")
            when {
                response.code in 200..299 -> return
                response.code in 400..499 -> {
                    val body = response.body?.string()
                    throw JishuApiException("Server returned ${response.code}: $body")
                }
                attempt < 1 -> {
                    JishuLogger.d("Transient contact error ${response.code}, retrying…")
                    executeContactWithRetry(request, attempt + 1)
                }
                else -> {
                    val body = response.body?.string()
                    throw JishuApiException("Server returned ${response.code} after retry: $body")
                }
            }
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.d("Network error on contact, retrying: ${e.message}")
                executeContactWithRetry(request, attempt + 1)
            } else {
                throw e
            }
        }
    }

    suspend fun fetchProposals(
        sort: String = "votes",
        status: ProposalStatus = ProposalStatus.OPEN
    ): List<Proposal> {
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("status", status.value)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        JishuLogger.d("GET $url")
        return executeFeedbackWithRetry(request) { body ->
            json.decodeFromString<ProposalListResponse>(body).proposals.map { it.toProposal() }
        }
    }

    suspend fun submitProposal(title: String, description: String?, voterToken: String): Proposal {
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals"
        val bodyJson = json.encodeToString(SubmitProposalRequest(title, description, voterToken))
            .toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.d("POST $url")
        return executeFeedbackWithRetry(request) { body ->
            json.decodeFromString<ProposalResponse>(body).proposal.toProposal()
        }
    }

    suspend fun vote(proposalId: String, voterToken: String): Int {
        val encodedProposalId = URLEncoder.encode(proposalId, Charsets.UTF_8.name())
        val url = "${config.baseUrl}/api/apps/${config.appId}/proposals/$encodedProposalId/vote"
        val bodyJson = json.encodeToString(VoteRequest(voterToken)).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(bodyJson)
            .build()
        JishuLogger.d("POST $url")
        return executeFeedbackWithRetry(request) { body ->
            json.decodeFromString<VoteResponse>(body).voteCount
        }
    }

    private suspend fun <T> executeFeedbackWithRetry(request: Request, attempt: Int = 0, parse: (String) -> T): T {
        return try {
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            val body = response.body?.string()
            JishuLogger.d("Feedback response ${response.code}")

            when {
                response.isSuccessful && body != null -> parse(body)
                response.code in 400..499 -> throw JishuApiException("Server returned ${response.code}: $body")
                attempt < 1 -> {
                    JishuLogger.d("Transient feedback error ${response.code}, retrying…")
                    executeFeedbackWithRetry(request, attempt + 1, parse)
                }
                else -> throw JishuApiException("Server returned ${response.code} after retry: $body")
            }
        } catch (e: IOException) {
            if (attempt < 1) {
                JishuLogger.d("Network error on feedback, retrying: ${e.message}")
                executeFeedbackWithRetry(request, attempt + 1, parse)
            } else {
                throw e
            }
        }
    }

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
