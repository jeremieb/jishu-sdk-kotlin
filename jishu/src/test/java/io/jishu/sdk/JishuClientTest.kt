package io.jishu.sdk

import android.content.Context
import android.content.SharedPreferences
import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.model.MatchType
import io.jishu.sdk.network.JishuApiException
import io.jishu.sdk.network.JishuClient
import io.jishu.sdk.review.ReviewStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JishuClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JishuClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = JishuConfig(
            baseUrl = server.url("").toString().trimEnd('/'),
            apiToken = "test-token",
            appId = "app_test",
            environment = "staging"
        )
        client = JishuClient(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeReviewStore(): ReviewStore {
        val values = mutableMapOf<String, Any>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putLong(any(), any()) } answers {
            values[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
            if (value == null) values.remove(key) else values[key] = value
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }

        val prefs = mockk<SharedPreferences>()
        every { prefs.getLong(any(), any()) } answers { values[firstArg<String>()] as? Long ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { values[firstArg<String>()] as? Int ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { values[firstArg<String>()] as? String ?: secondArg() }
        every { prefs.edit() } returns editor

        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return ReviewStore(context)
    }

    @Test
    fun `checkAccess returns AccessResult on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "granted": true,
                      "grantId": "grant_123",
                      "matchType": "device",
                      "expiresAt": "2026-04-24T12:00:00.000Z",
                      "serverTime": "2026-03-24T12:00:00.000Z"
                    }
                    """.trimIndent()
                )
        )

        val result = client.checkAccess(deviceId = "device-uuid", externalUserId = null)
        assertTrue(result.granted)
        assertEquals("grant_123", result.grantId)
        assertEquals(MatchType.DEVICE, result.matchType)
    }

    @Test
    fun `checkAccess encodes platform in request body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "granted": true,
                      "grantId": "grant_123",
                      "matchType": "device",
                      "expiresAt": "2026-04-24T12:00:00.000Z",
                      "serverTime": "2026-03-24T12:00:00.000Z"
                    }
                    """.trimIndent()
                )
        )

        client.checkAccess(deviceId = "device-uuid", externalUserId = "user_abc")

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"platform\":\"android\""))
        assertTrue(body.contains("\"deviceId\":\"device-uuid\""))
        assertTrue(body.contains("\"externalUserId\":\"user_abc\""))
        assertFalse(body.contains("\"environment\":null"))
    }

    @Test
    fun `checkAccess returns not granted on 200 with granted false`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "granted": false,
                      "grantId": null,
                      "matchType": "none",
                      "serverTime": "2026-03-24T12:00:00.000Z"
                    }
                    """.trimIndent()
                )
        )

        val result = client.checkAccess(deviceId = "device-uuid", externalUserId = null)
        assertTrue(!result.granted)
        assertEquals(MatchType.NONE, result.matchType)
    }

    @Test(expected = JishuApiException::class)
    fun `checkAccess throws JishuApiException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        client.checkAccess(deviceId = "device-uuid", externalUserId = null)
    }

    @Test
    fun `checkAccess retries once on 500 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "granted": true,
                      "grantId": "grant_retry",
                      "matchType": "user",
                      "expiresAt": "2026-04-24T12:00:00.000Z",
                      "serverTime": "2026-03-24T12:00:00.000Z"
                    }
                    """.trimIndent()
                )
        )

        val result = client.checkAccess(deviceId = "device-uuid", externalUserId = "user_abc")
        assertTrue(result.granted)
        assertEquals(MatchType.USER, result.matchType)
    }

    @Test
    fun `fetchReviewConfig uses cached value for same app`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "enabled": true,
                      "triggerMode": "manual",
                      "minLaunches": 1,
                      "minDaysSinceInstall": 0,
                      "triggerLogic": "AND",
                      "cooldownDays": 7,
                      "maxPromptsPerDevice": 2,
                      "promptTitle": "Enjoying the app?",
                      "promptQuestion": "Tell us what you think.",
                      "ratingThreshold": 4,
                      "feedbackPrompt": "What could we improve?",
                      "captureFeedbackOnNegative": true
                    }
                    """.trimIndent()
                )
        )
        val store = makeReviewStore()

        client.fetchReviewConfig(appId = "app_one", store = store)
        client.fetchReviewConfig(appId = "app_one", store = store)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchReviewConfig cache is isolated per app id`() = runTest {
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "enabled": true,
                          "triggerMode": "manual",
                          "minLaunches": 1,
                          "minDaysSinceInstall": 0,
                          "triggerLogic": "AND",
                          "cooldownDays": 7,
                          "maxPromptsPerDevice": 2,
                          "promptTitle": "Enjoying the app?",
                          "promptQuestion": "Tell us what you think.",
                          "ratingThreshold": 4,
                          "feedbackPrompt": "What could we improve?",
                          "captureFeedbackOnNegative": true
                        }
                        """.trimIndent()
                    )
            )
        }
        val store = makeReviewStore()

        client.fetchReviewConfig(appId = "app_one", store = store)
        client.fetchReviewConfig(appId = "app_two", store = store)

        assertEquals("/api/apps/app_one/review/config", server.takeRequest().path)
        assertEquals("/api/apps/app_two/review/config", server.takeRequest().path)
    }
}
