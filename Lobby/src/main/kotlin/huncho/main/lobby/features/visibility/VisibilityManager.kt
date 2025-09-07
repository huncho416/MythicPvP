package huncho.main.lobby.features.visibility

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.MinecraftServer
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerSpawnEvent
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

enum class VisibilityMode {
    ALL,     // Show all players
    STAFF,   // Show only staff
    NONE     // Hide all players
}

class VisibilityManager(private val plugin: LobbyPlugin) {
    
    private val playerVisibility = ConcurrentHashMap<String, VisibilityMode>()
    
    /**
     * Register event handlers for vanish visibility
     */
    fun registerEvents(eventHandler: GlobalEventHandler) {
        // DISABLED: This conflicts with VanishPluginMessageListener's more precise handling
        // eventHandler.addListener(PlayerSpawnEvent::class.java) { event ->
        //     // Update visibility for the spawning player
        //     CompletableFuture.runAsync {
        //         runBlocking {
        //             updatePlayerVisibilityForVanish(event.player)
        //             // Also update other players' visibility to this player
        //             updateVisibilityForAll()
        //         }
        //     }
        // }
    }
    
    /**
     * Set visibility mode for a player
     */
    fun setVisibility(player: Player, mode: VisibilityMode) {
        val uuid = player.uuid.toString()
        playerVisibility[uuid] = mode
        
        // Save to database
        runBlocking {
            updatePlayerSetting(player, "visibility", mode.name.lowercase())
        }
        
        // Apply visibility changes
        updatePlayerVisibility(player)
        
        // Send message
        val message = when (mode) {
            VisibilityMode.ALL -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-all")
            VisibilityMode.STAFF -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-staff")
            VisibilityMode.NONE -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-none")
        }
        MessageUtils.sendMessage(player, message)
    }
    
    /**
     * Toggle visibility mode for a player
     */
    fun toggleVisibility(player: Player) {
        val currentMode = getVisibility(player)
        val newMode = when (currentMode) {
            VisibilityMode.ALL -> VisibilityMode.STAFF
            VisibilityMode.STAFF -> VisibilityMode.NONE
            VisibilityMode.NONE -> VisibilityMode.ALL
        }
        setVisibility(player, newMode)
    }
    
    /**
     * Get visibility mode for a player
     */
    fun getVisibility(player: Player): VisibilityMode {
        val uuid = player.uuid.toString()
        return playerVisibility.getOrDefault(uuid, VisibilityMode.ALL)
    }
    
    /**
     * Load visibility setting from database
     */
    suspend fun loadVisibility(player: Player) {
        val uuid = player.uuid.toString()
        val setting = getPlayerSetting(player, "visibility")
        val mode = when (setting.lowercase()) {
            "staff" -> VisibilityMode.STAFF
            "none" -> VisibilityMode.NONE
            else -> VisibilityMode.ALL
        }
        playerVisibility[uuid] = mode
        updatePlayerVisibility(player)
    }
    
    /**
     * Update visibility for a specific player
     */
    private fun updatePlayerVisibility(viewer: Player) {
        val mode = getVisibility(viewer)
        
        plugin.lobbyInstance.players.forEach { target ->
            if (target == viewer) return@forEach
            
            // ENHANCED: Check if target is vanished first
            val isTargetVanished = try {
                plugin.radiumIntegration.isPlayerVanished(target.uuid).join()
            } catch (e: Exception) {
                plugin.logger.warn("Error checking vanish status for ${target.username}", e)
                false
            }
            
            val shouldShow = if (isTargetVanished) {
                // Target is vanished - only show if viewer can see vanished players
                try {
                    plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, target.uuid).join()
                } catch (e: Exception) {
                    plugin.logger.warn("Error checking vanish permissions for ${viewer.username} -> ${target.username}", e)
                    false
                }
            } else {
                // Target is not vanished - apply normal visibility settings
                when (mode) {
                    VisibilityMode.ALL -> true
                    VisibilityMode.STAFF -> isStaff(target)
                    VisibilityMode.NONE -> false
                }
            }
            
            if (shouldShow && !canSeePlayer(viewer, target)) {
                showPlayer(viewer, target)
                plugin.logger.debug("✅ Showing ${target.username} to ${viewer.username} (mode: $mode, vanished: $isTargetVanished)")
            } else if (!shouldShow && canSeePlayer(viewer, target)) {
                hidePlayer(viewer, target)
                plugin.logger.debug("🚫 Hiding ${target.username} from ${viewer.username} (mode: $mode, vanished: $isTargetVanished)")
            }
        }
    }
    
    /**
     * Check if target is staff using Radium API
     */
    private fun isStaff(player: Player): Boolean {
        return try {
            plugin.radiumIntegration.hasPermission(player.uuid, "radium.staff").get()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update visibility for a new player considering vanish status
     */
    fun updateVisibilityForNewPlayer(newPlayer: Player) {
        runBlocking {
            // Check if the new player is vanished
            val isVanished = plugin.radiumIntegration.isPlayerVanished(newPlayer.uuid).join()
            
            // Update visibility for all existing players
            plugin.lobbyInstance.players.forEach { existingPlayer ->
                if (existingPlayer != newPlayer) {
                    // Check if existing player can see the new player
                    val existingMode = getVisibility(existingPlayer)
                    val shouldShow = when (existingMode) {
                        VisibilityMode.ALL -> {
                            if (isVanished) {
                                // Check if existing player can see vanished players using weight-based system
                                plugin.radiumIntegration.canSeeVanishedPlayer(existingPlayer.uuid, newPlayer.uuid).join()
                            } else {
                                true
                            }
                        }
                        VisibilityMode.STAFF -> {
                            val newPlayerIsStaff = isStaff(newPlayer)
                            if (isVanished) {
                                newPlayerIsStaff && plugin.radiumIntegration.canSeeVanishedPlayer(existingPlayer.uuid, newPlayer.uuid).join()
                            } else {
                                newPlayerIsStaff
                            }
                        }
                        VisibilityMode.NONE -> false
                    }
                    
                    // Apply visibility using Minestom's visibility API
                    if (shouldShow) {
                        showPlayer(existingPlayer, newPlayer)
                    } else {
                        hidePlayer(existingPlayer, newPlayer)
                    }
                    
                    // Also check if new player can see existing player
                    val newPlayerMode = getVisibility(newPlayer)
                    val existingIsVanished = plugin.radiumIntegration.isPlayerVanished(existingPlayer.uuid).join()
                    val newPlayerCanSeeExisting = when (newPlayerMode) {
                        VisibilityMode.ALL -> {
                            if (existingIsVanished) {
                                plugin.radiumIntegration.canSeeVanishedPlayer(newPlayer.uuid, existingPlayer.uuid).join()
                            } else {
                                true
                            }
                        }
                        VisibilityMode.STAFF -> {
                            val existingIsStaff = isStaff(existingPlayer)
                            if (existingIsVanished) {
                                existingIsStaff && plugin.radiumIntegration.canSeeVanishedPlayer(newPlayer.uuid, existingPlayer.uuid).join()
                            } else {
                                existingIsStaff
                            }
                        }
                        VisibilityMode.NONE -> false
                    }
                    
                    if (newPlayerCanSeeExisting) {
                        showPlayer(newPlayer, existingPlayer)
                    } else {
                        hidePlayer(newPlayer, existingPlayer)
                    }
                }
            }
        }
    }
    
    /**
     * Update player visibility based on vanish status - CRITICAL for in-game vanish
     * ENHANCED: Now ensures proper entity hiding/showing for all viewers
     */
    suspend fun updatePlayerVisibilityForVanish(player: Player) {
        try {
            val isVanished = plugin.radiumIntegration.isPlayerVanished(player.uuid).join()
            
            // Get all players in the same instance
            val allPlayers = player.instance?.players ?: emptySet()
            
            plugin.logger.debug("Updating vanish visibility for ${player.username} (vanished: $isVanished) to ${allPlayers.size} viewers")
            
            for (viewer in allPlayers) {
                if (viewer.uuid != player.uuid) {
                    if (isVanished) {
                        // Check if this viewer can see the vanished player using rank weights
                        val canSee = canPlayerSeeVanished(viewer, player.uuid)
                        plugin.logger.debug("${viewer.username} ${if (canSee) "CAN" else "CANNOT"} see vanished ${player.username}")
                        
                        if (canSee) {
                            // Show vanished player to viewers who can see them (staff with sufficient rank)
                            showPlayerToViewer(viewer, player)
                        } else {
                            // CRITICAL: Hide vanished player from viewers who can't see them (defaults/lower ranks)
                            hidePlayerFromViewer(viewer, player)
                        }
                    } else {
                        // CRITICAL: Player is not vanished - ensure they're visible to everyone
                        showPlayerToViewer(viewer, player)
                    }
                }
            }
            
            // Force tab list refresh for this player
            plugin.tabListManager.updatePlayerTabList(player)
            
            plugin.logger.debug("Completed vanish visibility update for ${player.username}")
        } catch (e: Exception) {
            plugin.logger.error("Error updating vanish visibility for ${player.username}", e)
        }
    }
    
    /**
     * Update visibility bidirectionally between two players
     */
    private suspend fun updatePlayerVisibilityBidirectional(player1: Player, player2: Player) {
        // This is now handled in VanishPluginMessageListener to avoid duplication
        // Keeping this method for any special cases that might need it
    }
    
    /**
     * Update visibility for all online players - called when vanish status changes
     */
    private suspend fun updateVisibilityForAll() {
        try {
            val allPlayers = MinecraftServer.getConnectionManager().onlinePlayers
            
            for (player in allPlayers) {
                updatePlayerVisibilityForVanish(player)
            }
            
            plugin.logger.debug("Updated visibility for all ${allPlayers.size} online players")
        } catch (e: Exception) {
            plugin.logger.error("Error updating visibility for all players", e)
        }
    }
    
    /**
     * Hide a player from a specific viewer using Minestom's entity visibility
     * CRITICAL FIX: Correct viewer/target relationship for entity hiding
     */
    private fun hidePlayerFromViewer(viewer: Player, target: Player) {
        try {
            // FIXED: Remove viewer from target's viewer list (so viewer can't see target)
            if (target.viewers.contains(viewer)) {
                target.removeViewer(viewer)
                plugin.logger.debug("🚫 Hidden ${target.username} from ${viewer.username} (vanish)")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to hide ${target.username} from ${viewer.username}", e)
        }
    }
    
    /**
     * Show a player to a specific viewer using Minestom's entity visibility
     * CRITICAL FIX: Correct viewer/target relationship for entity showing
     */
    private fun showPlayerToViewer(viewer: Player, target: Player) {
        try {
            // FIXED: Add viewer to target's viewer list (so viewer can see target)
            if (!target.viewers.contains(viewer)) {
                target.addViewer(viewer)
                plugin.logger.debug("✅ Shown ${target.username} to ${viewer.username} (visible)")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to show ${target.username} to ${viewer.username}", e)
        }
    }
    
    /**
     * Update visibility for all players when a player's vanish status changes
     */
    suspend fun handleVanishStatusChange(playerUuid: UUID, isVanished: Boolean) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player != null) {
                updatePlayerVisibilityForVanish(player)
                plugin.logger.info("Handled vanish status change for ${player.username}: ${if (isVanished) "vanished" else "visible"}")
            }
        } catch (e: Exception) {
            plugin.logger.error("Error handling vanish status change for $playerUuid", e)
        }
    }

    /**
     * SIMPLIFIED: Weight-based vanish visibility checking
     * Direct integration with Radium's rank weight system - no enum conversion needed
     */
    suspend fun canPlayerSeeVanished(viewer: Player, vanishedPlayerUuid: UUID): Boolean {
        try {
            // Use Radium integration to check if viewer can see vanished player
            return plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, vanishedPlayerUuid).join()
        } catch (e: Exception) {
            plugin.logger.warn("Error checking vanish visibility for ${viewer.username}: ${e.message}")
            return false // Default to not showing vanished players on error
        }
    }
    
    /**
     * ENHANCED: Comprehensive player join visibility update
     * Ensures new players see correct vanish states and are seen correctly
     */
    suspend fun handlePlayerJoinVisibility(newPlayer: Player) {
        try {
            plugin.logger.debug("Handling join visibility for ${newPlayer.username}")
            
            // Check if the new player is vanished
            val newPlayerVanished = plugin.radiumIntegration.isPlayerVanished(newPlayer.uuid).join()
            
            // Update visibility for all existing players
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { existingPlayer ->
                if (existingPlayer.uuid != newPlayer.uuid) {
                    
                    // 1. Check if existing player can see the new player
                    if (newPlayerVanished) {
                        val canSeeNewPlayer = canPlayerSeeVanished(existingPlayer, newPlayer.uuid)
                        if (canSeeNewPlayer) {
                            showPlayerToViewer(existingPlayer, newPlayer)
                            plugin.tabListManager.showPlayerWithVanishIndicator(newPlayer, existingPlayer)
                        } else {
                            hidePlayerFromViewer(existingPlayer, newPlayer)
                            plugin.tabListManager.hidePlayerFromTab(newPlayer, existingPlayer)
                        }
                    } else {
                        showPlayerToViewer(existingPlayer, newPlayer)
                        plugin.tabListManager.showPlayerInTab(newPlayer, existingPlayer)
                    }
                    
                    // 2. Check if new player can see the existing player
                    val existingPlayerVanished = plugin.radiumIntegration.isPlayerVanished(existingPlayer.uuid).join()
                    if (existingPlayerVanished) {
                        val canSeeExistingPlayer = canPlayerSeeVanished(newPlayer, existingPlayer.uuid)
                        if (canSeeExistingPlayer) {
                            showPlayerToViewer(newPlayer, existingPlayer)
                            plugin.tabListManager.showPlayerWithVanishIndicator(existingPlayer, newPlayer)
                        } else {
                            hidePlayerFromViewer(newPlayer, existingPlayer)
                            plugin.tabListManager.hidePlayerFromTab(existingPlayer, newPlayer)
                        }
                    } else {
                        showPlayerToViewer(newPlayer, existingPlayer)
                        plugin.tabListManager.showPlayerInTab(existingPlayer, newPlayer)
                    }
                }
            }
            
            // CRITICAL: Update tab lists after visibility changes
            plugin.tabListManager.refreshAllTabLists()
            
            plugin.logger.debug("✅ Completed join visibility update for ${newPlayer.username}")
            
        } catch (e: Exception) {
            plugin.logger.error("Error handling player join visibility for ${newPlayer.username}", e)
        }
    }
    
    /**
     * Check if viewer can see target
     */
    private fun canSeePlayer(viewer: Player, target: Player): Boolean {
        // Check if the viewer is in the target's viewer list (so viewer can see target)
        return target.viewers.contains(viewer)
    }
    
    /**
     * Show target player to viewer
     */
    private fun showPlayer(viewer: Player, target: Player) {
        // Add the target player to viewer's visible entities
        target.addViewer(viewer)
    }
    
    /**
     * Hide target player from viewer
     */
    private fun hidePlayer(viewer: Player, target: Player) {
        // Remove the target player from viewer's visible entities
        target.removeViewer(viewer)
    }
    
    /**
     * Remove player from tracking
     */
    fun removePlayer(uuid: String) {
        playerVisibility.remove(uuid)
    }
    
    /**
     * Get visibility stats
     */
    fun getVisibilityStats(): Map<VisibilityMode, Int> {
        val stats = mutableMapOf<VisibilityMode, Int>()
        VisibilityMode.values().forEach { mode ->
            stats[mode] = playerVisibility.values.count { it == mode }
        }
        return stats
    }
    
    /**
     * Reset all players to default visibility
     */
    fun resetAllVisibility() {
        playerVisibility.clear()
        plugin.lobbyInstance.players.forEach { player ->
            updatePlayerVisibility(player)
        }
    }
    
    /**
     * Refresh visibility for all online players (useful after vanish state changes)
     */
    fun refreshVisibilityForAllPlayers() {
        try {
            plugin.lobbyInstance.players.forEach { player ->
                updatePlayerVisibility(player)
            }
            plugin.logger.debug("Refreshed visibility for all ${plugin.lobbyInstance.players.size} online players")
        } catch (e: Exception) {
            plugin.logger.error("Error refreshing visibility for all players", e)
        }
    }

    // Stub methods for missing RadiumIntegration calls
    private fun updatePlayerSetting(player: Player, setting: String, value: String) {
        // TODO: Implement with RadiumIntegration HTTP API
    }
    
    private fun getPlayerSetting(player: Player, setting: String): String {
        // TODO: Implement with RadiumIntegration HTTP API
        return "ALL"
    }
}
