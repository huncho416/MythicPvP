package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DismissReportCommand(private val plugin: LobbyPlugin) : Command("dismiss") {

    private val playerArg = ArgumentType.Word("player")
    private val reasonArg = ArgumentType.StringArray("reason")

    init {
        // Remove restrictive condition that causes red text - keep permission check in execution
        // This allows the command to appear normal in chat while still being secure

        // Syntax: /dismiss <player> [reason...]
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }

            val player = sender as Player
            player.checkPermissionAndExecute("radium.reports.dismiss") {
                val targetName = context.get(playerArg)
                val reason = context.get(reasonArg).joinToString(" ")

                GlobalScope.launch {
                    try {
                        val success = plugin.reportsManager.dismissLatestReport(player, targetName, reason)

                        if (success) {
                            player.sendMessage("§c§lReport Dismissed!")
                            player.sendMessage("§7Successfully dismissed the latest report for §f${targetName}")
                            if (reason.isNotEmpty()) {
                                player.sendMessage("§7Reason: §f${reason}")
                            }
                        } else {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7No pending reports found for §f${targetName}")
                        }
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to dismiss report: ${e.message}")
                    }
                }
            }
        }, playerArg, reasonArg)

        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }

            val player = sender as Player
            // Still check permission for help message
            if (!huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.reports.dismiss")) {
                player.sendMessage("§cYou don't have permission to use this command!")
                return@setDefaultExecutor
            }

            player.sendMessage("§c§lUsage:")
            player.sendMessage("§7/dismiss <player> [reason...] - Dismiss latest report for a player")
            player.sendMessage("§7Example: /dismiss PlayerName Not enough evidence")
        }
    }
}
