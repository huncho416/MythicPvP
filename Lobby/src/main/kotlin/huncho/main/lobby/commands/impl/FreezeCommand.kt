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
 * Freeze Command - Freezes a player preventing movement and most actions
 */
class FreezeCommand(private val plugin: LobbyPlugin) : Command("freeze") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.freeze")
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.freeze") {
                val targetName = context.get(playerArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.freezeManager.freezePlayer(player, targetName)
                        
                        if (result.isFailure) {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        // No success message needed - FreezeManager handles staff broadcast
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to freeze player: ${e.message}")
                        plugin.logger.error("Error freezing player", e)
                    }
                }
            }
        }, playerArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.freeze") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/freeze <player>")
                player.sendMessage("§7Freezes a player preventing movement and actions")
            }
        }
    }
}
