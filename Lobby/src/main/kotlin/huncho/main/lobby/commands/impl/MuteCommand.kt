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

class MuteCommand(private val plugin: LobbyPlugin) : Command("mute") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.mute").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        val durationArg = ArgumentType.Word("duration")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /mute <target> <duration> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val duration = context.get(durationArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /mute <player> <duration> <reason>")
                MessageUtils.sendMessage(sender, "&7Duration examples: 1d, 2h, 30m, 1w")
                return@addSyntax
            }
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.mute").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.mute or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the punishment using the proper API
                    executeMutePunishment(sender, target, duration, reason)
                }.exceptionally { ex ->
                    MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                    null
                }
            }.exceptionally { ex ->
                MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                null
            }
            
        }, targetArg, durationArg, reasonArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MessageUtils.sendMessage(sender, "&cUsage: /mute <player> <duration> <reason>")
                MessageUtils.sendMessage(sender, "&7Duration examples: 1d, 2h, 30m, 1w")
            }
        }
    }
    
    private fun executeMutePunishment(player: Player, target: String, duration: String, reason: String) {
        GlobalScope.launch {
            try {
                MessageUtils.sendMessage(player, "&7Processing mute for $target...")
                
                // First, resolve the target player
                val playerInfo = PlayerLookupUtils.resolvePlayer(target).get()
                if (playerInfo == null) {
                    MessageUtils.sendMessage(player, "&c✗ Player '$target' not found")
                    MessageUtils.sendMessage(player, "&7Make sure the player has joined the server before.")
                    return@launch
                }
                
                // Check rank weight permissions
                val canPunish = PlayerLookupUtils.canPunish(plugin, player.uuid, playerInfo.uuid).get()
                if (!canPunish) {
                    val weightInfo = PlayerLookupUtils.getRankWeightInfo(plugin, player.uuid, playerInfo.uuid).get()
                    MessageUtils.sendMessage(player, "&c✗ You cannot mute ${playerInfo.name}")
                    MessageUtils.sendMessage(player, "&7Your rank weight (${weightInfo.first}) is not higher than theirs (${weightInfo.second})")
                    return@launch
                }
                
                val request = PunishmentRequest(
                    target = playerInfo.name, // Use player name as target
                    type = "MUTE",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    duration = duration,
                    silent = false,
                    clearInventory = false
                )
                
                val result = plugin.radiumPunishmentAPI.issuePunishment(request)
                
                when {
                    result.isSuccess -> {
                        val response = (result as PunishmentApiResult.Success).response
                        MessageUtils.sendMessage(player, "&a✓ Successfully muted ${response.target} for $duration")
                        MessageUtils.sendMessage(player, "&7Reason: $reason")
                        
                        // Broadcast to staff if enabled
                        MinecraftServer.getConnectionManager().onlinePlayers.forEach { staff ->
                            if (staff != player && plugin.radiumIntegration.hasPermission(staff.uuid, "radium.staff").join()) {
                                MessageUtils.sendMessage(staff, "&e${player.username} muted ${response.target} for $duration: $reason")
                            }
                        }
                        
                        plugin.logger.info("${player.username} successfully muted ${playerInfo.name} (${playerInfo.uuid}) for $duration: $reason")
                    }
                    result.isError -> {
                        val errorMessage = result.getErrorMessage() ?: "Unknown error"
                        MessageUtils.sendMessage(player, "&c✗ Failed to mute ${playerInfo.name}")
                        MessageUtils.sendMessage(player, "&7Error: $errorMessage")
                        plugin.logger.warn("${player.username} failed to mute ${playerInfo.name}: $errorMessage")
                    }
                }
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&c✗ An error occurred while processing the mute")
                MessageUtils.sendMessage(player, "&7Please contact an administrator if this persists.")
                plugin.logger.error("Error processing mute command from ${player.username} for target $target", e)
            }
        }
    }
}
