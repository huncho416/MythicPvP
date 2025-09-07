package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.item.Material

/**
 * Aggressive hotbar swap protection that blocks ANY attempt to swap items
 * to/from hotbar slots that contain hub items, regardless of click type
 */
class HotbarSwapProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<InventoryClickEvent> {
    
    override fun eventType(): Class<InventoryClickEvent> = InventoryClickEvent::class.java
    
    override fun run(event: InventoryClickEvent): EventListener.Result {
        val player = event.player
        
        // Skip protection for admins
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }

        val clickType = event.clickType
        val slot = event.slot
        
        // Check if any hotbar slot contains a hub item or staff mode item
        val hubItemSlots = mutableSetOf<Int>()
        for (hotbarSlot in 0..8) {
            val hotbarItem = player.inventory.getItemStack(hotbarSlot)
            if (hotbarItem != null && hotbarItem.material() != Material.AIR) {
                if (monitor.isJoinItemByMaterial(hotbarItem.material()) ||
                    plugin.staffModeManager.isStaffModeItem(hotbarItem)) {
                    hubItemSlots.add(hotbarSlot)
                }
            }
        }
        
        if (hubItemSlots.isNotEmpty()) {
            // Block ALL interactions that could move items to/from hub item slots
            when {
                // For any inventory interaction
                event.inventory == null || event.inventory == player.inventory -> {
                    // Block interactions with hub item slots (except right-click for functionality)
                    if (slot in hubItemSlots && clickType != ClickType.RIGHT_CLICK) {
                        return EventListener.Result.INVALID
                    }
                    
                    // Block ANY click type that could potentially swap with hotbar
                    if (clickType != ClickType.LEFT_CLICK && clickType != ClickType.RIGHT_CLICK) {
                        return EventListener.Result.INVALID
                    }
                }
                
                // For menu/GUI interactions - block number key types
                else -> {
                    // This is a menu/GUI interaction
                    if (clickType != ClickType.LEFT_CLICK && clickType != ClickType.RIGHT_CLICK) {
                        return EventListener.Result.INVALID
                    }
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
