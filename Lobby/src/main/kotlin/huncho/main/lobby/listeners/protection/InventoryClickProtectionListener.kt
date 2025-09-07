package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.item.Material

/**
 * Comprehensive protection for inventory clicks (after InventoryPreClickEvent)
 * This handles all types of inventory manipulation including drag and drop, shift click, etc.
 */
class InventoryClickProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<InventoryClickEvent> {
    
    override fun eventType(): Class<InventoryClickEvent> = InventoryClickEvent::class.java
    
    override fun run(event: InventoryClickEvent): EventListener.Result {
        val player = event.player
        
        plugin.logger.info("[PROTECTION DEBUG] InventoryClickEvent from ${player.username} - Slot: ${event.slot}, Item: ${event.clickedItem?.material()}")
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) {
                plugin.logger.info("[PROTECTION DEBUG] ${player.username} has bypass permission")
                return EventListener.Result.SUCCESS
            }
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }

        // Check if this is a player's inventory interaction
        if (event.inventory == null || event.inventory == player.inventory) {
            val clickedItem = event.clickedItem
            val slot = event.slot
            
            // Block direct manipulation of hub items 
            if (clickedItem != null && clickedItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(clickedItem.material())) {
                    // Block manipulation of hub items in InventoryClickEvent
                    plugin.logger.info("[PROTECTION DEBUG] BLOCKING manipulation of hub item ${clickedItem.material()} from ${player.username}")
                    return EventListener.Result.INVALID
                }
                
                // Block manipulation of staff mode items
                if (plugin.staffModeManager.isStaffModeItem(clickedItem)) {
                    // Block manipulation of staff mode items (like the staff list book)
                    return EventListener.Result.INVALID
                }
            }
            
            // Block any slot interactions with hub items (covers shift-click, drag, etc)
            if (slot >= 0) { // Cover entire player inventory and equipment slots
                val itemInSlot = player.inventory.getItemStack(slot)
                if (itemInSlot != null && itemInSlot.material() != Material.AIR) {
                    if (monitor.isJoinItemByMaterial(itemInSlot.material())) {
                        // Block any click event on hub items
                        return EventListener.Result.INVALID
                    }
                    
                    // Block manipulation of staff mode items
                    if (plugin.staffModeManager.isStaffModeItem(itemInSlot)) {
                        // Block any click event on staff mode items
                        return EventListener.Result.INVALID
                    }
                }
            }
            
            // Block cursor item interactions if it's a hub item
            val cursorItem = event.cursorItem
            if (cursorItem != null && cursorItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(cursorItem.material())) {
                    // Block cursor interactions with hub items
                    return EventListener.Result.INVALID
                }
                
                if (plugin.staffModeManager.isStaffModeItem(cursorItem)) {
                    // Block cursor interactions with staff mode items
                    return EventListener.Result.INVALID
                }
            }
        } else {
            // This is a custom inventory (GUI) - allow all interactions unless it's trying to move hub items
            val clickedItem = event.clickedItem
            if (clickedItem != null && clickedItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(clickedItem.material())) {
                    // Even in GUIs, prevent taking hub items
                    return EventListener.Result.INVALID
                }
                
                if (plugin.staffModeManager.isStaffModeItem(clickedItem)) {
                    // Even in GUIs, prevent taking staff mode items
                    return EventListener.Result.INVALID
                }
            }
            
            // Block cursor item interactions in GUIs if it's a hub item
            val cursorItem = event.cursorItem
            if (cursorItem != null && cursorItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(cursorItem.material())) {
                    // Block putting hub items into GUIs
                    return EventListener.Result.INVALID
                }
                
                if (plugin.staffModeManager.isStaffModeItem(cursorItem)) {
                    // Block putting staff mode items into GUIs
                    return EventListener.Result.INVALID
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
