package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class BroadcastCommand(private val plugin: LobbyPlugin) : Command("broadcast") {
    
    private val messageArg = ArgumentType.StringArray("message")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.broadcast")
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.broadcast") {
                val message = context.get(messageArg).joinToString(" ")
                
                // Create broadcast message
                val broadcastComponent = Component.text()
                    .append(Component.text("[BROADCAST] ", NamedTextColor.YELLOW))
                    .append(MessageUtils.colorize(message))
                    .build()
                
                // Send to all online players on this server only
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                    onlinePlayer.sendMessage(broadcastComponent as Component)
                }
                
                player.sendMessage("§a§lBroadcast Sent!")
                player.sendMessage("§7Your broadcast has been sent to all players on this server")
                
                plugin.logger.info("Server broadcast sent by ${player.username}: $message")
            }
        }, messageArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.broadcast") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/broadcast <message...>")
                player.sendMessage("§7Example: /broadcast Welcome to the server!")
                player.sendMessage("§7Note: This sends a message to all players on this server only")
            }
        }
    }
}
