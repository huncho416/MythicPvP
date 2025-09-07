package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.item.Material

/**
 * Enhanced protection against number key swapping (1-9 keys) for hub items
 * This listener specifically handles InventoryClickEvent with number key types
 */
class NumberKeyProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<InventoryClickEvent> {
    
    override fun eventType(): Class<InventoryClickEvent> = InventoryClickEvent::class.java
    
    override fun run(event: InventoryClickEvent): EventListener.Result {
        val player = event.player
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }

        // Only handle player's own inventory interactions
        if (event.inventory == null || event.inventory == player.inventory) {
            val clickType = event.clickType
            val slot = event.slot
            
            // Always block number key clicks when there are hub items in hotbar
            if (isNumberKeyClick(clickType)) {
                // Check if any hotbar slot contains a hub item or staff mode item
                val hasHubItemInHotbar = (0..8).any { hotbarSlot ->
                    val hotbarItem = player.inventory.getItemStack(hotbarSlot)
                    hotbarItem != null && hotbarItem.material() != Material.AIR && 
                    (monitor.isJoinItemByMaterial(hotbarItem.material()) ||
                     plugin.staffModeManager.isStaffModeItem(hotbarItem))
                }
                
                if (hasHubItemInHotbar) {
                    return EventListener.Result.INVALID
                }
            }
            
            // Block direct manipulation of hub items and staff mode items (except right-click for functionality)
            if (slot in 0..8) { // Hotbar slots
                val itemInSlot = player.inventory.getItemStack(slot)
                if (itemInSlot != null && itemInSlot.material() != Material.AIR) {
                    if (monitor.isJoinItemByMaterial(itemInSlot.material()) ||
                        plugin.staffModeManager.isStaffModeItem(itemInSlot)) {
                        // Only allow right-click for hub item functionality
                        if (clickType != ClickType.RIGHT_CLICK) {
                            return EventListener.Result.INVALID
                        }
                    }
                }
            }
            
            // Block manipulation of clicked hub items and staff mode items
            val clickedItem = event.clickedItem
            if (clickedItem != null && clickedItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(clickedItem.material()) ||
                    plugin.staffModeManager.isStaffModeItem(clickedItem)) {
                    if (clickType != ClickType.RIGHT_CLICK) {
                        return EventListener.Result.INVALID
                    }
                }
            }
        } else {
            // This is a custom inventory (GUI/menu) - still need to block number key swaps
            val clickType = event.clickType
            
            // CRITICAL: Block number key interactions even in menus if hub items or staff mode items are in hotbar
            if (isNumberKeyClick(clickType)) {
                // Check if any hotbar slot contains a hub item or staff mode item
                val hasHubItemInHotbar = (0..8).any { hotbarSlot ->
                    val hotbarItem = player.inventory.getItemStack(hotbarSlot)
                    hotbarItem != null && hotbarItem.material() != Material.AIR && 
                    (monitor.isJoinItemByMaterial(hotbarItem.material()) ||
                     plugin.staffModeManager.isStaffModeItem(hotbarItem))
                }
                
                
                if (hasHubItemInHotbar) {
                    return EventListener.Result.INVALID
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Check if the click type represents a number key press (1-9)
     * This is a simple approach that checks for known problematic click types
     */
    private fun isNumberKeyClick(clickType: ClickType): Boolean {
        // Number keys in Minecraft generate different click types
        // Block any click type that's not a standard left/right click when hub items are present
        return when (clickType) {
            ClickType.LEFT_CLICK, ClickType.RIGHT_CLICK -> false
            else -> true // Block all other click types when hub items are present
        }
    }
}
