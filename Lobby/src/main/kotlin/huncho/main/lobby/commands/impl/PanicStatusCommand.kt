package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.PermissionCache
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import java.text.SimpleDateFormat
import java.util.*

/**
 * Panic Status Command - Allows staff to see who is currently in panic mode
 */
class PanicStatusCommand(private val plugin: LobbyPlugin) : Command("panicstatus") {
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            val player = sender as Player
            PermissionCache.hasPermissionCached(player, "hub.command.unpanic") ||
            PermissionCache.hasPermissionCached(player, "radium.staff") ||
            PermissionCache.hasPermissionCached(player, "lobby.admin")
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.unpanic") {
                // Get panic players from Radium instead of local panic manager
                plugin.radiumIntegration.getPanicPlayers().thenAccept { panicPlayers ->
                    // Debug logging
                    plugin.logger.info("DEBUG: PanicStatus - Found ${panicPlayers.size} panic players from Radium")
                    panicPlayers.forEach { (uuid, data) ->
                        plugin.logger.info("DEBUG: PanicStatus - Player ${data.username} (${uuid}) in panic since ${data.activationTime}")
                    }
                    
                    if (panicPlayers.isEmpty()) {
                        player.sendMessage("§a§lPanic Status")
                        player.sendMessage("§7No players are currently in panic mode.")
                        return@thenAccept
                    }
                    
                    player.sendMessage("§c§lPanic Status")
                    player.sendMessage("§7Players currently in panic mode:")
                    player.sendMessage("")
                    
                    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    
                    panicPlayers.forEach { (uuid, panicData) ->
                        val activationTime = Date(panicData.activationTime)
                        val timeString = dateFormat.format(activationTime)
                        val elapsed = (System.currentTimeMillis() - panicData.activationTime) / 60000
                        
                        player.sendMessage("§7• §c${panicData.username}")
                        player.sendMessage("§7  Activated: §f$timeString §7(${elapsed}m ago)")
                        if (panicData.reason != null) {
                            player.sendMessage("§7  Reason: §f${panicData.reason}")
                        }
                        player.sendMessage("")
                    }
                    
                    player.sendMessage("§7Use §f/unpanic <player> §7to remove panic mode from a player.")
                }.exceptionally { throwable ->
                    plugin.logger.error("Failed to get panic players from Radium", throwable)
                    player.sendMessage("§cError: Failed to retrieve panic status from backend")
                    null
                }
            }
        }
    }
}
