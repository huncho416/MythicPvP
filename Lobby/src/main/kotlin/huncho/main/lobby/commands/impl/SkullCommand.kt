package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class SkullCommand(private val plugin: LobbyPlugin) : Command("skull", "head") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.skull")
        }
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.skull") {
                val targetName = context.get(playerArg)
                
                // Find target player (online or offline)
                val target = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.username.equals(targetName, ignoreCase = true) }
                
                val finalTargetName = target?.username ?: targetName
                
                // Create player head
                val skull = ItemStack.builder(Material.PLAYER_HEAD)
                    .customName(Component.text("${finalTargetName}'s Head", NamedTextColor.YELLOW))
                    .lore(listOf(
                        Component.text("Player: $finalTargetName", NamedTextColor.GRAY),
                        Component.text("Obtained via /skull command", NamedTextColor.DARK_GRAY)
                    ))
                    .build()
                
                // TODO: Set skull texture using minecraft-heads-minestom library
                // For now, give basic player head
                
                // Try to add to player's inventory
                val added = player.inventory.addItemStack(skull)
                
                if (added) {
                    player.sendMessage("§a§lSkull Obtained!")
                    player.sendMessage("§7You received ${finalTargetName}'s head")
                } else {
                    player.sendMessage("§c§lInventory Full!")
                    player.sendMessage("§7Could not give ${finalTargetName}'s head - inventory is full")
                }
                
                plugin.logger.info("${player.username} obtained ${finalTargetName}'s skull")
            }
        }, playerArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.skull") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/skull <player>")
                player.sendMessage("§7Get a player's head/skull")
                player.sendMessage("§7Works with online and offline players")
            }
        }
    }
}
