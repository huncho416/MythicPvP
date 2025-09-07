package radium.backend.reports

import com.mongodb.reactivestreams.client.MongoDatabase
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bson.Document
import radium.backend.reports.models.Report
import radium.backend.reports.models.Request
import radium.backend.reports.models.ReportStatus
import radium.backend.reports.models.RequestStatus
import radium.backend.reports.models.RequestType
import java.time.Instant
import java.util.*

/**
 * Repository for reports and requests data operations
 * Follows the pattern established by the punishment system
 */
class ReportsRepository(
    private val database: MongoDatabase,
    private val logger: ComponentLogger
) {
    private val reportsCollection = database.getCollection("reports")
    private val requestsCollection = database.getCollection("requests")
    
    // Report operations
    
    suspend fun saveReport(report: Report): Boolean {
        return try {
            val document = Document().apply {
                append("_id", report.id.toString())
                append("reporterId", report.reporterId.toString())
                append("reporterName", report.reporterName)
                append("targetId", report.targetId.toString())
                append("targetName", report.targetName)
                append("reason", report.reason)
                append("description", report.description)
                append("serverName", report.serverName)
                append("timestamp", report.timestamp.toEpochMilli())
                append("status", report.status.name)
                append("handlerId", report.handlerId?.toString())
                append("handlerName", report.handlerName)
                append("resolution", report.resolution)
                append("resolvedAt", report.resolvedAt?.toEpochMilli())
            }
            
            reportsCollection.insertOne(document).awaitFirst()
            logger.debug(Component.text("Saved report ${report.id} to database"))
            true
        } catch (e: Exception) {
            logger.error(Component.text("Failed to save report ${report.id}: ${e.message}", NamedTextColor.RED))
            false
        }
    }
    
    suspend fun updateReport(report: Report): Boolean {
        return try {
            val filter = Document("_id", report.id.toString())
            val update = Document("\$set", Document().apply {
                append("status", report.status.name)
                append("handlerId", report.handlerId?.toString())
                append("handlerName", report.handlerName)
                append("resolution", report.resolution)
                append("resolvedAt", report.resolvedAt?.toEpochMilli())
            })
            
            val result = reportsCollection.updateOne(filter, update).awaitFirst()
            result.modifiedCount > 0
        } catch (e: Exception) {
            logger.error(Component.text("Failed to update report ${report.id}: ${e.message}", NamedTextColor.RED))
            false
        }
    }
    
    suspend fun findReportById(reportId: UUID): Report? {
        return try {
            val filter = Document("_id", reportId.toString())
            val document = reportsCollection.find(filter).awaitFirstOrNull()
            document?.let { documentToReport(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find report $reportId: ${e.message}", NamedTextColor.RED))
            null
        }
    }
    
    suspend fun findReportsByTarget(targetId: UUID): List<Report> {
        return try {
            val filter = Document("targetId", targetId.toString())
            reportsCollection.find(filter)
                .sort(Document("timestamp", -1))
                .asFlow()
                .toList()
                .map { documentToReport(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find reports for target $targetId: ${e.message}", NamedTextColor.RED))
            emptyList()
        }
    }
    
    suspend fun findReportsByReporter(reporterId: UUID): List<Report> {
        return try {
            val filter = Document("reporterId", reporterId.toString())
            reportsCollection.find(filter)
                .sort(Document("timestamp", -1))
                .asFlow()
                .toList()
                .map { documentToReport(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find reports by reporter $reporterId: ${e.message}", NamedTextColor.RED))
            emptyList()
        }
    }
    
    suspend fun findReportsByStatus(status: ReportStatus): List<Report> {
        return try {
            val filter = Document("status", status.name)
            reportsCollection.find(filter)
                .sort(Document("timestamp", -1))
                .asFlow()
                .toList()
                .map { documentToReport(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find reports by status $status: ${e.message}", NamedTextColor.RED))
            emptyList()
        }
    }
    
    // Request operations
    
    suspend fun saveRequest(request: Request): Boolean {
        return try {
            val document = Document().apply {
                append("_id", request.id.toString())
                append("playerId", request.playerId.toString())
                append("playerName", request.playerName)
                append("type", request.type.name)
                append("subject", request.subject)
                append("description", request.description)
                append("serverName", request.serverName)
                append("timestamp", request.timestamp.toEpochMilli())
                append("status", request.status.name)
                append("handlerId", request.handlerId?.toString())
                append("handlerName", request.handlerName)
                append("response", request.response)
                append("respondedAt", request.respondedAt?.toEpochMilli())
            }
            
            requestsCollection.insertOne(document).awaitFirst()
            logger.debug(Component.text("Saved request ${request.id} to database"))
            true
        } catch (e: Exception) {
            logger.error(Component.text("Failed to save request ${request.id}: ${e.message}", NamedTextColor.RED))
            false
        }
    }
    
    suspend fun updateRequest(request: Request): Boolean {
        return try {
            val filter = Document("_id", request.id.toString())
            val update = Document("\$set", Document().apply {
                append("status", request.status.name)
                append("handlerId", request.handlerId?.toString())
                append("handlerName", request.handlerName)
                append("response", request.response)
                append("respondedAt", request.respondedAt?.toEpochMilli())
            })
            
            val result = requestsCollection.updateOne(filter, update).awaitFirst()
            result.modifiedCount > 0
        } catch (e: Exception) {
            logger.error(Component.text("Failed to update request ${request.id}: ${e.message}", NamedTextColor.RED))
            false
        }
    }
    
    suspend fun findRequestById(requestId: UUID): Request? {
        return try {
            val filter = Document("_id", requestId.toString())
            val document = requestsCollection.find(filter).awaitFirstOrNull()
            document?.let { documentToRequest(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find request $requestId: ${e.message}", NamedTextColor.RED))
            null
        }
    }
    
    suspend fun findRequestsByPlayer(playerId: UUID): List<Request> {
        return try {
            val filter = Document("playerId", playerId.toString())
            requestsCollection.find(filter)
                .sort(Document("timestamp", -1))
                .asFlow()
                .toList()
                .map { documentToRequest(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find requests for player $playerId: ${e.message}", NamedTextColor.RED))
            emptyList()
        }
    }
    
    suspend fun findRequestsByStatus(status: RequestStatus): List<Request> {
        return try {
            val filter = Document("status", status.name)
            requestsCollection.find(filter)
                .sort(Document("timestamp", -1))
                .asFlow()
                .toList()
                .map { documentToRequest(it) }
        } catch (e: Exception) {
            logger.error(Component.text("Failed to find requests by status $status: ${e.message}", NamedTextColor.RED))
            emptyList()
        }
    }
    
    // Helper methods
    
    private fun documentToReport(document: Document): Report {
        return Report(
            id = UUID.fromString(document.getString("_id")),
            reporterId = UUID.fromString(document.getString("reporterId")),
            reporterName = document.getString("reporterName"),
            targetId = UUID.fromString(document.getString("targetId")),
            targetName = document.getString("targetName"),
            reason = document.getString("reason"),
            description = document.getString("description"),
            serverName = document.getString("serverName"),
            timestamp = Instant.ofEpochMilli(document.getLong("timestamp")),
            status = ReportStatus.valueOf(document.getString("status")),
            handlerId = document.getString("handlerId")?.let { UUID.fromString(it) },
            handlerName = document.getString("handlerName"),
            resolution = document.getString("resolution"),
            resolvedAt = document.getLong("resolvedAt")?.let { Instant.ofEpochMilli(it) }
        )
    }
    
    private fun documentToRequest(document: Document): Request {
        return Request(
            id = UUID.fromString(document.getString("_id")),
            playerId = UUID.fromString(document.getString("playerId")),
            playerName = document.getString("playerName"),
            type = RequestType.valueOf(document.getString("type")),
            subject = document.getString("subject"),
            description = document.getString("description"),
            serverName = document.getString("serverName"),
            timestamp = Instant.ofEpochMilli(document.getLong("timestamp")),
            status = RequestStatus.valueOf(document.getString("status")),
            handlerId = document.getString("handlerId")?.let { UUID.fromString(it) },
            handlerName = document.getString("handlerName"),
            response = document.getString("response"),
            respondedAt = document.getLong("respondedAt")?.let { Instant.ofEpochMilli(it) }
        )
    }
    
    // Statistics and cleanup methods
    
    suspend fun getReportsStatistics(): Map<String, Any> {
        return try {
            val total = reportsCollection.estimatedDocumentCount().awaitFirst()
            val pending = reportsCollection.countDocuments(Document("status", ReportStatus.PENDING.name)).awaitFirst()
            val resolved = reportsCollection.countDocuments(Document("status", ReportStatus.RESOLVED.name)).awaitFirst()
            
            mapOf(
                "total_reports" to total,
                "pending_reports" to pending,
                "resolved_reports" to resolved
            )
        } catch (e: Exception) {
            logger.error(Component.text("Failed to get reports statistics: ${e.message}", NamedTextColor.RED))
            emptyMap()
        }
    }
    
    suspend fun getRequestsStatistics(): Map<String, Any> {
        return try {
            val total = requestsCollection.estimatedDocumentCount().awaitFirst()
            val pending = requestsCollection.countDocuments(Document("status", RequestStatus.PENDING.name)).awaitFirst()
            val completed = requestsCollection.countDocuments(Document("status", RequestStatus.COMPLETED.name)).awaitFirst()
            
            mapOf(
                "total_requests" to total,
                "pending_requests" to pending,
                "completed_requests" to completed
            )
        } catch (e: Exception) {
            logger.error(Component.text("Failed to get requests statistics: ${e.message}", NamedTextColor.RED))
            emptyMap()
        }
    }
    
    suspend fun cleanupOldReports(daysCutoff: Long = 30): Long {
        return try {
            val cutoff = Instant.now().minusSeconds(daysCutoff * 24 * 3600)
            val filter = Document("\$and", listOf(
                Document("timestamp", Document("\$lt", cutoff.toEpochMilli())),
                Document("status", Document("\$in", listOf(ReportStatus.RESOLVED.name, ReportStatus.DISMISSED.name)))
            ))
            
            val result = reportsCollection.deleteMany(filter).awaitFirst()
            logger.info(Component.text("Cleaned up ${result.deletedCount} old reports"))
            result.deletedCount
        } catch (e: Exception) {
            logger.error(Component.text("Failed to cleanup old reports: ${e.message}", NamedTextColor.RED))
            0
        }
    }
    
    suspend fun cleanupOldRequests(daysCutoff: Long = 30): Long {
        return try {
            val cutoff = Instant.now().minusSeconds(daysCutoff * 24 * 3600)
            val filter = Document("\$and", listOf(
                Document("timestamp", Document("\$lt", cutoff.toEpochMilli())),
                Document("status", Document("\$in", listOf(RequestStatus.COMPLETED.name, RequestStatus.CANCELLED.name)))
            ))
            
            val result = requestsCollection.deleteMany(filter).awaitFirst()
            logger.info(Component.text("Cleaned up ${result.deletedCount} old requests"))
            result.deletedCount
        } catch (e: Exception) {
            logger.error(Component.text("Failed to cleanup old requests: ${e.message}", NamedTextColor.RED))
            0
        }
    }
}
