package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CompleteRequestCommand(private val plugin: LobbyPlugin) : Command("completerequest") {

    private val playerArg = ArgumentType.Word("player")
    private val responseArg = ArgumentType.StringArray("response")

    init {
        // Remove restrictive condition that causes red text - keep permission check in execution
        // This allows the command to appear normal in chat while still being secure

        // Syntax: /completerequest <player> [response...]
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }

            val player = sender as Player
            player.checkPermissionAndExecute("radium.requests.manage") {
                val targetName = context.get(playerArg)
                val response = context.get(responseArg).joinToString(" ")

                GlobalScope.launch {
                    try {
                        val success = plugin.reportsManager.completeLatestRequest(player, targetName, response)

                        if (success) {
                            player.sendMessage("§a§lRequest Completed!")
                            player.sendMessage("§7Successfully completed the latest request for §f${targetName}")
                            if (response.isNotEmpty()) {
                                player.sendMessage("§7Response: §f${response}")
                            }
                        } else {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7No pending requests found for §f${targetName}")
                        }
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to complete request: ${e.message}")
                    }
                }
            }
        }, playerArg, responseArg)

        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }

            val player = sender as Player
            // Still check permission for help message
            if (!huncho.main.lobby.utils.PermissionCache.hasPermissionCached(player, "radium.requests.manage")) {
                player.sendMessage("§cYou don't have permission to use this command!")
                return@setDefaultExecutor
            }

            player.sendMessage("§c§lUsage:")
            player.sendMessage("§7/completerequest <player> [response...] - Complete latest request for a player")
            player.sendMessage("§7Example: /completerequest PlayerName Your issue has been resolved")
        }
    }
}
