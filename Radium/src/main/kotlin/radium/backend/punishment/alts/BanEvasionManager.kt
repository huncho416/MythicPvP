package radium.backend.punishment.alts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import radium.backend.Radium
import radium.backend.punishment.models.PunishmentType
import radium.backend.player.Profile
import java.time.Instant
import java.util.*

/**
 * Manages ban evasion detection and alt account tracking
 * Integrates with the existing punishment system
 */
class BanEvasionManager(
    private val radium: Radium,
    private val logger: ComponentLogger
) {

    /**
     * Represents an alt account with its status
     */
    data class AltAccount(
        val uuid: UUID,
        val username: String,
        val ip: String,
        val lastSeen: Instant,
        val status: AltStatus
    )

    /**
     * Status of an alt account
     */
    enum class AltStatus(val displayName: String, val color: NamedTextColor) {
        ONLINE("Online", NamedTextColor.GREEN),
        BANNED("Banned", NamedTextColor.RED),
        OFFLINE("Offline", NamedTextColor.GRAY)
    }

    /**
     * Check for ban evasion when a player joins
     * This should be called from the login event listener
     */
    suspend fun checkBanEvasion(playerUuid: UUID, playerName: String, playerIp: String): BanEvasionResult? {
        return withContext(Dispatchers.IO) {
            try {
                // Get all alt accounts for this IP
                val altAccounts = getAltAccountsByIp(playerIp)
                
                // Check if any alt accounts are banned
                val bannedAlts = altAccounts.filter { it.status == AltStatus.BANNED }
                
                if (bannedAlts.isNotEmpty()) {
                    // Check if the current player is also banned
                    val currentPlayerBanned = radium.punishmentManager.isPlayerBanned(playerUuid.toString()) != null
                    
                    if (!currentPlayerBanned) {
                        // Potential ban evasion detected
                        return@withContext BanEvasionResult(
                            suspectedPlayer = playerUuid,
                            suspectedPlayerName = playerName,
                            suspectedPlayerIp = playerIp,
                            bannedAlts = bannedAlts,
                            allAlts = altAccounts
                        )
                    }
                }
                
                null
            } catch (e: Exception) {
                logger.error("Error checking ban evasion for player ${playerName}: ${e.message}")
                null
            }
        }
    }

    /**
     * Get all alt accounts for a specific IP address
     */
    suspend fun getAltAccountsByIp(ip: String): List<AltAccount> {
        return withContext(Dispatchers.IO) {
            try {
                val altAccounts = mutableListOf<AltAccount>()
                
                // Get all punishments for this IP to find associated player UUIDs
                val punishments = radium.punishmentManager.repository.findAllPunishmentsByIp(ip)
                
                val playerUuids = punishments.mapNotNull { 
                    try {
                        UUID.fromString(it.playerId)
                    } catch (e: Exception) {
                        null
                    }
                }.distinct()
                
                // Also check the profiles collection for players with this IP
                // Note: This assumes we track IP in profiles, which we may need to implement
                
                for (uuid in playerUuids) {
                    try {
                        // Get player profile from cache or load it
                        val profile = radium.connectionHandler.getPlayerProfile(uuid)
                        if (profile != null) {
                            // Check if player is currently banned
                            val isBanned = radium.punishmentManager.isPlayerBanned(uuid.toString()) != null
                            
                            // Check if player is online
                            val isOnline = radium.server.getPlayer(uuid).isPresent
                            
                            val status = when {
                                isOnline -> AltStatus.ONLINE
                                isBanned -> AltStatus.BANNED
                                else -> AltStatus.OFFLINE
                            }
                            
                            altAccounts.add(AltAccount(
                                uuid = uuid,
                                username = profile.username,
                                ip = ip,
                                lastSeen = profile.lastSeen,
                                status = status
                            ))
                        }
                    } catch (e: Exception) {
                        logger.warn("Error processing alt account for UUID ${uuid}: ${e.message}")
                    }
                }
                
                // Sort by last seen (most recent first)
                altAccounts.sortedByDescending { it.lastSeen }
            } catch (e: Exception) {
                logger.error("Error getting alt accounts for IP ${ip}: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Get all alt accounts for a specific player
     */
    suspend fun getAltAccountsByPlayer(playerUuid: UUID): List<AltAccount> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all IPs this player has used
                val punishments = radium.punishmentManager.repository.findActivePunishments(playerUuid.toString())
                
                val ips = punishments.mapNotNull { it.ip }.distinct()
                
                // Get all alt accounts for all these IPs
                val allAlts = mutableSetOf<AltAccount>()
                for (ip in ips) {
                    allAlts.addAll(getAltAccountsByIp(ip))
                }
                
                // Remove the original player from the list
                allAlts.filter { it.uuid != playerUuid }.sortedByDescending { it.lastSeen }
            } catch (e: Exception) {
                logger.error("Error getting alt accounts for player ${playerUuid}: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Get all players who have used a specific IP address
     */
    suspend fun getPlayersByIp(ip: String): List<AltAccount> {
        return getAltAccountsByIp(ip)
    }

    /**
     * Alert staff about potential ban evasion
     */
    suspend fun alertStaff(evasionResult: BanEvasionResult) {
        try {
            val message = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("BAN EVASION", NamedTextColor.RED))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(Component.text(evasionResult.suspectedPlayerName, NamedTextColor.YELLOW))
                .append(Component.text(" may be evading a ban!", NamedTextColor.RED))
                .build()
            
            val detailMessage = Component.text()
                .append(Component.text("Banned alts: ", NamedTextColor.GRAY))
                .append(Component.text(evasionResult.bannedAlts.joinToString(", ") { it.username }, NamedTextColor.RED))
                .append(Component.text(" (IP: ", NamedTextColor.GRAY))
                .append(Component.text(evasionResult.suspectedPlayerIp, NamedTextColor.YELLOW))
                .append(Component.text(")", NamedTextColor.GRAY))
                .build()
            
            // Send to all staff with ban evasion alert permission
            radium.server.allPlayers.forEach { staff ->
                if (staff.hasPermission("radium.banevasion.alerts")) {
                    staff.sendMessage(message)
                    staff.sendMessage(detailMessage)
                }
            }
            
            logger.info("Ban evasion alert sent for player ${evasionResult.suspectedPlayerName}")
        } catch (e: Exception) {
            logger.error("Error alerting staff about ban evasion: ${e.message}")
        }
    }

    /**
     * Result of ban evasion check
     */
    data class BanEvasionResult(
        val suspectedPlayer: UUID,
        val suspectedPlayerName: String,
        val suspectedPlayerIp: String,
        val bannedAlts: List<AltAccount>,
        val allAlts: List<AltAccount>
    )
}
