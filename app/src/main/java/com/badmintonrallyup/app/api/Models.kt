// DTOs mirroring the Rust backend (badminton-be-rust) — 1:1 with the iOS
// Models.swift. Envelope: { success, data, message }; snake_case JSON handled
// by the client's naming strategy.

package com.badmintonrallyup.app.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant =
        OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

typealias ApiUUID = @Serializable(with = UUIDSerializer::class) UUID
typealias ApiInstant = @Serializable(with = InstantSerializer::class) Instant

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
)

@Serializable
object EmptyData

// MARK: Auth

@Serializable
data class VerificationPending(
    val email: String,
    val expiresInMinutes: Long,
    val delivery: String,        // "email" | "server-log"
    val resendAfterSecs: Long,
)

@Serializable
data class LoginResult(
    val status: String,
    val isAdmin: Boolean,
    val accessToken: String? = null,   // native clients only
    val refreshToken: String? = null,
)

@Serializable
data class RefreshResult(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class MeProfile(
    val id: ApiUUID,
    val displayName: String,
    val email: String,
    val status: String,
    val isAdmin: Boolean,              // site operator, NOT group admin
    val notifPrefs: Map<String, Boolean>,
    val activeGroupId: ApiUUID? = null,
    val activeGroupName: String? = null,
    val activeGroupRole: String? = null,  // "admin" | "member"
    val groupsCount: Long,
)

// MARK: Groups

@Serializable
data class GroupBrief(
    val id: ApiUUID,
    val name: String,
    val role: String,
    val memberCount: Long,
    val isActive: Boolean,
)

@Serializable
data class GroupMemberRow(
    val id: ApiUUID,
    val displayName: String,
    val role: String,
    val joinedAt: ApiInstant,
)

@Serializable
data class GroupDetail(
    val id: ApiUUID,
    val name: String,
    val myRole: String,
    val venueName: String,
    val courtMin: Short,
    val courtMax: Short,
    val members: List<GroupMemberRow>,
)

@Serializable
data class MyInvite(
    val id: ApiUUID,
    val groupId: ApiUUID,
    val groupName: String,
    val invitedByName: String,
    val memberCount: Long,
)

@Serializable
data class GroupInviteRow(
    val id: ApiUUID,
    val email: String,
    val invitedByName: String,
    val createdAt: ApiInstant,
    val expiresAt: ApiInstant,
)

@Serializable
data class AutoPollConfig(
    val enabled: Boolean,
    val time: String,
    val note: String,
    val finalReminderTime: String,
)

// MARK: Polls

@Serializable
data class VoteEntry(
    val userId: ApiUUID,
    val displayName: String,
    val vote: String,            // yes | no | maybe
)

@Serializable
data class PublicUser(
    val id: ApiUUID,
    val displayName: String,
)

@Serializable
data class PollView(
    val id: ApiUUID,
    val gameDate: String,
    val proposedTime: String,
    val note: String? = null,
    val attendanceLocked: Boolean,
    val yesCount: Long,
    val noCount: Long,
    val maybeCount: Long,
    val votes: List<VoteEntry>,
    val myVote: String? = null,
    val attendees: List<PublicUser>,
)

@Serializable
data class PollSummary(
    val id: ApiUUID,
    val gameDate: String,
    val note: String? = null,
    val yesCount: Long,
    val attendeeCount: Long,
)

/** One row per group that has a poll today — Home Tonight card + Tonight's polls page. */
@Serializable
data class TonightPoll(
    val groupId: ApiUUID,
    val groupName: String,
    val id: ApiUUID,
    val proposedTime: String,
    val note: String? = null,
    val attendanceLocked: Boolean,
    val yesCount: Long,
    val maybeCount: Long,
    val noCount: Long,
    val myVote: String? = null,
)

// MARK: Courts

/** A group the court could be posted to, derived from the ticked logins. */
@Serializable
data class GroupCandidate(
    val id: ApiUUID,
    val name: String,
)

@Serializable
data class ReservationView(
    val id: ApiUUID,
    val courtNumber: Short,
    val attachedLogins: String? = null,
    val attachedCredentialIds: List<ApiUUID>,
    val reservedByName: String,
    val courtType: String,       // full | half
    val playerCount: Short? = null,
    val durationMinutes: Short,
    val startAt: ApiInstant,
    val expiryAt: ApiInstant,
    val queueNumber: Short? = null,
    val status: String,          // active | completed | cancelled
    val duplicateWarning: Boolean,
)

@Serializable
data class BoardMatch(
    val credentialIds: List<ApiUUID>,
    val bintangNames: List<String>,
    val alreadyInUseIds: List<ApiUUID>,
    val inUseCourt: Short? = null,
    val courtNumber: Short,
    val minutesLeft: Short? = null,
    val location: String,        // "current" | "queue"
    val queuePosition: Short? = null,
    val currentPlayers: List<String>,
    val queue: List<String>,
    val playerCount: Short,
)

@Serializable
data class BoardScanResult(
    val matches: List<BoardMatch>,
    val detectedCourts: Int,
    val message: String,
)

// MARK: Logins (court credentials)

@Serializable
data class CredentialView(
    val id: ApiUUID,
    val bintangName: String,
    val bintangPassword: String,
    val postedByName: String,
    val hasScreenshot: Boolean,
    val inUse: Boolean,
    val inUseCourt: Short? = null,
    // Optional so builds keep decoding against a server that predates multi-group.
    val inUseGroupName: String? = null,   // set when I belong to the group using this login
    val inUseElsewhere: Boolean? = null,  // in use by a group I'm not part of — court withheld
    val clearsAt: ApiInstant,
    val isMine: Boolean,
    val sharedGroupIds: List<ApiUUID>,
)

/**
 * Badge copy under the multi-group disclosure rules: members of the using
 * group (owners always qualify) see WHICH group holds the login; everyone
 * else only learns it's taken elsewhere.
 */
val CredentialView.inUseLabel: String
    get() {
        if (!inUse) return "login free"
        inUseGroupName?.let { group ->
            inUseCourt?.let { court -> return "court $court · $group" }
            return "in use · $group"
        }
        if (inUseElsewhere == true) return "in use · another group"
        inUseCourt?.let { return "in use · court $it" }
        return "in use"
    }

@Serializable
data class OcrLogin(
    val bintangName: String,
    val bintangPassword: String,
)

@Serializable
data class OcrResult(
    val logins: List<OcrLogin>,
    val ok: Boolean,
    val screenshotPath: String? = null,
    val message: String,
)

// MARK: kCal (strictly private to the account)

@Serializable
data class KcalToday(
    val myLog: MyLog? = null,
) {
    @Serializable
    data class MyLog(
        val kcal: Short,
        val note: String? = null,
    )
}

@Serializable
data class KcalHistory(
    val points: List<Point>,
    val totalSessions: Long,
    val avgKcal: Long,
    val maxKcal: Short,
) {
    @Serializable
    data class Point(
        val gameDate: String,
        val kcal: Short,
    )
}
