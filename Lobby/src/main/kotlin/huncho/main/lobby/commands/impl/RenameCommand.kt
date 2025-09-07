package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class RenameCommand(private val plugin: LobbyPlugin) : Command("rename") {
    
    private val nameArg = ArgumentType.StringArray("name")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.rename")
        }
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.rename") {
                val newName = context.get(nameArg).joinToString(" ")
                
                val heldItem = player.itemInMainHand
                if (heldItem.isAir) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7You must be holding an item to rename!")
                    return@checkPermissionAndExecute
                }
                
                // Create renamed item
                val renamedItem = heldItem.withCustomName(
                    Component.text(newName, NamedTextColor.WHITE)
                )
                
                // Replace item in hand
                player.setItemInMainHand(renamedItem)
                
                player.sendMessage("§a§lItem Renamed!")
                player.sendMessage("§7Your item has been renamed to: §f$newName")
            }
        }, nameArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.rename") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/rename <name...>")
                player.sendMessage("§7Example: /rename My Awesome Sword")
                player.sendMessage("§7Note: You must be holding the item you want to rename")
            }
        }
    }
}
