package huncho.main.lobby.gui.staff

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.gui.GUI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.future.await

/**
 * GUI for viewing online staff members
 */
class StaffOnlineGUI(private val plugin: LobbyPlugin) : GUI(
    Component.text("Online Staff", NamedTextColor.GOLD, TextDecoration.BOLD),
    54,
    InventoryType.CHEST_6_ROW
) {
    
    override fun setupInventory() {
        // This will be populated when opened
    }
    
    fun openStaffGUI(player: Player) {
        GlobalScope.launch {
            try {
                // Clear inventory first
                for (i in 0 until 54) {
                    inventory.setItemStack(i, ItemStack.AIR)
                }
                
                // Get all online players
                val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
                var staffCount = 0
                var slot = 0
                
                for (onlinePlayer in onlinePlayers) {
                    if (slot >= 54) break // Don't exceed inventory size
                    
                    // Check if player has staff permission
                    val hasStaffPerm = plugin.radiumIntegration.hasPermission(onlinePlayer.uuid, "hub.staff").await()
                    if (!hasStaffPerm) continue
                    
                    // Get player data for rank info
                    val playerData = plugin.radiumIntegration.getPlayerData(onlinePlayer.uuid).await()
                    val rank = playerData?.rank
                    val rankName = rank?.name ?: "Default"
                    val rankPrefix = rank?.prefix ?: ""
                    
                    // Check if player is vanished
                    val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(onlinePlayer.uuid)
                    val vanishStatus = if (isVanished) "§cVanished" else "§aVisible"
                    
                    // Check if player is in staff mode
                    val isStaffMode = plugin.staffModeManager.isInStaffMode(onlinePlayer)
                    val staffModeStatus = if (isStaffMode) "§eStaff Mode" else "§7Normal Mode"
                    
                    // Convert legacy color codes in rank prefix to proper Components
                    val formattedPrefix = if (rankPrefix.isNotEmpty()) {
                        // Convert legacy color codes (&) to Adventure Components
                        val cleanPrefix = rankPrefix.replace("&", "§")
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(cleanPrefix)
                    } else {
                        Component.empty()
                    }
                    
                    // Extract rank color for username - use the same color as in the prefix
                    val rankColor = if (rankPrefix.isNotEmpty()) {
                        // Extract color from prefix (look for & color codes)
                        when {
                            rankPrefix.contains("&4") || rankPrefix.contains("§4") -> NamedTextColor.DARK_RED
                            rankPrefix.contains("&c") || rankPrefix.contains("§c") -> NamedTextColor.RED
                            rankPrefix.contains("&a") || rankPrefix.contains("§a") -> NamedTextColor.GREEN
                            rankPrefix.contains("&b") || rankPrefix.contains("§b") -> NamedTextColor.AQUA
                            rankPrefix.contains("&e") || rankPrefix.contains("§e") -> NamedTextColor.YELLOW
                            rankPrefix.contains("&d") || rankPrefix.contains("§d") -> NamedTextColor.LIGHT_PURPLE
                            rankPrefix.contains("&9") || rankPrefix.contains("§9") -> NamedTextColor.BLUE
                            rankPrefix.contains("&6") || rankPrefix.contains("§6") -> NamedTextColor.GOLD
                            rankPrefix.contains("&f") || rankPrefix.contains("§f") -> NamedTextColor.WHITE
                            rankPrefix.contains("&2") || rankPrefix.contains("§2") -> NamedTextColor.DARK_GREEN
                            rankPrefix.contains("&1") || rankPrefix.contains("§1") -> NamedTextColor.DARK_BLUE
                            rankPrefix.contains("&5") || rankPrefix.contains("§5") -> NamedTextColor.DARK_PURPLE
                            else -> {
                                // Try rank.color field as fallback
                                rank?.color?.let { color ->
                                    when (color.lowercase()) {
                                        "dark_red", "4" -> NamedTextColor.DARK_RED
                                        "red", "c" -> NamedTextColor.RED
                                        "green", "a" -> NamedTextColor.GREEN
                                        "aqua", "b" -> NamedTextColor.AQUA
                                        "yellow", "e" -> NamedTextColor.YELLOW
                                        "light_purple", "d" -> NamedTextColor.LIGHT_PURPLE
                                        "blue", "9" -> NamedTextColor.BLUE
                                        "gold", "6" -> NamedTextColor.GOLD
                                        "white", "f" -> NamedTextColor.WHITE
                                        else -> NamedTextColor.WHITE
                                    }
                                } ?: NamedTextColor.WHITE
                            }
                        }
                    } else {
                        NamedTextColor.GRAY // Default for no rank
                    }
                    
                    // Create player head item
                    val playerHead = ItemStack.builder(Material.PLAYER_HEAD)
                        .customName(
                            formattedPrefix.append(Component.text(onlinePlayer.username, rankColor))
                        )
                        .lore(listOf(
                            Component.text("Rank: $rankName", NamedTextColor.GRAY),
                            Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(vanishStatus)),
                            Component.text("Mode: ", NamedTextColor.GRAY).append(Component.text(staffModeStatus)),
                            Component.text(""),
                            Component.text("Click to teleport", NamedTextColor.GREEN)
                        ))
                        .build()
                    
                    // Set item with click handler for teleportation
                    setItem(slot, playerHead) { clicker, _ ->
                        // Teleport functionality
                        clicker.teleport(onlinePlayer.position)
                        clicker.sendMessage(Component.text("Teleported to ${onlinePlayer.username}", NamedTextColor.GREEN))
                        clicker.closeInventory()
                    }
                    
                    slot++
                    staffCount++
                }
                
                // If no staff found, add a placeholder
                if (staffCount == 0) {
                    val noStaffItem = ItemStack.builder(Material.BARRIER)
                        .customName(Component.text("No Staff Online", NamedTextColor.RED))
                        .lore(listOf(
                            Component.text("No staff members are currently online", NamedTextColor.GRAY)
                        ))
                        .build()
                    setItem(22, noStaffItem) // Center slot
                } else {
                    // Add info item at bottom
                    val infoItem = ItemStack.builder(Material.BOOK)
                        .customName(Component.text("Staff Information", NamedTextColor.AQUA))
                        .lore(listOf(
                            Component.text("Total Staff Online: $staffCount", NamedTextColor.GRAY),
                            Component.text("§cRed = Vanished", NamedTextColor.GRAY),
                            Component.text("§aGreen = Visible", NamedTextColor.GRAY),
                            Component.text("§eYellow = Staff Mode", NamedTextColor.GRAY)
                        ))
                        .build()
                    setItem(49, infoItem) // Bottom middle
                }
                
                // Open the inventory for the player using parent method
                open(player)
                
            } catch (e: Exception) {
                plugin.logger.error("Error opening staff online GUI for ${player.username}", e)
                player.sendMessage(Component.text("Error opening staff list", NamedTextColor.RED))
            }
        }
    }
}
