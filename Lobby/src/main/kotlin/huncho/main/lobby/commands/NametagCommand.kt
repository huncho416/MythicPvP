package huncho.main.lobby.commands

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity
import net.minestom.server.entity.Player
import net.minestom.server.utils.entity.EntityFinder

/**
 * Nametag Command for managing player nametags
 */
class NametagCommand(private val plugin: LobbyPlugin) : Command("nametag", "nt") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "lobby.nametag") ||
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        val actionArg = ArgumentType.Word("action").from("reload", "update", "info", "stats")
        val playerArg = ArgumentEntity("player").onlyPlayers(true).singleEntity(true)
        
        // /nametag <action>
        addSyntax({ sender, context ->
            val player = sender as Player
            val action = context.get(actionArg)
            
            player.checkPermissionAndExecute("lobby.nametag") {
                when (action.lowercase()) {
                    "reload" -> {
                        player.checkPermissionAndExecute("lobby.nametag.reload") {
                            plugin.coroutineScope.launch {
                                try {
                                    plugin.nametagManager.updatePlayerNametag(player)
                                    player.sendMessage("§aYour nametag has been reloaded!")
                                } catch (e: Exception) {
                                    player.sendMessage("§cFailed to reload nametag: ${e.message}")
                                }
                            }
                        }
                    }
                    
                    "stats" -> {
                        player.checkPermissionAndExecute("lobby.nametag.stats") {
                            val stats = plugin.nametagManager.getStats()
                            player.sendMessage("§6=== Nametag Statistics ===")
                            player.sendMessage("§eCached Nametags: §f${stats["cached_nametags"]}")
                            player.sendMessage("§eOnline Players: §f${stats["online_players"]}")
                            player.sendMessage("§eUpdate Task Running: §f${stats["update_task_running"]}")
                        }
                    }
                    
                    "info" -> {
                        val nametagInfo = plugin.nametagManager.getPlayerNametagInfo(player.uuid)
                        if (nametagInfo != null) {
                            player.sendMessage("§6=== Your Nametag Info ===")
                            player.sendMessage("§ePrefix: §f'${nametagInfo.prefix}'")
                            player.sendMessage("§eSuffix: §f'${nametagInfo.suffix}'")
                            player.sendMessage("§eDisplay Name: ${nametagInfo.displayName}")
                            player.sendMessage("§ePriority: §f${nametagInfo.priority}")
                            player.sendMessage("§eHas Custom Nametag: §f${plugin.nametagManager.hasCustomNametag(player.uuid)}")
                        } else {
                            player.sendMessage("§cNo nametag info found!")
                        }
                    }
                    
                    else -> {
                        player.sendMessage("§cUsage: /nametag <reload|update|info|stats> [player]")
                    }
                }
            }
        }, actionArg)
        
        // /nametag <action> <player>
        addSyntax({ sender, context ->
            val player = sender as Player
            val action = context.get(actionArg)
            val targetFinder = context.get(playerArg)
            
            val target = targetFinder.findFirstPlayer(sender)
            if (target == null) {
                player.sendMessage("§cPlayer not found!")
                return@addSyntax
            }
            
            when (action.lowercase()) {
                "reload", "update" -> {
                    player.checkPermissionAndExecute("lobby.nametag.reload.others") {
                        plugin.coroutineScope.launch {
                            try {
                                plugin.nametagManager.forceRefreshNametag(target.uuid)
                                player.sendMessage("§aNametag reloaded for player §f${target.username}§a!")
                                target.sendMessage("§aYour nametag has been reloaded by an administrator!")
                            } catch (e: Exception) {
                                player.sendMessage("§cFailed to reload nametag for ${target.username}: ${e.message}")
                            }
                        }
                    }
                }
                
                "info" -> {
                    player.checkPermissionAndExecute("lobby.nametag.info.others") {
                        val nametagInfo = plugin.nametagManager.getPlayerNametagInfo(target.uuid)
                        if (nametagInfo != null) {
                            player.sendMessage("§6=== Nametag Info for ${target.username} ===")
                            player.sendMessage("§ePrefix: §f'${nametagInfo.prefix}'")
                            player.sendMessage("§eSuffix: §f'${nametagInfo.suffix}'")
                            player.sendMessage("§eDisplay Name: ${nametagInfo.displayName}")
                            player.sendMessage("§ePriority: §f${nametagInfo.priority}")
                            player.sendMessage("§eHas Custom Nametag: §f${plugin.nametagManager.hasCustomNametag(target.uuid)}")
                        } else {
                            player.sendMessage("§cFailed to get nametag info for ${target.username}")
                        }
                    }
                }
                
                else -> {
                    player.sendMessage("§cUsage: /nametag <reload|update|info> [player]")
                }
            }
        }, actionArg, playerArg)
        
        // Default usage
        setDefaultExecutor { sender, _ ->
            sender.sendMessage("§cUsage: /nametag <reload|update|info|stats> [player]")
        }
    }
}
