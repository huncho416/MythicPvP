package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PermissionCache
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.features.reports.ReportsManager
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import java.util.UUID

class ReportsCommand(private val plugin: LobbyPlugin) : Command("reports") {
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    PermissionCache.hasPermissionCached(sender, "radium.staff") ||
                    PermissionCache.hasPermissionCached(sender, "hub.command.reports") ||
                    PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        // View reports for a specific player
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.reports") {
                val targetName = context.get(playerArg)
                
                GlobalScope.launch {
                    try {
                        // Get target UUID from Radium
                        val targetData: huncho.main.lobby.integration.RadiumIntegration.PlayerData? = withContext(Dispatchers.IO) {
                            plugin.radiumIntegration.getPlayerDataByName(targetName).await()
                        }
                        if (targetData?.uuid == null) {
                            player.sendMessage("§c§lPlayer Not Found!")
                            player.sendMessage("§7Player '$targetName' was not found.")
                            return@launch
                        }
                        
                        // Fetch reports for the player
                        val reports = withContext(Dispatchers.IO) {
                            plugin.radiumIntegration.getReportsForPlayer(targetData.uuid).await()
                        }
                        
                        if (reports == null) {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7Failed to load reports for $targetName.")
                            return@launch
                        }
                        
                        if (reports.isEmpty()) {
                            player.sendMessage("§a§lReports for $targetName")
                            player.sendMessage("§7No reports found for this player.")
                            return@launch
                        }
                        
                        // Display reports in chat
                        player.sendMessage("§a§lReports for $targetName §7(${reports.size} total)")
                        player.sendMessage("§8§m                                                ")
                        
                        reports.forEachIndexed { index, report ->
                            val statusColor = when (report.status) {
                                "PENDING" -> "§e"
                                "INVESTIGATING" -> "§6"
                                "RESOLVED" -> "§a"
                                "DISMISSED" -> "§c"
                                else -> "§7"
                            }
                            
                            // Display report without ID
                            player.sendMessage("§7${index + 1}. §f${report.reason} $statusColor[${report.status}]")
                            
                            player.sendMessage("   §7Reporter: §f${report.reporterName}")
                            player.sendMessage("   §7Description: §f${report.description}")
                            player.sendMessage("   §7Server: §f${report.serverName}")
                            if (report.handlerName != null) {
                                player.sendMessage("   §7Handler: §f${report.handlerName}")
                            }
                            if (report.resolution != null) {
                                player.sendMessage("   §7Resolution: §f${report.resolution}")
                            }
                            player.sendMessage("")
                        }
                        
                        player.sendMessage("§8§m                                                ")
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to load reports: ${e.message}")
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
            player.checkPermissionAndExecute("hub.command.reports") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/reports <player> - View reports for a player")
                player.sendMessage("")
                player.sendMessage("§7Example:")
                player.sendMessage("§f/reports PlayerName")
            }
        }
    }
}
