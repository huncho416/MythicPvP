package radium.backend.freeze

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the freeze system for preventing player movement and actions
 */
class FreezeManager(private val radium: Radium) {
    
    companion object {
        private val frozenPlayers = ConcurrentHashMap<UUID, FreezeData>()
        
        fun isFrozen(uuid: UUID): Boolean {
            return frozenPlayers.containsKey(uuid)
        }
        
        fun getFreezeData(uuid: UUID): FreezeData? {
            return frozenPlayers[uuid]
        }
        
        fun addFrozenPlayer(uuid: UUID, freezeData: FreezeData) {
            frozenPlayers[uuid] = freezeData
        }
        
        fun removeFrozenPlayer(uuid: UUID): FreezeData? {
            return frozenPlayers.remove(uuid)
        }
        
        fun getAllFrozenPlayers(): Map<UUID, FreezeData> {
            return frozenPlayers.toMap()
        }
    }
    
    /**
     * Freeze a player
     */
    fun freezePlayer(targetUuid: UUID, targetName: String, staffName: String): Boolean {
        return try {
            val freezeData = FreezeData(
                playerUuid = targetUuid,
                playerName = targetName,
                staffName = staffName,
                freezeTime = System.currentTimeMillis()
            )
            
            addFrozenPlayer(targetUuid, freezeData)
            
            // Send freeze message to target player
            val targetPlayer = radium.server.getPlayer(targetUuid).orElse(null)
            if (targetPlayer != null) {
                sendFreezeMessage(targetPlayer)
                
                // Send freeze status to their current server
                val currentServer = targetPlayer.currentServer.orElse(null)
                if (currentServer != null) {
                    val message = "freeze:${targetUuid}:true"
                    currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:freeze"), message.toByteArray())
                }
            }
              // Broadcast to staff with enhanced formatting
            broadcastFreezeAction(targetName, staffName, true)
            
            radium.logger.info("$staffName froze $targetName")
            true
            
        } catch (e: Exception) {
            radium.logger.error("Failed to freeze player $targetName", e)
            false
        }
    }
    
    /**
     * Unfreeze a player
     */
    fun unfreezePlayer(targetUuid: UUID, targetName: String, staffName: String): Boolean {
        return try {
            val freezeData = removeFrozenPlayer(targetUuid)
            if (freezeData == null) {
                return false // Player wasn't frozen
            }
            
            // Send unfreeze message to target player
            val targetPlayer = radium.server.getPlayer(targetUuid).orElse(null)
            if (targetPlayer != null) {
                sendUnfreezeMessage(targetPlayer, staffName)
                
                // Send unfreeze status to their current server
                val currentServer = targetPlayer.currentServer.orElse(null)
                if (currentServer != null) {
                    val message = "freeze:${targetUuid}:false"
                    currentServer.sendPluginMessage(MinecraftChannelIdentifier.from("radium:freeze"), message.toByteArray())
                }
            }
            
            // Broadcast to staff with enhanced formatting
            broadcastFreezeAction(targetName, staffName, false)
            
            radium.logger.info("$staffName unfroze $targetName")
            true
            
        } catch (e: Exception) {
            radium.logger.error("Failed to unfreeze player $targetName", e)
            false
        }
    }

    /**
     * Send freeze message to target player
     */
    private fun sendFreezeMessage(player: com.velocitypowered.api.proxy.Player) {
        radium.scope.launch {
            val messages = listOf(
                "",
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&c&lYOU HAVE BEEN FROZEN",
                "",
                "&c&lDo not log out or you will be banned!",
                "&c&lJoin our TeamSpeak: &fts.mythicpvp.com",
                "",
                "&c&lReason: &fStaff investigation",
                "&c&lWait for a staff member to assist you.",
                "",
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""
            )
            
            messages.forEach { line ->
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line))
            }
        }
    }
    
    /**
     * Send unfreeze message to target player
     */
    private fun sendUnfreezeMessage(player: com.velocitypowered.api.proxy.Player, staffName: String) {
        radium.scope.launch {
            val messages = listOf(
                "",
                "&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&a&lYOU HAVE BEEN UNFROZEN",
                "",
                "&a&lYou may now move and continue playing.",
                "&a&lUnfrozen by: &f$staffName",
                "",
                "&a&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""
            )
            
            messages.forEach { line ->
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line))
            }
        }
    }
    
    /**
     * Broadcast freeze/unfreeze action to all staff with proper formatting
     * Format: "&c[F] {staff rank prefix}{staff color}{staff name} &bfroze {target rank prefix}{target color}{target name}"
     */
    private fun broadcastFreezeAction(targetName: String, staffName: String, isFreezing: Boolean) {
        radium.scope.launch {
            try {
                // Get the staff player for rank formatting
                val staffPlayer = radium.server.getPlayer(staffName).orElse(null)
                
                if (staffPlayer != null) {
                    // Get staff player's profile for rank formatting
                    val staffProfile = radium.connectionHandler.findPlayerProfile(staffPlayer.uniqueId.toString())
                    val staffHighestRank = staffProfile?.getHighestRank(radium.rankManager)
                    val staffPrefix = staffHighestRank?.prefix ?: ""
                    
                    // Extract color from prefix (e.g., "&4[Owner] &4" -> "&4")
                    val staffColor = if (staffPrefix.isNotEmpty() && staffPrefix.startsWith("&")) {
                        staffPrefix.substring(0, 2) // Extract first color code like "&4"
                    } else {
                        "&f" // Default to white if no color found
                    }
                    
                    // Get target player for rank formatting
                    val targetPlayer = radium.server.getPlayer(targetName).orElse(null)
                    var targetPrefix = ""
                    var targetColor = "&f"
                    
                    if (targetPlayer != null) {
                        val targetProfile = radium.connectionHandler.findPlayerProfile(targetPlayer.uniqueId.toString())
                        val targetHighestRank = targetProfile?.getHighestRank(radium.rankManager)
                        targetPrefix = targetHighestRank?.prefix ?: ""
                        
                        // Extract color from target's prefix
                        targetColor = if (targetPrefix.isNotEmpty() && targetPrefix.startsWith("&")) {
                            targetPrefix.substring(0, 2)
                        } else {
                            "&f"
                        }
                    }
                    
                    // Create the enhanced freeze/unfreeze message
                    val action = if (isFreezing) "froze" else "unfroze"
                    val freezeMessage = "&c[F] $staffPrefix$staffColor$staffName &b${action} $targetPrefix$targetColor$targetName"
                    
                    val message = LegacyComponentSerializer.legacyAmpersand().deserialize(freezeMessage)
                    
                    radium.server.allPlayers.forEach { player ->
                        if (player.hasPermission("radium.staff")) {
                            player.sendMessage(message)
                        }
                    }
                } else {
                    // Fallback if staff player not found online
                    val action = if (isFreezing) "froze" else "unfroze"
                    val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&c[F] &f$staffName &b$action &f$targetName"
                    )
                    
                    radium.server.allPlayers.forEach { player ->
                        if (player.hasPermission("radium.staff")) {
                            player.sendMessage(message)
                        }
                    }
                }
            } catch (e: Exception) {
                radium.logger.error("Failed to broadcast freeze action", e)
                
                // Simple fallback message
                val action = if (isFreezing) "froze" else "unfroze"
                val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&c[F] &f$staffName &b$action &f$targetName"
                )
                
                radium.server.allPlayers.forEach { player ->
                    if (player.hasPermission("radium.staff")) {
                        player.sendMessage(message)
                    }
                }
            }
        }
    }
    
    /**
     * Handle player disconnect (check if they logged out while frozen)
     */
    fun handlePlayerDisconnect(playerUuid: UUID, playerName: String) {
        val freezeData = getFreezeData(playerUuid)
        if (freezeData != null) {
            removeFrozenPlayer(playerUuid)
            
            // Log the freeze evasion
            radium.logger.warn("$playerName logged out while frozen (froze by ${freezeData.staffName})")
            
            // Broadcast to staff
            radium.scope.launch {
                val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&c$playerName logged out while frozen! (Frozen by ${freezeData.staffName})"
                )
                
                radium.server.allPlayers.forEach { player ->
                    if (player.hasPermission("radium.staff")) {
                        player.sendMessage(message)
                    }
                }
            }
        }
    }
}

/**
 * Data class representing a frozen player
 */
data class FreezeData(
    val playerUuid: UUID,
    val playerName: String,
    val staffName: String,
    val freezeTime: Long
)
