package io.jishu.sdk.feedback

/**
 * Public feature proposal model returned by [io.jishu.sdk.Jishu.fetchProposals]
 * and [io.jishu.sdk.Jishu.submitProposal].
 */
data class Proposal(
    val id: String,
    val title: String,
    val description: String,
    val status: ProposalStatus,
    val voteCount: Int,
    val createdAt: String,
)

enum class ProposalStatus(val value: String) {
    OPEN("open"),
    PLANNED("planned"),
    IN_PROGRESS("in_progress"),
    SHIPPED("shipped"),
    REJECTED("rejected");

    companion object {
        fun fromValue(value: String): ProposalStatus {
            return entries.firstOrNull { it.value == value } ?: OPEN
        }
    }
}
