package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.features.reports.ReportsManager
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RequestCommand(private val plugin: LobbyPlugin) : Command("request") {
    
    private val typeArg = ArgumentType.Word("type")
    private val subjectArg = ArgumentType.Word("subject")
    private val descriptionArg = ArgumentType.StringArray("description")

    init {
        // Main request command - for creating requests
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            // Remove permission check - allow all players to make requests
            val typeStr = context.get(typeArg)
            val subject = context.get(subjectArg)
            val description = context.get(descriptionArg).joinToString(" ")
            
            // Parse request type
            val requestType = try {
                ReportsManager.RequestType.valueOf(typeStr.uppercase())
            } catch (e: IllegalArgumentException) {
                player.sendMessage("§c§lInvalid request type!")
                player.sendMessage("§7Valid types: ${ReportsManager.RequestType.values().joinToString(", ") { it.name.lowercase() }}")
                return@addSyntax
            }
            
            GlobalScope.launch {
                try {
                    val result = plugin.reportsManager.createRequest(player, requestType, subject, description)
                    
                    if (result.isSuccess) {
                        player.sendMessage("§a§lRequest Created!")
                        player.sendMessage("§7You have created a §b${requestType.displayName} §7request: §f${subject}")
                        player.sendMessage("§7Staff have been notified and will respond soon.")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7$error")
                    }
                } catch (e: Exception) {
                    player.sendMessage("§c§lError!")
                    player.sendMessage("§7Failed to create request: ${e.message}")
                }
            }
        }, typeArg, subjectArg, descriptionArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.sendMessage("§c§lUsage:")
            player.sendMessage("§7/request <type> <subject> <description...> - Create a request")
            
            // Show staff commands if they have permission
            if (PermissionCache.hasPermissionCached(player, "radium.requests.manage")) {
                player.sendMessage("")
                player.sendMessage("§e§lStaff Commands:")
                player.sendMessage("§7/completerequest <player> [response...] - Complete latest request")
                player.sendMessage("§7/cancelrequest <player> [reason...] - Cancel latest request")
            }
            
            player.sendMessage("")
            player.sendMessage("§7Available types:")
            ReportsManager.RequestType.values().forEach { type ->
                player.sendMessage("§f- ${type.name.lowercase()}: §7${type.description}")
            }
            player.sendMessage("§7Example: /request help spawn How do I get back to spawn?")
        }
    }
}
