package huncho.main.lobby.features.reports

import huncho.main.lobby.LobbyPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import net.minestom.server.entity.Player
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for handling player reports and requests with database synchronization
 * Integrates with StomUI for animated GUIs
 */
class ReportsManager(private val plugin: LobbyPlugin) {
    
    private val logger: Logger = LoggerFactory.getLogger(ReportsManager::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Report cache for quick access
    private val activeReports = ConcurrentHashMap<UUID, MutableList<Report>>()
    private val reportCooldowns = ConcurrentHashMap<UUID, Instant>()
    
    // Request cache
    private val activeRequests = ConcurrentHashMap<UUID, MutableList<Request>>()
    private val requestCooldowns = ConcurrentHashMap<UUID, Instant>()
    
    // Configuration
    private var reportCooldownSeconds = 300 // 5 minutes
    private var requestCooldownSeconds = 60 // 1 minute
    private var maxReportsPerPlayer = 5
    private var maxRequestsPerPlayer = 3
    
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
        var resolution: String? = null
    )
    
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
        var response: String? = null
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
    
    fun initialize() {
        logger.info("Initializing Reports Manager...")
        
        try {
            // Load configuration
            loadConfiguration()
            
            // Load active reports and requests from Radium
            coroutineScope.launch {
                loadActiveReports()
                loadActiveRequests()
            }
            
            // Reports Manager initialized
        } catch (e: Exception) {
            logger.error("Failed to initialize Reports Manager", e)
        }
    }
    
    private fun loadConfiguration() {
        try {
            val config = plugin.configManager.mainConfig
            reportCooldownSeconds = plugin.configManager.getInt(config, "reports.cooldown_seconds", 300)
            requestCooldownSeconds = plugin.configManager.getInt(config, "requests.cooldown_seconds", 60)
            maxReportsPerPlayer = plugin.configManager.getInt(config, "reports.max_per_player", 5)
            maxRequestsPerPlayer = plugin.configManager.getInt(config, "requests.max_per_player", 3)
        } catch (e: Exception) {
            logger.warn("Failed to load reports configuration, using defaults", e)
        }
    }
    
    private suspend fun loadActiveReports() {
        try {
            // TODO: Load from Radium database via API
            // For now, initialize empty cache
            logger.debug("Loading active reports from database...")
        } catch (e: Exception) {
            logger.error("Failed to load active reports", e)
        }
    }
    
    private suspend fun loadActiveRequests() {
        try {
            // TODO: Load from Radium database via API
            // For now, initialize empty cache
            logger.debug("Loading active requests from database...")
        } catch (e: Exception) {
            logger.error("Failed to load active requests", e)
        }
    }
    
    /**
     * Create a new report
     */
    suspend fun createReport(
        reporter: Player,
        targetName: String,
        reason: String,
        description: String
    ): Result<Report> {
        return try {
            // Check cooldown
            val lastReport = reportCooldowns[reporter.uuid]
            if (lastReport != null && Instant.now().isBefore(lastReport.plusSeconds(reportCooldownSeconds.toLong()))) {
                val remainingTime = lastReport.plusSeconds(reportCooldownSeconds.toLong()).epochSecond - Instant.now().epochSecond
                return Result.failure(Exception("You must wait $remainingTime seconds before creating another report"))
            }
            
            // Check max reports
            val playerReports = activeReports[reporter.uuid] ?: mutableListOf()
            if (playerReports.size >= maxReportsPerPlayer) {
                return Result.failure(Exception("You have reached the maximum number of active reports ($maxReportsPerPlayer)"))
            }
            
            // Resolve target UUID from Radium
            val targetPlayerData: huncho.main.lobby.integration.RadiumIntegration.PlayerData? = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerDataByName(targetName).await()
            }
            if (targetPlayerData == null) {
                return Result.failure(Exception("Player '$targetName' not found"))
            }
            
            // Create report
            val report = Report(
                reporterId = reporter.uuid,
                reporterName = reporter.username,
                targetId = targetPlayerData.uuid!!,
                targetName = targetPlayerData.username,
                reason = reason,
                description = description,
                serverName = "lobby" // Or get current server from reporter
            )
            
            // Add to cache
            activeReports.computeIfAbsent(reporter.uuid) { mutableListOf() }.add(report)
            reportCooldowns[reporter.uuid] = Instant.now()
            
            // Save to database via Radium
            saveReportToDatabase(report)
            
            // Notify staff
            notifyStaffNewReport(report)
            
            logger.info("Report created: ${reporter.username} reported ${targetName} for $reason")
            Result.success(report)
        } catch (e: Exception) {
            logger.error("Failed to create report", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a new request
     */
    suspend fun createRequest(
        player: Player,
        type: RequestType,
        subject: String,
        description: String
    ): Result<Request> {
        return try {
            // Check cooldown
            val lastRequest = requestCooldowns[player.uuid]
            if (lastRequest != null && Instant.now().isBefore(lastRequest.plusSeconds(requestCooldownSeconds.toLong()))) {
                val remainingTime = lastRequest.plusSeconds(requestCooldownSeconds.toLong()).epochSecond - Instant.now().epochSecond
                return Result.failure(Exception("You must wait $remainingTime seconds before creating another request"))
            }
            
            // Check max requests
            val playerRequests = activeRequests[player.uuid] ?: mutableListOf()
            if (playerRequests.size >= maxRequestsPerPlayer) {
                return Result.failure(Exception("You have reached the maximum number of active requests ($maxRequestsPerPlayer)"))
            }
            
            // Create request
            val request = Request(
                playerId = player.uuid,
                playerName = player.username,
                type = type,
                subject = subject,
                description = description,
                serverName = "lobby"
            )
            
            // Add to cache
            activeRequests.computeIfAbsent(player.uuid) { mutableListOf() }.add(request)
            requestCooldowns[player.uuid] = Instant.now()
            
            // Save to database via Radium
            saveRequestToDatabase(request)
            
            // Notify staff
            notifyStaffNewRequest(request)
            
            logger.info("Request created: ${player.username} created ${type.displayName} request: $subject")
            Result.success(request)
        } catch (e: Exception) {
            logger.error("Failed to create request", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get reports for a player
     */
    fun getPlayerReports(playerUuid: UUID): List<Report> {
        return activeReports[playerUuid]?.toList() ?: emptyList()
    }
    
    /**
     * Get requests for a player
     */
    fun getPlayerRequests(playerUuid: UUID): List<Request> {
        return activeRequests[playerUuid]?.toList() ?: emptyList()
    }
    
    /**
     * Get all pending reports (for staff)
     */
    fun getAllPendingReports(): List<Report> {
        return activeReports.values.flatten().filter { it.status == ReportStatus.PENDING }
    }
    
    /**
     * Get all reports regardless of status (for staff)
     */
    fun getAllReports(): List<Report> {
        return activeReports.values.flatten()
    }
    
    /**
     * Update report status
     */
    suspend fun updateReportStatus(
        reportId: UUID,
        newStatus: ReportStatus,
        handler: Player,
        resolution: String? = null
    ): Boolean {
        return try {
            // Find report
            val report = findReport(reportId)
            if (report == null) {
                logger.warn("Report $reportId not found")
                return false
            }
            
            // Update report
            report.status = newStatus
            report.handlerId = handler.uuid
            report.handlerName = handler.username
            report.resolution = resolution
            
            // Save to database
            saveReportToDatabase(report)
            
            // Notify involved parties
            notifyReportUpdate(report)
            
            logger.info("Report for ${report.targetName} updated by ${handler.username}: $newStatus")
            true
        } catch (e: Exception) {
            logger.error("Failed to update report status", e)
            false
        }
    }
    
    /**
     * Update request status
     */
    suspend fun updateRequestStatus(
        requestId: UUID,
        newStatus: RequestStatus,
        handler: Player,
        response: String? = null
    ): Boolean {
        return try {
            // Find request
            val request = findRequest(requestId)
            if (request == null) {
                logger.warn("Request $requestId not found")
                return false
            }
            
            // Update request
            request.status = newStatus
            request.handlerId = handler.uuid
            request.handlerName = handler.username
            request.response = response
            
            // Save to database
            saveRequestToDatabase(request)
            
            // Notify player
            notifyRequestUpdate(request)
            
            logger.info("Request by ${request.playerName} updated by ${handler.username}: $newStatus")
            true
        } catch (e: Exception) {
            logger.error("Failed to update request status", e)
            false
        }
    }
    
    private fun findReport(reportId: UUID): Report? {
        return activeReports.values.flatten().find { it.id == reportId }
    }
    
    private fun findRequest(requestId: UUID): Request? {
        return activeRequests.values.flatten().find { it.id == requestId }
    }
    
    private suspend fun saveReportToDatabase(report: Report) {
        try {
            val response: huncho.main.lobby.integration.RadiumIntegration.ReportsApiResponse = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.createReport(
                    reporterId = report.reporterId.toString(),
                    reporterName = report.reporterName,
                    targetId = report.targetId.toString(),
                    targetName = report.targetName,
                    reason = report.reason,
                    description = report.description,
                    serverName = report.serverName
                ).await()
            }
            
            if (!response.success) {
                logger.error("Failed to save report to Radium: ${response.message}")
            } else {
                logger.debug("Successfully saved report for ${report.targetName} to Radium")
            }
        } catch (e: Exception) {
            logger.error("Failed to save report to database", e)
        }
    }
    
    private suspend fun saveRequestToDatabase(request: Request) {
        try {
            // DISABLE saving to Radium to prevent duplicate notifications
            // The Lobby will handle all notifications directly
            logger.debug("Request saved locally: ${request.playerName} - ${request.subject}")

            // TODO: If database persistence is needed, implement a way to save
            // without triggering Radium backend notifications

            /* DISABLED TO PREVENT DUPLICATE BROADCASTS
            val response: huncho.main.lobby.integration.RadiumIntegration.ReportsApiResponse = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.createRequest(
                    playerId = request.playerId.toString(),
                    playerName = request.playerName,
                    type = request.type.name,
                    subject = request.subject,
                    description = request.description,
                    serverName = request.serverName
                ).await()
            }
            
            if (!response.success) {
                logger.error("Failed to save request to Radium: ${response.message}")
            } else {
                logger.debug("Successfully saved request by ${request.playerName} to Radium")
            }
            */
        } catch (e: Exception) {
            logger.error("Failed to save request to database", e)
        }
    }
    
    private fun notifyStaffNewReport(report: Report) {
        try {
            // NUCLEAR APPROACH: Bypass all existing systems and send directly
            val message = "§b[SC] §eNew Report - §e${report.reporterName} §7reported §e${report.targetName} §7for: §f${report.reason}"

            // Send directly to all online players with staff permissions
            net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach { player ->
                try {
                    // Check if player has staff permissions using PermissionCache
                    if (huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.staff") ||
                        huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.reports.receive") ||
                        huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.reports.resolve") ||
                        huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.reports.dismiss")) {
                        player.sendMessage(message)
                        logger.info("Sent report broadcast to staff member: ${player.username}")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send report message to ${player.username}", e)
                }
            }

            logger.info("Report broadcast sent: ${report.reporterName} reported ${report.targetName}")
        } catch (e: Exception) {
            logger.error("Failed to notify staff of new report", e)
        }
    }
    
    private fun notifyStaffNewRequest(request: Request) {
        try {
            // NUCLEAR APPROACH: Bypass all existing systems and send directly
            val message = "§b[SC] §eNew Request - §e${request.playerName} §7needs help with: §f${request.subject}"

            // Send directly to all online players with staff permissions
            net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach { player ->
                try {
                    // Check if player has staff permissions using PermissionCache
                    if (huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.staff") ||
                        huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.requests.receive") ||
                        huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.requests.manage")) {
                        player.sendMessage(message)
                        logger.info("Sent request broadcast to staff member: ${player.username}")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send request message to ${player.username}", e)
                }
            }

            logger.info("Request broadcast sent: ${request.playerName} needs help with ${request.subject}")
        } catch (e: Exception) {
            logger.error("Failed to notify staff of new request", e)
        }
    }
    
    private fun notifyReportUpdate(report: Report) {
        try {
            // Notify reporter
            val reporter = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.uuid == report.reporterId }
            reporter?.sendMessage("§6[Reports] §7Your report against §f${report.targetName} §7has been updated: §a${report.status}")
            
            // If resolved, include resolution
            if (report.status == ReportStatus.RESOLVED && report.resolution != null) {
                reporter?.sendMessage("§6[Reports] §7Resolution: §f${report.resolution}")
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify report update", e)
        }
    }
    
    private fun notifyRequestUpdate(request: Request) {
        try {
            // Notify requester
            val player = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()
                .find { it.uuid == request.playerId }
            player?.sendMessage("§6[Requests] §7Your ${request.type.displayName} request has been updated: §a${request.status}")
            
            // If completed, include response
            if (request.status == RequestStatus.COMPLETED && request.response != null) {
                player?.sendMessage("§6[Requests] §7Response: §f${request.response}")
            }
        } catch (e: Exception) {
            logger.warn("Failed to notify request update", e)
        }
    }
    
    /**
     * Resolve the latest pending report for a target player
     */
    suspend fun resolveLatestReport(
        staff: Player,
        targetName: String,
        resolution: String
    ): Boolean {
        return try {
            // Get target UUID from Radium
            val targetData = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerDataByName(targetName).await()
            }
            
            if (targetData?.uuid == null) {
                return false
            }
            
            // Get reports for the target
            val reports = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getReportsForPlayer(targetData.uuid).await()
            }
            
            // Find the latest pending report
            val latestPendingReport = reports?.firstOrNull { it.status == "PENDING" }
            if (latestPendingReport == null) {
                return false
            }
            
            // Call Radium API to resolve the report
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.updateReportStatus(
                    reportId = latestPendingReport.id,
                    status = "RESOLVED",
                    handlerId = staff.uuid.toString(),
                    handlerName = staff.username,
                    resolution = resolution.ifEmpty { "Resolved by staff" }
                ).await()
            }
            
            success
        } catch (e: Exception) {
            logger.error("Failed to resolve report for $targetName", e)
            false
        }
    }
    
    /**
     * Dismiss the latest pending report for a target player
     */
    suspend fun dismissLatestReport(
        staff: Player,
        targetName: String,
        reason: String
    ): Boolean {
        return try {
            // Get target UUID from Radium
            val targetData = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerDataByName(targetName).await()
            }
            
            if (targetData?.uuid == null) {
                return false
            }
            
            // Get reports for the target
            val reports = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getReportsForPlayer(targetData.uuid).await()
            }
            
            // Find the latest pending report
            val latestPendingReport = reports?.firstOrNull { it.status == "PENDING" }
            if (latestPendingReport == null) {
                return false
            }
            
            // Call Radium API to dismiss the report
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.updateReportStatus(
                    reportId = latestPendingReport.id,
                    status = "DISMISSED",
                    handlerId = staff.uuid.toString(),
                    handlerName = staff.username,
                    resolution = reason.ifEmpty { "Dismissed by staff" }
                ).await()
            }
            
            success
        } catch (e: Exception) {
            logger.error("Failed to dismiss report for $targetName", e)
            false
        }
    }

    /**
     * Complete the latest pending request for a player
     */
    suspend fun completeLatestRequest(
        staff: Player,
        playerName: String,
        response: String
    ): Boolean {
        return try {
            // Get player UUID from Radium
            val playerData = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerDataByName(playerName).await()
            }
            
            if (playerData?.uuid == null) {
                return false
            }
            
            // Get requests for the player
            val requests = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getRequestsForPlayer(playerData.uuid).await()
            }
            
            // Find the latest pending request
            val latestPendingRequest = requests?.firstOrNull { it.status == "PENDING" }
            if (latestPendingRequest == null) {
                return false
            }
            
            // Call Radium API to complete the request
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.updateRequestStatus(
                    requestId = latestPendingRequest.id,
                    status = "COMPLETED",
                    handlerId = staff.uuid.toString(),
                    handlerName = staff.username,
                    response = response.ifEmpty { "Completed by staff" }
                ).await()
            }
            
            success
        } catch (e: Exception) {
            logger.error("Failed to complete request for $playerName", e)
            false
        }
    }
    
    /**
     * Cancel the latest pending request for a player
     */
    suspend fun cancelLatestRequest(
        staff: Player,
        playerName: String,
        reason: String
    ): Boolean {
        return try {
            // Get player UUID from Radium
            val playerData = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getPlayerDataByName(playerName).await()
            }
            
            if (playerData?.uuid == null) {
                return false
            }
            
            // Get requests for the player
            val requests = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.getRequestsForPlayer(playerData.uuid).await()
            }
            
            // Find the latest pending request
            val latestPendingRequest = requests?.firstOrNull { it.status == "PENDING" }
            if (latestPendingRequest == null) {
                return false
            }
            
            // Call Radium API to cancel the request
            val success = withContext(Dispatchers.IO) {
                plugin.radiumIntegration.updateRequestStatus(
                    requestId = latestPendingRequest.id,
                    status = "CANCELLED",
                    handlerId = staff.uuid.toString(),
                    handlerName = staff.username,
                    response = reason.ifEmpty { "Cancelled by staff" }
                ).await()
            }
            
            success
        } catch (e: Exception) {
            logger.error("Failed to cancel request for $playerName", e)
            false
        }
    }

    /**
     * Cleanup old reports and requests
     */
    private suspend fun cleanupOldEntries() {
        try {
            val cutoff = Instant.now().minusSeconds(86400 * 7) // 7 days
            
            // Cleanup reports
            activeReports.values.forEach { reports ->
                reports.removeIf { it.timestamp.isBefore(cutoff) && it.status != ReportStatus.PENDING }
            }
            
            // Cleanup requests
            activeRequests.values.forEach { requests ->
                requests.removeIf { it.timestamp.isBefore(cutoff) && it.status != RequestStatus.PENDING }
            }
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        }
    }
    
    /**
     * Get statistics
     */
    fun getStats(): Map<String, Any> {
        val totalReports = activeReports.values.sumOf { it.size }
        val pendingReports = getAllPendingReports().size
        val totalRequests = activeRequests.values.sumOf { it.size }
        
        return mapOf(
            "total_reports" to totalReports,
            "pending_reports" to pendingReports,
            "total_requests" to totalRequests,
            "pending_requests" to 0, // Will be updated via async call
            "active_players_with_reports" to activeReports.size,
            "active_players_with_requests" to activeRequests.size
        )
    }
    
    fun shutdown() {
        logger.info("Shutting down Reports Manager...")
        
        try {
            // Cancel coroutines
            coroutineScope.cancel()
            
            // Clear caches
            activeReports.clear()
            activeRequests.clear()
            reportCooldowns.clear()
            requestCooldowns.clear()
            
            logger.info("Reports Manager shutdown complete!")
        } catch (e: Exception) {
            logger.error("Error during Reports Manager shutdown", e)
        }
    }
}
