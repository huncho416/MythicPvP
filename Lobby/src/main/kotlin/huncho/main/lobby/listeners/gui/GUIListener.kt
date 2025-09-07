package huncho.main.lobby.listeners.gui

import huncho.main.lobby.gui.GUI
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.inventory.InventoryCloseEvent

/**
 * Handles GUI interactions for custom GUI system
 */
class GUIListener : EventListener<InventoryPreClickEvent> {
    
    override fun eventType(): Class<InventoryPreClickEvent> = InventoryPreClickEvent::class.java
    
    override fun run(event: InventoryPreClickEvent): EventListener.Result {
        val player = event.player
        val slot = event.slot
        val clickedItem = event.clickedItem
        
        // Check if player has a GUI open
        val gui = GUI.getPlayerGui(player)
        if (gui != null) {
            // Handle the click through our GUI system
            gui.handleClick(player, slot, clickedItem)
            
            // Cancel the event to prevent item movement
            event.isCancelled = true
            return EventListener.Result.INVALID
        }
        
        return EventListener.Result.SUCCESS
    }
}

/**
 * Handles GUI close events
 */
class GUICloseListener : EventListener<InventoryCloseEvent> {
    
    override fun eventType(): Class<InventoryCloseEvent> = InventoryCloseEvent::class.java
    
    override fun run(event: InventoryCloseEvent): EventListener.Result {
        val player = event.player as? Player ?: return EventListener.Result.SUCCESS
        
        // Remove the GUI tracking when inventory is closed
        GUI.removePlayerGui(player)
        
        return EventListener.Result.SUCCESS
    }
}
