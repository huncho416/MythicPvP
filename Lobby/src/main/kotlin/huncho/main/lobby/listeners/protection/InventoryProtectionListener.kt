package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryPreClickEvent

import net.minestom.server.item.Material

class InventoryProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<InventoryPreClickEvent> {
    
    override fun eventType(): Class<InventoryPreClickEvent> = InventoryPreClickEvent::class.java
    
    override fun run(event: InventoryPreClickEvent): EventListener.Result {
        val player = event.player
        val clickedItem = event.clickedItem
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
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
                    // Block manipulation of hub items in InventoryPreClickEvent
                    return EventListener.Result.INVALID
                }
            }
            
            // For hotbar slots with hub items, block any modifications
            if (slot in 0..8) {
                val itemInSlot = player.inventory.getItemStack(slot)
                if (itemInSlot != null && itemInSlot.material() != Material.AIR) {
                    if (monitor.isJoinItemByMaterial(itemInSlot.material())) {
                        // Block any pre-click event on hub items
                        return EventListener.Result.INVALID
                    }
                }
            }
        } else {
            // This is a custom inventory (GUI) - allow all interactions
            return EventListener.Result.SUCCESS
        }
        
        return EventListener.Result.SUCCESS
    }
}
