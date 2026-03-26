package io.jishu.sdk.contact

/**
 * Input for a contact form submission via [io.jishu.sdk.Jishu.sendContactMessage].
 *
 * @param senderName   Optional display name of the sender.
 * @param senderEmail  Email address of the sender.
 * @param subject      Optional message subject.
 * @param body         Message body. Must not be empty.
 * @param userId       Optional user identifier — the same ID used for entitlement checks.
 *                     When null, the SDK automatically fills in [io.jishu.sdk.Jishu.displayUserID]
 *                     so the app owner can add the sender directly to a promo grant from the dashboard.
 */
data class ContactMessage(
    val senderName: String? = null,
    val senderEmail: String,
    val subject: String? = null,
    val body: String,
    val userId: String? = null
)
