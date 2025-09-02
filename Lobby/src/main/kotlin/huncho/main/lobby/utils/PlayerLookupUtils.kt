package huncho.main.lobby.utils

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Utility for resolving player information (UUID and name) for punishment commands
 * Handles both online and offline player lookups
 */
object PlayerLookupUtils {
    
    /**
     * Represents resolved player information
     */
    data class PlayerInfo(
        val uuid: UUID,
        val name: String,
        val isOnline: Boolean = false
    )
    
    /**
     * Resolve a player by name or UUID string, returning both UUID and current name
     * This handles both online and offline players using Mojang API if needed
     */
    fun resolvePlayer(input: String): CompletableFuture<PlayerInfo?> {
        val future = CompletableFuture<PlayerInfo?>()
        
        try {
            // First, check if it's a UUID
            val uuid = try {
                UUID.fromString(input)
            } catch (e: IllegalArgumentException) {
                null
            }
            
            if (uuid != null) {
                // Input is a UUID, look up the player
                resolveByUuid(uuid, future)
            } else {
                // Input is a name, look up the player
                resolveByName(input, future)
            }
        } catch (e: Exception) {
            future.complete(null)
        }
        
        return future
    }
    
    /**
     * Resolve player by UUID
     */
    private fun resolveByUuid(uuid: UUID, future: CompletableFuture<PlayerInfo?>) {
        // Check if player is online first
        val onlinePlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        if (onlinePlayer != null) {
            future.complete(PlayerInfo(uuid, onlinePlayer.username, true))
            return
        }
        
        // Player is offline, use Mojang API to get current name
        // For now, we'll use a simple approach - in production you'd want to use Mojang API
        // or cache player data in your database
        
        // Fallback: return UUID with unknown name (Radium should handle this)
        future.complete(PlayerInfo(uuid, uuid.toString().substring(0, 8), false))
    }
    
    /**
     * Resolve player by name
     */
    private fun resolveByName(name: String, future: CompletableFuture<PlayerInfo?>) {
        // Check if player is online first
        val onlinePlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { 
            it.username.equals(name, ignoreCase = true) 
        }
        if (onlinePlayer != null) {
            LobbyPlugin.logger.debug("Found online player $name with UUID ${onlinePlayer.uuid}")
            future.complete(PlayerInfo(onlinePlayer.uuid, onlinePlayer.username, true))
            return
        }
        
        LobbyPlugin.logger.debug("Player $name is not online, checking Radium API...")
        
        // Player is offline - try to look up via Radium integration
        try {
            // Use Radium's player data API to find the player
            val plugin = LobbyPlugin
            plugin.radiumIntegration.getPlayerUuidByName(name).thenAccept { uuid ->
                LobbyPlugin.logger.debug("Radium API lookup for $name returned UUID: $uuid")
                if (uuid != null) {
                    future.complete(PlayerInfo(uuid, name, false))
                } else {
                    // Still not found - return null
                    LobbyPlugin.logger.warn("Player $name not found in Radium API")
                    future.complete(null)
                }
            }.exceptionally { ex ->
                plugin.logger.warn("Failed to lookup player '$name' via Radium: ${ex.message}")
                future.complete(null)
                null
            }
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Exception during player lookup for $name: ${e.message}", e)
            future.complete(null)
        }
    }
    
    /**
     * Check if a staff member can punish a target based on rank weights
     * Returns true if punishment is allowed, false otherwise
     */
    fun canPunish(plugin: LobbyPlugin, staffUuid: UUID, targetUuid: UUID): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        try {
            // Get staff rank weight
            plugin.radiumIntegration.getRankWeight(staffUuid).thenAccept { staffWeight ->
                // Get target rank weight
                plugin.radiumIntegration.getRankWeight(targetUuid).thenAccept { targetWeight ->
                    // Staff can punish target if their weight is higher
                    val canPunish = staffWeight > targetWeight
                    future.complete(canPunish)
                }.exceptionally { ex ->
                    plugin.logger.warn("Failed to get target rank weight for $targetUuid: ${ex.message}")
                    // If we can't get target weight, allow punishment (fail open)
                    future.complete(true)
                    null
                }
            }.exceptionally { ex ->
                plugin.logger.warn("Failed to get staff rank weight for $staffUuid: ${ex.message}")
                // If we can't get staff weight, allow punishment (fail open)
                future.complete(true)
                null
            }
        } catch (e: Exception) {
            plugin.logger.error("Error checking punishment permissions", e)
            future.complete(true) // Fail open
        }
        
        return future
    }
    
    /**
     * Get rank weight information for display in error messages
     */
    fun getRankWeightInfo(plugin: LobbyPlugin, staffUuid: UUID, targetUuid: UUID): CompletableFuture<Pair<Int, Int>> {
        val future = CompletableFuture<Pair<Int, Int>>()
        
        plugin.radiumIntegration.getRankWeight(staffUuid).thenAccept { staffWeight ->
            plugin.radiumIntegration.getRankWeight(targetUuid).thenAccept { targetWeight ->
                future.complete(Pair(staffWeight, targetWeight))
            }.exceptionally { 
                future.complete(Pair(staffWeight, 0))
                null
            }
        }.exceptionally { 
            future.complete(Pair(0, 0))
            null
        }
        
        return future
    }
}
