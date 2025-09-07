package radium.backend.reports

import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import radium.backend.Radium
import radium.backend.player.TabListManager
import radium.backend.reports.models.Report
import radium.backend.reports.models.Request
import radium.backend.reports.models.ReportStatus
import radium.backend.reports.models.RequestStatus
import radium.backend.reports.models.RequestType
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for handling reports and requests
 * Provides high-level operations and caching for the reports system
 */
class ReportsManager(
    private val radium: Radium,
    private val repository: ReportsRepository,
    private val logger: ComponentLogger
) {
    
    // Cooldown tracking to prevent spam
    private val reportCooldowns = ConcurrentHashMap<UUID, Instant>()
    private val requestCooldowns = ConcurrentHashMap<UUID, Instant>()
    
    // Configuration values
    private var reportCooldownSeconds = 300L // 5 minutes
    private var requestCooldownSeconds = 60L // 1 minute
    private var maxReportsPerPlayer = 5
    private var maxRequestsPerPlayer = 3
    
    suspend fun initialize() {
        logger.info("Initializing Reports Manager...")
        
        // Load configuration if needed
        loadConfiguration()
        
        logger.info("Reports Manager initialized successfully")
    }
    
    private fun loadConfiguration() {
        try {
            // TODO: Load from configuration files if needed
            // For now using default values
        } catch (e: Exception) {
            logger.warn("Failed to load reports configuration, using defaults", e)
        }
    }
    
    // Report operations
    
    suspend fun createReport(
        reporterId: UUID,
        reporterName: String,
        targetId: UUID,
        targetName: String,
        reason: String,
        description: String,
        serverName: String
    ): Result<Report> {
        return try {
            // Check cooldown
            val lastReport = reportCooldowns[reporterId]
            if (lastReport != null && Instant.now().isBefore(lastReport.plusSeconds(reportCooldownSeconds))) {
                val remainingTime = lastReport.plusSeconds(reportCooldownSeconds).epochSecond - Instant.now().epochSecond
                return Result.failure(Exception("You must wait $remainingTime seconds before creating another report"))
            }
            
            // Check max reports (we could query database here if needed)
            val recentReports = repository.findReportsByReporter(reporterId)
            val activeReports = recentReports.filter { 
                it.status == ReportStatus.PENDING || it.status == ReportStatus.INVESTIGATING 
            }
            
            if (activeReports.size >= maxReportsPerPlayer) {
                return Result.failure(Exception("You have reached the maximum number of active reports ($maxReportsPerPlayer)"))
            }
            
            // Create report
            val report = Report(
                reporterId = reporterId,
                reporterName = reporterName,
                targetId = targetId,
                targetName = targetName,
                reason = reason,
                description = description,
                serverName = serverName
            )
            
            // Save to database
            val saved = repository.saveReport(report)
            if (!saved) {
                return Result.failure(Exception("Failed to save report to database"))
            }
            
            // Update cooldown
            reportCooldowns[reporterId] = Instant.now()
            
            // DISABLED: Don't notify staff here since Lobby already handles notifications
            // notifyStaffNewReport(report)

            logger.info("Report created: $reporterName reported $targetName for $reason")
            Result.success(report)
        } catch (e: Exception) {
            logger.error("Failed to create report", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateReportStatus(
        reportId: UUID,
        newStatus: ReportStatus,
        handlerId: UUID,
        handlerName: String,
        resolution: String? = null
    ): Boolean {
        return try {
            val report = repository.findReportById(reportId)
            if (report == null) {
                logger.warn("Report $reportId not found")
                return false
            }
            
            report.status = newStatus
            report.handlerId = handlerId
            report.handlerName = handlerName
            report.resolution = resolution
            if (newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
                report.resolvedAt = Instant.now()
            }
            
            val updated = repository.updateReport(report)
            if (updated) {
                // Notify reporter
                notifyReportUpdate(report)
                logger.info("Report for ${report.targetName} updated by $handlerName: $newStatus")
            }
            
            updated
        } catch (e: Exception) {
            logger.error("Failed to update report status", e)
            false
        }
    }
    
    // Request operations
    
    suspend fun createRequest(
        playerId: UUID,
        playerName: String,
        type: RequestType,
        subject: String,
        description: String,
        serverName: String
    ): Result<Request> {
        return try {
            // Check cooldown
            val lastRequest = requestCooldowns[playerId]
            if (lastRequest != null && Instant.now().isBefore(lastRequest.plusSeconds(requestCooldownSeconds))) {
                val remainingTime = lastRequest.plusSeconds(requestCooldownSeconds).epochSecond - Instant.now().epochSecond
                return Result.failure(Exception("You must wait $remainingTime seconds before creating another request"))
            }
            
            // Check max requests
            val recentRequests = repository.findRequestsByPlayer(playerId)
            val activeRequests = recentRequests.filter { 
                it.status == RequestStatus.PENDING || it.status == RequestStatus.IN_PROGRESS 
            }
            
            if (activeRequests.size >= maxRequestsPerPlayer) {
                return Result.failure(Exception("You have reached the maximum number of active requests ($maxRequestsPerPlayer)"))
            }
            
            // Create request
            val request = Request(
                playerId = playerId,
                playerName = playerName,
                type = type,
                subject = subject,
                description = description,
                serverName = serverName
            )
            
            // Save to database
            val saved = repository.saveRequest(request)
            if (!saved) {
                return Result.failure(Exception("Failed to save request to database"))
            }
            
            // Update cooldown
            requestCooldowns[playerId] = Instant.now()
            
            // DISABLED: Don't notify staff here since Lobby already handles notifications
            // notifyStaffNewRequest(request)

            logger.info("Request created: $playerName created ${type.displayName} request: $subject")
            Result.success(request)
        } catch (e: Exception) {
            logger.error("Failed to create request", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateRequestStatus(
        requestId: UUID,
        newStatus: RequestStatus,
        handlerId: UUID,
        handlerName: String,
        response: String? = null
    ): Boolean {
        return try {
            val request = repository.findRequestById(requestId)
            if (request == null) {
                logger.warn("Request $requestId not found")
                return false
            }
            
            request.status = newStatus
            request.handlerId = handlerId
            request.handlerName = handlerName
            request.response = response
            if (newStatus == RequestStatus.COMPLETED || newStatus == RequestStatus.CANCELLED) {
                request.respondedAt = Instant.now()
            }
            
            val updated = repository.updateRequest(request)
            if (updated) {
                // Notify requester
                notifyRequestUpdate(request)
                logger.info("Request by ${request.playerName} updated by $handlerName: $newStatus")
            }
            
            updated
        } catch (e: Exception) {
            logger.error("Failed to update request status", e)
            false
        }
    }
    
    // Query operations
    
    suspend fun getReportsByTarget(targetId: UUID): List<Report> {
        return repository.findReportsByTarget(targetId)
    }
    
    suspend fun getReportsByReporter(reporterId: UUID): List<Report> {
        return repository.findReportsByReporter(reporterId)
    }
    
    suspend fun getReportsByStatus(status: ReportStatus): List<Report> {
        return repository.findReportsByStatus(status)
    }
    
    suspend fun getRequestsByPlayer(playerId: UUID): List<Request> {
        return repository.findRequestsByPlayer(playerId)
    }
    
    suspend fun getRequestsByStatus(status: RequestStatus): List<Request> {
        return repository.findRequestsByStatus(status)
    }
    
    suspend fun getReport(reportId: UUID): Report? {
        return repository.findReportById(reportId)
    }
    
    suspend fun getRequest(requestId: UUID): Request? {
        return repository.findRequestById(requestId)
    }
    
    // Statistics and maintenance
    
    suspend fun getStatistics(): Map<String, Any> {
        val reportsStats = repository.getReportsStatistics()
        val requestsStats = repository.getRequestsStatistics()
        
        return reportsStats + requestsStats + mapOf(
            "report_cooldown_seconds" to reportCooldownSeconds,
            "request_cooldown_seconds" to requestCooldownSeconds,
            "max_reports_per_player" to maxReportsPerPlayer,
            "max_requests_per_player" to maxRequestsPerPlayer,
            "active_report_cooldowns" to reportCooldowns.size,
            "active_request_cooldowns" to requestCooldowns.size
        )
    }
    
    suspend fun cleanupOldEntries(daysCutoff: Long = 30): Pair<Long, Long> {
        val reportsCleanedUp = repository.cleanupOldReports(daysCutoff)
        val requestsCleanedUp = repository.cleanupOldRequests(daysCutoff)
        
        // Also cleanup cooldowns for offline players
        val now = Instant.now()
        reportCooldowns.entries.removeIf { 
            now.isAfter(it.value.plusSeconds(reportCooldownSeconds)) 
        }
        requestCooldowns.entries.removeIf { 
            now.isAfter(it.value.plusSeconds(requestCooldownSeconds)) 
        }
        
        return Pair(reportsCleanedUp, requestsCleanedUp)
    }
    
    // Notification methods
    
    private fun notifyStaffNewReport(report: Report) {
        // COMPLETELY DISABLED - Lobby handles all notifications
        // This method is intentionally left empty to prevent duplicate broadcasts
    }
    
    private fun notifyStaffNewRequest(request: Request) {
        // COMPLETELY DISABLED - Lobby handles all notifications
        // This method is intentionally left empty to prevent duplicate broadcasts
    }
    
    private fun notifyReportUpdate(report: Report) {
        try {
            // Try to find the reporter on the network
            val reporter = radium.server.getPlayer(report.reporterId).orElse(null)
            
            if (reporter != null) {
                val message = radium.yamlFactory.getMessage("reports.reporter.update") 
                    ?: "&6[Reports] &7Your report against &f{target} &7has been updated: &a{status}"
                
                val formattedMessage = message
                    .replace("{target}", report.targetName)
                    .replace("{status}", report.status.name)
                
                reporter.sendMessage(TabListManager.safeParseColoredText(formattedMessage))
                
                // Include resolution if available
                if (report.resolution != null) {
                    val resolutionMessage = radium.yamlFactory.getMessage("reports.reporter.resolution") 
                        ?: "&6[Reports] &7Resolution: &f{resolution}"
                    
                    reporter.sendMessage(TabListManager.safeParseColoredText(
                        resolutionMessage.replace("{resolution}", report.resolution!!)
                    ))
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify report update", e)
        }
    }
    
    private fun notifyRequestUpdate(request: Request) {
        try {
            // Try to find the requester on the network
            val requester = radium.server.getPlayer(request.playerId).orElse(null)
            
            if (requester != null) {
                val message = radium.yamlFactory.getMessage("reports.requester.update") 
                    ?: "&6[Requests] &7Your {type} request has been updated: &a{status}"
                
                val formattedMessage = message
                    .replace("{type}", request.type.displayName)
                    .replace("{status}", request.status.name)
                
                requester.sendMessage(TabListManager.safeParseColoredText(formattedMessage))
                
                // Include response if available
                if (request.response != null) {
                    val responseMessage = radium.yamlFactory.getMessage("reports.requester.response") 
                        ?: "&6[Requests] &7Response: &f{response}"
                    
                    requester.sendMessage(TabListManager.safeParseColoredText(
                        responseMessage.replace("{response}", request.response!!)
                    ))
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify request update", e)
        }
    }
}
