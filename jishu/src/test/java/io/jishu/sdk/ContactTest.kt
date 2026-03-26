package io.jishu.sdk

import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.contact.ContactMessage
import io.jishu.sdk.network.JishuApiException
import io.jishu.sdk.network.JishuClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ContactTest {

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
    fun `sendContactMessage succeeds on 201`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ok":true}"""))
        client.sendContactMessage(ContactMessage(senderEmail = "user@example.com", body = "Hello!"))
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
    }

    @Test
    fun `sendContactMessage targets correct path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        client.sendContactMessage(ContactMessage(senderEmail = "user@example.com", body = "Hello!"))
        val recorded = server.takeRequest()
        assertEquals("/api/apps/app_test/contact", recorded.path)
    }

    @Test
    fun `sendContactMessage has no Authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        client.sendContactMessage(ContactMessage(senderEmail = "user@example.com", body = "Hello!"))
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test(expected = JishuApiException::class)
    fun `sendContactMessage throws JishuApiException on 429`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}"""))
        client.sendContactMessage(ContactMessage(senderEmail = "user@example.com", body = "Hello!"))
    }

    @Test
    fun `sendContactMessage retries once on 500 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(201))
        client.sendContactMessage(ContactMessage(senderEmail = "user@example.com", body = "Hello!"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `sendContactMessage encodes all fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        client.sendContactMessage(
            ContactMessage(
                senderName = "Alice",
                senderEmail = "alice@example.com",
                subject = "Hello",
                body = "World"
            )
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assert(body.contains("\"senderName\":\"Alice\""))
        assert(body.contains("\"senderEmail\":\"alice@example.com\""))
        assert(body.contains("\"subject\":\"Hello\""))
        assert(body.contains("\"body\":\"World\""))
    }
}
