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

The current server contract is live on staging and should now be treated as the SDK v1 contract.

Endpoint:

- `POST /api/apps/:appId/contact`
- `POST /api/v1/mobile/entitlements/check`

Base URL rule:

- callers pass the root origin only, for example `https://jishu.page` or `https://staging.jishu.page`
- the SDK appends `/api/apps/:appId/contact`
- the SDK appends `/api/v1/mobile/entitlements/check`
- reject base URLs that already contain a path component other than `/`

Headers:

- `Authorization: Bearer <apiToken>`
- `Content-Type: application/json`
- no auth header for this endpoint

Request body:

```json
{
  "senderEmail": "visitor@example.com",
  "senderName": "Jane Visitor",
  "subject": "Quick question",
  "body": "Hi, I wanted to ask about..."
  "appId": "app_id",
  "platform": "android",
  "externalUserId": "customer_123",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "environment": "staging"
}
```

Success response:

```json
{
  "granted": true,
  "grantId": "grant_id_or_null",
  "matchType": "device",
{ "ok": true }
  "expiresAt": "2026-04-24T12:00:00.000Z",
  "serverTime": "2026-03-24T12:00:00.000Z"
}
```

Error shape:
Notes:

```json
{ "error": "Human-readable message" }
```

Validation rules:

- `senderEmail` is required, valid email, max 255 chars
- `body` is required, max 5000 chars
- `senderName` is optional, max 255 chars
- `platform` is always hardcoded to `"android"` inside the SDK
- at least one of `externalUserId` or `deviceId` must be sent
- `environment` is optional and may be `production`, `staging`, `testflight`, `internal`, or omitted
- `subject` is optional, max 255 chars
- endpoint is public, but `appId` must exist server-side
- the server may return `matchType = "none"` with `granted = false`
- rate limited per IP hash + app

## Public API

The messaging surface should be extremely small.

Recommended public API:
Keep the first public API narrow:

```kotlin
object Jishu {
    fun configure(
        context: Context,
        baseUrl: String,
        apiToken: String,
        appId: String,
        apiToken: String? = null,
        environment: String? = null,
        enableDebugLogs: Boolean = false
    )

    val displayUserID: String

    suspend fun sendContactMessage(message: ContactMessage)
    suspend fun checkAccess(externalUserId: String? = null): AccessResult
}
```

Phase 2 optional helper:

```kotlin
suspend fun hasAccessWithRevenueCat(
    entitlementId: String,
    externalUserId: String? = null
): RevenueCatAccessResult
```

Required request model:
Required response model:

```kotlin
data class ContactMessage(
    val senderEmail: String,
    val senderName: String? = null,
data class AccessResult(
    val granted: Boolean,
    val grantId: String?,
    val matchType: MatchType,
    val subject: String? = null,
    val body: String
    val expiresAt: Instant?,
    val serverTime: Instant
)

enum class MatchType {
    USER,
    DEVICE,
    NONE
}
```

Recommended error model:
## Identity Rules

- reuse `JishuApiException` where reasonable
- preserve the server `{ error }` message when safe to do so
Use `deviceId` internally, but expose it to app developers as:
- keep configuration errors separate from API request errors

Important compatibility note:
- `Jishu.displayUserID`

- if promo-access APIs remain in the SDK, `apiToken` may still be used there
Implementation requirements:
- messaging itself must not require an API token

- generate a UUID once
- persist it locally
- return the same value on every launch
## Expected Android Integration
- never rotate it automatically

The intended developer experience should look roughly like this:
Storage recommendation:

```kotlin
- `SharedPreferences` for the first cut
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
Reason:

- simplest setup for a public package v1
## Implementation Requirements
- DataStore can be revisited later if needed

Suggested additions in the Kotlin repo:
Important warning for package docs:

```text
jishu/src/main/java/io/jishu/sdk/contact/ContactMessage.kt
jishu/src/main/java/io/jishu/sdk/network/dto/ContactMessageRequest.kt
```

- reinstalling the app creates a new ID
- any active promo grant attached to the old ID stops matching
Expected updates:
- `externalUserId` is the preferred mode whenever the customer already has authentication

- `jishu/src/main/java/io/jishu/sdk/Jishu.kt`
## Network Behavior
- `jishu/src/main/java/io/jishu/sdk/config/JishuConfig.kt`
- `jishu/src/main/java/io/jishu/sdk/network/JishuClient.kt`
- `jishu/src/main/java/io/jishu/sdk/network/JishuApiException.kt` if needed

Behavior requirements:
Implementation requirements:

- use OkHttp
- use OkHttp + Kotlin serialization or Moshi
- use the repo's existing JSON approach for request serialization
- 10 second timeout
- no automatic retry for 4xx responses
- no retries for 4xx responses
- at most 1 retry for transient transport failures or 5xx responses
- never log message body or raw API token in debug output
- validate required fields before sending when it improves developer feedback
- never log the raw API token
- debug logging must be opt-in

## Example App Requirement
Caching rule for v1:

- cache only positive responses
- cache until the earlier of:
  - `expiresAt`
Add a small Android example app to demonstrate messaging end to end.
  - 5 minutes from fetch time
- do not cache negative responses beyond the current call

The example app should include:
## Suggested Module Layout

```text
jishu/
  src/main/java/.../Jishu.kt
  src/main/java/.../config/JishuConfig.kt
  src/main/java/.../identity/DeviceIdStore.kt
- SDK configuration
- a simple contact screen
- loading state
- success message
- failure message from SDK exception
  src/main/java/.../network/JishuClient.kt
  src/main/java/.../model/AccessResult.kt
  src/main/java/.../cache/AccessCache.kt
  src/main/java/.../logging/JishuLogger.kt
```

The example should be checked into the SDK repo, not left as README pseudocode only.
Tests:

## Suggested Repo Layout

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
jishu/build.gradle.kts
jishu/src/test/java/.../
jishu/src/main/java/
jishu/src/test/java/
example-app/
README.md
LICENSE
  JishuClientTest.kt
  DeviceIdStoreTest.kt
  AccessCacheTest.kt
  AccessResultParsingTest.kt
```

Android baseline:
## Android Baseline

Suggested minimum:

- `minSdk 24`
- coroutines for async API
- Kotlin coroutines for the public async API

If the package needs wider compatibility later, revisit after the first working release.

## RevenueCat Scope

Do not make RevenueCat a hard dependency in the first package cut.

Recommended approach:

- ship the core entitlement client first
- add RevenueCat helpers in a later pass
- if added, keep the bridge isolated so apps not using RevenueCat do not pay for that dependency

## Tests
Minimum docs requirement when RevenueCat support is added:

- authenticated apps should use their real stable user ID for both RevenueCat `appUserID` and Jishu `externalUserId`
Add tests covering:
- unauthenticated apps may use `Jishu.displayUserID`, but docs must warn about reinstall identity loss

- contact request serialization
## Repo Bootstrap
- success response handling
- API error parsing from `{ error: "..." }`
- missing configuration behavior
- base URL validation

Suggested test files:
Suggested repo contents:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
jishu/src/test/java/io/jishu/sdk/
jishu/build.gradle.kts
README.md
  ContactRequestSerializationTest.kt
  ContactRequestTest.kt
LICENSE
.gitignore
```

Recommended publishing target:

- Maven Central

Use `MockWebServer` for request / response coverage.
This is the cleanest public-distribution path if the repo is separate and public.

## README Sections
## README First Draft Sections

The Kotlin repo README should include:
The separate public repo should start with these sections:

1. What Jishu messaging is
1. What Jishu promo access is
2. Installation
3. Configure the SDK
4. Send a contact message from a ViewModel
5. Error handling
6. Example app
7. Optional note about promo access if it remains in the SDK
3. Quickstart
4. `displayUserID` and identity guidance
5. Staging test example
6. RevenueCat integration notes
7. Reinstall limitation warning
8. Security notes for API tokens

## First Milestone Checklist

1. Update configuration model so messaging can work without an API token
1. Create Gradle module and publishing skeleton
2. Implement `configure`
3. Implement persistent `displayUserID`
2. Add `ContactMessage`
4. Implement `checkAccess(externalUserId)`
5. Decode live staging response
3. Add network request for `POST /api/apps/:appId/contact`
6. Add unit tests for config validation and parsing
7. Add a tiny sample app or README usage snippet

## Staging Smoke Test Target

Use the currently live staging environment:

- base URL: `https://staging.jishu.page`

Manual test flow:

1. create an app in Jishu staging Promo access
4. Add `Jishu.sendContactMessage(...)`
2. create a grant for either a `User ID` or `Phone ID`
3. create an API token in Account → API access
5. Add an Android example app with a working contact screen
6. Add unit tests for serialization and error handling
4. configure the SDK with staging base URL, token, and app ID
5. call `checkAccess`

Expected success shape:

- `granted == true`
- `matchType == MatchType.USER` or `MatchType.DEVICE`
- `expiresAt != null`

## Non-Goals For First Cut

- Google Play Billing integration
- Play Integrity
- analytics
- webhooks
- background scheduled refresh
- multi-process storage hardening

7. Update README quickstart for messaging
## Open Decisions To Keep In Repo README

- whether to keep `object Jishu` only or also expose an instance-based client later
- whether RevenueCat helpers live in the main module or a companion module
- whether to offer a Flow-based convenience wrapper in addition to the suspend API
