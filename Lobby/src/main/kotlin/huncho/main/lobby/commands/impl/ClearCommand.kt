package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack

class ClearCommand(private val plugin: LobbyPlugin) : Command("clear") {
    
    private val playerArg = ArgumentType.Word("player").setDefaultValue("")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.clear.self") ||
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.clear.others")
        }
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            val targetName = context.get(playerArg)
            
            if (targetName.isEmpty()) {
                // Clear own inventory
                player.checkPermissionAndExecute("hub.command.clear.self") {
                    clearPlayerInventory(player)
                    player.sendMessage("§a§lInventory Cleared!")
                    player.sendMessage("§7Your inventory has been cleared.")
                }
            } else {
                // Clear another player's inventory
                player.checkPermissionAndExecute("hub.command.clear.others") {
                    val target = MinecraftServer.getConnectionManager().onlinePlayers.find { 
                        it.username.equals(targetName, ignoreCase = true) 
                    }
                    
                    if (target == null) {
                        player.sendMessage("§c§lPlayer Not Found!")
                        player.sendMessage("§7Player §e$targetName §7is not online!")
                        return@checkPermissionAndExecute
                    }
                    
                    clearPlayerInventory(target)
                    
                    player.sendMessage("§a§lInventory Cleared!")
                    player.sendMessage("§7Cleared inventory of §e${target.username}")
                    
                    target.sendMessage("§c§lInventory Cleared!")
                    target.sendMessage("§7Your inventory has been cleared by §e${player.username}")
                }
            }
        }, playerArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            // Clear own inventory by default
            player.checkPermissionAndExecute("hub.command.clear.self") {
                clearPlayerInventory(player)
                player.sendMessage("§a§lInventory Cleared!")
                player.sendMessage("§7Your inventory has been cleared.")
            }
        }
    }
    
    private fun clearPlayerInventory(player: Player) {
        // Clear main inventory
        for (i in 0 until player.inventory.size) {
            player.inventory.setItemStack(i, ItemStack.AIR)
        }
        
        // Clear armor slots (Minestom doesn't have separate armor methods)
        // These are handled by the main inventory clear above
        
        // Clear off-hand (also handled by main inventory)
    }
}
