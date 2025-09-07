package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class SudoCommand(private val plugin: LobbyPlugin) : Command("sudo") {
    
    private val playerArg = ArgumentType.Word("player")
    private val commandArg = ArgumentType.StringArray("command")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.sudo")
        }
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.sudo") {
                val targetName = context.get(playerArg)
                val command = context.get(commandArg).joinToString(" ")
                
                // Find target player
                val target = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.username.equals(targetName, ignoreCase = true) }
                
                if (target == null) {
                    player.sendMessage("§cPlayer '$targetName' not found or is not online!")
                    return@checkPermissionAndExecute
                }
                
                if (command.startsWith("/")) {
                    // Execute as command
                    val commandToExecute = command.substring(1)
                    
                    player.sendMessage("§aSudo command executed on ${target.username}: /$commandToExecute")
                    
                    // Execute the command as the target player
                    MinecraftServer.getCommandManager().execute(target, commandToExecute)
                    
                    // Notify target player
                    target.sendMessage("§c§lSudo: §7You were forced to execute: §e/$commandToExecute")
                    
                } else {
                    // Send as chat message
                    player.sendMessage("§aSudo command executed on ${target.username}: $command")
                    
                    // Make target say the message
                    MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                        onlinePlayer.sendMessage("§7<${target.username}> §f$command")
                    }
                    
                    // Notify target player
                    target.sendMessage("§c§lSudo: §7You were forced to say: §e$command")
                }
                
                plugin.logger.info("${player.username} used sudo on ${target.username}: $command")
            }
        }, playerArg, commandArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.sudo") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/sudo <player> <command>")
                player.sendMessage("§7Force a player to execute a command")
            }
        }
    }
}
