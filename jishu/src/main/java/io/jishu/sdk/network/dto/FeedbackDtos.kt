package io.jishu.sdk.network.dto

import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProposalDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
        val voteCount: Int = 0,
    val createdAt: String = "",
) {
    fun toProposal() = Proposal(
        id = id,
        title = title,
        description = description,
        status = ProposalStatus.fromValue(status),
        voteCount = voteCount,
        createdAt = createdAt,
    )
}

@Serializable
internal data class ProposalListResponse(
    val proposals: List<ProposalDto>,
)

@Serializable
internal data class ProposalResponse(
    val proposal: ProposalDto,
)

@Serializable
internal data class VoteResponse(
    @SerialName("vote_count") val voteCount: Int,
)

@Serializable
internal data class SubmitProposalRequest(
    val title: String,
    val description: String? = null,
    @SerialName("voter_token") val voterToken: String,
    @SerialName("osName") val osName: String? = null,
    @SerialName("osVersion") val osVersion: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
)

@Serializable
internal data class VoteRequest(
    @SerialName("voter_token") val voterToken: String,
    @SerialName("osName") val osName: String? = null,
    @SerialName("osVersion") val osVersion: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
)
