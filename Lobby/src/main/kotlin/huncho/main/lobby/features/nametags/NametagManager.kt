package huncho.main.lobby.features.nametags

import huncho.main.lobby.LobbyPlugin
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.scoreboard.Team
import net.minestom.server.scoreboard.TeamBuilder
import net.minestom.server.scoreboard.TeamManager
import net.minestom.server.timer.TaskSchedule
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// MSNameTags imports
import com.github.echolightmc.msnametags.NameTag
import com.github.echolightmc.msnametags.NameTagManager as MSNameTagManager

/**
 * Manager for handling player nametags using MSNameTags
 */
class NametagManager(private val plugin: LobbyPlugin) {

    private val miniMessage = MiniMessage.miniMessage()
    
    // MSNameTags components
    private lateinit var nameTagManager: MSNameTagManager
    private lateinit var nameTagTeam: Team
    
    // Cache for nametag information
    private val nametagCache = ConcurrentHashMap<UUID, NametagInfo>()
    private val customNametags = ConcurrentHashMap<UUID, Boolean>()
    private val playerNameTags = ConcurrentHashMap<UUID, NameTag>()
    
    /**
     * Data class for nametag information
     */
    data class NametagInfo(
        val prefix: String,
        val suffix: String,
        val displayName: Component,
        val priority: Int = 0
    )

    /**
     * Initialize the nametag manager
     */
    fun initialize() {
        plugin.logger.info("Initializing NametagManager with direct Minestom team approach...")
        
        try {
            // Setup global team to hide default nametags
            val teamManager = MinecraftServer.getTeamManager()
            nameTagTeam = TeamBuilder("nametag-hider", teamManager)
                .collisionRule(net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule.NEVER)
                .nameTagVisibility(net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility.NEVER)
                .build()
            
            // We'll create individual teams for each player with custom prefixes
            // This bypasses MSNameTags entirely and uses Minestom's built-in team system
            
            // Register event listeners
            val eventHandler = MinecraftServer.getGlobalEventHandler()
            setupEventListeners(eventHandler)
            
            // Start periodic update task
            startUpdateTask()
            
            // NametagManager initialized
        } catch (e: Exception) {
            plugin.logger.error("Failed to initialize NametagManager", e)
        }
    }

    /**
     * Setup event listeners for player join/leave
     */
    private fun setupEventListeners(eventHandler: net.minestom.server.event.GlobalEventHandler) {
        // Player spawn event - create nametag
        eventHandler.addListener(EventListener.of(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            
            // Immediately add player to the team that hides default nametags
            nameTagTeam.addMember(player.username)
            
            // CRITICAL FIX: Do NOT modify any player visibility properties here
            // This was causing players to reappear when "Hide All Players" was selected
            // Let VisibilityManager handle ALL player visibility
            
            GlobalScope.launch {
                delay(100) // Small delay to ensure player is fully loaded
                // CRITICAL FIX: Only create nametag, don't interfere with visibility settings
                createPlayerNametagOnly(player)
            }
        })
        
        // Player disconnect event - cleanup nametag
        eventHandler.addListener(EventListener.of(PlayerDisconnectEvent::class.java) { event ->
            val player = event.player
            
            // Remove player from the nametag team
            nameTagTeam.removeMember(player.username)
            
            // CRITICAL FIX: Do NOT modify player visibility here - let VisibilityManager handle it
            // This was causing conflicts with hide/show staff settings
            // player.customName = null
            // player.isCustomNameVisible = true
            
            removePlayerNametag(player)
        })
    }

    /**
     * Create nametag for a player using Minestom's team system directly (bypassing MSNameTags)
     * ENHANCED: Does not interfere with visibility settings
     */
    private suspend fun createPlayerNametagOnly(player: Player) {
        try {
            // Get player's rank data from Radium
            val playerData = plugin.radiumIntegration.getPlayerData(player.uuid).await()
            
            val nametagData = if (playerData != null) {
                val rank = playerData.rank
                val nametagFormat = rank?.nametag // Get the custom nametag format from rank
                
                // Debug logging

                
                Pair(Pair(rank?.prefix ?: "", rank?.weight ?: 0), nametagFormat)
            } else {

                Pair(Pair("", 0), null)
            }
            
            val (prefix, priority) = nametagData.first
            val nametagFormat = nametagData.second
            
            // For default players (no rank/nametag), use grey username
            val nametagText = if (nametagFormat != null && nametagFormat.isNotEmpty()) {
                // Use the custom nametag format, but DON'T include {name} in team prefix

                
                // The nametag format should be set as team prefix WITHOUT the player name
                // The player name will be automatically appended by Minecraft
                val cleanFormat = nametagFormat.replace("{name}", "").trim()
                
                // Limit nametag length to prevent protocol errors (max 64 characters for team prefix)
                val truncatedText = if (cleanFormat.length > 60) {
                    plugin.logger.warn("Nametag too long for ${player.username}, truncating: $cleanFormat")
                    cleanFormat.take(60) + "..."
                } else {
                    cleanFormat
                }
                
                // Convert legacy formatting to MiniMessage format
                val miniMessageText = convertLegacyToMiniMessage(truncatedText)
                plugin.logger.info("DEBUG: Converted to MiniMessage: '$miniMessageText'")
                
                try {
                    miniMessage.deserialize(miniMessageText)
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to parse nametag format for ${player.username}: ${e.message}")
                    Component.text("").color(NamedTextColor.GRAY) // Default grey for no rank
                }
            } else if (prefix.isNotEmpty()) {
                // Fallback to prefix (without name - name will be appended automatically)
                plugin.logger.info("DEBUG: Using fallback prefix: '$prefix'")
                val truncatedText = if (prefix.length > 60) {
                    prefix.take(60)
                } else {
                    prefix
                }
                val miniMessageText = convertLegacyToMiniMessage(truncatedText)
                
                try {
                    miniMessage.deserialize(miniMessageText)
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to parse prefix format for ${player.username}: ${e.message}")
                    Component.text("").color(NamedTextColor.GRAY)
                }
            } else {
                // Default players - use grey color prefix (empty prefix, grey name color)
                plugin.logger.info("DEBUG: Using default grey color for ${player.username}")
                Component.text("").color(NamedTextColor.GRAY)
            }
            
            // Create nametag info
            val displayName = Component.text(player.username).color(NamedTextColor.WHITE)
            val nametagInfo = NametagInfo(prefix, "", displayName, priority)
            nametagCache[player.uuid] = nametagInfo
            
            // DIRECT APPROACH: Use Minestom's team system to set a custom prefix
            val teamManager = MinecraftServer.getTeamManager()
            val teamName = "player-${player.username}"
            var playerTeam = teamManager.getTeam(teamName)
            
            if (playerTeam == null) {
                // Create a unique team for this player with rate limiting
                try {
                    val teamBuilder = TeamBuilder(teamName, teamManager)
                        .collisionRule(net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule.NEVER)
                        .nameTagVisibility(net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility.ALWAYS)
                        .prefix(nametagText)
                        .suffix(Component.empty())
                    
                    // Set name color for default players (grey)
                    if (nametagFormat == null || nametagFormat.isEmpty()) {
                        teamBuilder.teamColor(NamedTextColor.GRAY)
                    } else {
                        teamBuilder.teamColor(NamedTextColor.WHITE) // Let the prefix handle the color
                    }
                    
                    playerTeam = teamBuilder.build()
                    plugin.logger.debug("Created new team for player ${player.username}")
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to create team for ${player.username}: ${e.message}")
                    return
                }
            } else {
                // Only update if the prefix has actually changed
                if (playerTeam.prefix != nametagText) {
                    playerTeam.prefix = nametagText
                    playerTeam.suffix = Component.empty()
                    
                    // Update team color for default players
                    if (nametagFormat == null || nametagFormat.isEmpty()) {
                        playerTeam.teamColor = NamedTextColor.GRAY
                    } else {
                        playerTeam.teamColor = NamedTextColor.WHITE
                    }
                    
                    plugin.logger.debug("Updated team prefix for player ${player.username}")
                } else {
                    plugin.logger.debug("Skipping team update for ${player.username} - no changes")
                    return
                }
            }
            
            // Add player to their personal team (only if not already a member)
            if (playerTeam != null && !playerTeam.members.contains(player.username)) {
                playerTeam.addMember(player.username)
            }
            
            // CRITICAL FIX: Do NOT modify player visibility here - let VisibilityManager handle it
            // This was causing players to reappear after hide/show staff toggles
            // player.isCustomNameVisible = false
            // player.customName = null
            // player.displayName = null
            
            plugin.logger.debug("Created nametag-only for player ${player.username}: format='${nametagFormat ?: "default"}', text='${nametagText}'")
        } catch (e: Exception) {
            plugin.logger.error("Failed to create nametag for player ${player.username}", e)
        }
    }

    /**
     * Create nametag for a player using Minestom's team system directly (bypassing MSNameTags)
     * @deprecated Use createPlayerNametagOnly instead to avoid visibility conflicts
     */
    private suspend fun createPlayerNametag(player: Player) {
        try {
            // Get player's rank data from Radium
            val playerData = plugin.radiumIntegration.getPlayerData(player.uuid).await()
            
            val nametagData = if (playerData != null) {
                val rank = playerData.rank
                val nametagFormat = rank?.nametag // Get the custom nametag format from rank
                
                // Debug logging

                
                Pair(Pair(rank?.prefix ?: "", rank?.weight ?: 0), nametagFormat)
            } else {

                Pair(Pair("", 0), null)
            }
            
            val (prefix, priority) = nametagData.first
            val nametagFormat = nametagData.second
            
            // For default players (no rank/nametag), use grey username
            val nametagText = if (nametagFormat != null && nametagFormat.isNotEmpty()) {
                // Use the custom nametag format, but DON'T include {name} in team prefix

                
                // The nametag format should be set as team prefix WITHOUT the player name
                // The player name will be automatically appended by Minecraft
                val cleanFormat = nametagFormat.replace("{name}", "").trim()
                
                // Limit nametag length to prevent protocol errors (max 64 characters for team prefix)
                val truncatedText = if (cleanFormat.length > 60) {
                    plugin.logger.warn("Nametag too long for ${player.username}, truncating: $cleanFormat")
                    cleanFormat.take(60) + "..."
                } else {
                    cleanFormat
                }
                
                // Convert legacy formatting to MiniMessage format
                val miniMessageText = convertLegacyToMiniMessage(truncatedText)
                plugin.logger.info("DEBUG: Converted to MiniMessage: '$miniMessageText'")
                
                try {
                    miniMessage.deserialize(miniMessageText)
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to parse nametag format for ${player.username}: ${e.message}")
                    Component.text("").color(NamedTextColor.GRAY) // Default grey for no rank
                }
            } else if (prefix.isNotEmpty()) {
                // Fallback to prefix (without name - name will be appended automatically)
                plugin.logger.info("DEBUG: Using fallback prefix: '$prefix'")
                val truncatedText = if (prefix.length > 60) {
                    prefix.take(60)
                } else {
                    prefix
                }
                val miniMessageText = convertLegacyToMiniMessage(truncatedText)
                
                try {
                    miniMessage.deserialize(miniMessageText)
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to parse prefix format for ${player.username}: ${e.message}")
                    Component.text("").color(NamedTextColor.GRAY)
                }
            } else {
                // Default players - use grey color prefix (empty prefix, grey name color)
                plugin.logger.info("DEBUG: Using default grey color for ${player.username}")
                Component.text("").color(NamedTextColor.GRAY)
            }
            
            // Create nametag info
            val displayName = Component.text(player.username).color(NamedTextColor.WHITE)
            val nametagInfo = NametagInfo(prefix, "", displayName, priority)
            nametagCache[player.uuid] = nametagInfo
            
            // DIRECT APPROACH: Use Minestom's team system to set a custom prefix
            val teamManager = MinecraftServer.getTeamManager()
            val teamName = "player-${player.username}"
            var playerTeam = teamManager.getTeam(teamName)
            
            if (playerTeam == null) {
                // Create a unique team for this player with rate limiting
                try {
                    val teamBuilder = TeamBuilder(teamName, teamManager)
                        .collisionRule(net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule.NEVER)
                        .nameTagVisibility(net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility.ALWAYS)
                        .prefix(nametagText)
                        .suffix(Component.empty())
                    
                    // Set name color for default players (grey)
                    if (nametagFormat == null || nametagFormat.isEmpty()) {
                        teamBuilder.teamColor(NamedTextColor.GRAY)
                    } else {
                        teamBuilder.teamColor(NamedTextColor.WHITE) // Let the prefix handle the color
                    }
                    
                    playerTeam = teamBuilder.build()
                    plugin.logger.debug("Created new team for player ${player.username}")
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to create team for ${player.username}: ${e.message}")
                    return
                }
            } else {
                // Only update if the prefix has actually changed
                if (playerTeam.prefix != nametagText) {
                    playerTeam.prefix = nametagText
                    playerTeam.suffix = Component.empty()
                    
                    // Update team color for default players
                    if (nametagFormat == null || nametagFormat.isEmpty()) {
                        playerTeam.teamColor = NamedTextColor.GRAY
                    } else {
                        playerTeam.teamColor = NamedTextColor.WHITE
                    }
                    
                    plugin.logger.debug("Updated team prefix for player ${player.username}")
                } else {
                    plugin.logger.debug("Skipping team update for ${player.username} - no changes")
                    return
                }
            }
            
            // Add player to their personal team (only if not already a member)
            if (playerTeam != null && !playerTeam.members.contains(player.username)) {
                playerTeam.addMember(player.username)
            }
            
            // Hide the original nametag completely
            player.isCustomNameVisible = false
            player.customName = null
            player.displayName = null
            
            plugin.logger.debug("Created direct team-based nametag for player ${player.username}: format='${nametagFormat ?: "default"}', text='${nametagText}'")
        } catch (e: Exception) {
            plugin.logger.error("Failed to create nametag for player ${player.username}", e)
        }
    }

    /**
     * Update a player's nametag based on their rank
     */
    suspend fun updatePlayerNametag(player: Player) {
        try {
            // Remove existing nametag first
            removePlayerNametag(player)
            
            // Create new nametag with updated data - use visibility-safe version
            createPlayerNametagOnly(player)
            
            plugin.logger.debug("Updated nametag for player ${player.username}")
        } catch (e: Exception) {
            plugin.logger.error("Failed to update nametag for player ${player.username}", e)
            throw e
        }
    }

    /**
     * Force refresh a player's nametag
     */
    suspend fun forceRefreshNametag(playerUuid: UUID) {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
        if (player != null) {
            // Clear cache and update - use visibility-safe version
            nametagCache.remove(playerUuid)
            updatePlayerNametag(player)
        }
    }

    /**
     * Remove a player's nametag when they disconnect
     */
    fun removePlayerNametag(player: Player) {
        try {
            nametagCache.remove(player.uuid)
            customNametags.remove(player.uuid)
            
            // Remove player from the nametag team
            nameTagTeam.removeMember(player.username)
            
            // Remove player from their personal team
            val teamManager = MinecraftServer.getTeamManager()
            val playerTeam = teamManager.getTeam("player-${player.username}")
            playerTeam?.let {
                it.removeMember(player.username)
                // Optionally unregister the team if it's empty
                if (it.members.isEmpty()) {
                    teamManager.deleteTeam(it)
                }
            }
            
            // CRITICAL FIX: Do NOT modify player visibility here - let VisibilityManager handle it
            // This was causing conflicts with hide/show staff settings
            // player.customName = null
            // player.isCustomNameVisible = true
            
            // Remove the MSNameTags nametag if it exists
            val nameTag = playerNameTags.remove(player.uuid)
            nameTag?.let {
                // MSNameTags handles cleanup automatically when players disconnect
                // No need to manually clear viewers
            }
            
            plugin.logger.debug("Removed nametag for player ${player.username}")
        } catch (e: Exception) {
            plugin.logger.error("Failed to remove nametag for player ${player.username}", e)
        }
    }

    /**
     * Check if a player has a custom nametag
     */
    fun hasCustomNametag(playerUuid: UUID): Boolean {
        return customNametags[playerUuid] ?: false
    }

    /**
     * Get nametag info for a player
     */
    fun getPlayerNametagInfo(playerUuid: UUID): NametagInfo? {
        return nametagCache[playerUuid]
    }

    /**
     * Get statistics about the nametag system
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "cached_nametags" to nametagCache.size,
            "online_players" to MinecraftServer.getConnectionManager().onlinePlayers.size,
            "update_task_running" to true,
            "custom_nametags" to customNametags.size,
            "team_name" to if (::nameTagTeam.isInitialized) nameTagTeam.teamName else "not_initialized"
        )
    }

    /**
     * Start the periodic update task for nametags (disabled to prevent protocol errors)
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun startUpdateTask() {
        // DISABLED: Periodic nametag updates to prevent network protocol errors
        // Nametags will only update when ranks change or players reconnect
        plugin.logger.info("Periodic nametag updates disabled to prevent protocol errors")
        
        // If we need periodic updates in the future, uncomment this with a longer interval:
        /*
        // Update nametags every 5 minutes instead of 30 seconds
        MinecraftServer.getSchedulerManager().submitTask {
            GlobalScope.launch {
                try {
                    val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
                    for (player in onlinePlayers) {
                        // Only update if the nametag data has actually changed
                        val currentNametagInfo = nametagCache[player.uuid]
                        if (currentNametagInfo != null) {
                            // Skip update if nametag hasn't changed
                            continue
                        }
                        updatePlayerNametag(player)
                        delay(500) // Longer delay to prevent overwhelming the system
                    }
                } catch (e: Exception) {
                    plugin.logger.error("Error in nametag update task", e)
                }
            }
            TaskSchedule.tick(6000) // 5 minutes at 20 TPS
        }
        */
    }

    /**
     * Shutdown the nametag manager
     */
    fun shutdown() {
        try {
            // Clear all nametags - MSNameTags handles this automatically
            nametagCache.clear()
            customNametags.clear()
            playerNameTags.clear()
            
            plugin.logger.info("NametagManager shutdown successfully")
        } catch (e: Exception) {
            plugin.logger.error("Error during NametagManager shutdown", e)
        }
    }

    /**
     * Convert legacy color codes (&) to MiniMessage format
     */
    private fun convertLegacyToMiniMessage(text: String): String {
        return text
            .replace("&0", "<black>")
            .replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>")
            .replace("&5", "<dark_purple>")
            .replace("&6", "<gold>")
            .replace("&7", "<gray>")
            .replace("&8", "<dark_gray>")
            .replace("&9", "<blue>")
            .replace("&a", "<green>")
            .replace("&b", "<aqua>")
            .replace("&c", "<red>")
            .replace("&d", "<light_purple>")
            .replace("&e", "<yellow>")
            .replace("&f", "<white>")
            .replace("&k", "<obfuscated>")
            .replace("&l", "<bold>")
            .replace("&m", "<strikethrough>")
            .replace("&n", "<underlined>")
            .replace("&o", "<italic>")
            .replace("&r", "<reset>")
    }
}
