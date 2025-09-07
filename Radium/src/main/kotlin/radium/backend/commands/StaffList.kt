package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to list staff online similar to staff mode's stafflist option
 */
class StaffList(private val radium: Radium) {
    
    @Command("stafflist")
    @CommandPermission("radium.command.stafflist")
    @Description("Lists staff online")
    fun stafflist(actor: VelocityCommandActor) {
        radium.scope.launch {
            try {
                val staffPlayers = mutableListOf<String>()
                
                // Get all online players and check if they have staff permissions
                radium.server.allPlayers.forEach { player ->
                    val profile = radium.connectionHandler.getPlayerProfile(player.uniqueId, player.username)
                    if (profile != null) {
                        // Check if player has any staff permission
                        if (profile.hasEffectivePermission("radium.staff") || 
                            profile.hasEffectivePermission("radium.command.*") ||
                            profile.hasEffectivePermission("*")) {
                            
                            val rank = profile.getHighestRankCached(radium.rankManager)
                            val vanishData = radium.networkVanishManager.getVanishData(player.uniqueId)
                            val isVanished = vanishData != null
                            
                            val serverName = player.currentServer.map { it.serverInfo.name }.orElse("None")
                            val prefix = rank?.prefix?.replace("&", "§") ?: ""
                            val suffix = rank?.suffix?.replace("&", "§") ?: ""
                            val vanishIndicator = if (isVanished) " §7[V]" else ""
                            
                            staffPlayers.add("  §7- $prefix${player.username}$suffix§7 on §b$serverName$vanishIndicator")
                        }
                    }
                }
                
                // Build and send the response
                val component = Component.text()
                    .append(Component.text("═══════════════════════════════════", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH))
                    .append(Component.newline())
                    .append(Component.text("Online Staff (${staffPlayers.size})", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("═══════════════════════════════════", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH))
                    .append(Component.newline())
                
                if (staffPlayers.isEmpty()) {
                    component.append(Component.text("  No staff members are currently online.", NamedTextColor.GRAY))
                } else {
                    staffPlayers.forEach { staffInfo ->
                        component.append(Component.text(staffInfo.replace("§", "&"), NamedTextColor.WHITE))
                        component.append(Component.newline())
                    }
                }
                
                component.append(Component.text("═══════════════════════════════════", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH))
                
                actor.reply(component.build())
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to retrieve staff list: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing stafflist command", e)
            }
        }
    }
}
