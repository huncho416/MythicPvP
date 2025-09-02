package huncho.main.lobby.listeners

import com.google.gson.Gson
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.VanishData
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerPluginMessageEvent
import net.minestom.server.timer.TaskSchedule
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles plugin messages from Radium for vanish state management
 */
class VanishPluginMessageListener(private val plugin: LobbyPlugin) : EventListener<PlayerPluginMessageEvent> {
    
    private val gson = Gson()
    private val vanishData = ConcurrentHashMap<UUID, VanishData>()
    
    override fun eventType(): Class<PlayerPluginMessageEvent> = PlayerPluginMessageEvent::class.java
    
    override fun run(event: PlayerPluginMessageEvent): EventListener.Result {
        // Handle plugin messages from Radium
        if (event.identifier == "radium:vanish") {
            try {
                val message = String(event.message)

                
                // Try to parse as a general JSON first to detect message type
                val jsonMap = gson.fromJson(message, Map::class.java) as Map<String, Any?>
                val action = jsonMap["action"] as? String
                
                when (action) {
                    "batch_update" -> {
                        // Handle batch update messages
                        val updates = jsonMap["updates"] as? List<Map<String, Any?>>
                        if (updates != null) {
                            for (update in updates) {
                                val batchMessage = VanishMessage(
                                    action = update["action"] as? String,
                                    player = update["player"] as? String,
                                    playerName = update["playerName"] as? String,
                                    vanished = update["vanished"] as? Boolean ?: false,
                                    level = (update["level"] as? Number)?.toInt() ?: 0
                                )
                                handleVanishMessage(batchMessage)
                            }
                        } else {
                            plugin.logger.warn("Received batch_update with no updates array")
                        }
                    }
                    "set_vanish" -> {
                        // Handle individual vanish messages
                        val vanishMessage = gson.fromJson(message, VanishMessage::class.java)

                        handleVanishMessage(vanishMessage)
                    }
                    else -> {

                    }
                }
                
            } catch (e: Exception) {
                plugin.logger.warn("Error processing vanish plugin message: ${String(event.message)}", e)
            }
        }
        
        return EventListener.Result.SUCCESS
    }
      private fun handleVanishMessage(message: VanishMessage) {
        try {
            // Add null safety for player UUID
            val playerUuidString = message.player
            if (playerUuidString.isNullOrEmpty()) {
                plugin.logger.warn("Received vanish message with null/empty player UUID: $message")
                return
            }

            val playerUuid = UUID.fromString(playerUuidString)
            
            // Try to get player name from the message or from connected players
            val playerName = message.playerName 
                ?: MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }?.username 
                ?: "Unknown"
            
            when (message.action) {
                "set_vanish" -> {
                    if (message.vanished) {
                        // Player became vanished
                        val data = VanishData(
                            playerUuid = playerUuid,
                            playerName = playerName,
                            vanished = true,
                            level = message.level,
                            requiredWeight = message.level,
                            timestamp = System.currentTimeMillis()
                        )
                        vanishData[playerUuid] = data
                        

                        
                        // Notify packet vanish manager
                        try {
                            plugin.packetVanishManager.handlePlayerVanish(playerUuid)
                        } catch (e: Exception) {

                        }
                    } else {
                        // Player became unvanished
                        val oldData = vanishData.remove(playerUuid)
                        val finalPlayerName = oldData?.playerName ?: playerName
                        

                        
                        // Notify packet vanish manager first
                        try {
                            plugin.packetVanishManager.handlePlayerUnvanish(playerUuid)
                        } catch (e: Exception) {

                        }
                        
                        // Schedule a delayed visibility update to ensure packet manager processes first
                        val scheduler = MinecraftServer.getSchedulerManager()
                        scheduler.submitTask {
                            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
                            if (player != null) {

                                updatePlayerVisibility(player)
                            }
                            TaskSchedule.stop()
                        }
                    }
                    
                    // For vanish case, update visibility immediately
                    // For unvanish case, we already scheduled a delayed update above
                    if (message.vanished) {
                        val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
                        if (player != null) {
                            updatePlayerVisibility(player)
                        } else {

                        }
                    }
                    
                } else -> {

                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error handling vanish message: $message", e)
        }
    }
    
    private fun updatePlayerVisibility(player: Player) {
        try {
            val isVanished = isPlayerVanished(player.uuid)
            

            
            if (!isVanished) {
                // Player is unvanished - they should be visible to everyone

                
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                    if (viewer.uuid != player.uuid) {
                        // Force remove and re-add viewer to trigger fresh spawn packets
                        if (player.viewers.contains(viewer)) {
                            player.removeViewer(viewer)

                        }
                        
                        // Add viewer back
                        player.addViewer(viewer)

                        
                        // Also ensure the unvanished player can see other players
                        if (!viewer.viewers.contains(player)) {
                            viewer.addViewer(player)

                        }
                    }
                }
            } else {
                // Player is vanished - check permissions for each viewer
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                    if (viewer.uuid != player.uuid) {
                        val canSee = try {
                            val result = plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, player.uuid).join()

                            result
                        } catch (e: Exception) {
                            plugin.logger.warn("Error checking vanish permissions for ${viewer.username} -> ${player.username}", e)
                            false
                        }
                        
                        if (canSee) {
                            // Show player to viewer
                            if (!player.viewers.contains(viewer)) {
                                player.addViewer(viewer)

                            }
                        } else {
                            // Hide player from viewer
                            if (player.viewers.contains(viewer)) {
                                player.removeViewer(viewer)

                            }
                        }
                    }
                }
            }
            

            
        } catch (e: Exception) {
            plugin.logger.warn("Error updating player visibility for ${player.username}", e)
        }
    }
    
    /**
     * Check if a player is vanished
     */
    fun isPlayerVanished(playerUuid: UUID): Boolean {
        return vanishData[playerUuid]?.vanished ?: false
    }
    
    /**
     * Check if a viewer can see a vanished player
     */
    suspend fun canSeeVanished(viewer: Player, vanishedPlayerUuid: UUID): Boolean {
        return try {
            plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, vanishedPlayerUuid).join()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a viewer can see a vanished player (UUID version)
     */
    suspend fun canSeeVanished(viewerUuid: UUID, vanishedPlayerUuid: UUID): Boolean {
        return try {
            plugin.radiumIntegration.canSeeVanishedPlayer(viewerUuid, vanishedPlayerUuid).join()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get vanish data for a player
     */
    fun getVanishData(playerUuid: UUID): VanishData? {
        return vanishData[playerUuid]
    }
    
    /**
     * Handle player join for vanish system
     */
    fun handlePlayerJoin(player: Player) {
        // Check if player is vanished and update visibility accordingly
        updatePlayerVisibility(player)
    }
    
    /**
     * Start vanish enforcement task
     */
    fun startVanishEnforcementTask() {
        val scheduler = MinecraftServer.getSchedulerManager()
        
        scheduler.submitTask {
            // Periodic enforcement to ensure vanish states are maintained
            try {
                enforceVanishStates()
            } catch (e: Exception) {
                plugin.logger.warn("Error in vanish enforcement task", e)
            }
            TaskSchedule.tick(100) // Every 5 seconds
        }
        

    }
    
    private fun enforceVanishStates() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            try {
                updatePlayerVisibility(player)
            } catch (e: Exception) {
                plugin.logger.warn("Error enforcing vanish state for ${player.username}", e)
            }
        }
    }
    
    /**
     * Clear all vanish data
     */
    fun clear() {
        vanishData.clear()

    }
    
    /**
     * Data class for vanish plugin messages
     */
    private data class VanishMessage(
        val action: String?,
        val player: String?,
        val playerName: String?,
        val vanished: Boolean = false,
        val level: Int = 0
    )
}
