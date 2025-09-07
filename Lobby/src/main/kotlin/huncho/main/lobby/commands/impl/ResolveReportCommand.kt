package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ResolveReportCommand(private val plugin: LobbyPlugin) : Command("resolve") {

    private val playerArg = ArgumentType.Word("player")
    private val resolutionArg = ArgumentType.StringArray("resolution")

    init {
        // Remove restrictive condition that causes red text - keep permission check in execution
        // This allows the command to appear normal in chat while still being secure

        // Syntax: /resolve <player> [resolution...]
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }

            val player = sender as Player
            player.checkPermissionAndExecute("radium.reports.resolve") {
                val targetName = context.get(playerArg)
                val resolution = context.get(resolutionArg).joinToString(" ")

                GlobalScope.launch {
                    try {
                        val success = plugin.reportsManager.resolveLatestReport(player, targetName, resolution)

                        if (success) {
                            player.sendMessage("§a§lReport Resolved!")
                            player.sendMessage("§7Successfully resolved the latest report for §f${targetName}")
                            if (resolution.isNotEmpty()) {
                                player.sendMessage("§7Resolution: §f${resolution}")
                            }
                        } else {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7No pending reports found for §f${targetName}")
                        }
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to resolve report: ${e.message}")
                    }
                }
            }
        }, playerArg, resolutionArg)

        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }

            val player = sender as Player
            // Still check permission for help message
            if (!huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.reports.resolve")) {
                player.sendMessage("§cYou don't have permission to use this command!")
                return@setDefaultExecutor
            }

            player.sendMessage("§c§lUsage:")
            player.sendMessage("§7/resolve <player> [resolution...] - Resolve latest report for a player")
            player.sendMessage("§7Example: /resolve PlayerName Investigated and handled appropriately")
        }
    }
}
