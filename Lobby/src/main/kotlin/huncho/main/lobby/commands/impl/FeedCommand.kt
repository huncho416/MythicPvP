package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Feed Command - Feeds self or other players
 */
class FeedCommand(private val plugin: LobbyPlugin) : Command("feed") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.feed.self") ||
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.feed.others")
        }
        // /feed <player>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.feed.others") {
                val targetName = context.get(playerArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.feedPlayer(player, targetName)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lFed!")
                            player.sendMessage("§7You have fed §f$targetName")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to feed player: ${e.message}")
                        plugin.logger.error("Error feeding player", e)
                    }
                }
            }
        }, playerArg)
        
        // /feed (self)
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.feed") {
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.feedPlayer(player, player.username)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lFed!")
                            player.sendMessage("§7You have been fed")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to feed: ${e.message}")
                        plugin.logger.error("Error feeding self", e)
                    }
                }
            }
        }
    }
}
