package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack

class MoreCommand(private val plugin: LobbyPlugin) : Command("more") {
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.more")
        }
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.more") {
                val heldItem = player.itemInMainHand
                if (heldItem.isAir) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7You must be holding an item!")
                    return@checkPermissionAndExecute
                }
                
                val maxStackSize = heldItem.material().maxStackSize()
                if (maxStackSize <= 1) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7This item cannot be stacked!")
                    return@checkPermissionAndExecute
                }
                
                // Set to max stack size
                val maxedItem = heldItem.withAmount(maxStackSize)
                player.setItemInMainHand(maxedItem)
                
                player.sendMessage("§a§lMore Success!")
                player.sendMessage("§7Your item stack has been set to maximum size (§e$maxStackSize§7)")
            }
        }
    }
}
