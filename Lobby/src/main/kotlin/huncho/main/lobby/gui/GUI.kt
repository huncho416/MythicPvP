package huncho.main.lobby.gui

import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple custom GUI system for Minestom inventories
 */
abstract class GUI(
    protected val title: Component,
    protected val size: Int = 54,
    protected val inventoryType: InventoryType = InventoryType.CHEST_6_ROW
) {
    
    protected val inventory: Inventory = Inventory(inventoryType, title)
    protected val clickHandlers = ConcurrentHashMap<Int, (Player, ItemStack?) -> Unit>()
    protected val viewers = mutableSetOf<Player>()
    
    companion object {
        private val openGuis = ConcurrentHashMap<UUID, GUI>()
        
        fun getPlayerGui(player: Player): GUI? = openGuis[player.uuid]
        fun removePlayerGui(player: Player) = openGuis.remove(player.uuid)
    }
    
    init {
        setupInventory()
    }
    
    /**
     * Setup the inventory contents - override this method
     */
    abstract fun setupInventory()
    
    /**
     * Set an item in a specific slot with a click handler
     */
    protected fun setItem(slot: Int, item: ItemStack, clickHandler: ((Player, ItemStack?) -> Unit)? = null) {
        inventory.setItemStack(slot, item)
        if (clickHandler != null) {
            clickHandlers[slot] = clickHandler
        }
    }
    
    /**
     * Fill empty slots with a filler item
     */
    protected fun fillEmptySlots(fillerItem: ItemStack) {
        for (i in 0 until inventory.size) {
            if (inventory.getItemStack(i).isAir) {
                inventory.setItemStack(i, fillerItem)
            }
        }
    }
    
    /**
     * Open the GUI for a player
     */
    fun open(player: Player) {
        // Close any existing GUI
        close(player)
        
        // Track this GUI
        openGuis[player.uuid] = this
        viewers.add(player)
        
        // Open inventory
        player.openInventory(inventory)
    }
    
    /**
     * Close the GUI for a player
     */
    fun close(player: Player) {
        openGuis.remove(player.uuid)
        viewers.remove(player)
        player.closeInventory()
    }
    
    /**
     * Handle inventory click
     */
    fun handleClick(player: Player, slot: Int, clickedItem: ItemStack?) {
        val handler = clickHandlers[slot]
        if (handler != null) {
            try {
                handler(player, clickedItem)
            } catch (e: Exception) {
                player.sendMessage("§cAn error occurred while processing your click!")
            }
        }
    }
    
    /**
     * Update the GUI contents
     */
    fun refresh() {
        inventory.clear()
        clickHandlers.clear()
        setupInventory()
    }
}
