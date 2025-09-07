package huncho.main.lobby.listeners.features

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent

class StaffModeListener(private val plugin: LobbyPlugin) : EventListener<PlayerUseItemEvent> {
    
    override fun eventType() = PlayerUseItemEvent::class.java
    
    override fun run(event: PlayerUseItemEvent): EventListener.Result {
        val player = event.player
        val item = event.itemStack
        
        // Check if player is in staff mode and using a staff mode item
        if (plugin.staffModeManager.isInStaffMode(player) && 
            plugin.staffModeManager.isStaffModeItem(item)) {
            
            // Only handle the interaction if there's no entity target
            // Entity interactions will be handled by StaffModeEntityInteractListener
            // This prevents double execution when right-clicking players
            plugin.staffModeManager.handleStaffModeItemClick(player, item, null)
            
            // Cancel the event to prevent normal item usage
            return EventListener.Result.INVALID
        }
        
        return EventListener.Result.SUCCESS
    }
}

class StaffModeEntityInteractListener(private val plugin: LobbyPlugin) : EventListener<PlayerEntityInteractEvent> {
    
    override fun eventType() = PlayerEntityInteractEvent::class.java
    
    override fun run(event: PlayerEntityInteractEvent): EventListener.Result {
        val player = event.player
        val target = event.target
        
        // Check if target is a player and player is in staff mode
        if (target is Player && plugin.staffModeManager.isInStaffMode(player)) {
            val heldItem = player.itemInMainHand
            
            if (plugin.staffModeManager.isStaffModeItem(heldItem)) {
                // Handle staff mode item interaction with target player
                plugin.staffModeManager.handleStaffModeItemClick(player, heldItem, target)
                
                // Cancel the event to prevent normal interaction
                return EventListener.Result.INVALID
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}

class StaffModeDisconnectListener(private val plugin: LobbyPlugin) : EventListener<PlayerDisconnectEvent> {
    
    override fun eventType() = PlayerDisconnectEvent::class.java
    
    override fun run(event: PlayerDisconnectEvent): EventListener.Result {
        val player = event.player
        
        // Force disable staff mode when player disconnects
        plugin.staffModeManager.forceDisableStaffMode(player)
        
        return EventListener.Result.SUCCESS
    }
}

class StaffModeInventoryListener(private val plugin: LobbyPlugin) : EventListener<InventoryPreClickEvent> {
    
    override fun eventType() = InventoryPreClickEvent::class.java
    
    override fun run(event: InventoryPreClickEvent): EventListener.Result {
        val player = event.player
        
        // Check if player is in staff mode
        if (plugin.staffModeManager.isInStaffMode(player)) {
            // Check if this is the player's own inventory (staff mode items)
            if (event.inventory == player.inventory) {
                // Allow clicking on staff mode items but prevent moving them
                val clickedSlot = event.slot
                val clickedItem = event.clickedItem
                
                // Check if they're trying to interact with a staff mode item
                if (clickedItem != null && plugin.staffModeManager.isStaffModeItem(clickedItem)) {
                    // Allow interaction with staff mode items - they'll be handled by the use item event
                    // The main protection is preventing placement of other items in staff slots
                }
                
                // Block any attempts to place items in staff mode inventory slots
                if (clickedSlot in 0..8) { // Hotbar slots where staff items are
                    val staffModeItems = plugin.staffModeManager.getStaffModeItemSlots()
                    if (staffModeItems.containsKey(clickedSlot)) {
                        // This slot has a staff mode item, don't allow changes except for clicks
                        val clickedItem = event.clickedItem
                        if (clickedItem != null && plugin.staffModeManager.isStaffModeItem(clickedItem)) {
                            // Allow clicking staff mode items for their functionality
                            return EventListener.Result.SUCCESS
                        } else {
                            // Don't allow placing non-staff items in staff slots
                            return EventListener.Result.INVALID
                        }
                    }
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}

class StaffModeDropItemListener(private val plugin: LobbyPlugin) : EventListener<ItemDropEvent> {
    
    override fun eventType() = ItemDropEvent::class.java
    
    override fun run(event: ItemDropEvent): EventListener.Result {
        val player = event.player
        val droppedItem = event.itemStack
        
        // Prevent dropping staff mode items
        if (plugin.staffModeManager.isInStaffMode(player) && 
            plugin.staffModeManager.isStaffModeItem(droppedItem)) {
            return EventListener.Result.INVALID
        }
        
        return EventListener.Result.SUCCESS
    }
}
