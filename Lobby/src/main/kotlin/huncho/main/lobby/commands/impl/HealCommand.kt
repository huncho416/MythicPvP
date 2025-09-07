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
 * Heal Command - Heals self or other players
 */
class HealCommand(private val plugin: LobbyPlugin) : Command("heal") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.heal.self") ||
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.heal.others")
        }
        // /heal <player>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.heal.others") {
                val targetName = context.get(playerArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.healPlayer(player, targetName)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lHealed!")
                            player.sendMessage("§7You have healed §f$targetName")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to heal player: ${e.message}")
                        plugin.logger.error("Error healing player", e)
                    }
                }
            }
        }, playerArg)
        
        // /heal (self)
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.heal") {
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.healPlayer(player, player.username)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lHealed!")
                            player.sendMessage("§7You have been healed")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to heal: ${e.message}")
                        plugin.logger.error("Error healing self", e)
                    }
                }
            }
        }
    }
}
