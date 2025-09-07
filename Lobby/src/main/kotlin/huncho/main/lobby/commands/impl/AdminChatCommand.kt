package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PermissionCache
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Admin Chat Command - Restricted to admin staff and above
 * Uses prefix "&c[AC]" instead of "&b[SC]" 
 */
class AdminChatCommand(private val plugin: LobbyPlugin) : Command("adminchat", "ac") {
    
    private val messageArg = ArgumentType.StringArray("message")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            val player = sender as Player
            // Use cached permission check for tab completion filtering
            PermissionCache.hasPermissionCached(player, "hub.command.adminchat")
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.adminchat") {
                val message = context.get(messageArg).joinToString(" ")
                
                if (message.isBlank()) {
                    player.sendMessage("§c§lUsage:")
                    player.sendMessage("§7/adminchat <message>")
                    return@checkPermissionAndExecute
                }
                
                GlobalScope.launch {
                    try {
                        // Send admin chat message through Radium integration
                        val success = plugin.radiumIntegration.sendAdminChatMessage(player, message)
                        
                        if (!success) {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7Failed to send admin chat message.")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to send admin chat message: ${e.message}")
                        plugin.logger.error("Error sending admin chat message", e)
                    }
                }
            }
        }, messageArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.adminchat") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/adminchat <message>")
                player.sendMessage("§7/ac <message>")
                player.sendMessage("§7Send a message to all online admin staff")
            }
        }
    }
}
