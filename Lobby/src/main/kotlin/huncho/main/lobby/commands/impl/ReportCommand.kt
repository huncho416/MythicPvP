package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await

class ReportCommand(private val plugin: LobbyPlugin) : Command("report") {
    
    private val playerArg = ArgumentType.Word("player")
    private val reasonArg = ArgumentType.Word("reason")
    private val descriptionArg = ArgumentType.StringArray("description")

    init {
        // Main report command - for creating reports
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            // Remove permission check - allow all players to report
            val targetName = context.get(playerArg)
            val reason = context.get(reasonArg)
            val description = context.get(descriptionArg).joinToString(" ")
            
            // Launch coroutine for async operation
            GlobalScope.launch {
                try {
                    val result = plugin.reportsManager.createReport(player, targetName, reason, description)
                    
                    if (result.isSuccess) {
                        player.sendMessage("§a§lReport Created!")
                        player.sendMessage("§7You have reported §f${targetName} §7for §c${reason}")
                        player.sendMessage("§7Staff have been notified and will investigate.")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7$error")
                    }
                } catch (e: Exception) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7Failed to create report: ${e.message}")
                }
            }
        }, playerArg, reasonArg, descriptionArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            // Show different help based on permissions
            player.sendMessage("§c§lUsage:")
            player.sendMessage("§7/report <player> <reason> <description...> - Report a player")
            
            // Show staff commands if they have permission
            if (PermissionCache.hasPermissionCached(player, "radium.reports.resolve") || 
                PermissionCache.hasPermissionCached(player, "radium.reports.dismiss")) {
                player.sendMessage("")
                player.sendMessage("§e§lStaff Commands:")
                player.sendMessage("§7/resolve report <player> [resolution...] - Resolve latest report")
                player.sendMessage("§7/dismiss report <player> [reason...] - Dismiss latest report")
            }
            
            player.sendMessage("")
            player.sendMessage("§7Example: /report PlayerName Hacking They are using killaura")
        }
    }
}
