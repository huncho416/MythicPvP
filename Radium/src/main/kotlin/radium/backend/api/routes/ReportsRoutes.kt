package radium.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import radium.backend.Radium
import radium.backend.reports.models.ReportStatus
import radium.backend.reports.models.RequestStatus
import radium.backend.reports.models.RequestType
import java.util.*

data class CreateReportRequest(
    val reporterId: String,
    val reporterName: String,
    val targetId: String,
    val targetName: String,
    val reason: String,
    val description: String,
    val serverName: String
)

data class CreateRequestRequest(
    val playerId: String,
    val playerName: String,
    val type: String,
    val subject: String,
    val description: String,
    val serverName: String
)

data class UpdateReportRequest(
    val reportId: String,
    val status: String,
    val handlerId: String,
    val handlerName: String,
    val resolution: String?
)

data class UpdateRequestRequest(
    val requestId: String,
    val status: String,
    val handlerId: String,
    val handlerName: String,
    val response: String?
)

data class ReportsResponse(
    val success: Boolean,
    val message: String?,
    val data: Any? = null
)

/**
 * API routes for the reports and requests system
 * Allows lobby servers to interact with reports via HTTP
 */
fun Route.reportsRoutes(
    plugin: Radium,
    logger: ComponentLogger
) {
    // Create report
    post("/reports/create") {
        try {
            val request = call.receive<CreateReportRequest>()
            
            val result = plugin.reportsManager.createReport(
                reporterId = UUID.fromString(request.reporterId),
                reporterName = request.reporterName,
                targetId = UUID.fromString(request.targetId),
                targetName = request.targetName,
                reason = request.reason,
                description = request.description,
                serverName = request.serverName
            )
            
            if (result.isSuccess) {
                val report = result.getOrNull()!!
                call.respond(ReportsResponse(
                    success = true,
                    message = "Report created successfully",
                    data = mapOf(
                        "reportId" to report.id.toString(),
                        "timestamp" to report.timestamp.toString()
                    )
                ))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = error
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to create report via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Create request
    post("/requests/create") {
        try {
            val request = call.receive<CreateRequestRequest>()
            
            val requestType = try {
                RequestType.valueOf(request.type.uppercase())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Invalid request type: ${request.type}"
                ))
                return@post
            }
            
            val result = plugin.reportsManager.createRequest(
                playerId = UUID.fromString(request.playerId),
                playerName = request.playerName,
                type = requestType,
                subject = request.subject,
                description = request.description,
                serverName = request.serverName
            )
            
            if (result.isSuccess) {
                val req = result.getOrNull()!!
                call.respond(ReportsResponse(
                    success = true,
                    message = "Request created successfully",
                    data = mapOf(
                        "requestId" to req.id.toString(),
                        "timestamp" to req.timestamp.toString()
                    )
                ))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = error
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to create request via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Update report status
    post("/reports/update") {
        try {
            val request = call.receive<UpdateReportRequest>()
            
            val status = try {
                ReportStatus.valueOf(request.status.uppercase())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Invalid report status: ${request.status}"
                ))
                return@post
            }
            
            val success = plugin.reportsManager.updateReportStatus(
                reportId = UUID.fromString(request.reportId),
                newStatus = status,
                handlerId = UUID.fromString(request.handlerId),
                handlerName = request.handlerName,
                resolution = request.resolution
            )
            
            if (success) {
                call.respond(ReportsResponse(
                    success = true,
                    message = "Report updated successfully"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, ReportsResponse(
                    success = false,
                    message = "Report not found or update failed"
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to update report via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Update request status
    post("/requests/update") {
        try {
            val request = call.receive<UpdateRequestRequest>()
            
            val status = try {
                RequestStatus.valueOf(request.status.uppercase())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Invalid request status: ${request.status}"
                ))
                return@post
            }
            
            val success = plugin.reportsManager.updateRequestStatus(
                requestId = UUID.fromString(request.requestId),
                newStatus = status,
                handlerId = UUID.fromString(request.handlerId),
                handlerName = request.handlerName,
                response = request.response
            )
            
            if (success) {
                call.respond(ReportsResponse(
                    success = true,
                    message = "Request updated successfully"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, ReportsResponse(
                    success = false,
                    message = "Request not found or update failed"
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to update request via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Get reports for player
    get("/reports/player/{playerId}") {
        try {
            val playerId = call.parameters["playerId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Missing playerId parameter"
                ))
                return@get
            }
            val uuid = UUID.fromString(playerId)
            
            val reports = plugin.reportsManager.getReportsByTarget(uuid)
            
            call.respond(ReportsResponse(
                success = true,
                message = "Reports retrieved successfully",
                data = reports.map { report ->
                    mapOf(
                        "id" to report.id.toString(),
                        "reporterId" to report.reporterId.toString(),
                        "reporterName" to report.reporterName,
                        "targetId" to report.targetId.toString(),
                        "targetName" to report.targetName,
                        "reason" to report.reason,
                        "description" to report.description,
                        "serverName" to report.serverName,
                        "timestamp" to report.timestamp.toString(),
                        "status" to report.status.name,
                        "handlerId" to report.handlerId?.toString(),
                        "handlerName" to report.handlerName,
                        "resolution" to report.resolution,
                        "resolvedAt" to report.resolvedAt?.toString()
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("Failed to get reports for player via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Get requests for player
    get("/requests/player/{playerId}") {
        try {
            val playerId = call.parameters["playerId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Missing playerId parameter"
                ))
                return@get
            }
            val uuid = UUID.fromString(playerId)
            
            val requests = plugin.reportsManager.getRequestsByPlayer(uuid)
            
            call.respond(ReportsResponse(
                success = true,
                message = "Requests retrieved successfully",
                data = requests.map { request ->
                    mapOf(
                        "id" to request.id.toString(),
                        "playerId" to request.playerId.toString(),
                        "playerName" to request.playerName,
                        "type" to request.type.name,
                        "subject" to request.subject,
                        "description" to request.description,
                        "serverName" to request.serverName,
                        "timestamp" to request.timestamp.toString(),
                        "status" to request.status.name,
                        "handlerId" to request.handlerId?.toString(),
                        "handlerName" to request.handlerName,
                        "response" to request.response,
                        "respondedAt" to request.respondedAt?.toString()
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("Failed to get requests for player via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Get reports by status
    get("/reports/status/{status}") {
        try {
            val statusStr = call.parameters["status"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Missing status parameter"
                ))
                return@get
            }
            val status = try {
                ReportStatus.valueOf(statusStr.uppercase())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Invalid report status: $statusStr"
                ))
                return@get
            }
            
            val reports = plugin.reportsManager.getReportsByStatus(status)
            
            call.respond(ReportsResponse(
                success = true,
                message = "Reports retrieved successfully",
                data = reports.map { report ->
                    mapOf(
                        "id" to report.id.toString(),
                        "reporterId" to report.reporterId.toString(),
                        "reporterName" to report.reporterName,
                        "targetId" to report.targetId.toString(),
                        "targetName" to report.targetName,
                        "reason" to report.reason,
                        "description" to report.description,
                        "serverName" to report.serverName,
                        "timestamp" to report.timestamp.toString(),
                        "status" to report.status.name
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("Failed to get reports by status via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Get requests by status
    get("/requests/status/{status}") {
        try {
            val statusStr = call.parameters["status"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Missing status parameter"
                ))
                return@get
            }
            val status = try {
                RequestStatus.valueOf(statusStr.uppercase())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ReportsResponse(
                    success = false,
                    message = "Invalid request status: $statusStr"
                ))
                return@get
            }
            
            val requests = plugin.reportsManager.getRequestsByStatus(status)
            
            call.respond(ReportsResponse(
                success = true,
                message = "Requests retrieved successfully",
                data = requests.map { request ->
                    mapOf(
                        "id" to request.id.toString(),
                        "playerId" to request.playerId.toString(),
                        "playerName" to request.playerName,
                        "type" to request.type.name,
                        "subject" to request.subject,
                        "description" to request.description,
                        "serverName" to request.serverName,
                        "timestamp" to request.timestamp.toString(),
                        "status" to request.status.name
                    )
                }
            ))
        } catch (e: Exception) {
            logger.error("Failed to get requests by status via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    // Get reports statistics
    get("/reports/stats") {
        try {
            val stats = plugin.reportsManager.getStatistics()
            
            call.respond(ReportsResponse(
                success = true,
                message = "Statistics retrieved successfully",
                data = stats
            ))
        } catch (e: Exception) {
            logger.error("Failed to get reports statistics via API", e)
            call.respond(HttpStatusCode.InternalServerError, ReportsResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            ))
        }
    }
    
    logger.info("Reports API routes registered")
}
