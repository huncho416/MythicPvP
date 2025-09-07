package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class TeleportHereCommand(private val plugin: LobbyPlugin) : Command("teleporthere", "tphere") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "hub.command.teleporthere") ||
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
            player.checkPermissionAndExecute("hub.command.teleporthere") {
                val targetName = context.get(playerArg)
                
                // Find target player
                val target = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.username.equals(targetName, ignoreCase = true) }
                
                if (target == null) {
                    player.sendMessage("§cPlayer '$targetName' not found or is not online!")
                    return@checkPermissionAndExecute
                }
                
                if (target == player) {
                    player.sendMessage("§cYou cannot teleport yourself to yourself!")
                    return@checkPermissionAndExecute
                }
                
                // Teleport target to player
                val playerPos = player.position
                target.teleport(playerPos)
                
                player.sendMessage("§a§lTeleported!")
                player.sendMessage("§7${target.username} has been teleported to you")
                
                // Notify target
                target.sendMessage("§a§lTeleported!")
                target.sendMessage("§7You have been teleported to ${player.username}")
                
                plugin.logger.info("${player.username} teleported ${target.username} to themselves")
            }
        }, playerArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleporthere") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/teleporthere <player>")
                player.sendMessage("§7/tphere <player>")
                player.sendMessage("§7Teleport another player to your location")
            }
        }
    }
}
