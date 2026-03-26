# Jishu Kotlin SDK Messaging Doc

This file is the implementation handoff for the separate public Kotlin SDK repository at:

- `/Users/jeremieberduck/Developer/jishu-sdk-kotlin`

Artifact / module name:

- `jishu`

Primary goal:

- make contact messaging a first-class SDK capability for native Android apps
- let app developers wire a simple Compose or View-based form to a `ContactMessage` object and send it to Jishu with minimal setup
- ship an example Android app that demonstrates the full integration flow

This document is about messaging support. Existing promo-access functionality may remain in the SDK, but messaging is the new priority capability.

## Product Behavior

The host app is configured with a Jishu `appId`.

The SDK should let the app developer:

1. create a simple contact screen
2. map the entered values into a `ContactMessage`
3. call a single SDK method
4. let the SDK send `POST /api/apps/:appId/contact`

On success:

- the message is stored by Jishu
- the app owner sees it in Dashboard → User Messages
- the app owner may receive a push notification in Jishu client apps

## Locked API Contract

Endpoint:

- `POST /api/apps/:appId/contact`

Base URL rule:

- callers pass the root origin only, for example `https://jishu.page` or `https://staging.jishu.page`
- the SDK appends `/api/apps/:appId/contact`
- reject base URLs that already contain a path component other than `/`

Headers:

- `Content-Type: application/json`
- no auth header for this endpoint

Request body:

```json
{
  "senderEmail": "visitor@example.com",
  "senderName": "Jane Visitor",
  "subject": "Quick question",
  "body": "Hi, I wanted to ask about..."
}
```

Success response:

```json
{ "ok": true }
```

Error shape:

```json
{ "error": "Human-readable message" }
```

Validation rules:

- `senderEmail` is required, valid email, max 255 chars
- `body` is required, max 5000 chars
- `senderName` is optional, max 255 chars
- `subject` is optional, max 255 chars
- endpoint is public, but `appId` must exist server-side
- rate limited per IP hash + app

## Public API

The messaging surface should be extremely small.

Recommended public API:

```kotlin
object Jishu {
    fun configure(
        context: Context,
        baseUrl: String,
        appId: String,
        apiToken: String? = null,
        environment: String? = null,
        enableDebugLogs: Boolean = false
    )

    suspend fun sendContactMessage(message: ContactMessage)
}
```

Required request model:

```kotlin
data class ContactMessage(
    val senderEmail: String,
    val senderName: String? = null,
    val subject: String? = null,
    val body: String
)
```

Recommended error model:

- reuse `JishuApiException` where reasonable
- preserve the server `{ error }` message when safe to do so
- keep configuration errors separate from API request errors

Important compatibility note:

- if promo-access APIs remain in the SDK, `apiToken` may still be used there
- messaging itself must not require an API token

## Expected Android Integration

The intended developer experience should look roughly like this:

```kotlin
viewModelScope.launch {
    try {
        Jishu.sendContactMessage(
            ContactMessage(
                senderEmail = email,
                senderName = name.takeIf { it.isNotBlank() },
                subject = subject.takeIf { it.isNotBlank() },
                body = body
            )
        )
    } catch (e: JishuApiException) {
        uiState = uiState.copy(error = e.message)
    }
}
```

That is the bar: the host app should be able to connect a basic screen to a message object and send it with one SDK call.

## Implementation Requirements

Suggested additions in the Kotlin repo:

```text
jishu/src/main/java/io/jishu/sdk/contact/ContactMessage.kt
jishu/src/main/java/io/jishu/sdk/network/dto/ContactMessageRequest.kt
```

Expected updates:

- `jishu/src/main/java/io/jishu/sdk/Jishu.kt`
- `jishu/src/main/java/io/jishu/sdk/config/JishuConfig.kt`
- `jishu/src/main/java/io/jishu/sdk/network/JishuClient.kt`
- `jishu/src/main/java/io/jishu/sdk/network/JishuApiException.kt` if needed

Behavior requirements:

- use OkHttp
- use the repo's existing JSON approach for request serialization
- 10 second timeout
- no automatic retry for 4xx responses
- at most 1 retry for transient transport failures or 5xx responses
- never log message body or raw API token in debug output
- validate required fields before sending when it improves developer feedback

## Example App Requirement

Add a small Android example app to demonstrate messaging end to end.

The example app should include:

- SDK configuration
- a simple contact screen
- loading state
- success message
- failure message from SDK exception

The example should be checked into the SDK repo, not left as README pseudocode only.

## Suggested Repo Layout

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
jishu/build.gradle.kts
jishu/src/main/java/
jishu/src/test/java/
example-app/
README.md
LICENSE
```

Android baseline:

- `minSdk 24`
- coroutines for async API

## Tests

Add tests covering:

- contact request serialization
- success response handling
- API error parsing from `{ error: "..." }`
- missing configuration behavior
- base URL validation

Suggested test files:

```text
jishu/src/test/java/io/jishu/sdk/
  ContactRequestSerializationTest.kt
  ContactRequestTest.kt
```

Use `MockWebServer` for request / response coverage.

## README Sections

The Kotlin repo README should include:

1. What Jishu messaging is
2. Installation
3. Configure the SDK
4. Send a contact message from a ViewModel
5. Error handling
6. Example app
7. Optional note about promo access if it remains in the SDK

## First Milestone Checklist

1. Update configuration model so messaging can work without an API token
2. Add `ContactMessage`
3. Add network request for `POST /api/apps/:appId/contact`
4. Add `Jishu.sendContactMessage(...)`
5. Add an Android example app with a working contact screen
6. Add unit tests for serialization and error handling
7. Update README quickstart for messaging
