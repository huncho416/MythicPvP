package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.api.PunishmentApiResult
import huncho.main.lobby.models.PunishmentRevokeRequest
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PlayerLookupUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
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
                MessageUtils.sendMessage(player, "&7Processing unban for $target...")
                
                // First, resolve the target player
                val playerInfo = PlayerLookupUtils.resolvePlayer(target).get()
                if (playerInfo == null) {
                    MessageUtils.sendMessage(player, "&c✗ Player '$target' not found")
                    MessageUtils.sendMessage(player, "&7Make sure the player has joined the server before.")
                    return@launch
                }
                
                val request = PunishmentRevokeRequest(
                    target = playerInfo.name,
                    type = "BAN",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    silent = false
                )
                
                val result = plugin.radiumPunishmentAPI.revokePunishment(request)
                
                when {
                    result.isSuccess -> {
                        val response = (result as PunishmentApiResult.Success).response
                        MessageUtils.sendMessage(player, "&a✓ Successfully unbanned ${response.target}")
                        MessageUtils.sendMessage(player, "&7Reason: $reason")
                        
                        // Broadcast to staff if enabled
                        MinecraftServer.getConnectionManager().onlinePlayers.forEach { staff ->
                            if (staff != player && plugin.radiumIntegration.hasPermission(staff.uuid, "radium.staff").join()) {
                                MessageUtils.sendMessage(staff, "&a${player.username} unbanned ${response.target}: $reason")
                            }
                        }
                        
                        plugin.logger.info("${player.username} successfully unbanned ${playerInfo.name} (${playerInfo.uuid}) for: $reason")
                    }
                    result.isError -> {
                        val errorMessage = result.getErrorMessage() ?: "Unknown error"
                        MessageUtils.sendMessage(player, "&c✗ Failed to unban ${playerInfo.name}")
                        MessageUtils.sendMessage(player, "&7Error: $errorMessage")
                        plugin.logger.warn("${player.username} failed to unban ${playerInfo.name}: $errorMessage")
                    }
                }
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&c✗ An error occurred while processing the unban")
                MessageUtils.sendMessage(player, "&7Please contact an administrator if this persists.")
                plugin.logger.error("Error processing unban command from ${player.username} for target $target", e)
            }
        }
    }
}
