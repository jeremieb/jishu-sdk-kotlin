package io.jishu.sdk

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.model.MatchType
import io.jishu.sdk.network.JishuApiException
import io.jishu.sdk.network.JishuClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
}
