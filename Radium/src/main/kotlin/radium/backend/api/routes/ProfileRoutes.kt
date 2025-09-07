package radium.backend.api.routes

import com.velocitypowered.api.proxy.ProxyServer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import radium.backend.Radium
import radium.backend.api.dto.*

fun Route.profileRoutes(plugin: Radium, server: ProxyServer, logger: ComponentLogger) {
    route("/profiles") {
        // Get player profile
        get("/{player}") {
            val playerName = call.parameters["player"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Missing player parameter")
            )

            // First try to find online player
            val onlinePlayer = server.getPlayer(playerName).orElse(null)
            
            val profile: radium.backend.player.Profile?
            val playerUuid: java.util.UUID
            val actualUsername: String
            
            if (onlinePlayer != null) {
                // Player is online - get from connection handler
                profile = plugin.connectionHandler.getPlayerProfile(onlinePlayer.uniqueId)
                playerUuid = onlinePlayer.uniqueId
                actualUsername = onlinePlayer.username
            } else {
                // Player is offline - search database by username
                try {
                    val foundProfile = plugin.connectionHandler.findPlayerProfileByUsername(playerName)
                    if (foundProfile != null) {
                        profile = foundProfile
                        playerUuid = foundProfile.uuid
                        actualUsername = foundProfile.username
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Player profile not found"))
                        return@get
                    }
                } catch (e: Exception) {
                    logger.warn("Error searching for offline player $playerName: ${e.message}")
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Player not found"))
                    return@get
                }
            }

            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
                return@get
            }

            val highestRank = profile.getHighestRank(plugin.rankManager)
            logger.debug("DEBUG: Profile API - Player: $actualUsername, Rank: ${highestRank?.name}, NameTag: '${highestRank?.nameTag}'")
            val rankResponse = if (highestRank != null) {
                RankResponse(
                    name = highestRank.name,
                    weight = highestRank.weight,
                    prefix = highestRank.prefix,
                    color = highestRank.color,
                    permissions = highestRank.permissions.toList(),
                    nameTag = highestRank.nameTag
                )
            } else null

            val response = ProfileResponse(
                username = actualUsername,
                uuid = playerUuid.toString(),
                rank = rankResponse,
                permissions = highestRank?.permissions?.toList() ?: emptyList(),
                prefix = highestRank?.prefix,
                color = highestRank?.color,
                isVanished = onlinePlayer?.let { plugin.staffManager.isVanished(it) } ?: false,
                lastSeen = profile.lastSeen?.toEpochMilli()
            )

            call.respond(response)
        }

        // Get profile by UUID
        get("/uuid/{uuid}") {
            val uuidString = call.parameters["uuid"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Missing uuid parameter")
            )

            val uuid = try {
                java.util.UUID.fromString(uuidString)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid UUID format")
                )
            }

            val profile = plugin.connectionHandler.getPlayerProfile(uuid)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
                return@get
            }

            val player = server.getPlayer(uuid).orElse(null)
            val highestRank = profile.getHighestRank(plugin.rankManager)
            logger.debug("DEBUG: Profile API (UUID) - Player: ${profile.username}, Rank: ${highestRank?.name}, NameTag: '${highestRank?.nameTag}'")
            val rankResponse = if (highestRank != null) {
                RankResponse(
                    name = highestRank.name,
                    weight = highestRank.weight,
                    prefix = highestRank.prefix,
                    color = highestRank.color,
                    permissions = highestRank.permissions.toList(),
                    nameTag = highestRank.nameTag
                )
            } else null

            val response = ProfileResponse(
                username = profile.username,
                uuid = uuid.toString(),
                rank = rankResponse,
                permissions = highestRank?.permissions?.toList() ?: emptyList(),
                prefix = highestRank?.prefix,
                color = highestRank?.color,
                isVanished = player?.let { plugin.staffManager.isVanished(it) } ?: false,
                lastSeen = profile.lastSeen?.toEpochMilli()
            )

            call.respond(response)
        }
    }
}
