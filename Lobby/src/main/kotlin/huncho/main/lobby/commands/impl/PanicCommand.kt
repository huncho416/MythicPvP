package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Panic Command - Allows players to enter panic mode during emergencies
 */
class PanicCommand(private val plugin: LobbyPlugin) : Command("panic") {
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            val player = sender as Player
            PermissionCache.hasPermissionCached(player, "hub.command.panic") ||
            PermissionCache.hasPermissionCached(player, "radium.staff") ||
            PermissionCache.hasPermissionCached(player, "lobby.admin")
        }
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            
            GlobalScope.launch {
                try {
                    val result = plugin.panicManager.enterPanicMode(player)
                    
                    if (result.isSuccess) {
                        player.sendMessage("§c§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
                        player.sendMessage("§c§lPANIC MODE ACTIVATED!")
                        player.sendMessage("§7")
                        player.sendMessage("§7You have been put into panic mode.")
                        player.sendMessage("§7All staff have been notified of your situation.")
                        player.sendMessage("§7")
                        player.sendMessage("§7Please join our TeamSpeak: §ets.mythicpvp.net")
                        player.sendMessage("§7Or contact us on Discord for immediate help.")
                        player.sendMessage("§7")
                        player.sendMessage("§c§lStay calm, help is on the way!")
                        player.sendMessage("§c§l■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7$error")
                    }
                    
                } catch (e: Exception) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7Failed to activate panic mode: ${e.message}")
                    plugin.logger.error("Error activating panic mode", e)
                }
            }
        }
    }
}
