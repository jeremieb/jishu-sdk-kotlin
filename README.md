# Jishu Android SDK

![github package](https://github.com/user-attachments/assets/e01c22ca-72bf-4474-bbfc-d262fdcf8bed)

A lightweight Android library for [Jishu](https://jishu.page) — check promo access grants, send contact form messages, and collect feature proposals from native Android apps.

- **Current version:** `0.1.5`
- **Minimum SDK:** Android 7.0 (API 24)
- **Kotlin:** 2.0+

---

## Table of Contents

1. [What is Jishu promo access?](#what-is-jishu-promo-access)
2. [Installation](#installation)
3. [Quickstart](#quickstart)
4. [Contact form](#contact-form)
5. [Feature feedback](#feature-feedback)
6. [User identity and `displayUserID`](#user-identity-and-displayuserid)
7. [Staging smoke test](#staging-smoke-test)
8. [RevenueCat integration](#revenuecat-integration)
9. [Reinstall limitation](#reinstall-limitation)
10. [Security notes](#security-notes)
11. [Publishing a new version](#publishing-a-new-version)
12. [Running the tests](#running-the-tests)

---

## What is Jishu promo access?

Jishu promo access lets you grant specific users or devices early or exclusive access to your app — without going through the Play Store review cycle. You create grants in the Jishu dashboard (by user ID or device ID) and this SDK checks at runtime whether the current user holds an active grant.

---

## Installation

Add the dependency to your app or module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("page.jishu:jishu-android:0.1.5")
}
```

Or in Groovy `build.gradle`:

```groovy
dependencies {
    implementation 'page.jishu:jishu-android:0.1.5'
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

Sync your project and you're ready to call `Jishu.configure(...)`.

### Local development

To build and test against a local snapshot before publishing:

```bash
./gradlew :jishu:publishToMavenLocal
```

Then add `mavenLocal()` **before** `mavenCentral()` in your app's `settings.gradle.kts`.

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

Call `checkAccess` from a coroutine or `viewModelScope`. The call is safe to make on every screen load — positive results are cached for up to 30 minutes.

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

### 3. Add the feedback feature

Once the SDK is configured with your `appId`, you can call the feedback endpoints from any `ViewModel` or coroutine scope. The SDK automatically reuses the configured app and uses `Jishu.displayUserID` as the stable voter token.

If you want to expose feature requests in your app UI, jump to the [Feature feedback](#feature-feedback) section below for a complete example.

If you only want promo-access checks, you can stop after `checkAccess()`. The feedback API is optional and can be added screen-by-screen later.

---

## Contact form

`Jishu.sendContactMessage(message)` lets your users send a message directly to you from within your app. Messages land in the **User Messages** inbox in your Jishu dashboard, where you can read and reply via email.

### Basic usage

```kotlin
viewModelScope.launch {
    try {
        Jishu.sendContactMessage(ContactMessage(
            senderEmail = "jane@example.com",
            body = "Hi, I have a question about my account."
        ))
        // Show a "Message sent!" confirmation
    } catch (e: JishuApiException) {
        when {
            e.message?.contains("429") == true ->
                // Rate limit hit — ask the user to wait before trying again
            else ->
                // Network error or validation failure
        }
    }
}
```

### `ContactMessage` fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `senderEmail` | `String` | Yes | Must be a valid email address |
| `body` | `String` | Yes | Max 5 000 characters |
| `senderName` | `String?` | No | Displayed in the dashboard message list |
| `subject` | `String?` | No | Shown as the message subject line |
| `userId` | `String?` | No | Automatically filled with `Jishu.displayUserID` when `null`. Lets the app owner add this sender to a promo grant directly from the dashboard. |

The SDK automatically includes `platform: "android"` in every request. The Jishu dashboard displays an **Android** badge on each message so you can tell at a glance which platform the sender is on — no action required on your side.

```kotlin
// All fields
val message = ContactMessage(
    senderName  = "Jane Smith",
    senderEmail = "jane@example.com",
    subject     = "Question about my portfolio",
    body        = "I noticed that my site is not loading correctly on Chrome..."
    // userId is automatically filled with Jishu.displayUserID
)
Jishu.sendContactMessage(message)
```

### Input sanitization

The SDK automatically trims leading and trailing whitespace from all string fields before sending. Optional fields (`senderName`, `subject`) are treated as absent if they are blank after trimming, so you do not need to sanitize `ContactMessage` values before passing them in.

### Rate limiting

The endpoint is public (no API token required) and rate-limited to **10 messages per hour per IP address** per app. On limit hit, `JishuApiException` is thrown with a message containing `429`. Show a user-friendly message and do not retry automatically.

### Errors

| Condition | Thrown |
|-----------|--------|
| `configure` not called before sending | `IllegalStateException` |
| Validation failed (missing email or body, value too long) | `JishuApiException` (HTTP 400) |
| The `appId` does not exist or is inactive | `JishuApiException` (HTTP 404) |
| Rate limit — more than 10 messages/hour from this IP | `JishuApiException` (HTTP 429) |
| Server error — the SDK retries once automatically | `JishuApiException` (HTTP 500) |

### Compose example

```kotlin
@Composable
fun ContactFormScreen(viewModel: ContactFormViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var body  by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Your email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Message") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.send(email, body) },
            enabled = email.isNotBlank() && body.isNotBlank() && uiState !is UiState.Sending,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send") }

        when (val s = uiState) {
            is UiState.Success -> Text("Message sent!", color = MaterialTheme.colorScheme.primary)
            is UiState.Error   -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

class ContactFormViewModel : ViewModel() {
    sealed class UiState { object Idle : UiState(); object Sending : UiState()
        object Success : UiState(); data class Error(val message: String) : UiState() }

    val uiState = MutableStateFlow<UiState>(UiState.Idle)

    fun send(email: String, body: String) {
        viewModelScope.launch {
            uiState.value = UiState.Sending
            try {
                Jishu.sendContactMessage(ContactMessage(senderEmail = email, body = body))
                uiState.value = UiState.Success
            } catch (e: JishuApiException) {
                val msg = if (e.message?.contains("429") == true)
                    "Too many messages — please wait an hour and try again."
                else
                    "Could not send message. Please try again."
                uiState.value = UiState.Error(msg)
            }
        }
    }
}
```

---



## Feature feedback

`Jishu.fetchProposals()`, `Jishu.submitProposal(...)`, and `Jishu.vote(...)` wrap the public feedback endpoints for the configured app. No API token is sent on these requests.

### What you need before using it

1. Call `Jishu.configure(...)` once at app startup.
2. Use the same `appId` that is registered in your Jishu dashboard.
3. Make sure the backend feedback routes are live for that app:
   `GET /api/apps/:appId/proposals`
   `POST /api/apps/:appId/proposals`
   `POST /api/apps/:appId/proposals/:id/vote`

### Basic usage

```kotlin
viewModelScope.launch {
    val proposals = Jishu.fetchProposals()
    val created = Jishu.submitProposal(
        title = "Offline mode",
        description = "Let me keep reading when I lose connection."
    )
    val updatedVoteCount = Jishu.vote(created.id)
}
```

### Typical app integration

The simplest integration is:

1. Load proposals when the screen opens with `Jishu.fetchProposals()`.
2. Submit a new idea with `Jishu.submitProposal(title, description)`.
3. Update the vote count for an item with `Jishu.vote(proposalId)`.
4. Store the result in your own screen state; the SDK does not manage UI state for you.

### ViewModel example

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jishu.sdk.Jishu
import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedbackViewModel : ViewModel() {

    private val _proposals = MutableStateFlow<List<Proposal>>(emptyList())
    val proposals = _proposals.asStateFlow()

    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun load(sort: String = "votes", status: ProposalStatus = ProposalStatus.OPEN) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            runCatching { Jishu.fetchProposals(sort = sort, status = status) }
                .onSuccess { _proposals.value = it }
                .onFailure { error.value = it.message ?: "Failed to load proposals" }
            isLoading.value = false
        }
    }

    fun submit(title: String, description: String?) {
        viewModelScope.launch {
            runCatching { Jishu.submitProposal(title = title, description = description) }
                .onSuccess { created -> _proposals.update { listOf(created) + it } }
                .onFailure { error.value = it.message ?: "Failed to submit proposal" }
        }
    }

    fun vote(proposalId: String) {
        viewModelScope.launch {
            runCatching { Jishu.vote(proposalId) }
                .onSuccess { updatedCount ->
                    _proposals.update { list ->
                        list.map { proposal ->
                            if (proposal.id == proposalId) {
                                proposal.copy(voteCount = updatedCount)
                            } else {
                                proposal
                            }
                        }
                    }
                }
                .onFailure { error.value = it.message ?: "Failed to vote" }
        }
    }
}
```

### Public API

```kotlin
suspend fun fetchProposals(
    sort: String = "votes",
    status: ProposalStatus = ProposalStatus.OPEN
): List<Proposal>

suspend fun submitProposal(
    title: String,
    description: String? = null
): Proposal

suspend fun vote(proposalId: String): Int
```

### Models

| Type | Notes |
|------|-------|
| `Proposal` | `id`, `title`, `description`, `status`, `voteCount`, `createdAt` |
| `ProposalStatus` | `OPEN`, `PLANNED`, `IN_PROGRESS`, `SHIPPED`, `REJECTED` |

### Behavior notes

- `submitProposal` and `vote` use a stable device-scoped voter token that is generated separately from `displayUserID` and persisted in `SharedPreferences` under the key `voter_token`.
- The feedback endpoints are public and rate-limited by the backend.
- Duplicate votes from the same device are ignored by the server.
- The SDK retries once on transport failures or 5xx responses, matching the contact form behavior.
- `fetchProposals()` defaults to `sort = "votes"` and `status = ProposalStatus.OPEN`, so shipped or rejected ideas must be requested explicitly.

### Common errors

| Condition | Thrown |
|-----------|--------|
| `configure` not called before use | `IllegalStateException` |
| Invalid request or rate limit | `JishuApiException` (HTTP 4xx) |
| Server error after one retry | `JishuApiException` |

### Implementation notes

- Use your own app state for loading, success, and error UI. The SDK only handles HTTP and model parsing.
- Use `ProposalStatus.OPEN` for the public “vote on ideas” view, and request other statuses only if you want to show planned or shipped roadmap items.
- Do not generate or pass your own `voter_token`; the SDK manages its own stable voter token internally and sends it automatically on `submitProposal` and `vote` calls.

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

**`displayUserID` and contact messages:**

When a user submits a contact form, the SDK automatically includes their `displayUserID` as the `userId` field of the message. This lets you see the sender's Jishu identity directly in the **User Messages** dashboard and add them to a promo grant with one click — no copy-pasting required. If your app has its own auth system, pass an explicit `userId` to `ContactMessage` to use your stable user ID instead:

```kotlin
Jishu.sendContactMessage(ContactMessage(
    senderEmail = "jane@example.com",
    body = "Hi, I have a question.",
    userId = currentUser.id   // override with your own stable ID
))
```

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
    debugLevel = JishuDebugLevel.VERBOSE  // prints request/response info to Logcat under tag "JishuSDK"
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
git tag 0.1.5
git push origin 0.1.5
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
| `AccessCacheTest` | Cache hit/miss, expiry via `expiresAt`, 30-minute cap, clear |
| `AccessResultParsingTest` | Full response, `matchType: none`, null fields, ISO 8601 dates |
| `ContactTest` | 201 success, correct path, no auth header, 429 error, 500 retry, body encoding |

### Testing in your Android project (local library)

While developing, you can point your app at the local library instead of the published version:

1. Run `./gradlew :jishu:publishToMavenLocal` in this repository.
2. Add `mavenLocal()` to your app's `settings.gradle.kts` repository list (before `mavenCentral()`).
3. Add the dependency normally — Gradle will resolve it from your local cache.

To switch back to the published version later, remove `mavenLocal()` and re-sync.

### Integration test against staging

Follow the [Staging smoke test](#staging-smoke-test) section above. Configure the SDK with `enableDebugLogs = true` and filter Logcat by the tag `JishuSDK` to see the full request/response cycle.
