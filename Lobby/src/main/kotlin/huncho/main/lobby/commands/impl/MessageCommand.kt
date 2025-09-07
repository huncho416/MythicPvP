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

class MessageCommand(private val plugin: LobbyPlugin) : Command("message", "msg", "tell", "whisper") {
    
    private val playerArg = ArgumentType.Word("player")
    private val messageArg = ArgumentType.StringArray("message")
    
    init {
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.message") {
                val targetName = context.get(playerArg)
                val message = context.get(messageArg).joinToString(" ")
                
                if (message.isBlank()) {
                    player.sendMessage("§c§lUsage:")
                    player.sendMessage("§7/msg <player> <message>")
                    return@checkPermissionAndExecute
                }
                
                val target = MinecraftServer.getConnectionManager().onlinePlayers.find { 
                    it.username.equals(targetName, ignoreCase = true) 
                }
                
                if (target == null) {
                    player.sendMessage("§c§lPlayer Not Found!")
                    player.sendMessage("§7Player §f$targetName §7is not online.")
                    return@checkPermissionAndExecute
                }
                
                if (target == player) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7You cannot send a message to yourself!")
                    return@checkPermissionAndExecute
                }
                
                GlobalScope.launch {
                    try {
                        // Get sender's rank information from Radium
                        val senderData = plugin.radiumIntegration.getPlayerData(player.uuid).await()
                        val senderRank = senderData?.rank
                        val senderPrefix = senderRank?.prefix ?: ""
                        val senderColor = senderRank?.color ?: "§f"
                        
                        // Get target's rank for reply purposes
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
                        
                        // Set last message data for reply
                        LastMessageManager.setLastMessage(player.uuid, target.uuid)
                        LastMessageManager.setLastMessage(target.uuid, player.uuid)
                        
                        plugin.logger.info("Private message from ${player.username} to ${target.username}: $message")
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to send private message: ${e.message}")
                        plugin.logger.error("Error sending private message", e)
                    }
                }
            }
        }, playerArg, messageArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.message") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/msg <player> <message>")
                player.sendMessage("§7Example: /msg PlayerName Hello there!")
            }
        }
    }
}
