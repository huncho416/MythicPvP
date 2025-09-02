package radium.backend.player.staff

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.player.TabListEntry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import radium.backend.Radium
import radium.backend.util.YamlFactory
import java.util.UUID

class StaffManager(private val radium: Radium) {

    private val yamlFactory = radium.yamlFactory

    // Map of online staff: UUID -> Player
    private val onlineStaff: MutableMap<UUID, Player> = mutableMapOf()

    // Map of vanished staff: UUID -> Player
    val vanishedStaff: MutableMap<String, Player> = mutableMapOf()

    // Track player's channel status (talking and/or listening)
    data class ChannelStatus(val player: Player, var isListening: Boolean = false, var isTalking: Boolean = false)

    // Single map to manage staff channel status
    private val messageChannel: MutableMap<UUID, ChannelStatus> = mutableMapOf()
    
    // Track the last known server for each staff member
    private val playerServers: MutableMap<UUID, String> = mutableMapOf()

    /**
     * Adds a player to the online staff map.
     */
    suspend fun addStaff(player: Player) {
        // Get player profile to access rank information for the connection message
        val playerProfile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
        val highestRank = playerProfile?.getHighestRank(radium.rankManager)
        val prefix = highestRank?.prefix ?: ""
        
        // Extract color from prefix (e.g., "&4[Owner] &4" -> "&4")
        val chatColor = if (prefix.isNotEmpty() && prefix.startsWith("&")) {
            prefix.substring(0, 2) // Extract first color code like "&4"
        } else {
            "&f" // Default to white if no color found
        }
        
        val serverName = player.currentServer.orElse(null)?.serverInfo?.name?.takeIf { it != "Unknown" } ?: "Lobby"
        
        // Store the server for later use in disconnect message
        playerServers[player.uniqueId] = serverName
        
        // Use the configurable connection message from lang.yml with rank prefix, color, and server
        val connectionMessage = yamlFactory.getMessageComponent("staff.connected", 
            "player" to player.username,
            "prefix" to prefix,
            "chatColor" to chatColor,
            "server" to serverName
        )
        sendStaffMessage(connectionMessage)

        onlineStaff[player.uniqueId] = player
        // Automatically enable staff messaging for new staff members
        setListening(player, true)
        setTalking(player, true)

        // Check if autoVanish is enabled for this staff member
        // Get the player's profile from cache or database
        val profile = radium.connectionHandler.getPlayerProfile(player.uniqueId, player.username)

        // Check if autoVanish is enabled (default to false if profile is null or setting is not found)
        val autoVanishEnabled = profile?.getSetting("autoVanish")?.toBoolean() ?: false

        if (autoVanishEnabled) {
            // Auto-vanish the staff member using async method
            radium.networkVanishManager.setVanishStateAsync(player, true)

        }
    }

    /**
     * Removes a player from the online staff map and sends disconnect message.
     */
    fun removeStaff(player: Player) {
        if (onlineStaff.containsKey(player.uniqueId)) {
            // Send disconnect message with rank color asynchronously
            GlobalScope.launch {
                try {
                    val playerProfile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
                    val highestRank = playerProfile?.getHighestRank(radium.rankManager)
                    val prefix = highestRank?.prefix ?: ""
                    
                    // Extract color from prefix (e.g., "&4[Owner] &4" -> "&4")
                    val chatColor = if (prefix.isNotEmpty() && prefix.startsWith("&")) {
                        prefix.substring(0, 2) // Extract first color code like "&4"
                    } else {
                        "&f" // Default to white if no color found
                    }
                    
                    // Get the last known server for this player
                    val serverName = playerServers[player.uniqueId] ?: "Lobby"
                    
                    val disconnectMessage = yamlFactory.getMessageComponent("staff.disconnected", 
                        "player" to player.username,
                        "prefix" to prefix,
                        "chatColor" to chatColor,
                        "server" to serverName
                    )
                    sendStaffMessage(disconnectMessage)
                } catch (e: Exception) {
                    // Fallback message if there's an error getting rank info
                    val serverName = playerServers[player.uniqueId] ?: "Lobby"
                    val disconnectMessage = yamlFactory.getMessageComponent("staff.disconnected", 
                        "player" to player.username,
                        "prefix" to "",
                        "chatColor" to "&f",
                        "server" to serverName
                    )
                    sendStaffMessage(disconnectMessage)
                }
            }
        }
        
        onlineStaff.remove(player.uniqueId)
        messageChannel.remove(player.uniqueId)
        playerServers.remove(player.uniqueId) // Clean up server tracking
    }

    /**
     * Checks if a player is staff (has staff permissions)
     */
    fun isStaff(player: Player): Boolean {
        return player.hasPermission("radium.staff") || onlineStaff.containsKey(player.uniqueId)
    }

    /**
     * Checks if a player is currently recognized as online staff.
     */
    fun isStaffOnline(player: Player): Boolean {
        return onlineStaff.containsKey(player.uniqueId)
    }

    /**
     * Returns a list of all currently online staff players.
     */
    fun getOnlineStaff(): List<Player> {
        return onlineStaff.values.toList()
    }

    /**
     * Sets a player's listening status for the staff channel.
     */
    fun setListening(player: Player, listening: Boolean) {
        val status = messageChannel.getOrPut(player.uniqueId) { ChannelStatus(player) }
        status.isListening = listening
    }

    /**
     * Sets a player's talking status for the staff channel.
     * Note: A player can only talk if they are also listening.
     */
    fun setTalking(player: Player, talking: Boolean) {
        val status = messageChannel.getOrPut(player.uniqueId) { ChannelStatus(player) }
        // Only allow talking if listening is enabled or we're turning talking off
        if (talking && !status.isListening) {
            setListening(player, true)  // Auto-enable listening when talking is enabled
        }
        status.isTalking = talking
    }

    /**
     * Checks if a player is currently listening to the staff channel.
     */
    fun isListening(player: Player): Boolean {
        return messageChannel[player.uniqueId]?.isListening == true
    }

    /**
     * Checks if a player is currently in talking mode for the staff channel.
     */
    fun isTalking(player: Player): Boolean {
        return messageChannel[player.uniqueId]?.isTalking == true
    }

    /**
     * Toggles a player's vanish status using the new network vanish system.
     * Returns true if the player is now vanished, false if they are now visible.
     */
    fun vanishToggle(player: Player): Boolean {
        val currentlyVanished = isVanished(player)
        val newState = !currentlyVanished
        
        radium.networkVanishManager.setVanishStateAsync(player, newState)
        return newState
    }

    /**
     * Checks if a player is currently vanished.
     */
    fun isVanished(player: Player): Boolean {
        return radium.networkVanishManager.isVanished(player.uniqueId)
    }

    /**
     * Gets all currently vanished staff members.
     */
    fun getVanishedStaff(): List<Player> {
        return radium.networkVanishManager.getVanishedPlayers().mapNotNull { (playerId, _) ->
            radium.server.getPlayer(playerId).orElse(null)
        }.filter { isStaff(it) }
    }

    /**
     * Sends a message to all players listening to the staff channel and the console.
     */
    fun sendStaffMessage(message: Component) {
        val staffPrefix = yamlFactory.getMessageComponent("staff.prefix")
        // Send to all players listening to the staff channel
        messageChannel.values.filter { it.isListening }.forEach { status ->
            status.player.sendMessage(staffPrefix.append(message))
        }
        // Send to console
        radium.logger.info((staffPrefix.append(message)))
    }

    @Subscribe(priority = 1000) // Very high priority to run before other chat handlers
    fun staffChatListener(event: PlayerChatEvent) {
        val player = event.player
        val message = event.message
        val status = messageChannel[player.uniqueId]

        // Only process messages from players who are both in the listening channel and talking mode
        if (status?.isTalking == true) {
            radium.logger.info("[SC] ${player.username} → $message")
            
            // COMPLETELY DISABLED: Chat event modification for Minecraft 1.19.1+ compatibility
            // Any modification to PlayerChatEvent.result causes "illegal protocol state" errors
            // Staff chat messages will appear in both staff chat and public chat until a better solution is found
            // Remove this warning message as it's not critical
            // radium.logger.debug("Staff chat detected but chat suppression disabled for 1.19.1+ compatibility")
            
            /* DISABLED: All chat event modification causes disconnects in 1.19.1+
            try {
                // First try denying the event completely
                event.result = PlayerChatEvent.ChatResult.denied()
            } catch (e: Exception) {
                try {
                    // If denied() doesn't work, try empty message
                    event.result = PlayerChatEvent.ChatResult.message("")
                } catch (e2: Exception) {
                    // If both approaches fail, log but continue with staff chat
                    radium.logger.warn("Could not suppress staff chat message due to signed chat restrictions. Message will appear in both staff and public chat.")
                }
            }
            */

            // Send to staff chat (this will work regardless of chat suppression)
            sendStaffChatMessage(player, message)
        }
    }
    
    /**
     * Sends a message to staff chat with proper formatting
     */
    private fun sendStaffChatMessage(player: Player, message: String) {
        // Launch coroutine to handle async profile lookup
        GlobalScope.launch {
            try {
                // Get player profile to access rank information
                val profile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
                if (profile != null) {
                    // Get highest rank for prefix and name color
                    val highestRank = profile.getHighestRank(radium.rankManager)
                    val prefix = highestRank?.prefix ?: ""
                    
                    // Extract color from prefix (e.g., "&4[Owner] &4" -> "&4")
                    val chatColor = if (prefix.isNotEmpty() && prefix.startsWith("&")) {
                        prefix.substring(0, 2) // Extract first color code like "&4"
                    } else {
                        "&f" // Default to white if no color found
                    }
                    
                    // Use the chat format from the lang.yml with proper formatting
                    val chatFormat = yamlFactory.getMessageComponent("staff.chat_format",
                        "prefix" to prefix,
                        "player" to player.username,
                        "chatColor" to chatColor,
                        "message" to message
                    )

                    sendStaffMessage(chatFormat)
                } else {
                    // Fallback if profile not found
                    val chatFormat = yamlFactory.getMessageComponent("staff.chat_format",
                        "prefix" to "",
                        "player" to player.username,
                        "chatColor" to "&7",
                        "message" to message
                    )
                    sendStaffMessage(chatFormat)
                }
            } catch (e: Exception) {
                radium.logger.warn("Failed to send staff chat message from ${player.username}: ${e.message}")
            }
        }
    }

    @Subscribe(priority = 100)
    fun staffServerChange(event: ServerPostConnectEvent) {
        val player = event.player
        val from = event.previousServer?.serverInfo
        val to = event.player.currentServer.get().serverInfo

        // Only handle if the player is listening to staff messages and switched servers
        if (isListening(player) && from != null) {
            // Update the stored server for this player
            val newServerName = to.name.takeIf { it != "Unknown" } ?: "Server"
            playerServers[player.uniqueId] = newServerName
            
            // Get player rank information asynchronously for the server switch message
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val playerProfile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
                    val highestRank = playerProfile?.getHighestRank(radium.rankManager)
                    val prefix = highestRank?.prefix ?: ""
                    
                    // Extract color from prefix (e.g., "&4[Owner] &4" -> "&4")
                    val chatColor = if (prefix.isNotEmpty() && prefix.startsWith("&")) {
                        prefix.substring(0, 2) // Extract first color code like "&4"
                    } else {
                        "&f" // Default to white if no color found
                    }
                    
                    val serverSwitchMessage = yamlFactory.getMessageComponent("staff.server_switch",
                        "player" to player.username,
                        "prefix" to prefix,
                        "chatColor" to chatColor,
                        "from" to (from.name.takeIf { it != "Unknown" } ?: "Lobby"),
                        "to" to (to.name.takeIf { it != "Unknown" } ?: "Server")
                    )
                    sendStaffMessage(serverSwitchMessage)
                } catch (e: Exception) {
                    // Fallback message without prefix if there's an error
                    val serverSwitchMessage = yamlFactory.getMessageComponent("staff.server_switch",
                        "player" to player.username,
                        "prefix" to "",
                        "chatColor" to "&f",
                        "from" to (from.name.takeIf { it != "Unknown" } ?: "Lobby"),
                        "to" to (to.name.takeIf { it != "Unknown" } ?: "Server")
                    )
                    sendStaffMessage(serverSwitchMessage)
                }
            }
        }
    }

    /**
     * Notifies a player with a message when their staff chat talking mode is enabled.
     */
    fun notifyStaffChatEnabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("staff.chat_enabled"))
    }

    /**
     * Notifies a player with a message when their staff chat talking mode is disabled.
     */
    fun notifyStaffChatDisabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("staff.chat_disabled"))
    }

    /**
     * Notifies a player with a message when their staff chat listening mode is enabled.
     */
    fun notifyStaffListeningEnabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("staff.listening_disabled")) // Note: Message key seems reversed but matches the content
    }

    /**
     * Notifies a player with a message when their staff chat listening mode is disabled.
     */
    fun notifyStaffListeningDisabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("staff.listening_enabled")) // Note: Message key seems reversed but matches the content
    }

    /**
     * Notifies a player with a message when they are vanished.
     */
    fun notifyVanishEnabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("vanish.now_vanished"))
    }

    /**
     * Notifies a player with a message when they are unvanished.
     */
    fun notifyVanishDisabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("vanish.now_visible"))
    }

    /**
     * Notifies a player with a message when auto-vanish is enabled.
     */
    fun notifyAutoVanishEnabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("vanish.auto_enabled"))
    }

    /**
     * Notifies a player with a message when auto-vanish is disabled.
     */
    fun notifyAutoVanishDisabled(player: Player) {
        player.sendMessage(yamlFactory.getMessageComponent("vanish.auto_disabled"))
    }

    /**
     * Checks if a player can see another vanished player based on rank weight and permissions.
     * Higher rank weight staff can see lower rank vanished staff.
     * Players with radium.vanish.see permission can see all vanished players.
     */
    suspend fun canSeeVanishedPlayer(viewer: Player, vanishedPlayer: Player): Boolean {
        // If the viewer has the special permission to see all vanished players
        if (viewer.hasPermission("radium.vanish.see")) {
            return true
        }
        
        // If they're the same player, they can always see themselves
        if (viewer.uniqueId == vanishedPlayer.uniqueId) {
            return true
        }
        
        // Get both players' profiles to compare rank weights
        val viewerProfile = radium.connectionHandler.findPlayerProfile(viewer.uniqueId.toString())
        val vanishedProfile = radium.connectionHandler.findPlayerProfile(vanishedPlayer.uniqueId.toString())
        
        // If either profile is not found, default to not being able to see
        if (viewerProfile == null || vanishedProfile == null) {
            return false
        }
        
        // Get the highest rank for both players
        val viewerHighestRank = viewerProfile.getHighestRank(radium.rankManager)
        val vanishedHighestRank = vanishedProfile.getHighestRank(radium.rankManager)
        
        // If either player has no ranks, use default behavior
        val viewerWeight = viewerHighestRank?.weight ?: 0
        val vanishedWeight = vanishedHighestRank?.weight ?: 0
        
        // Viewer can see vanished player if their rank weight is higher or equal
        val canSee = viewerWeight >= vanishedWeight
        
        return canSee
    }
    
    /**
     * Gets all vanished players that a specific player can see based on rank weight.
     */
    suspend fun getVisibleVanishedPlayers(viewer: Player): List<Player> {
        val visibleVanished = mutableListOf<Player>()
        
        for (vanishedPlayer in vanishedStaff.values) {
            if (canSeeVanishedPlayer(viewer, vanishedPlayer)) {
                visibleVanished.add(vanishedPlayer)
            }
        }
        
        return visibleVanished
    }

    /**
     * Publishes vanish event to Redis
     */
    private fun publishVanishEvent(player: Player, isVanished: Boolean) {
        try {
            val message = mapOf(
                "uuid" to player.uniqueId.toString(),
                "username" to player.username,
                "vanished" to isVanished.toString(),
                "timestamp" to System.currentTimeMillis().toString()
            ).entries.joinToString(",") { "${it.key}=${it.value}" }
            
            radium.lettuceCache.sync().publish("radium:player:vanish", message)

            
        } catch (e: Exception) {
            radium.logger.error("Failed to publish vanish event for ${player.username}: ${e.message}")
        }
    }

    /**
     * Synchronous version of canSeeVanishedPlayer for tab list updates
     * Uses cached profile data for rank-weight comparison when available
     */
    fun canSeeVanishedPlayerSync(viewer: Player, vanishedPlayer: Player): Boolean {
        // If the viewer has the special permission to see all vanished players
        if (viewer.hasPermission("radium.vanish.see")) {
            return true
        }
        
        // If they're the same player, they can always see themselves
        if (viewer.uniqueId == vanishedPlayer.uniqueId) {
            return true
        }
        
        try {
            // Try to get cached profiles for rank-weight comparison
            val viewerProfile = radium.connectionHandler.getPlayerProfile(viewer.uniqueId, viewer.username)
            val vanishedProfile = radium.connectionHandler.getPlayerProfile(vanishedPlayer.uniqueId, vanishedPlayer.username)
            
            if (viewerProfile != null && vanishedProfile != null) {
                // Use cached data for proper rank-weight comparison
                val viewerHighestRank = viewerProfile.getHighestRankCached(radium.rankManager)
                val vanishedHighestRank = vanishedProfile.getHighestRankCached(radium.rankManager)
                
                val viewerWeight = viewerHighestRank?.weight ?: 0
                val vanishedWeight = vanishedHighestRank?.weight ?: 0
                

                
                // Viewer can see vanished player if their rank weight is higher or equal
                return viewerWeight >= vanishedWeight
            }
        } catch (e: Exception) {
            radium.logger.warn("Failed to get cached profiles for vanish visibility check: ${e.message}")
        }
        
        // Fallback: Only staff can see vanished players if no profile data available
        val canSee = isStaffOnline(viewer)

        return canSee
    }

    /**
     * Enhanced async version that uses proper rank-weight comparison
     * This should be used when you need accurate rank-based visibility logic
     */
    suspend fun canSeeVanishedPlayerEnhanced(viewer: Player, vanishedPlayer: Player): Boolean {
        // If the viewer has the special permission to see all vanished players
        if (viewer.hasPermission("radium.vanish.see")) {
            return true
        }
        
        // If they're the same player, they can always see themselves
        if (viewer.uniqueId == vanishedPlayer.uniqueId) {
            return true
        }
        
        try {
            // Get both players' profiles to compare rank weights
            val viewerProfile = radium.connectionHandler.findPlayerProfile(viewer.uniqueId.toString())
            val vanishedProfile = radium.connectionHandler.findPlayerProfile(vanishedPlayer.uniqueId.toString())
            
            // If either profile is not found, default to staff-only visibility
            if (viewerProfile == null || vanishedProfile == null) {
                return isStaffOnline(viewer) && isStaffOnline(vanishedPlayer)
            }
            
            // Get the highest rank for both players
            val viewerHighestRank = viewerProfile.getHighestRank(radium.rankManager)
            val vanishedHighestRank = vanishedProfile.getHighestRank(radium.rankManager)
            
            val viewerWeight = viewerHighestRank?.weight ?: 0
            val vanishedWeight = vanishedHighestRank?.weight ?: 0
            
            // Viewer can see vanished player if their rank weight is higher or equal
            val canSee = viewerWeight >= vanishedWeight
            
            return canSee
        } catch (e: Exception) {
            radium.logger.warn("Failed to check enhanced vanish visibility for ${viewer.username} -> ${vanishedPlayer.username}: ${e.message}")
            // Fallback to simplified logic
            return canSeeVanishedPlayerSync(viewer, vanishedPlayer)
        }
    }
}
