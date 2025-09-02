package radium.backend.player

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.TabCompleteEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.player.TabListEntry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import radium.backend.util.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Completely manages tab list display and visibility for the entire network
 * Takes full control over tab lists to prevent conflicts with backend servers
 */
class TabListManager(private val radium: Radium) {

    private val processingPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val isInitialized = ConcurrentHashMap<UUID, Boolean>()

    companion object {
        /**
         * Cleans corrupted color codes and normalizes them to ampersand format
         * CRITICAL: Fixes UTF-8 corruption where & becomes ∩┐╜ and other encoding issues
         */
        fun cleanColorCodes(text: String?): String {
            if (text.isNullOrBlank()) return ""
            
            return text
                .replace("∩┐╜", "&")     // Fix UTF-8 corruption of &
                .replace("§", "&")      // Normalize section signs to ampersand
                .replace("∩", "&")      // Additional corruption patterns
                .replace("┐", "")       // Remove stray corruption characters
                .replace("╜", "")       // Remove stray corruption characters
                .replace("Â", "")       // Remove UTF-8 BOM corruption
                .replace("âž§", "&")    // Another corruption pattern
                .replace(Regex("&([^0-9a-fk-orA-FK-OR])"), "")  // Remove invalid codes
                .replace(Regex("&{2,}"), "&")  // Replace multiple & with single &
        }
        
        /**
         * Safely parses color codes and creates a Component
         * CRITICAL: Handle all possible corrupted encoding scenarios
         */
        fun safeParseColoredText(text: String?): Component {
            if (text.isNullOrBlank()) return Component.empty()
            
            return try {
                // Clean the text aggressively before parsing
                val cleanText = cleanColorCodes(text)
                
                LegacyComponentSerializer.legacyAmpersand().deserialize(cleanText)
            } catch (e: Exception) {
                // Ultimate fallback: strip all formatting and return white text
                val plainText = text.replace(Regex("[&§∩┐╜âž][0-9a-fk-orA-FK-OR]?"), "")
                    .replace(Regex("[∩┐╜âž]"), "")
                Component.text(plainText, NamedTextColor.WHITE)
            }
        }
    }

    /**
     * Converts legacy color codes to Adventure Components safely
     * CRITICAL FIX: Handle corrupted UTF-8 characters that replace & with ∩┐╜
     */
    private fun parseColoredText(text: String): Component {
        return if (text.isBlank()) {
            Component.empty()
        } else {
            try {
                // CRITICAL: Fix corrupted UTF-8 characters first
                val cleanText = text
                    .replace("∩┐╜", "&")  // Fix corrupted UTF-8 encoding of &
                    .replace("§", "&")   // Normalize section signs to &
                    .replace(Regex("&([^0-9a-fk-orA-FK-OR])"), "") // Remove invalid color codes
                
                radium.logger.debug("Parsing colored text: '$text' -> '$cleanText'")
                
                // Use legacy serializer with & codes (ampersand)
                LegacyComponentSerializer.legacyAmpersand().deserialize(cleanText)
            } catch (e: Exception) {
                radium.logger.warn("Failed to parse colored text '$text': ${e.message}")
                // Fallback: strip all formatting and return plain white text
                val plainText = text.replace(Regex("[&§∩┐╜][0-9a-fk-orA-FK-OR]?"), "")
                Component.text(plainText, NamedTextColor.WHITE)
            }
        }
    }

    /**
     * Completely rebuilds a player's tab list with proper formatting and vanish handling
     * This takes full control and clears/rebuilds the entire tab list
     */
    suspend fun rebuildPlayerTabList(player: Player) {
        if (processingPlayers.putIfAbsent(player.uniqueId, true) != null) {
            return
        }

        try {
            radium.logger.debug("Rebuilding complete tab list for ${player.username}")
            
            // Store current online players before clearing
            val onlinePlayers = radium.server.allPlayers.toList()
            radium.logger.debug("Found ${onlinePlayers.size} online players to add to ${player.username}'s tab")
            
            // Clear the current tab list completely
            player.tabList.clearAll()
            
            // Small delay to ensure clear is processed
            delay(50)
            
            var addedCount = 0
            
            // Add all online players including self with proper formatting and visibility
            onlinePlayers.forEach { otherPlayer ->
                try {
                    val isVanished = radium.networkVanishManager.isVanished(otherPlayer.uniqueId)
                    val shouldBeVisible = if (isVanished) {
                        radium.staffManager.canSeeVanishedPlayerSync(player, otherPlayer)
                    } else {
                        true
                    }
                    
                    if (shouldBeVisible) {
                        val displayName = buildPlayerDisplayName(otherPlayer, isVanished)
                        
                        // Create and add tab entry using proper Velocity API
                        val tabEntry = TabListEntry.builder()
                            .tabList(player.tabList)  // CRITICAL FIX: Set the tablist
                            .profile(otherPlayer.gameProfile)
                            .displayName(displayName)
                            .latency(otherPlayer.ping.toInt())
                            .gameMode(0)
                            .build()
                        
                        player.tabList.addEntry(tabEntry)
                        addedCount++
                        radium.logger.debug("Added ${otherPlayer.username} to ${player.username}'s tab list (vanished: $isVanished)")
                    } else {
                        radium.logger.debug("Skipped ${otherPlayer.username} for ${player.username} (vanished and no permission to see)")
                    }
                } catch (e: Exception) {
                    radium.logger.warn("Failed to add ${otherPlayer.username} to ${player.username}'s tab: ${e.message}")
                }
            }
            
            isInitialized[player.uniqueId] = true
            radium.logger.debug("Completed tab list rebuild for ${player.username} - added $addedCount of ${onlinePlayers.size} players")
            
        } catch (e: Exception) {
            radium.logger.error("Failed to rebuild tab list for ${player.username}: ${e.message}")
        } finally {
            processingPlayers.remove(player.uniqueId)
        }
    }

    /**
     * Builds the display name for a player including rank formatting and vanish indicator
     * CRITICAL FIX: Clean corrupted color codes at every step
     */
    private suspend fun buildPlayerDisplayName(player: Player, isVanished: Boolean): Component {
        val profile = radium.connectionHandler.findPlayerProfile(player.uniqueId.toString())
        
        val displayText = StringBuilder()
        
        // Add vanish indicator first if vanished
        if (isVanished) {
            try {
                val vanishIndicator = radium.yamlFactory.getMessage("vanish.tablist_indicator")
                if (vanishIndicator.isNotBlank() && vanishIndicator != "vanish.tablist_indicator") {
                    displayText.append(cleanColorCodes(vanishIndicator))
                } else {
                    // Fallback if message not found
                    displayText.append("&7[V] ")
                }
            } catch (e: Exception) {
                displayText.append("&7[V] ")
            }
        }
        
        // Build the rank and name display
        if (profile != null) {
            try {
                val highestRank = profile.getHighestRankCached(radium.rankManager)
                
                if (highestRank != null) {
                    // Clean all rank data of corrupted characters
                    val rawTabPrefix = if (!highestRank.tabPrefix.isNullOrBlank()) {
                        highestRank.tabPrefix!!.trim()
                    } else {
                        highestRank.prefix?.trim() ?: ""
                    }
                    val rawTabSuffix = if (!highestRank.tabSuffix.isNullOrBlank()) {
                        highestRank.tabSuffix!!.trim()
                    } else {
                        highestRank.suffix?.trim() ?: ""
                    }
                    val rawPlayerColor = highestRank.color?.trim() ?: "&f"
                    
                    // Debug logging to track corruption
                    radium.logger.debug("Raw rank data for ${player.username}: prefix='$rawTabPrefix', color='$rawPlayerColor', suffix='$rawTabSuffix'")
                    
                    val tabPrefix = cleanColorCodes(rawTabPrefix)
                    val tabSuffix = cleanColorCodes(rawTabSuffix)
                    val playerColor = cleanColorCodes(rawPlayerColor)
                    
                    // Debug logging to track cleaning
                    if (tabPrefix != rawTabPrefix) radium.logger.info("CLEANED PREFIX for ${player.username}: '$rawTabPrefix' -> '$tabPrefix'")
                    if (playerColor != rawPlayerColor) radium.logger.info("CLEANED COLOR for ${player.username}: '$rawPlayerColor' -> '$playerColor'")
                    if (tabSuffix != rawTabSuffix) radium.logger.info("CLEANED SUFFIX for ${player.username}: '$rawTabSuffix' -> '$tabSuffix'")
                    
                    // Build the complete display string with clean data
                    // FIXED: Handle tabPrefix color codes properly - if tabPrefix exists, use it, otherwise use grey
                    if (tabPrefix.isNotEmpty()) {
                        // Use tabPrefix which may include color codes that affect the name
                        displayText.append(tabPrefix)
                        // If tabPrefix doesn't end with a color code, append the player name with rank color
                        if (!tabPrefix.matches(Regex(".*&[0-9a-fk-orA-FK-OR]\\s*$"))) {
                            displayText.append(playerColor)
                        }
                        displayText.append(player.username)
                    } else {
                        // No tabPrefix - use grey color for default players without tabPrefix
                        displayText.append("&7").append(player.username)
                    }
                    displayText.append(tabSuffix)
                } else {
                    // Default rank - get default rank configuration or fallback to grey
                    val defaultRank = radium.rankManager.getCachedRank("Default") ?: radium.rankManager.getCachedRank("DEFAULT")
                    if (defaultRank != null && !defaultRank.tabPrefix.isNullOrBlank()) {
                        val tabPrefix = cleanColorCodes(defaultRank.tabPrefix!!.trim())
                        displayText.append(tabPrefix)
                        // If tabPrefix doesn't end with a color code, append the player name with rank color
                        if (!tabPrefix.matches(Regex(".*&[0-9a-fk-orA-FK-OR]\\s*$"))) {
                            val defaultColor = cleanColorCodes(defaultRank.color?.trim() ?: "&7")
                            displayText.append(defaultColor)
                        }
                        displayText.append(player.username)
                        val tabSuffix = cleanColorCodes(defaultRank.tabSuffix?.trim() ?: "")
                        displayText.append(tabSuffix)
                    } else {
                        // No tabPrefix - use grey color for default players
                        displayText.append("&7").append(player.username)
                    }
                }
            } catch (e: Exception) {
                radium.logger.warn("Failed to build rank display for ${player.username}: ${e.message}")
                displayText.append("&f").append(player.username)
            }
        } else {
            // No profile found - use default grey color
            displayText.append("&7").append(player.username)
        }
        
        // Convert the complete string to a single component using our safe parser
        val finalText = displayText.toString()
        radium.logger.debug("Building display name for ${player.username}: '$finalText'")
        
        return safeParseColoredText(finalText)
    }

    /**
     * Updates all players' tab lists when a player's vanish state changes
     */
    suspend fun handleVanishStateChange(player: Player, isNowVanished: Boolean) {
        radium.logger.info("Handling vanish state change for ${player.username}: vanished=$isNowVanished")
        
        // CRITICAL FIX: Rebuild the vanished player's own tab list completely
        // This ensures they can see themselves with the [V] indicator when vanished
        try {
            rebuildPlayerTabList(player)
            radium.logger.debug("Rebuilt ${player.username}'s own tab list with vanish state: $isNowVanished")
        } catch (e: Exception) {
            radium.logger.warn("Failed to rebuild ${player.username}'s own tab list: ${e.message}")
        }
        
        // CRITICAL FIX: For unvanish, rebuild ALL players' tab lists to ensure player reappears
        if (!isNowVanished) {
            radium.logger.info("Player ${player.username} unvanished - rebuilding all tab lists")
            try {
                delay(100) // Small delay to ensure vanish state is stable
                radium.server.allPlayers.forEach { otherPlayer ->
                    if (otherPlayer.uniqueId != player.uniqueId) {
                        rebuildPlayerTabList(otherPlayer)
                        radium.logger.debug("Rebuilt ${otherPlayer.username}'s tab list to show unvanished ${player.username}")
                    }
                }
                radium.logger.debug("Completed tab list rebuilds for unvanished player ${player.username}")
            } catch (e: Exception) {
                radium.logger.warn("Failed to rebuild tab lists for unvanished player ${player.username}: ${e.message}")
            }
            return // Exit early for unvanish - full rebuilds handle everything
        }
        
        // For vanish, update the vanished player in all other players' tab lists individually
        val onlinePlayers = radium.server.allPlayers.toList()
        radium.logger.debug("Updating vanished ${player.username} in ${onlinePlayers.size - 1} other players' tab lists")
        
        var successCount = 0
        var failureCount = 0
        
        onlinePlayers.forEach { otherPlayer ->
            if (otherPlayer.uniqueId == player.uniqueId) return@forEach
            
            try {
                val shouldBeVisible = radium.staffManager.canSeeVanishedPlayerSync(otherPlayer, player)
                val tabEntry = otherPlayer.tabList.getEntry(player.uniqueId)
                
                if (shouldBeVisible) {
                    // Player should be visible - update or add entry with vanish indicator
                    val displayName = buildPlayerDisplayName(player, true)
                    
                    if (tabEntry.isPresent) {
                        tabEntry.get().setDisplayName(displayName)
                        radium.logger.debug("Updated vanished ${player.username} in ${otherPlayer.username}'s tab")
                    } else {
                        // Re-add the player to tab list with vanish indicator
                        val newTabEntry = TabListEntry.builder()
                            .tabList(otherPlayer.tabList)
                            .profile(player.gameProfile)
                            .displayName(displayName)
                            .latency(player.ping.toInt())
                            .gameMode(0)
                            .build()
                        
                        otherPlayer.tabList.addEntry(newTabEntry)
                        radium.logger.debug("Added vanished ${player.username} to ${otherPlayer.username}'s tab")
                    }
                } else {
                    // Player should not be visible - remove if present
                    if (tabEntry.isPresent) {
                        otherPlayer.tabList.removeEntry(player.uniqueId)
                        radium.logger.debug("Removed vanished ${player.username} from ${otherPlayer.username}'s tab")
                    }
                }
                successCount++
            } catch (e: Exception) {
                failureCount++
                radium.logger.warn("Failed to update ${player.username} in ${otherPlayer.username}'s tab: ${e.message}")
                
                // Try emergency tab list rebuild for this player if their tab becomes corrupted
                try {
                    val tabEntries = otherPlayer.tabList.entries.size
                    if (tabEntries == 0) {
                        radium.logger.warn("${otherPlayer.username}'s tab list is empty! Triggering emergency rebuild...")
                        rebuildPlayerTabList(otherPlayer)
                    }
                } catch (emergencyException: Exception) {
                    radium.logger.error("Emergency tab rebuild also failed for ${otherPlayer.username}: ${emergencyException.message}")
                }
            }
        }
        
        radium.logger.info("Vanish state update complete for ${player.username}: $successCount successes, $failureCount failures")
    }

    /**
     * Updates all tab lists when ranks change
     */
    suspend fun handleRankChange(player: Player) {
        radium.logger.info("Handling rank change for ${player.username}")
        
        val isVanished = radium.networkVanishManager.isVanished(player.uniqueId)
        val newDisplayName = buildPlayerDisplayName(player, isVanished)
        
        // Update this player in all other players' tab lists
        radium.server.allPlayers.forEach { otherPlayer ->
            if (otherPlayer.uniqueId == player.uniqueId) return@forEach
            
            try {
                val tabEntry = otherPlayer.tabList.getEntry(player.uniqueId)
                if (tabEntry.isPresent) {
                    tabEntry.get().setDisplayName(newDisplayName)
                    radium.logger.debug("Updated ${player.username}'s rank display in ${otherPlayer.username}'s tab")
                }
            } catch (e: Exception) {
                radium.logger.warn("Failed to update ${player.username}'s rank in ${otherPlayer.username}'s tab: ${e.message}")
            }
        }
    }

    /**
     * Completely rebuilds all players' tab lists
     */
    suspend fun rebuildAllTabLists() {
        radium.logger.info("Rebuilding all tab lists for ${radium.server.allPlayers.size} players")
        
        radium.server.allPlayers.forEach { player ->
            GlobalScope.launch {
                rebuildPlayerTabList(player)
            }
        }
    }

    /**
     * Updates a single player's tab list by rebuilding it
     */
    suspend fun updatePlayerTabList(player: Player) {
        try {
            if (!isInitialized.getOrDefault(player.uniqueId, false)) {
                // If not initialized, do a full rebuild
                rebuildPlayerTabList(player)
            } else {
                // Otherwise just refresh the existing entries
                forceTabListRefresh(player)
            }
        } catch (e: Exception) {
            radium.logger.warn("Failed to update tab list for ${player.username}: ${e.message}")
        }
    }

    /**
     * Updates tab list for all online players
     */
    suspend fun updateAllPlayersTabList() {
        val playerCount = radium.server.allPlayers.size
        radium.logger.debug("Updating tab list for all $playerCount online players")
        
        radium.server.allPlayers.forEach { player ->
            try {
                updatePlayerTabList(player)
            } catch (e: Exception) {
                radium.logger.warn("Failed to update tab list for ${player.username}: ${e.message}")
            }
        }
        
        radium.logger.debug("Completed tab list update for all players")
    }

    /**
     * Forces a complete tab list refresh for a specific player
     * This is a simpler approach that just updates existing entries
     */
    suspend fun forceTabListRefresh(targetPlayer: Player) {
        try {
            radium.logger.debug("Forcing tab list refresh for ${targetPlayer.username}")
            
            // Update all existing tab entries for this player
            radium.server.allPlayers.forEach { otherPlayer ->
                if (otherPlayer.uniqueId == targetPlayer.uniqueId) return@forEach
                
                val tabEntry = targetPlayer.tabList.getEntry(otherPlayer.uniqueId)
                if (tabEntry.isPresent) {
                    val isVanished = radium.networkVanishManager.isVanished(otherPlayer.uniqueId)
                    val shouldBeVisible = if (isVanished) {
                        radium.staffManager.canSeeVanishedPlayerSync(targetPlayer, otherPlayer)
                    } else {
                        true
                    }
                    
                    if (!shouldBeVisible) {
                        // Remove vanished players that shouldn't be visible
                        targetPlayer.tabList.removeEntry(otherPlayer.uniqueId)
                    } else {
                        // Update the display name for visible players
                        updateSinglePlayerTabEntry(targetPlayer, otherPlayer, isVanished)
                    }
                }
            }
            
            radium.logger.debug("Completed tab list refresh for ${targetPlayer.username}")
            
        } catch (e: Exception) {
            radium.logger.warn("Failed to refresh tab list for ${targetPlayer.username}: ${e.message}")
        }
    }
    
    /**
     * Updates a single player's tab entry in another player's tab list
     */
    private suspend fun updateSinglePlayerTabEntry(viewer: Player, target: Player, isTargetVanished: Boolean) {
        try {
            val tabEntry = viewer.tabList.getEntry(target.uniqueId)
            if (!tabEntry.isPresent) return
            
            val profile = radium.connectionHandler.findPlayerProfile(target.uniqueId.toString())
            
            if (profile != null) {
                val highestRank = profile.getHighestRank(radium.rankManager)
                
                // Clean all color codes to prevent corruption
                val tabPrefix = cleanColorCodes(
                    if (!highestRank?.tabPrefix.isNullOrEmpty()) {
                        highestRank?.tabPrefix!!.trim()
                    } else {
                        highestRank?.prefix?.trim() ?: ""
                    }
                )
                
                val tabSuffix = cleanColorCodes(
                    if (!highestRank?.tabSuffix.isNullOrEmpty()) {
                        highestRank?.tabSuffix!!.trim()
                    } else {
                        highestRank?.suffix?.trim() ?: ""
                    }
                )
                
                val playerColor = cleanColorCodes(highestRank?.color?.trim() ?: "&7")
                
                // Build complete display string with clean data
                val displayString = StringBuilder()
                displayString.append(tabPrefix)
                displayString.append(playerColor).append(target.username)
                displayString.append(tabSuffix)
                
                // Add vanish indicator if needed
                if (isTargetVanished) {
                    val baseDisplay = displayString.toString()
                    try {
                        val vanishIndicator = cleanColorCodes(radium.yamlFactory.getMessage("vanish.tablist_indicator"))
                        displayString.clear()
                        displayString.append(vanishIndicator).append(baseDisplay)
                    } catch (e: Exception) {
                        displayString.clear()
                        displayString.append("&7[V] ").append(baseDisplay)
                    }
                }
                
                val finalDisplayName = safeParseColoredText(displayString.toString())
                tabEntry.get().setDisplayName(finalDisplayName)
            }
            
        } catch (e: Exception) {
            radium.logger.warn("Failed to update tab entry for ${target.username} in ${viewer.username}'s tab: ${e.message}")
        }
    }

    /**
     * Ensures a player is properly visible or hidden in tab lists based on vanish state
     */
    suspend fun ensurePlayerVisibleInTabLists(player: Player) {
        try {
            val isVanished = radium.networkVanishManager.isVanished(player.uniqueId)
            radium.logger.debug("Ensuring ${player.username} visibility in tab lists (vanished: $isVanished)")
            
            // Small delay to ensure vanish state is stable
            delay(100)
            
            // Force update this player's tab list entry
            updatePlayerTabList(player)
            
            radium.logger.debug("Updated tab visibility for ${player.username}")
            
        } catch (e: Exception) {
            radium.logger.warn("Failed to ensure ${player.username} is visible in tab lists: ${e.message}")
        }
    }

    /**
     * Emergency tab list rebuild for all players - use when tab lists get corrupted
     */
    suspend fun emergencyRebuildAllTabLists() {
        radium.logger.warn("Emergency rebuild of all tab lists initiated")
        
        // Clear processing flags to allow rebuilds
        processingPlayers.clear()
        isInitialized.clear()
        
        // Rebuild each player's tab list
        radium.server.allPlayers.forEach { player ->
            try {
                rebuildPlayerTabList(player)
                radium.logger.debug("Emergency rebuild completed for ${player.username}")
            } catch (e: Exception) {
                radium.logger.error("Emergency rebuild failed for ${player.username}: ${e.message}")
            }
        }
        
        radium.logger.info("Emergency tab list rebuild completed for ${radium.server.allPlayers.size} players")
    }

    @Subscribe
    fun onPlayerConnect(event: ServerPostConnectEvent) {
        GlobalScope.launch {
            // Small delay to ensure player data is loaded
            delay(200)
            
            // Update the connecting player's tab list
            updatePlayerTabList(event.player)
            
            // Update all other players' tab lists to show/hide the new player appropriately
            updateAllPlayersTabList()
        }
    }
}
