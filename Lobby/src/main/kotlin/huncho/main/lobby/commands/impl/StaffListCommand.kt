package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class StaffListCommand(private val plugin: LobbyPlugin) : Command("stafflist", "slist", "staff") {
    
    init {
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    PermissionCache.hasPermissionCached(sender, "radium.staff") ||
                    PermissionCache.hasPermissionCached(sender, "hub.command.stafflist") ||
                    PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.stafflist") {
                GlobalScope.launch {
                    try {
                        // Get all online players
                        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers.toList()
                        val staffMembers = mutableListOf<Player>()
                        
                        // Check each player for staff permissions
                        for (onlinePlayer in onlinePlayers) {
                            // Check if player has staff permission
                            val isStaff = plugin.radiumIntegration.hasPermission(onlinePlayer.uuid, "hub.staff").join()
                            if (isStaff) {
                                staffMembers.add(onlinePlayer)
                            }
                        }
                        
                        if (staffMembers.isEmpty()) {
                            player.sendMessage("§c§lNo Staff Online")
                            player.sendMessage("§7There are currently no staff members online.")
                            return@launch
                        }
                        
                        player.sendMessage("§6§lOnline Staff (${staffMembers.size})")
                        player.sendMessage("§7§m                                    ")
                        
                        staffMembers.forEach { staffMember ->
                            val vanishStatus = if (plugin.vanishPluginMessageListener.isPlayerVanished(staffMember.uuid)) {
                                "§7[§cVanished§7]"
                            } else {
                                "§7[§aVisible§7]"
                            }
                            
                            // Get rank data from Radium
                            val playerData = plugin.radiumIntegration.getPlayerData(staffMember.uuid).join()
                            val rankPrefix = if (playerData?.rank?.prefix != null && playerData.rank.prefix.isNotEmpty()) {
                                playerData.rank.prefix
                            } else {
                                "§7[§bStaff§7]"
                            }
                            
                            // Extract the rank color for the username from the prefix
                            val usernameColor = try {
                                val rankPrefix = playerData?.rank?.prefix ?: ""
                                when {
                                    rankPrefix.contains("&4") || rankPrefix.contains("§4") -> "§4"
                                    rankPrefix.contains("&c") || rankPrefix.contains("§c") -> "§c"
                                    rankPrefix.contains("&a") || rankPrefix.contains("§a") -> "§a"
                                    rankPrefix.contains("&b") || rankPrefix.contains("§b") -> "§b"
                                    rankPrefix.contains("&e") || rankPrefix.contains("§e") -> "§e"
                                    rankPrefix.contains("&d") || rankPrefix.contains("§d") -> "§d"
                                    rankPrefix.contains("&9") || rankPrefix.contains("§9") -> "§9"
                                    rankPrefix.contains("&6") || rankPrefix.contains("§6") -> "§6"
                                    rankPrefix.contains("&f") || rankPrefix.contains("§f") -> "§f"
                                    rankPrefix.contains("&2") || rankPrefix.contains("§2") -> "§2"
                                    rankPrefix.contains("&1") || rankPrefix.contains("§1") -> "§1"
                                    rankPrefix.contains("&5") || rankPrefix.contains("§5") -> "§5"
                                    else -> {
                                        // Fallback to rank.color field
                                        val rankColor = playerData?.rank?.color ?: ""
                                        when (rankColor.lowercase()) {
                                            "dark_red", "4" -> "§4"
                                            "red", "c" -> "§c"
                                            "green", "a" -> "§a"
                                            "aqua", "b" -> "§b"
                                            "yellow", "e" -> "§e"
                                            "light_purple", "d" -> "§d"
                                            "blue", "9" -> "§9"
                                            "gold", "6" -> "§6"
                                            "white", "f" -> "§f"
                                            else -> "§f"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                "§f"
                            }
                            
                            // Apply the same color to the prefix as the username
                            val coloredPrefix = if (rankPrefix.contains("§") || rankPrefix.contains("&")) {
                                // Apply the username color to the prefix text (replace existing colors)
                                usernameColor + rankPrefix.replace(Regex("[§&][0-9a-fk-or]"), "")
                            } else {
                                usernameColor + rankPrefix
                            }
                            
                            player.sendMessage("§7• $coloredPrefix $usernameColor${staffMember.username} $vanishStatus")
                        }
                        
                        player.sendMessage("§7§m                                    ")
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to retrieve staff list: ${e.message}")
                    }
                }
            }
        }
    }
}
