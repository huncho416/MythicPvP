package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

class TeleportPositionCommand(private val plugin: LobbyPlugin) : Command("teleportposition", "tppos") {
    
    private val xArg = ArgumentType.Double("x")
    private val yArg = ArgumentType.Double("y")
    private val zArg = ArgumentType.Double("z")
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "hub.command.teleportposition") ||
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
            player.checkPermissionAndExecute("hub.command.teleportposition") {
                val x = context.get(xArg)
                val y = context.get(yArg)
                val z = context.get(zArg)
                
                // Validate coordinates
                if (y < -64 || y > 320) {
                    player.sendMessage("§cInvalid Y coordinate! Must be between -64 and 320")
                    return@checkPermissionAndExecute
                }
                
                // Create position and teleport
                val targetPos = Pos(x, y, z)
                player.teleport(targetPos)
                
                player.sendMessage("§a§lTeleported!")
                player.sendMessage("§7Teleported to coordinates:")
                player.sendMessage("§7X: §e${String.format("%.2f", x)}")
                player.sendMessage("§7Y: §e${String.format("%.2f", y)}")
                player.sendMessage("§7Z: §e${String.format("%.2f", z)}")
                
                plugin.logger.info("${player.username} teleported to coordinates $x, $y, $z")
            }
        }, xArg, yArg, zArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleportposition") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/teleportposition <x> <y> <z>")
                player.sendMessage("§7/tppos <x> <y> <z>")
                player.sendMessage("§7Teleport to specific coordinates")
                player.sendMessage("§7Example: /tppos 100 65 -200")
                player.sendMessage("§7Y coordinate must be between -64 and 320")
            }
        }
    }
}
