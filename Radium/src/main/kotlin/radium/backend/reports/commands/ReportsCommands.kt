package radium.backend.reports.commands

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Optional
import revxrsal.commands.velocity.annotation.CommandPermission
import radium.backend.Radium
import radium.backend.reports.models.ReportStatus
import radium.backend.reports.models.RequestStatus
import radium.backend.reports.models.RequestType
import java.util.*

/**
 * Command for creating and managing reports from Radium
 * Used primarily by the lobby server via API
 */
@Command("report")
@CommandPermission("radium.report.use")
class ReportCommand(private val radium: Radium) {

    @Command("report")
    @CommandPermission("radium.report.create")
    suspend fun createReport(
        actor: Player,
        targetName: String,
        reason: String,
        @Optional description: String?
    ) {
        try {
            // Find target player
            val targetProfile = radium.connectionHandler.findPlayerProfile(targetName)
            if (targetProfile == null) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.target_not_found", 
                    "target" to targetName))
                return
            }
            
            val finalDescription = description ?: reason
            
            val result = radium.reportsManager.createReport(
                reporterId = actor.uniqueId,
                reporterName = actor.username,
                targetId = targetProfile.uuid,
                targetName = targetProfile.username,
                reason = reason,
                description = finalDescription,
                serverName = "velocity"
            )
            
            if (result.isSuccess) {
                val report = result.getOrNull()!!
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.create.success", 
                    "target" to targetName,
                    "reason" to reason,
                    "id" to report.id.toString()))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.create.error", 
                    "error" to error))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "create report",
                "message" to e.message.toString()))
        }
    }
}

/**
 * Command for creating and managing requests from Radium
 */
@Command("request")
@CommandPermission("radium.request.use")
class RequestCommand(private val radium: Radium) {

    @Command("request")
    @CommandPermission("radium.request.create")
    suspend fun createRequest(
        actor: Player,
        type: String,
        subject: String,
        @Optional description: String?
    ) {
        try {
            val requestType = try {
                RequestType.valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.invalid_type", 
                    "type" to type))
                return
            }
            
            val finalDescription = description ?: subject
            
            val result = radium.reportsManager.createRequest(
                playerId = actor.uniqueId,
                playerName = actor.username,
                type = requestType,
                subject = subject,
                description = finalDescription,
                serverName = "velocity"
            )
            
            if (result.isSuccess) {
                val request = result.getOrNull()!!
                actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.create.success", 
                    "type" to requestType.displayName,
                    "subject" to subject,
                    "id" to request.id.toString()))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.create.error", 
                    "error" to error))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "create request",
                "message" to e.message.toString()))
        }
    }
}

/**
 * Command for staff to manage reports and requests
 */
@Command("reports")
@CommandPermission("radium.reports.manage")
class ReportsManagementCommand(private val radium: Radium) {

    @Subcommand("view")
    @CommandPermission("radium.reports.view")
    suspend fun viewReports(
        actor: Player,
        @Optional targetName: String?
    ) {
        try {
            if (targetName != null) {
                // View reports for specific player
                val targetProfile = radium.connectionHandler.findPlayerProfile(targetName)
                if (targetProfile == null) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.target_not_found", 
                        "target" to targetName))
                    return
                }
                
                val reports = radium.reportsManager.getReportsByTarget(targetProfile.uuid)
                
                if (reports.isEmpty()) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.view.none", 
                        "target" to targetName))
                    return
                }
                
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.view.header", 
                    "target" to targetName,
                    "count" to reports.size.toString()))
                
                reports.take(10).forEach { report ->
                    val statusColor = when (report.status) {
                        ReportStatus.PENDING -> NamedTextColor.YELLOW
                        ReportStatus.INVESTIGATING -> NamedTextColor.GOLD
                        ReportStatus.RESOLVED -> NamedTextColor.GREEN
                        ReportStatus.DISMISSED -> NamedTextColor.RED
                    }
                    
                    actor.sendMessage(Component.text()
                        .append(Component.text("ID: ", NamedTextColor.GRAY))
                        .append(Component.text(report.id.toString().substring(0, 8), NamedTextColor.WHITE))
                        .append(Component.text(" | Reporter: ", NamedTextColor.GRAY))
                        .append(Component.text(report.reporterName, NamedTextColor.WHITE))
                        .append(Component.text(" | Reason: ", NamedTextColor.GRAY))
                        .append(Component.text(report.reason, NamedTextColor.RED))
                        .append(Component.text(" | Status: ", NamedTextColor.GRAY))
                        .append(Component.text(report.status.name, statusColor))
                        .build())
                }
                
                if (reports.size > 10) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.view.more", 
                        "count" to (reports.size - 10).toString()))
                }
            } else {
                // View all pending reports
                val pendingReports = radium.reportsManager.getReportsByStatus(ReportStatus.PENDING)
                
                if (pendingReports.isEmpty()) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.view.no_pending"))
                    return
                }
                
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.view.pending_header", 
                    "count" to pendingReports.size.toString()))
                
                pendingReports.take(10).forEach { report ->
                    actor.sendMessage(Component.text()
                        .append(Component.text("ID: ", NamedTextColor.GRAY))
                        .append(Component.text(report.id.toString().substring(0, 8), NamedTextColor.WHITE))
                        .append(Component.text(" | ", NamedTextColor.GRAY))
                        .append(Component.text(report.reporterName, NamedTextColor.WHITE))
                        .append(Component.text(" → ", NamedTextColor.GRAY))
                        .append(Component.text(report.targetName, NamedTextColor.WHITE))
                        .append(Component.text(" | ", NamedTextColor.GRAY))
                        .append(Component.text(report.reason, NamedTextColor.RED))
                        .build())
                }
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "view reports",
                "message" to e.message.toString()))
        }
    }
    
    @Subcommand("resolve")
    @CommandPermission("radium.reports.resolve")
    suspend fun resolveReport(
        actor: Player,
        reportId: String,
        @Optional resolution: String?
    ) {
        try {
            val uuid = try {
                UUID.fromString(reportId)
            } catch (e: IllegalArgumentException) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.invalid_id", 
                    "id" to reportId))
                return
            }
            
            val success = radium.reportsManager.updateReportStatus(
                reportId = uuid,
                newStatus = ReportStatus.RESOLVED,
                handlerId = actor.uniqueId,
                handlerName = actor.username,
                resolution = resolution ?: "Resolved by staff"
            )
            
            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.resolve.success", 
                    "id" to reportId))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.resolve.failed", 
                    "id" to reportId))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "resolve report",
                "message" to e.message.toString()))
        }
    }
    
    @Subcommand("dismiss")
    @CommandPermission("radium.reports.dismiss")
    suspend fun dismissReport(
        actor: Player,
        reportId: String,
        @Optional reason: String?
    ) {
        try {
            val uuid = try {
                UUID.fromString(reportId)
            } catch (e: IllegalArgumentException) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.invalid_id", 
                    "id" to reportId))
                return
            }
            
            val success = radium.reportsManager.updateReportStatus(
                reportId = uuid,
                newStatus = ReportStatus.DISMISSED,
                handlerId = actor.uniqueId,
                handlerName = actor.username,
                resolution = reason ?: "Dismissed by staff"
            )
            
            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.dismiss.success", 
                    "id" to reportId))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.dismiss.failed", 
                    "id" to reportId))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "dismiss report",
                "message" to e.message.toString()))
        }
    }
    
    @Subcommand("stats")
    @CommandPermission("radium.reports.stats")
    suspend fun showStatistics(actor: Player) {
        try {
            val stats = radium.reportsManager.getStatistics()
            
            actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.stats.header"))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.stats.total", 
                "count" to (stats["total_reports"] ?: 0).toString()))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.stats.pending", 
                "count" to (stats["pending_reports"] ?: 0).toString()))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.stats.resolved", 
                "count" to (stats["resolved_reports"] ?: 0).toString()))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.stats.total", 
                "count" to (stats["total_requests"] ?: 0).toString()))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.stats.pending", 
                "count" to (stats["pending_requests"] ?: 0).toString()))
            actor.sendMessage(radium.yamlFactory.getMessageComponent("requests.stats.completed", 
                "count" to (stats["completed_requests"] ?: 0).toString()))
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "get statistics",
                "message" to e.message.toString()))
        }
    }
    
    @Subcommand("cleanup")
    @CommandPermission("radium.reports.admin")
    suspend fun cleanupOldEntries(
        actor: Player,
        @Optional days: String?
    ) {
        try {
            val daysCutoff = days?.toLongOrNull() ?: 30L
            
            val (reportsCleanedUp, requestsCleanedUp) = radium.reportsManager.cleanupOldEntries(daysCutoff)
            
            actor.sendMessage(radium.yamlFactory.getMessageComponent("reports.cleanup.success", 
                "reports" to reportsCleanedUp.toString(),
                "requests" to requestsCleanedUp.toString(),
                "days" to daysCutoff.toString()))
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation", 
                "operation" to "cleanup old entries",
                "message" to e.message.toString()))
        }
    }
}
