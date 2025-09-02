package radium.backend.vanish

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import radium.backend.player.TabListManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Network-wide vanish system that manages vanish state across all servers
 */
class NetworkVanishManager(private val radium: Radium) {
    
    companion object {
        val VANISH_CHANNEL = MinecraftChannelIdentifier.create("radium", "vanish")
    }
    
    private val vanishedPlayers = ConcurrentHashMap<UUID, VanishData>()
    private val pendingUpdates = ConcurrentHashMap<UUID, Boolean>()
    private var batcherStarted = false
    
    init {
        // Register plugin message channel
        radium.server.channelRegistrar.register(VANISH_CHANNEL)
    }
    
    /**
     * Set vanish state for a player
     */
    suspend fun setVanishState(player: Player, vanished: Boolean, level: VanishLevel? = null, vanishedBy: Player? = null, reason: String? = null): Boolean {
        val currentlyVanished = isVanished(player.uniqueId)
        
        if (vanished == currentlyVanished) {
            return false // No change needed
        }
        
        if (vanished) {
            val vanishLevel = level ?: VanishLevel.fromRankWeight(player, radium)
            val vanishData = VanishData.create(
                playerId = player.uniqueId,
                level = vanishLevel,
                vanishedBy = vanishedBy?.uniqueId,
                reason = reason
            )
            vanishedPlayers[player.uniqueId] = vanishData
            
            // Hide player from other players
            hidePlayerFromOthers(player, vanishData)

            
            // Send staff notification
            sendStaffVanishNotification(player, true)
        } else {
            vanishedPlayers.remove(player.uniqueId)
            
            // Show player to all other players
            showPlayerToOthers(player)

            
            // Send staff notification
            sendStaffVanishNotification(player, false)
        }
        
        // Update tab lists through TabListManager
        radium.tabListManager.handleVanishStateChange(player, vanished)
        
        // Schedule batch update for backend servers
        scheduleVanishUpdate(player.uniqueId, vanished)
        

        return true
    }
    
    /**
     * Set vanish state for a player (convenience method for non-async callers)
     */
    fun setVanishStateAsync(player: Player, vanished: Boolean, level: VanishLevel? = null, vanishedBy: Player? = null, reason: String? = null) {
        radium.scope.launch {
            setVanishState(player, vanished, level, vanishedBy, reason)
        }
    }
    
    /**
     * Check if a player is vanished
     */
    fun isVanished(playerId: UUID): Boolean {
        return vanishedPlayers.containsKey(playerId)
    }
    
    /**
     * Get vanish data for a player
     */
    fun getVanishData(playerId: UUID): VanishData? {
        return vanishedPlayers[playerId]
    }
    
    /**
     * Get all vanished players
     */
    fun getVanishedPlayers(): Map<UUID, VanishData> {
        return vanishedPlayers.toMap()
    }
    
    /**
     * Check if viewer can see a vanished player
     */
    suspend fun canSeeVanished(viewer: Player, vanishedPlayerId: UUID): Boolean {
        val vanishData = getVanishData(vanishedPlayerId) ?: return true
        return VanishLevel.canSeeVanished(viewer, vanishData.level, radium)
    }
    
    /**
     * Check if viewer can see a vanished player (convenience method for non-async callers)
     */
    fun canSeeVanishedAsync(viewer: Player, vanishedPlayerId: UUID, callback: (Boolean) -> Unit) {
        radium.scope.launch {
            val result = canSeeVanished(viewer, vanishedPlayerId)
            callback(result)
        }
    }
    
    /**
     * Hide player from tab list (except for staff who can see them)
     */
    private suspend fun hideFromTabList(vanishedPlayer: Player, vanishData: VanishData) {
        radium.server.allPlayers.forEach { viewer ->
            val tabList = viewer.tabList
            
            if (VanishLevel.canSeeVanished(viewer, vanishData.level, radium)) {
                // Keep the player in the tab list for staff who can see them
                // The TabListManager will handle adding the vanish indicator

            } else {
                // Remove from tab list for players who cannot see them
                tabList.removeEntry(vanishedPlayer.uniqueId)

            }
        }
        
        // Now update all display names via TabListManager to handle vanish indicators
        radium.tabListManager.handleVanishStateChange(vanishedPlayer, true)
    }
    
    /**
     * Show player in tab list for everyone (refresh tab list properly on unvanish)
     */
    private suspend fun showInTabList(player: Player) {
        // Wait for rank data to be available
        kotlinx.coroutines.delay(100)
        

        
        // Use TabListManager to properly format the display name without vanish indicator
        radium.tabListManager.handleVanishStateChange(player, false)
        

    }
      /**
     * Update tab list visibility for all players when a new player joins
     */
    suspend fun updateTabListForNewPlayer(newPlayer: Player) {
        try {
            // Wait for rank data to be available
            kotlinx.coroutines.delay(150)
            
            radium.logger.debug("Updating tab list visibility for new player ${newPlayer.username}")
            
            // Let TabListManager handle all the tab list logic
            radium.tabListManager.rebuildPlayerTabList(newPlayer)
            
            radium.logger.debug("Completed tab list visibility update for ${newPlayer.username}")
        } catch (e: Exception) {
            radium.logger.warn("Failed to update tab list for new player ${newPlayer.username}: ${e.message}")
        }
    }

    /**
     * Refresh tab list for all players (useful for manual refresh)
     */
    suspend fun refreshAllTabLists() {
        try {
            radium.tabListManager.rebuildAllTabLists()
            radium.logger.info("Refreshed tab lists for all players")
        } catch (e: Exception) {
            radium.logger.warn("Failed to refresh all tab lists: ${e.message}")
        }
    }
    
    /**
     * Schedule a vanish update for batch processing
     */
    private fun scheduleVanishUpdate(playerId: UUID, vanished: Boolean) {
        // Start batcher if not already started
        startUpdateBatcher()
        pendingUpdates[playerId] = vanished
    }
    
    /**
     * Start the batch update processor (lazy initialization)
     */
    private fun startUpdateBatcher() {
        if (!batcherStarted) {
            batcherStarted = true
            radium.scope.launch {
                while (true) {
                    delay(50) // Process batch every 50ms
                    processBatchUpdates()
                }
            }
        }
    }
    
    /**
     * Process batch vanish updates
     */
    private fun processBatchUpdates() {
        if (pendingUpdates.isEmpty()) return
        
        val batch = HashMap(pendingUpdates)
        pendingUpdates.clear()
        
        // Send batch update to all servers
        sendBatchVanishUpdate(batch)
    }
    
    /**
     * Send batch vanish updates to all backend servers
     */
    private fun sendBatchVanishUpdate(updates: Map<UUID, Boolean>) {
        val batchUpdates = updates.map { (playerId, vanished) ->
            val vanishData = getVanishData(playerId)
            val vanishLevelValue = vanishData?.level?.minWeight ?: VanishLevel.HELPER.minWeight
            
            // Try to get player name from connected players
            val playerName = radium.server.allPlayers.find { it.uniqueId == playerId }?.username ?: "Unknown"
            
            mapOf(
                "action" to "set_vanish",
                "player" to playerId.toString(),
                "playerName" to playerName,
                "vanished" to vanished,
                "level" to vanishLevelValue // Send as integer, not string
            )
        }
        
        val jsonMessage = mapOf(
            "action" to "batch_update",
            "updates" to batchUpdates
        )
        
        val jsonString = com.google.gson.Gson().toJson(jsonMessage)
        val messageBytes = jsonString.toByteArray(Charsets.UTF_8)
        
        // Send to all servers
        radium.server.allServers.forEach { server ->
            try {
                server.sendPluginMessage(VANISH_CHANNEL, messageBytes)
            } catch (e: Exception) {
                radium.logger.warn("Failed to send vanish update to server ${server.serverInfo.name}: ${e.message}")
            }
        }
    }
    
    /**
     * Handle player server connections
     */
    @Subscribe
    fun onServerSwitch(event: ServerConnectedEvent) {
        val player = event.player
        val playerId = player.uniqueId
        
        // If player is vanished, notify the destination server
        val vanishData = getVanishData(playerId)
        if (vanishData != null) {
            // Create JSON message for consistency with batch updates
            val jsonMessage = mapOf(
                "action" to "set_vanish",
                "player" to playerId.toString(),
                "playerName" to player.username,
                "vanished" to true,
                "level" to vanishData.level.minWeight
            )
            
            val jsonString = com.google.gson.Gson().toJson(jsonMessage)
            val messageBytes = jsonString.toByteArray(Charsets.UTF_8)
            
            try {
                event.server.sendPluginMessage(VANISH_CHANNEL, messageBytes)

            } catch (e: Exception) {
                radium.logger.warn("Failed to notify server about vanish state: ${e.message}")
            }
        }
    }
    
    /**
     * Handle initial server connections
     */
    @Subscribe
    fun onInitialServerConnect(event: PlayerChooseInitialServerEvent) {
        // Update tab list for the new player after a short delay
        radium.scope.launch {
            delay(200) // Wait for player to fully connect and rank data to be available
            val player = event.player
            updateTabListForNewPlayer(player)
        }
    }
    
    /**
     * Get vanish statistics for admin commands
     */
    fun getVanishStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        stats["total_vanished"] = vanishedPlayers.size
        stats["by_level"] = VanishLevel.values().associate { level ->
            level.displayName to vanishedPlayers.values.count { it.level == level }
        }
        return stats
    }
    
    /**
     * Initialize the vanish manager (call after plugin is fully loaded)
     */
    fun initialize() {
        startUpdateBatcher()

    }
    
    /**
     * Convert legacy color codes to proper Adventure Component
     */
    private fun parseColoredText(text: String): Component {
        return if (text.isEmpty()) {
            Component.empty()
        } else {
            // Convert & codes to § codes, then parse with LegacyComponentSerializer
            val legacyText = text.replace('&', '§')
            LegacyComponentSerializer.legacySection().deserialize(legacyText)
        }
    }
    /**
     * Hide player from other players in the game world
     */
    private suspend fun hidePlayerFromOthers(vanishedPlayer: Player, vanishData: VanishData) {

        
        // Send comprehensive vanish update to ALL servers
        sendVanishUpdateToAllServers(vanishedPlayer, true, vanishData.level)
        
        radium.server.allPlayers.forEach { viewer ->
            if (viewer.uniqueId == vanishedPlayer.uniqueId) return@forEach
            
            try {
                val canSee = radium.staffManager.canSeeVanishedPlayerSync(viewer, vanishedPlayer)
                if (!canSee) {

                }
            } catch (e: Exception) {
                radium.logger.warn("Failed to process visibility for ${vanishedPlayer.username} and ${viewer.username}: ${e.message}")
            }
        }
        

    }

    /**
     * Show player to all other players in the game world
     */
    private suspend fun showPlayerToOthers(player: Player) {
        radium.logger.info("Showing ${player.username} to all other players in the game world")
        
        // Send comprehensive unvanish update to ALL servers
        sendVanishUpdateToAllServers(player, false, VanishLevel.HELPER)
        
        radium.server.allPlayers.forEach { viewer ->
            if (viewer.uniqueId == player.uniqueId) return@forEach
            
            try {
                radium.logger.debug("Showing ${player.username} to ${viewer.username}")
            } catch (e: Exception) {
                radium.logger.warn("Failed to show ${player.username} to ${viewer.username}: ${e.message}")
            }
        }
    }
    
    /**
     * Send vanish update to a specific server
     */
    private fun sendVanishUpdate(player: Player, vanished: Boolean, server: com.velocitypowered.api.proxy.server.RegisteredServer?) {
        if (server == null) return
        
        val vanishData = getVanishData(player.uniqueId)
        val vanishLevelValue = vanishData?.level?.minWeight ?: VanishLevel.HELPER.minWeight
        
        val message = mapOf(
            "action" to "set_vanish",
            "player" to player.uniqueId.toString(),
            "playerName" to player.username,
            "vanished" to vanished,
            "level" to vanishLevelValue  // Send as integer, not string
        )
        
        val jsonString = com.google.gson.Gson().toJson(message)
        val messageBytes = jsonString.toByteArray(Charsets.UTF_8)
        
        try {
            server.sendPluginMessage(VANISH_CHANNEL, messageBytes)

        } catch (e: Exception) {
            radium.logger.warn("Failed to send vanish update to server ${server.serverInfo.name}: ${e.message}")
        }
    }
    
    /**
     * Send vanish update to ALL servers to ensure comprehensive entity visibility updates
     */
    private fun sendVanishUpdateToAllServers(player: Player, vanished: Boolean, level: VanishLevel) {
        val message = mapOf(
            "action" to "set_vanish",
            "player" to player.uniqueId.toString(),
            "playerName" to player.username,
            "vanished" to vanished,
            "level" to level.minWeight
        )
        
        val jsonString = com.google.gson.Gson().toJson(message)
        val messageBytes = jsonString.toByteArray(Charsets.UTF_8)
        

        
        // Send to ALL servers
        radium.server.allServers.forEach { server ->
            try {
                server.sendPluginMessage(VANISH_CHANNEL, messageBytes)

            } catch (e: Exception) {
                radium.logger.warn("Failed to send comprehensive vanish update to server ${server.serverInfo.name}: ${e.message}")
            }
        }
        

    }

    /**
     * Send vanish notification to all staff members
     */
    private suspend fun sendStaffVanishNotification(player: Player, isVanishing: Boolean) {
        try {
            // Get player's profile and rank information
            val profile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
            val server = player.currentServer.orElse(null)?.server?.serverInfo?.name ?: "Unknown"
            
            // Get the vanishing player's rank weight to determine who can see the notification
            val vanishingPlayerWeight = if (profile != null) {
                val highestRank = profile.getHighestRank(radium.rankManager)
                highestRank?.weight ?: 0
            } else {
                0
            }
            
            // Build the rank prefix with proper formatting
            val rankPrefix = if (profile != null) {
                val highestRank = profile.getHighestRank(radium.rankManager)
                if (highestRank != null) {
                    val cleanPrefix = TabListManager.cleanColorCodes(highestRank.prefix?.trim() ?: "")
                    val cleanColor = TabListManager.cleanColorCodes(highestRank.color?.trim() ?: "&f")
                    "$cleanPrefix$cleanColor"
                } else {
                    "&f"
                }
            } else {
                "&f"
            }
            
            val action = if (isVanishing) "vanished" else "unvanished"
            
            // Get the staff notification message from lang.yml
            val messageTemplate = radium.yamlFactory.getMessage("vanish.staff_notification")
            val finalTemplate = if (messageTemplate == "vanish.staff_notification" || messageTemplate.isBlank()) {
                "&8[STAFF] {prefix}{player}&8 has &7{action} &8on &e{server}"
            } else {
                messageTemplate
            }
            
            val message = finalTemplate
                .replace("{prefix}", rankPrefix)
                .replace("{player}", player.username)
                .replace("{action}", action)
                .replace("{server}", server)
            
            // Parse the message with color codes
            val component = TabListManager.safeParseColoredText(message)
            
            // Send to staff members who have equal or higher rank weight than the vanishing player
            radium.server.allPlayers.forEach { staffPlayer ->
                if (staffPlayer.uniqueId != player.uniqueId && radium.staffManager.isStaff(staffPlayer)) {
                    // Check if this staff member should see the notification based on rank weight
                    val staffProfile = radium.connectionHandler.findPlayerProfile(staffPlayer.uniqueId.toString())
                    val staffWeight = if (staffProfile != null) {
                        val staffHighestRank = staffProfile.getHighestRank(radium.rankManager)
                        staffHighestRank?.weight ?: 0
                    } else {
                        0
                    }
                    
                    // Only send notification if staff has equal or higher rank weight
                    if (staffWeight >= vanishingPlayerWeight) {
                        staffPlayer.sendMessage(component)

                    }
                }
            }
            

        } catch (e: Exception) {
            radium.logger.warn("Failed to send staff vanish notification for ${player.username}: ${e.message}")
        }
    }
}
