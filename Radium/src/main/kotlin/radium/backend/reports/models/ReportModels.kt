package radium.backend.reports.models

import java.time.Instant
import java.util.UUID

/**
 * Data model for player reports
 */
data class Report(
    val id: UUID = UUID.randomUUID(),
    val reporterId: UUID,
    val reporterName: String,
    val targetId: UUID,
    val targetName: String,
    val reason: String,
    val description: String,
    val serverName: String,
    val timestamp: Instant = Instant.now(),
    var status: ReportStatus = ReportStatus.PENDING,
    var handlerId: UUID? = null,
    var handlerName: String? = null,
    var resolution: String? = null,
    var resolvedAt: Instant? = null
)

/**
 * Data model for player requests
 */
data class Request(
    val id: UUID = UUID.randomUUID(),
    val playerId: UUID,
    val playerName: String,
    val type: RequestType,
    val subject: String,
    val description: String,
    val serverName: String,
    val timestamp: Instant = Instant.now(),
    var status: RequestStatus = RequestStatus.PENDING,
    var handlerId: UUID? = null,
    var handlerName: String? = null,
    var response: String? = null,
    var respondedAt: Instant? = null
)

enum class ReportStatus {
    PENDING, INVESTIGATING, RESOLVED, DISMISSED
}

enum class RequestStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}

enum class RequestType(val displayName: String, val description: String) {
    HELP("Help", "Request assistance from staff"),
    APPEAL("Appeal", "Appeal a punishment"),
    UNBAN("Unban Request", "Request an unban"),
    UNMUTE("Unmute Request", "Request an unmute"),
    GENERAL("General", "General inquiry"),
    TECHNICAL("Technical", "Technical support"),
    OTHER("Other", "Other type of request")
}
