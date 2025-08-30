package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.api.PunishmentApiResult
import huncho.main.lobby.models.PunishmentRevokeRequest
import huncho.main.lobby.utils.MessageUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class UnbanCommand(private val plugin: LobbyPlugin) : Command("unban") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.unban").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /unban <target> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /unban <player> <reason>")
                return@addSyntax
            }
            
            // Use async permission checking with debug logging
            LobbyPlugin.logger.info("Checking permissions for ${sender.username}: radium.punish.unban and lobby.admin")
            
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.unban").thenAccept { hasRadiumPerm ->
                LobbyPlugin.logger.info("${sender.username} - radium.punish.unban: $hasRadiumPerm")
                
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    LobbyPlugin.logger.info("${sender.username} - lobby.admin: $hasLobbyAdmin")
                    
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.unban or lobby.admin")
                        MessageUtils.sendMessage(sender, "&7Debug: radium.punish.unban=$hasRadiumPerm, lobby.admin=$hasLobbyAdmin")
                        return@thenAccept
                    }
                    
                    // Execute the command if permission check passes
                    LobbyPlugin.logger.info("${sender.username} passed permission check, executing unban command")
                    executeUnbanPunishment(sender, target, reason)
                }.exceptionally { ex ->
                    MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                    null
                }
            }.exceptionally { ex ->
                MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                null
            }
            
        }, targetArg, reasonArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MessageUtils.sendMessage(sender, "&cUsage: /unban <player> <reason>")
            }
        }
    }
    
    private fun executeUnbanPunishment(player: Player, target: String, reason: String) {
        GlobalScope.launch {
            try {
                val request = PunishmentRevokeRequest(
                    target = target,
                    type = "BAN",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    silent = false
                )
                
                val result = plugin.radiumPunishmentAPI.revokePunishment(request)
                
                val message = if (result.isSuccess) {
                    val response = (result as PunishmentApiResult.Success).response
                    "&aSuccessfully unbanned ${response.target}: ${response.message}"
                } else {
                    "&cFailed to unban $target: ${result.getErrorMessage()}"
                }
                
                MessageUtils.sendMessage(player, message)
                LobbyPlugin.logger.info("${player.username} attempted to unban $target - Success: ${result.isSuccess}")
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&cAn error occurred while processing the unban: ${e.message}")
                LobbyPlugin.logger.error("Error processing unban command from ${player.username}", e)
            }
        }
    }
}
