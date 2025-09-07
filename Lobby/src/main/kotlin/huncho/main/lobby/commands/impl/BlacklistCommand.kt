package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.api.PunishmentApiResult
import huncho.main.lobby.models.PunishmentRequest
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PlayerLookupUtils
import huncho.main.lobby.utils.PermissionCache
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component

class BlacklistCommand(private val plugin: LobbyPlugin) : Command("blacklist") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    PermissionCache.hasPermissionCached(sender, "radium.punish.blacklist") ||
                    PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /blacklist <target> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /blacklist <player> <reason>")
                return@addSyntax
            }
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.blacklist").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.blacklist or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the punishment using the proper API
                    executeBlacklistPunishment(sender, target, reason)
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
                MessageUtils.sendMessage(sender, "&cUsage: /blacklist <player> <reason>")
            }
        }
    }
    
    private fun executeBlacklistPunishment(player: Player, target: String, reason: String) {
        GlobalScope.launch {
            try {
                // First resolve the target player
                val lookupResult = PlayerLookupUtils.resolvePlayer(target).await()
                if (lookupResult == null) {
                    player.sendMessage(Component.text("§cPlayer not found: $target"))
                    return@launch
                }
                
                val request = PunishmentRequest(
                    target = lookupResult.name,
                    type = "BLACKLIST",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    duration = null, // Blacklists are permanent
                    silent = false,
                    clearInventory = true // Blacklists clear inventory by default
                )
                
                val result = plugin.radiumPunishmentAPI.issuePunishment(request)
                
                val message = if (result.isSuccess) {
                    val response = (result as PunishmentApiResult.Success).response
                    "&aSuccessfully blacklisted ${response.target}: ${response.message}"
                } else {
                    "&cFailed to blacklist $target: ${result.getErrorMessage()}"
                }
                
                MessageUtils.sendMessage(player, message)
                LobbyPlugin.logger.info("${player.username} attempted to blacklist $target - Success: ${result.isSuccess}")
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&cAn error occurred while processing the blacklist: ${e.message}")
                LobbyPlugin.logger.error("Error processing blacklist command from ${player.username}", e)
            }
        }
    }
}
