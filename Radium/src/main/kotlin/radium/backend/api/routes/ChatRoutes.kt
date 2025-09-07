package radium.backend.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import radium.backend.commands.AdminChat
import radium.backend.commands.StaffChat
import radium.backend.api.dto.ErrorResponse
import revxrsal.commands.velocity.actor.VelocityCommandActor
import java.util.*

/**
 * API routes for chat functionality (admin chat, staff chat, etc.)
 */
fun Route.chatRoutes(radium: Radium, logger: ComponentLogger) {
    route("/chat") {
        // Admin chat endpoint
        post("/admin") {
            try {
                val body = call.receive<Map<String, Any>>()
                val senderUuid = body["sender_uuid"]?.toString()
                val senderName = body["sender_name"]?.toString()
                val message = body["message"]?.toString()
                
                if (senderName.isNullOrBlank() || message.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sender_name or message", "Both sender_name and message are required"))
                    return@post
                }
                
                // Find player if UUID provided
                val senderPlayer = if (senderUuid != null) {
                    try {
                        radium.server.getPlayer(UUID.fromString(senderUuid)).orElse(null)
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                // Check permission
                if (senderPlayer != null && !senderPlayer.hasPermission("radium.adminchat")) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("No permission for admin chat", "Player does not have radium.adminchat permission"))
                    return@post
                }
                
                // Get sender profile for formatting
                val senderProfile = if (senderPlayer != null) {
                    radium.connectionHandler.getPlayerProfile(senderPlayer.uniqueId, senderPlayer.username)
                } else null
                
                val senderRank = if (senderProfile != null) {
                    senderProfile.getHighestRankCached(radium.rankManager)
                } else null
                
                // Clean up rank prefix/suffix - remove any invalid characters and ensure proper format
                val senderPrefix = senderRank?.prefix?.let { prefix ->
                    // Remove any non-printable characters and normalize color codes
                    prefix.replace("∩┐╜", "&").replace("§", "&").replace("[^\\x20-\\x7E&]".toRegex(), "").trim()
                } ?: ""
                val senderSuffix = senderRank?.suffix?.let { suffix ->
                    // Remove any non-printable characters and normalize color codes  
                    suffix.replace("∩┐╜", "&").replace("§", "&").replace("[^\\x20-\\x7E&]".toRegex(), "").trim()
                } ?: ""
                
                // Format the admin chat message
                val formattedMessage = "&c[AC] $senderPrefix$senderName$senderSuffix&f: $message"
                val chatComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage)
                
                // Send to all players with admin chat permission
                var recipientCount = 0
                radium.server.allPlayers.forEach { player ->
                    if (player.hasPermission("radium.adminchat")) {
                        player.sendMessage(chatComponent)
                        recipientCount++
                    }
                }
                
                // Log admin chat message
                logger.info("[AdminChat] $senderName: $message")
                
                call.respond(mapOf(
                    "success" to true,
                    "recipients" to recipientCount
                ))
                
            } catch (e: Exception) {
                logger.error("Error handling admin chat API request", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error", e.message ?: "Unknown error"))
            }
        }
        
        // Staff chat endpoint
        post("/staff") {
            try {
                val body = call.receive<Map<String, Any>>()
                val senderUuid = body["sender_uuid"]?.toString()
                val senderName = body["sender_name"]?.toString()
                val message = body["message"]?.toString()
                
                if (senderName.isNullOrBlank() || message.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing sender_name or message", "Both sender_name and message are required"))
                    return@post
                }
                
                // Find player if UUID provided
                val senderPlayer = if (senderUuid != null) {
                    try {
                        radium.server.getPlayer(UUID.fromString(senderUuid)).orElse(null)
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                // Check permission (skip for direct system messages)
                if (senderPlayer != null && !senderPlayer.hasPermission("radium.staffchat") && senderName != "SYSTEM_DIRECT") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("No permission for staff chat", "Player does not have radium.staffchat permission"))
                    return@post
                }
                
                // Handle direct system messages differently (they already contain proper formatting)
                val formattedMessage = if (senderName == "SYSTEM_DIRECT") {
                    message // Use message as-is for direct system messages
                } else {
                    // Get sender profile for formatting
                    val senderProfile = if (senderPlayer != null) {
                        radium.connectionHandler.getPlayerProfile(senderPlayer.uniqueId, senderPlayer.username)
                    } else null
                    
                    val senderRank = if (senderProfile != null) {
                        senderProfile.getHighestRankCached(radium.rankManager)
                    } else null
                    
                    // Clean up rank prefix/suffix - remove any invalid characters and ensure proper format
                    val senderPrefix = senderRank?.prefix?.let { prefix ->
                        // Remove any non-printable characters and normalize color codes
                        prefix.replace("∩┐╜", "&").replace("§", "&").replace("[^\\x20-\\x7E&]".toRegex(), "").trim()
                    } ?: ""
                    val senderSuffix = senderRank?.suffix?.let { suffix ->
                        // Remove any non-printable characters and normalize color codes  
                        suffix.replace("∩┐╜", "&").replace("§", "&").replace("[^\\x20-\\x7E&]".toRegex(), "").trim()
                    } ?: ""
                    
                    // Format the staff chat message
                    "&b[SC] $senderPrefix$senderName$senderSuffix&f: $message"
                }
                
                val chatComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage)
                
                // Send to all players with staff chat permission
                var recipientCount = 0
                radium.server.allPlayers.forEach { player ->
                    if (player.hasPermission("radium.staffchat")) {
                        player.sendMessage(chatComponent)
                        recipientCount++
                    }
                }
                
                // Log staff chat message
                val logSenderName = if (senderName == "SYSTEM_DIRECT") "System" else senderName
                logger.info("[StaffChat] $logSenderName: $message")
                
                call.respond(mapOf(
                    "success" to true,
                    "recipients" to recipientCount
                ))
                
            } catch (e: Exception) {
                logger.error("Error handling staff chat API request", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error", e.message ?: "Unknown error"))
            }
        }
    }
}
