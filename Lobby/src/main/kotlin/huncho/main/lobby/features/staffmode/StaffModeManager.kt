package huncho.main.lobby.features.staffmode

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages staff mode functionality including item management and state tracking
 */
class StaffModeManager(private val plugin: LobbyPlugin) {
    
    private val staffModeData = ConcurrentHashMap<UUID, StaffModeData>()
    private val processingOperations = Collections.synchronizedSet(mutableSetOf<String>())
    private val STAFF_MODE_TAG = Tag.Boolean("staff_mode_item")
    
    fun initialize() {
        plugin.logger.info("StaffModeManager initialized")
    }
    
    /**
     * Check if a player is in staff mode
     */
    fun isInStaffMode(player: Player): Boolean {
        return staffModeData.containsKey(player.uuid)
    }
    
    /**
     * Toggle staff mode for a player
     */
    fun toggleStaffMode(player: Player): Boolean {
        return if (isInStaffMode(player)) {
            disableStaffMode(player)
            false
        } else {
            enableStaffMode(player)
            true
        }
    }
    
    /**
     * Enable staff mode for a player
     */
    private fun enableStaffMode(player: Player) {
        try {
            // Store current inventory (only non-air items to save space)
            val hubItems = mutableListOf<ItemStack>()
            for (i in 0 until player.inventory.size) {
                val item = player.inventory.getItemStack(i)
                // Store even air items to maintain slot positions
                hubItems.add(item)
            }
            
            // Store staff mode data
            staffModeData[player.uuid] = StaffModeData(hubItems, System.currentTimeMillis())
            
            // Clear inventory and give staff items
            player.inventory.clear()
            giveStaffItems(player)
            
            player.sendMessage(
                Component.text("Staff Mode ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text("ENABLED", NamedTextColor.GREEN))
            )
            
            plugin.logger.info("${player.username} enabled staff mode - stored ${hubItems.count { !it.isAir }} items")
        } catch (e: Exception) {
            plugin.logger.error("Error enabling staff mode for ${player.username}", e)
            player.sendMessage(
                Component.text("Error enabling staff mode! Contact an administrator.", NamedTextColor.RED)
            )
        }
    }
    
    /**
     * Disable staff mode for a player
     */
    private fun disableStaffMode(player: Player) {
        val data = staffModeData.remove(player.uuid) ?: return
        
        try {
            // Clear staff items
            player.inventory.clear()
            
            // Restore hub items with better error handling
            data.hubItems.forEachIndexed { index, item ->
                try {
                    if (index < player.inventory.size && !item.isAir) {
                        player.inventory.setItemStack(index, item)
                    }
                } catch (e: Exception) {
                    plugin.logger.warn("Failed to restore item at slot $index for ${player.username}: ${e.message}")
                }
            }
            
            // Ensure the player has their join items restored if they had none
            if (data.hubItems.isEmpty() || data.hubItems.all { it.isAir }) {
                plugin.logger.debug("No hub items to restore for ${player.username}, giving default join items")
                // The join item monitor will automatically give items on next check
            }
            
            player.sendMessage(
                Component.text("Staff Mode ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("DISABLED", NamedTextColor.RED))
            )
            
            plugin.logger.info("${player.username} disabled staff mode - restored ${data.hubItems.size} items")
        } catch (e: Exception) {
            plugin.logger.error("Error disabling staff mode for ${player.username}", e)
            player.sendMessage(
                Component.text("Error disabling staff mode! Contact an administrator.", NamedTextColor.RED)
            )
        }
    }
    
    /**
     * Give staff mode items to a player
     */
    private fun giveStaffItems(player: Player) {
        val compass = ItemStack.builder(Material.COMPASS)
            .customName(Component.text("Teleport Tool", NamedTextColor.AQUA, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Right-click to teleport through walls", NamedTextColor.GRAY),
                Component.text("Right-click a player to teleport to them", NamedTextColor.GRAY),
                Component.text("Does NOT open server selector", NamedTextColor.GOLD)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
        
        // Get a custom staff head from Minecraft-Heads.com
        val staffHead = try {
            // Try to get a custom staff head, fallback to regular player head if failed
            val customHead = plugin.headsIntegration.getHeadByName("staff", "Staff Online").get()
            if (customHead != null) {
                // Modify the head with proper name and lore
                ItemStack.builder(customHead.material())
                    .amount(customHead.amount())
                    .customName(Component.text("Staff Online", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .lore(listOf(
                        Component.text("Click to view all online staff", NamedTextColor.GRAY),
                        Component.text("Shows ranks, heads, and vanish status", NamedTextColor.GRAY),
                        Component.text("Powered by Minecraft-Heads.com", NamedTextColor.DARK_GRAY)
                    ))
                    .build()
                    .withTag(STAFF_MODE_TAG, true)
            } else {
                createFallbackStaffHead()
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to load custom staff head, using fallback: ${e.message}")
            createFallbackStaffHead()
        }
        
        val netherStar = ItemStack.builder(Material.NETHER_STAR)
            .customName(Component.text("Random Teleport", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Click to teleport to a random player", NamedTextColor.GRAY)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
        
        val carpet = ItemStack.builder(Material.WHITE_CARPET)
            .customName(Component.text("Better View", NamedTextColor.WHITE, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Hold this item for better fullscreen view", NamedTextColor.GRAY),
                Component.text("Perfect for screenshots", NamedTextColor.GRAY),
                Component.text("Does NOT teleport - just hold it", NamedTextColor.GOLD)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
        
        val book = ItemStack.builder(Material.BOOK)
            .customName(Component.text("Inspect Inventory", NamedTextColor.GOLD, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Right-click a player to inspect their inventory", NamedTextColor.GRAY),
                Component.text("Read-only unless you have invsee permission", NamedTextColor.GRAY)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
        
        val ice = ItemStack.builder(Material.ICE)
            .customName(Component.text("Freeze Tool", NamedTextColor.AQUA, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Right-click a player to freeze/unfreeze", NamedTextColor.GRAY),
                Component.text("Same functionality as /freeze command", NamedTextColor.GRAY)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
        
        // Vanish dye - color changes based on current vanish status
        val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(player.uuid)
        val vanishDye = if (isVanished) {
            ItemStack.builder(Material.LIME_DYE)
                .customName(Component.text("Vanish: ON", NamedTextColor.GREEN, TextDecoration.BOLD))
                .lore(listOf(
                    Component.text("You are currently vanished", NamedTextColor.GREEN),
                    Component.text("Click to become visible", NamedTextColor.GRAY)
                ))
                .build()
                .withTag(STAFF_MODE_TAG, true)
        } else {
            ItemStack.builder(Material.GRAY_DYE)
                .customName(Component.text("Vanish: OFF", NamedTextColor.GRAY, TextDecoration.BOLD))
                .lore(listOf(
                    Component.text("You are currently visible", NamedTextColor.RED),
                    Component.text("Click to vanish", NamedTextColor.GRAY)
                ))
                .build()
                .withTag(STAFF_MODE_TAG, true)
        }
        
        // Set items in specific slots
        player.inventory.setItemStack(0, compass)
        player.inventory.setItemStack(1, staffHead)
        player.inventory.setItemStack(2, netherStar)
        player.inventory.setItemStack(3, carpet)
        player.inventory.setItemStack(4, book)
        player.inventory.setItemStack(6, ice)
        player.inventory.setItemStack(8, vanishDye)
    }
    
    /**
     * Update vanish dye for a player in staff mode
     */
    fun updateVanishDye(player: Player) {
        if (!isInStaffMode(player)) return
        
        val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(player.uuid)
        val vanishDye = if (isVanished) {
            ItemStack.builder(Material.LIME_DYE)
                .customName(Component.text("Vanish: ON", NamedTextColor.GREEN, TextDecoration.BOLD))
                .lore(listOf(
                    Component.text("You are currently vanished", NamedTextColor.GREEN),
                    Component.text("Click to become visible", NamedTextColor.GRAY)
                ))
                .build()
                .withTag(STAFF_MODE_TAG, true)
        } else {
            ItemStack.builder(Material.GRAY_DYE)
                .customName(Component.text("Vanish: OFF", NamedTextColor.GRAY, TextDecoration.BOLD))
                .lore(listOf(
                    Component.text("You are currently visible", NamedTextColor.RED),
                    Component.text("Click to vanish", NamedTextColor.GRAY)
                ))
                .build()
                .withTag(STAFF_MODE_TAG, true)
        }
        
        player.inventory.setItemStack(8, vanishDye)
    }
    
    /**
     * Check if an item is a staff mode item
     */
    fun isStaffModeItem(item: ItemStack): Boolean {
        return item.hasTag(STAFF_MODE_TAG) && item.getTag(STAFF_MODE_TAG) == true
    }
    
    /**
     * Handle staff mode item interactions
     */
    fun handleStaffModeItemClick(player: Player, item: ItemStack, target: Player? = null): Boolean {
        if (!isStaffModeItem(item)) return false
        
        when (item.material()) {
            Material.COMPASS -> handleCompassClick(player, target)
            Material.PLAYER_HEAD -> handleStaffListClick(player)
            Material.NETHER_STAR -> handleRandomTeleportClick(player)
            Material.WHITE_CARPET -> handleBetterViewClick(player)
            Material.BOOK -> handleInspectClick(player, target)
            Material.ICE -> {
                // Only handle freeze if there's a target player
                if (target != null) {
                    handleFreezeClick(player, target)
                } else {
                    player.sendMessage(
                        Component.text("Right-click a player to freeze/unfreeze them", NamedTextColor.RED)
                    )
                }
            }
            Material.LIME_DYE, Material.GRAY_DYE -> handleVanishToggle(player)
            else -> return false
        }
        
        return true
    }
    
    private fun handleCompassClick(player: Player, target: Player?) {
        if (target != null) {
            // Teleport to target player
            player.teleport(target.position)
            player.sendMessage(
                Component.text("Teleported to ", NamedTextColor.GREEN)
                    .append(Component.text(target.username, NamedTextColor.YELLOW))
            )
        } else {
            // Teleport through walls functionality - teleport in the direction player is looking
            val direction = player.position.direction()
            val teleportDistance = 5.0 // Distance to teleport forward
            val newPosition = player.position.add(
                direction.x() * teleportDistance,
                direction.y() * teleportDistance,
                direction.z() * teleportDistance
            )
            
            player.teleport(newPosition)
            player.sendMessage(
                Component.text("Teleported through wall", NamedTextColor.GREEN)
            )
        }
    }
    
    private fun handleStaffListClick(player: Player) {
        // Open staff online GUI
        val staffOnlineGUI = huncho.main.lobby.gui.staff.StaffOnlineGUI(plugin)
        staffOnlineGUI.openStaffGUI(player)
    }
    
    private fun handleRandomTeleportClick(player: Player) {
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers.filter { it != player }
        if (onlinePlayers.isEmpty()) {
            player.sendMessage(
                Component.text("No players online to teleport to", NamedTextColor.RED)
            )
            return
        }
        
        val randomPlayer = onlinePlayers.random()
        player.teleport(randomPlayer.position)
        player.sendMessage(
            Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(randomPlayer.username, NamedTextColor.YELLOW))
        )
    }
    
    private fun handleBetterViewClick(player: Player) {
        // Better view tool - just hold it, no teleporting
        player.sendMessage(
            Component.text("Hold this item for better fullscreen view!", NamedTextColor.GREEN)
        )
        // Note: The actual better view functionality would need client-side modifications
        // This tool is just meant to be held for immersion
    }
    
    private fun handleInspectClick(player: Player, target: Player?) {
        if (target == null) {
            player.sendMessage(
                Component.text("Right-click a player to inspect their inventory", NamedTextColor.RED)
            )
            return
        }
        
        // Open the target's inventory for inspection
        val targetInventory = Inventory(InventoryType.CHEST_6_ROW, Component.text("${target.username}'s Inventory"))
        
        // Copy the target's inventory contents
        for (slot in 0 until target.inventory.size) {
            val item = target.inventory.getItemStack(slot)
            if (!item.isAir) {
                targetInventory.setItemStack(slot, item)
            }
        }
        
        // Open the inventory for the staff member
        player.openInventory(targetInventory)
        player.sendMessage(
            Component.text("Inspecting ", NamedTextColor.GREEN)
                .append(Component.text(target.username + "'s", NamedTextColor.YELLOW))
                .append(Component.text(" inventory", NamedTextColor.GREEN))
        )
    }
    
    private fun handleFreezeClick(player: Player, target: Player?) {
        if (target == null) {
            player.sendMessage(
                Component.text("Right-click a player to freeze/unfreeze them", NamedTextColor.RED)
            )
            return
        }
        
        // Prevent concurrent freeze/unfreeze operations on the same target
        val targetId = target.uuid.toString()
        if (processingOperations.contains(targetId)) {
            player.sendMessage(
                Component.text("A freeze operation is already in progress for ${target.username}", NamedTextColor.YELLOW)
            )
            return
        }
        
        processingOperations.add(targetId)
        
        // Use the freeze manager to toggle freeze
        GlobalScope.launch {
            try {
                if (plugin.freezeManager.isFrozen(target.uuid)) {
                    val result = plugin.freezeManager.unfreezePlayer(player, target.username)
                    if (result.isFailure) {
                        player.sendMessage(
                            Component.text("Failed to unfreeze ${target.username}: ${result.exceptionOrNull()?.message}", NamedTextColor.RED)
                        )
                    }
                } else {
                    val result = plugin.freezeManager.freezePlayer(player, target.username)
                    if (result.isFailure) {
                        player.sendMessage(
                            Component.text("Failed to freeze ${target.username}: ${result.exceptionOrNull()?.message}", NamedTextColor.RED)
                        )
                    }
                }
            } finally {
                processingOperations.remove(targetId)
            }
        }
    }
    
    private fun handleVanishToggle(player: Player) {
        // Send vanish toggle request to Radium
        GlobalScope.launch {
            try {
                val currentlyVanished = plugin.vanishPluginMessageListener.isPlayerVanished(player.uuid)
                plugin.logger.debug("Handling vanish toggle for ${player.username}, currently vanished: $currentlyVanished")
                
                // Send toggle request to Radium
                plugin.radiumIntegration.sendVanishToggle(player.uuid)
                    .thenAccept { success ->
                        plugin.logger.debug("Vanish toggle response for ${player.username}: $success")
                        if (success) {
                            // Toggle the state - note the logic is inverted because we're showing the NEW state
                            if (currentlyVanished) {
                                player.sendMessage(
                                    Component.text("Vanish ", NamedTextColor.RED, TextDecoration.BOLD)
                                        .append(Component.text("DISABLED", NamedTextColor.RED))
                                )
                            } else {
                                player.sendMessage(
                                    Component.text("Vanish ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                        .append(Component.text("ENABLED", NamedTextColor.GREEN))
                                )
                            }
                            
                            // Update the vanish dye after a longer delay to ensure state has changed
                            MinecraftServer.getSchedulerManager().scheduleTask({
                                updateVanishDye(player)
                            }, TaskSchedule.tick(10), TaskSchedule.stop())
                        } else {
                            player.sendMessage(
                                Component.text("Failed to toggle vanish - check server connection", NamedTextColor.RED)
                            )
                            plugin.logger.warn("Vanish toggle failed for ${player.username} - Radium response was false")
                        }
                    }
                    .exceptionally { throwable ->
                        player.sendMessage(
                            Component.text("Error toggling vanish: ${throwable.message}", NamedTextColor.RED)
                        )
                        plugin.logger.error("Exception during vanish toggle for ${player.username}", throwable)
                        null
                    }
            } catch (e: Exception) {
                player.sendMessage(
                    Component.text("Error toggling vanish: ${e.message}", NamedTextColor.RED)
                )
                plugin.logger.error("Error toggling vanish for ${player.username}", e)
            }
        }
    }
    
    /**
     * Force disable staff mode for a player (e.g., on disconnect)
     */
    fun forceDisableStaffMode(player: Player) {
        if (isInStaffMode(player)) {
            staffModeData.remove(player.uuid)
            plugin.logger.info("Force disabled staff mode for ${player.username}")
        }
    }
    
    /**
     * Get staff mode data for a player
     */
    fun getStaffModeData(player: Player): StaffModeData? {
        return staffModeData[player.uuid]
    }
    
    /**
     * Create a fallback staff head when custom head loading fails
     */
    private fun createFallbackStaffHead(): ItemStack {
        return ItemStack.builder(Material.PLAYER_HEAD)
            .customName(Component.text("Staff Online", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .lore(listOf(
                Component.text("Click to view all online staff", NamedTextColor.GRAY),
                Component.text("Shows ranks, heads, and vanish status", NamedTextColor.GRAY)
            ))
            .build()
            .withTag(STAFF_MODE_TAG, true)
    }
    
    /**
     * Shutdown the staff mode manager
     */
    fun shutdown() {
        // Clear all staff mode players
        staffModeData.clear()
        
        plugin.logger.info("StaffModeManager shutdown complete")
    }
    
    /**
     * Get the staff mode item slots mapping
     */
    fun getStaffModeItemSlots(): Map<Int, String> {
        return mapOf(
            0 to "compass",     // Teleport
            1 to "staff_head",  // Better View
            2 to "nether_star", // Staff Online
            3 to "carpet",      // Freeze Tool
            4 to "book",        // Inspect
            6 to "ice",         // Random Teleport
            8 to "vanish_dye"   // Vanish Toggle
        )
    }
}

/**
 * Data class to store staff mode information
 */
data class StaffModeData(
    val hubItems: List<ItemStack>,
    val enabledAt: Long
)
