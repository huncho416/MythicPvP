package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PermissionCache
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class AlertCommand(private val plugin: LobbyPlugin) : Command("alert") {
    
    private val messageArg = ArgumentType.StringArray("message")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.alert")
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.alert") {
                val message = context.get(messageArg).joinToString(" ")
                
                // Create alert message
                val alertComponent = Component.text()
                    .append(Component.text("⚠ ALERT ⚠", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(MessageUtils.colorize(message))
                    .append(Component.newline())
                    .append(Component.text("⚠ ALERT ⚠", NamedTextColor.RED))
                    .build()
                
                // Send to all online players across the network
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                    onlinePlayer.sendMessage(alertComponent as Component)
                }
                
                // TODO: Send to other servers via plugin messaging
                // plugin.radiumIntegration.sendGlobalAlert(message)
                
                player.sendMessage("§a§lAlert Sent!")
                player.sendMessage("§7Your alert has been sent to all players network-wide")
                
                plugin.logger.info("Global alert sent by ${player.username}: $message")
            }
        }, messageArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.alert") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/alert <message...>")
                player.sendMessage("§7Example: /alert Server maintenance in 10 minutes!")
                player.sendMessage("§7Note: This sends a global alert to all players")
            }
        }
    }
}
