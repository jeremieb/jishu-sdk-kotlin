package io.jishu.sdk.contact

/**
 * Input for a contact form submission via [io.jishu.sdk.Jishu.sendContactMessage].
 *
 * @param senderName   Optional display name of the sender.
 * @param senderEmail  Email address of the sender.
 * @param subject      Optional message subject.
 * @param body         Message body. Must not be empty.
 */
data class ContactMessage(
    val senderName: String? = null,
    val senderEmail: String,
    val subject: String? = null,
    val body: String
)
