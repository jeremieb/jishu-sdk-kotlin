# Promo Access Kotlin Package Start Doc

This file is the implementation handoff for the separate public Kotlin SDK repository.

Artifact / module name:

- `jishu`

Primary goal:

- provide a lightweight Android SDK that can check Jishu promo access from native apps and later support RevenueCat merge helpers

## Locked API Contract

The current server contract is live on staging and should now be treated as the SDK v1 contract.

Endpoint:

- `POST /api/v1/mobile/entitlements/check`

Base URL rule:

- callers pass the root origin only, for example `https://jishu.page` or `https://staging.jishu.page`
- the SDK appends `/api/v1/mobile/entitlements/check`
- reject base URLs that already contain a path component other than `/`

Headers:

- `Authorization: Bearer <apiToken>`
- `Content-Type: application/json`

Request body:

```json
{
  "appId": "app_id",
  "platform": "android",
  "externalUserId": "customer_123",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "environment": "staging"
}
```

Response body:

```json
{
  "granted": true,
  "grantId": "grant_id_or_null",
  "matchType": "device",
  "expiresAt": "2026-04-24T12:00:00.000Z",
  "serverTime": "2026-03-24T12:00:00.000Z"
}
```

Notes:

- `platform` is always hardcoded to `"android"` inside the SDK
- at least one of `externalUserId` or `deviceId` must be sent
- `environment` is optional and may be `production`, `staging`, `testflight`, `internal`, or omitted
- the server may return `matchType = "none"` with `granted = false`

## Public API

Keep the first public API narrow:

```kotlin
object Jishu {
    fun configure(
        context: Context,
        baseUrl: String,
        apiToken: String,
        appId: String,
        environment: String? = null,
        enableDebugLogs: Boolean = false
    )

    val displayUserID: String

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

Required response model:

```kotlin
data class AccessResult(
    val granted: Boolean,
    val grantId: String?,
    val matchType: MatchType,
    val expiresAt: Instant?,
    val serverTime: Instant
)

enum class MatchType {
    USER,
    DEVICE,
    NONE
}
```

## Identity Rules

Use `deviceId` internally, but expose it to app developers as:

- `Jishu.displayUserID`

Implementation requirements:

- generate a UUID once
- persist it locally
- return the same value on every launch
- never rotate it automatically

Storage recommendation:

- `SharedPreferences` for the first cut

Reason:

- simplest setup for a public package v1
- DataStore can be revisited later if needed

Important warning for package docs:

- reinstalling the app creates a new ID
- any active promo grant attached to the old ID stops matching
- `externalUserId` is the preferred mode whenever the customer already has authentication

## Network Behavior

Implementation requirements:

- use OkHttp + Kotlin serialization or Moshi
- 10 second timeout
- no retries for 4xx responses
- at most 1 retry for transient transport failures or 5xx responses
- never log the raw API token
- debug logging must be opt-in

Caching rule for v1:

- cache only positive responses
- cache until the earlier of:
  - `expiresAt`
  - 5 minutes from fetch time
- do not cache negative responses beyond the current call

## Suggested Module Layout

```text
jishu/
  src/main/java/.../Jishu.kt
  src/main/java/.../config/JishuConfig.kt
  src/main/java/.../identity/DeviceIdStore.kt
  src/main/java/.../network/JishuClient.kt
  src/main/java/.../model/AccessResult.kt
  src/main/java/.../cache/AccessCache.kt
  src/main/java/.../logging/JishuLogger.kt
```

Tests:

```text
jishu/src/test/java/.../
  JishuClientTest.kt
  DeviceIdStoreTest.kt
  AccessCacheTest.kt
  AccessResultParsingTest.kt
```

## Android Baseline

Suggested minimum:

- `minSdk 24`
- Kotlin coroutines for the public async API

If the package needs wider compatibility later, revisit after the first working release.

## RevenueCat Scope

Do not make RevenueCat a hard dependency in the first package cut.

Recommended approach:

- ship the core entitlement client first
- add RevenueCat helpers in a later pass
- if added, keep the bridge isolated so apps not using RevenueCat do not pay for that dependency

Minimum docs requirement when RevenueCat support is added:

- authenticated apps should use their real stable user ID for both RevenueCat `appUserID` and Jishu `externalUserId`
- unauthenticated apps may use `Jishu.displayUserID`, but docs must warn about reinstall identity loss

## Repo Bootstrap

Suggested repo contents:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
jishu/build.gradle.kts
README.md
LICENSE
.gitignore
```

Recommended publishing target:

- Maven Central

This is the cleanest public-distribution path if the repo is separate and public.

## README First Draft Sections

The separate public repo should start with these sections:

1. What Jishu promo access is
2. Installation
3. Quickstart
4. `displayUserID` and identity guidance
5. Staging test example
6. RevenueCat integration notes
7. Reinstall limitation warning
8. Security notes for API tokens

## First Milestone Checklist

1. Create Gradle module and publishing skeleton
2. Implement `configure`
3. Implement persistent `displayUserID`
4. Implement `checkAccess(externalUserId)`
5. Decode live staging response
6. Add unit tests for config validation and parsing
7. Add a tiny sample app or README usage snippet

## Staging Smoke Test Target

Use the currently live staging environment:

- base URL: `https://staging.jishu.page`

Manual test flow:

1. create an app in Jishu staging Promo access
2. create a grant for either a `User ID` or `Phone ID`
3. create an API token in Account → API access
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

## Open Decisions To Keep In Repo README

- whether to keep `object Jishu` only or also expose an instance-based client later
- whether RevenueCat helpers live in the main module or a companion module
- whether to offer a Flow-based convenience wrapper in addition to the suspend API
