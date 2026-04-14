package io.jishu.sdk

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.feedback.ProposalStatus
import io.jishu.sdk.network.JishuApiException
import io.jishu.sdk.network.JishuClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedbackTest {

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
    fun `fetchProposals decodes response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "proposals": [
                        {
                          "id": "prop_123",
                          "title": "Dark mode",
                          "description": "Please add dark mode.",
                          "status": "open",
                          "voteCount": 12,
                          "createdAt": "2026-03-28T12:00:00.000Z"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val result = client.fetchProposals(sort = "votes", status = ProposalStatus.OPEN)
        assertEquals(1, result.size)
        assertEquals("prop_123", result.first().id)
        assertEquals(12, result.first().voteCount)
        assertEquals(ProposalStatus.OPEN, result.first().status)
    }

    @Test
    fun `fetchProposals targets correct path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"proposals":[]}"""))
        client.fetchProposals(sort = "recent", status = ProposalStatus.PLANNED)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/apps/app_test/proposals?sort=recent&status=planned", recorded.path)
    }

    @Test
    fun `submitProposal encodes payload and has no Authorization header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "proposal": {
                        "id": "prop_123",
                        "title": "Dark mode",
                        "description": "Please add dark mode.",
                        "status": "open",
                        "voteCount": 1,
                        "createdAt": "2026-03-28T12:00:00.000Z"
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.submitProposal(
            title = "Dark mode",
            description = "Please add dark mode.",
            voterToken = "device-uuid"
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertEquals("POST", recorded.method)
        assertEquals("/api/apps/app_test/proposals", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(body.contains("\"title\":\"Dark mode\""))
        assertTrue(body.contains("\"description\":\"Please add dark mode.\""))
        assertTrue(body.contains("\"voter_token\":\"device-uuid\""))
        assertTrue(body.contains("\"osName\":"))
        assertTrue(body.contains("\"osVersion\":"))
        assertTrue(body.contains("\"deviceName\":"))
        assertEquals("prop_123", result.id)
    }

    @Test(expected = JishuApiException::class)
    fun `vote throws JishuApiException on 429`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}"""))
        client.vote(proposalId = "prop_123", voterToken = "device-uuid")
    }

    @Test
    fun `vote targets correct path and returns updated count`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"vote_count":7}"""))
        val voteCount = client.vote(proposalId = "prop_123", voterToken = "device-uuid")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertEquals("POST", recorded.method)
        assertEquals("/api/apps/app_test/proposals/prop_123/vote", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(body.contains("\"voter_token\":\"device-uuid\""))
        assertTrue(body.contains("\"osName\":"))
        assertTrue(body.contains("\"osVersion\":"))
        assertTrue(body.contains("\"deviceName\":"))
        assertEquals(7, voteCount)
    }

    @Test
    fun `submitProposal retries once on 500 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "proposal": {
                        "id": "prop_retry",
                        "title": "Dark mode",
                        "description": "",
                        "status": "open",
                        "voteCount": 1,
                        "createdAt": "2026-03-28T12:00:00.000Z"
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.submitProposal(
            title = "Dark mode",
            description = null,
            voterToken = "device-uuid"
        )

        assertEquals("prop_retry", result.id)
        assertEquals(2, server.requestCount)
    }
}
