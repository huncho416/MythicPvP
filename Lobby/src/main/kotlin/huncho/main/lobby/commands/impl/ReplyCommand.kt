package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.features.messaging.LastMessageManager
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await

class ReplyCommand(private val plugin: LobbyPlugin) : Command("reply", "r") {
    
    private val messageArg = ArgumentType.StringArray("message")
    
    init {
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.message") {
                val message = context.get(messageArg).joinToString(" ")
                
                if (message.isBlank()) {
                    player.sendMessage("§c§lUsage:")
                    player.sendMessage("§7/reply <message>")
                    return@checkPermissionAndExecute
                }
                
                val targetUuid = LastMessageManager.getLastMessage(player.uuid)
                if (targetUuid == null) {
                    player.sendMessage("§c§lNo Recent Messages!")
                    player.sendMessage("§7You have no one to reply to.")
                    return@checkPermissionAndExecute
                }
                
                val target = MinecraftServer.getConnectionManager().onlinePlayers.find { 
                    it.uuid == targetUuid 
                }
                
                if (target == null) {
                    player.sendMessage("§c§lPlayer Offline!")
                    player.sendMessage("§7The player you're trying to reply to is no longer online.")
                    LastMessageManager.removePlayer(targetUuid)
                    return@checkPermissionAndExecute
                }
                
                GlobalScope.launch {
                    try {
                        // Get sender's rank information from Radium
                        val senderData = plugin.radiumIntegration.getPlayerData(player.uuid).await()
                        val senderRank = senderData?.rank
                        val senderPrefix = senderRank?.prefix ?: ""
                        val senderColor = senderRank?.color ?: "§f"
                        
                        // Get target's rank for display purposes
                        val targetData = plugin.radiumIntegration.getPlayerData(target.uuid).await()
                        val targetRank = targetData?.rank
                        val targetPrefix = targetRank?.prefix ?: ""
                        val targetColor = targetRank?.color ?: "§f"
                        
                        // Format messages with rank information
                        val senderMessage = "§7[§fYOU §7→ ${targetPrefix}${targetColor}${target.username}§7] §f$message"
                        val targetMessage = "§7[${senderPrefix}${senderColor}${player.username} §7→ §fYOU§7] §f$message"
                        
                        // Send messages
                        player.sendMessage(senderMessage)
                        target.sendMessage(targetMessage)
                        
                        // Update last message data for both players
                        LastMessageManager.setLastMessage(player.uuid, target.uuid)
                        LastMessageManager.setLastMessage(target.uuid, player.uuid)
                        
                        plugin.logger.info("Reply from ${player.username} to ${target.username}: $message")
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to send reply: ${e.message}")
                        plugin.logger.error("Error sending reply", e)
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
            player.checkPermissionAndExecute("hub.command.message") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/reply <message>")
                player.sendMessage("§7Example: /reply Thanks for the message!")
            }
        }
    }
}
