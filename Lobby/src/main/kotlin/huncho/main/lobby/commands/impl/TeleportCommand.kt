package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class TeleportCommand(private val plugin: LobbyPlugin) : Command("teleport", "tp") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "hub.command.teleport") ||
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleport") {
                val targetName = context.get(playerArg)
                
                // Find target player
                val target = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.username.equals(targetName, ignoreCase = true) }
                
                if (target == null) {
                    player.sendMessage("§cPlayer '$targetName' not found or is not online!")
                    return@checkPermissionAndExecute
                }
                
                if (target == player) {
                    player.sendMessage("§cYou cannot teleport to yourself!")
                    return@checkPermissionAndExecute
                }
                
                // Teleport to target
                val targetPos = target.position
                player.teleport(targetPos)
                
                player.sendMessage("§a§lTeleported!")
                player.sendMessage("§7You have been teleported to ${target.username}")
                
                // Notify target (optional)
                target.sendMessage("§7${player.username} teleported to you")
            }
        }, playerArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleport") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/teleport <player>")
                player.sendMessage("§7/tp <player>")
                player.sendMessage("§7Teleport yourself to another player")
            }
        }
    }
}
