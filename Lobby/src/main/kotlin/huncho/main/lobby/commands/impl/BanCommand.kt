package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.api.PunishmentApiResult
import huncho.main.lobby.models.PunishmentRequest
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.PlayerLookupUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class BanCommand(private val plugin: LobbyPlugin) : Command("ban") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.ban").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /ban <target> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /ban <player> <reason>")
                return@addSyntax
            }
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.ban").thenAccept outerPermCheck@{ hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept innerPermCheck@{ hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.ban or lobby.admin")
                        return@innerPermCheck
                    }
                    
                    // Execute the punishment using the proper API
                    executeBanPunishment(sender, target, reason)
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
                MessageUtils.sendMessage(sender, "&cUsage: /ban <player> <reason>")
            }
        }
    }
    
    private fun executeBanPunishment(player: Player, target: String, reason: String) {
        GlobalScope.launch {
            try {
                MessageUtils.sendMessage(player, "&7Processing ban for $target...")
                plugin.logger.info("${player.username} is attempting to ban $target for: $reason")
                
                // First, resolve the target player
                val playerInfo = PlayerLookupUtils.resolvePlayer(target).get()
                plugin.logger.info("Player lookup result for $target: $playerInfo")
                if (playerInfo == null) {
                    MessageUtils.sendMessage(player, "&c✗ Player '$target' not found")
                    MessageUtils.sendMessage(player, "&7Make sure the player has joined the server before.")
                    plugin.logger.warn("Failed to resolve player: $target")
                    return@launch
                }
                
                plugin.logger.info("Resolved player: ${playerInfo.name} (${playerInfo.uuid})")
                
                // Check rank weight permissions
                val canPunish = PlayerLookupUtils.canPunish(plugin, player.uuid, playerInfo.uuid).get()
                plugin.logger.info("Rank weight check - ${player.username} can punish ${playerInfo.name}: $canPunish")
                if (!canPunish) {
                    val weightInfo = PlayerLookupUtils.getRankWeightInfo(plugin, player.uuid, playerInfo.uuid).get()
                    MessageUtils.sendMessage(player, "&c✗ You cannot punish ${playerInfo.name}")
                    MessageUtils.sendMessage(player, "&7Your rank weight (${weightInfo.first}) is not higher than theirs (${weightInfo.second})")
                    plugin.logger.warn("${player.username} cannot punish ${playerInfo.name} - insufficient rank weight")
                    return@launch
                }
                
                val request = PunishmentRequest(
                    target = playerInfo.name, // Use player name as target
                    type = "BAN",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    duration = null, // Permanent ban
                    silent = false,
                    clearInventory = false
                )
                
                val result = plugin.radiumPunishmentAPI.issuePunishment(request)
                
                when {
                    result.isSuccess -> {
                        val response = (result as PunishmentApiResult.Success).response
                        MessageUtils.sendMessage(player, "&a✓ Successfully banned ${response.target}")
                        MessageUtils.sendMessage(player, "&7Reason: $reason")
                        
                        // Broadcast to staff if enabled
                        MinecraftServer.getConnectionManager().onlinePlayers.forEach { staff ->
                            if (staff != player && plugin.radiumIntegration.hasPermission(staff.uuid, "radium.staff").join()) {
                                MessageUtils.sendMessage(staff, "&c${player.username} banned ${response.target}: $reason")
                            }
                        }
                        
                        plugin.logger.info("${player.username} successfully banned ${playerInfo.name} (${playerInfo.uuid}) for: $reason")
                    }
                    result.isError -> {
                        val errorMessage = result.getErrorMessage() ?: "Unknown error"
                        MessageUtils.sendMessage(player, "&c✗ Failed to ban ${playerInfo.name}")
                        MessageUtils.sendMessage(player, "&7Error: $errorMessage")
                        
                        if (errorMessage.contains("404") || errorMessage.contains("not found")) {
                            MessageUtils.sendMessage(player, "&ePlayer data may be corrupted or invalid.")
                        } else if (errorMessage.contains("Service unavailable")) {
                            MessageUtils.sendMessage(player, "&eRadium punishment service is temporarily unavailable. Try again later.")
                        }
                        
                        plugin.logger.warn("${player.username} failed to ban ${playerInfo.name}: $errorMessage")
                    }
                }
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&c✗ An error occurred while processing the ban")
                MessageUtils.sendMessage(player, "&7Please contact an administrator if this persists.")
                plugin.logger.error("Error processing ban command from ${player.username} for target $target", e)
            }
        }
    }
}
