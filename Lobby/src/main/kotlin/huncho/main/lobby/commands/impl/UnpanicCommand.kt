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
 * Unpanic Command - Staff command to remove panic mode from players
 */
class UnpanicCommand(private val plugin: LobbyPlugin) : Command("unpanic") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            val player = sender as Player
            PermissionCache.hasPermissionCached(player, "hub.command.unpanic") ||
            PermissionCache.hasPermissionCached(player, "radium.staff") ||
            PermissionCache.hasPermissionCached(player, "lobby.admin")
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.unpanic") {
                val targetName = context.get(playerArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.panicManager.exitPanicMode(player, targetName)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lUNPANIC")
                            player.sendMessage("§7You have removed panic mode from §f$targetName")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to remove panic mode: ${e.message}")
                        plugin.logger.error("Error removing panic mode", e)
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
            player.checkPermissionAndExecute("hub.command.unpanic") {
                player.sendMessage("§a§lUsage:")
                player.sendMessage("§7/unpanic <player>")
                player.sendMessage("§7Removes panic mode from a player")
            }
        }
    }
}
