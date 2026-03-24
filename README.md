# Jishu Android SDK

![github package](https://github.com/user-attachments/assets/e01c22ca-72bf-4474-bbfc-d262fdcf8bed)

A lightweight Android library that checks [Jishu](https://jishu.page) promo access from native Android apps, with an optional bridge for RevenueCat entitlements.

- **Current version:** `0.1.0`
- **Minimum SDK:** Android 7.0 (API 24)
- **Kotlin:** 2.0+

---

## Table of Contents

1. [What is Jishu promo access?](#what-is-jishu-promo-access)
2. [Installation](#installation)
3. [Quickstart](#quickstart)
4. [User identity and `displayUserID`](#user-identity-and-displayuserid)
5. [Staging smoke test](#staging-smoke-test)
6. [RevenueCat integration](#revenuecat-integration)
7. [Reinstall limitation](#reinstall-limitation)
8. [Security notes](#security-notes)
9. [Publishing a new version](#publishing-a-new-version)
10. [Running the tests](#running-the-tests)

---

## What is Jishu promo access?

Jishu promo access lets you grant specific users or devices early or exclusive access to your app — without going through the Play Store review cycle. You create grants in the Jishu dashboard (by user ID or device ID) and this SDK checks at runtime whether the current user holds an active grant.

---

## Installation

### Gradle (once published to Maven Central)

Add the dependency to your app or module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.jishu:jishu-android:0.1.0")
}
```

Or in Groovy `build.gradle`:

```groovy
dependencies {
    implementation 'io.jishu:jishu-android:0.1.0'
}
```

Make sure `mavenCentral()` is in your repository list (`settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### Local development (before Maven Central)

Build and install the library to your local Maven cache:

```bash
./gradlew :jishu:publishToMavenLocal
```

Then add `mavenLocal()` **before** `mavenCentral()` in your app's `settings.gradle.kts`, and use the same dependency coordinate above.

---

## Quickstart

### 1. Configure once at startup

Call `configure` from your `Application` class before any other SDK call. Create a custom Application class if you do not have one yet.

```kotlin
import io.jishu.sdk.Jishu

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Jishu.configure(
            context = this,
            baseUrl = "https://jishu.page",
            apiToken = "YOUR_API_TOKEN",
            appId = "YOUR_APP_ID"
        )
    }
}
```

Register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ... >
```

### 2. Check access

Call `checkAccess` from a coroutine or `viewModelScope`. The call is safe to make on every screen load — positive results are cached for up to 5 minutes.

```kotlin
import io.jishu.sdk.Jishu

viewModelScope.launch {
    val result = Jishu.checkAccess()
    if (result.granted) {
        // Show exclusive content
    }
}
```

Pass your own user ID when the customer is signed in — this is the preferred mode:

```kotlin
val result = Jishu.checkAccess(externalUserId = currentUser.id)
```

---

## User identity and `displayUserID`

`Jishu.displayUserID` is a stable, device-scoped identifier automatically generated on first launch and persisted in `SharedPreferences`. You can use it as a device identity in the Jishu dashboard.

```kotlin
println(Jishu.displayUserID) // e.g. "550e8400-e29b-41d4-a716-446655440000"
```

**When to use `displayUserID` vs `externalUserId`:**

| Scenario | Recommended approach |
|---|---|
| App has its own auth system | Pass `externalUserId = currentUser.id` — most reliable |
| Unauthenticated app or guest mode | Omit `externalUserId`; the SDK sends `displayUserID` automatically |

Grants are matched on the server side against whichever identity you send. Mixing both identities in different calls for the same user can produce inconsistent results.

---

## Staging smoke test

Use this flow to verify the SDK works end-to-end against the live staging environment before shipping to production.

### Prerequisites

1. Create an app in the Jishu staging dashboard at `https://staging.jishu.page`.
2. Create a promo grant for a **User ID** or **Phone ID** in the app's Promo Access section.
3. Create an API token under **Account → API Access**.

### Configure for staging

```kotlin
Jishu.configure(
    context = this,
    baseUrl = "https://jishu.page",
    apiToken = "YOUR_API_TOKEN",
    appId = "YOUR_APP_ID",
    environment = "production",
    enableDebugLogs = true  // prints request/response info to Logcat under tag "JishuSDK"
)
```

### Call `checkAccess`

```kotlin
val result = Jishu.checkAccess(externalUserId = "the_user_id_you_granted")
println(result.granted)    // true
println(result.matchType)  // MatchType.USER
println(result.expiresAt)  // Instant?
```

Expected successful response shape:

```
granted    = true
matchType  = MatchType.USER  (or MatchType.DEVICE)
expiresAt  ≠ null
```

---

## RevenueCat integration

RevenueCat support is **not included in this first release** to keep the core library dependency-free.

A RevenueCat bridge will be added in a future minor release as an opt-in module, so apps that do not use RevenueCat pay no extra dependency cost.

**Planned API shape (subject to change):**

```kotlin
val result = Jishu.hasAccessWithRevenueCat(
    entitlementId = "pro",
    externalUserId = currentUser.id
)
// result.jishuGranted || result.revenueCatEntitled
```

**Notes when RevenueCat support lands:**

- Authenticated apps should use the same stable user ID for both `Purchases.sharedInstance.logIn(appUserID)` and `Jishu.checkAccess(externalUserId = ...)`.
- Unauthenticated apps may use `Jishu.displayUserID`, but see the [reinstall warning](#reinstall-limitation) below.

---

## Reinstall limitation

> **Warning:** Reinstalling the app generates a new `displayUserID`.

Any active promo grant that was matched via the old device ID will no longer match after reinstall. This is a limitation of device-scoped identity stored in `SharedPreferences`, which is cleared when the app is uninstalled.

**Mitigations:**

- Use `externalUserId` whenever the user is authenticated — server-side user IDs survive reinstalls.
- Document to your users that reinstalling requires re-enrolling in any device-based promo.

---

## Security notes

- **Never hard-code your production API token in source control.** Inject it at build time via `BuildConfig` fields in your `build.gradle.kts`, environment variables in CI, or a secrets manager.
- The SDK never prints the raw API token in logs, even when `enableDebugLogs = true`.
- The API token should be treated as a server secret scoped to your app. Rotate it from **Account → API Access** if it is ever exposed.

Example: injecting the token safely via `BuildConfig`:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "JISHU_API_TOKEN", "\"${System.getenv("JISHU_API_TOKEN")}\"")
    }
}
```

```kotlin
// Application.onCreate()
Jishu.configure(
    context = this,
    baseUrl = "https://jishu.page",
    apiToken = BuildConfig.JISHU_API_TOKEN,
    appId = "YOUR_APP_ID"
)
```

---

## Publishing a new version

The library is versioned via **git tags**. Use full three-part semantic versioning (`MAJOR.MINOR.PATCH`).

### Checklist before tagging

- [ ] Bump `version` in `jishu/build.gradle.kts` to match the new tag.
- [ ] Update this README's **Current version** field at the top.
- [ ] Ensure `./gradlew :jishu:assembleRelease` and `./gradlew :jishu:test` both pass with zero errors.
- [ ] Commit all changes.

### Tag and push

```bash
git tag 0.1.0
git push origin 0.1.0
```

Or push all local tags at once:

```bash
git push origin --tags
```

### Creating a GitHub Release (recommended)

After pushing the tag, create a GitHub Release for discoverability and changelogs:

1. Go to your repository on GitHub.
2. Click **Releases → Draft a new release**.
3. Select the tag you just pushed.
4. Write a short changelog and click **Publish release**.

---

## Running the tests

### Unit tests (no network required)

```bash
./gradlew :jishu:test
```

All tests should pass. The test suite covers:

| Suite | What it tests |
|---|---|
| `JishuClientTest` | 200 success, 401 no-retry, 500 retry-then-succeed, request shape |
| `DeviceIdStoreTest` | UUID generation, persistence, no re-generation on second call |
| `AccessCacheTest` | Cache hit/miss, expiry via `expiresAt`, 5-minute cap, clear |
| `AccessResultParsingTest` | Full response, `matchType: none`, null fields, ISO 8601 dates |

### Testing in your Android project (local library)

While developing, you can point your app at the local library instead of the published version:

1. Run `./gradlew :jishu:publishToMavenLocal` in this repository.
2. Add `mavenLocal()` to your app's `settings.gradle.kts` repository list (before `mavenCentral()`).
3. Add the dependency normally — Gradle will resolve it from your local cache.

To switch back to the published version later, remove `mavenLocal()` and re-sync.

### Integration test against staging

Follow the [Staging smoke test](#staging-smoke-test) section above. Configure the SDK with `enableDebugLogs = true` and filter Logcat by the tag `JishuSDK` to see the full request/response cycle.
