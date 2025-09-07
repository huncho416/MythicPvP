package radium.backend.punishment.commands

import revxrsal.commands.annotation.Command
import revxrsal.commands.velocity.annotation.CommandPermission
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import radium.backend.Radium
import radium.backend.punishment.alts.BanEvasionManager
import java.util.*

/**
 * Command to view alt accounts of a player
 * Follows the pattern of other punishment commands
 */
@Command("alts")
@CommandPermission("radium.command.alts")
class Alts(private val radium: Radium) {

    @Command("alts <target>")
    suspend fun onAltsCommand(
        sender: CommandSource,
        target: String
    ) {
        try {
            // Try to find the player online first
            val onlinePlayer = radium.server.getPlayer(target).orElse(null)
            val targetUuid = if (onlinePlayer != null) {
                onlinePlayer.uniqueId
            } else {
                // For offline players, search in the profile cache
                radium.connectionHandler.getAllProfiles().find { profile ->
                    profile.username.equals(target, ignoreCase = true)
                }?.uuid
            }
            
            if (targetUuid == null) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Player ", NamedTextColor.RED))
                        .append(Component.text(target, NamedTextColor.YELLOW))
                        .append(Component.text(" not found.", NamedTextColor.RED))
                        .build()
                )
                return
            }

            // Get target profile to ensure they exist
            val targetProfile = radium.connectionHandler.getPlayerProfile(targetUuid)
            if (targetProfile == null) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Profile for ", NamedTextColor.RED))
                        .append(Component.text(target, NamedTextColor.YELLOW))
                        .append(Component.text(" not found.", NamedTextColor.RED))
                        .build()
                )
                return
            }

            // Get alt accounts
            val altAccounts = radium.banEvasionManager.getAltAccountsByPlayer(targetUuid)
            
            if (altAccounts.isEmpty()) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("No alt accounts found for ", NamedTextColor.GRAY))
                        .append(Component.text(targetProfile.username, NamedTextColor.YELLOW))
                        .append(Component.text(".", NamedTextColor.GRAY))
                        .build()
                )
                return
            }

            // Send header
            sender.sendMessage(
                Component.text()
                    .append(Component.text("Alt accounts for ", NamedTextColor.GRAY))
                    .append(Component.text(targetProfile.username, NamedTextColor.YELLOW))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(altAccounts.size.toString(), NamedTextColor.WHITE))
                    .append(Component.text(" found):", NamedTextColor.GRAY))
                    .build()
            )

            // Group alts by IP
            val altsByIp = altAccounts.groupBy { it.ip }
            
            for ((ip, alts) in altsByIp) {
                // IP header
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("IP: ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(ip, NamedTextColor.AQUA))
                        .build()
                )

                // List alts for this IP
                for (alt in alts) {
                    val statusComponent = Component.text()
                        .append(Component.text("[", NamedTextColor.DARK_GRAY))
                        .append(Component.text(alt.status.displayName, alt.status.color))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY))
                        .build()

                    val nameComponent = Component.text(alt.username, NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.suggestCommand("/checkpunishments ${alt.username}"))
                        .hoverEvent(HoverEvent.showText(
                            Component.text()
                                .append(Component.text("UUID: ", NamedTextColor.GRAY))
                                .append(Component.text(alt.uuid.toString(), NamedTextColor.WHITE))
                                .append(Component.newline())
                                .append(Component.text("Last seen: ", NamedTextColor.GRAY))
                                .append(Component.text(alt.lastSeen.toString(), NamedTextColor.WHITE))
                                .append(Component.newline())
                                .append(Component.text("Click to check punishments", NamedTextColor.YELLOW))
                                .build()
                        ))

                    sender.sendMessage(
                        Component.text()
                            .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                            .append(statusComponent)
                            .append(Component.space())
                            .append(nameComponent)
                            .build()
                    )
                }
            }

            // Footer with additional actions
            if (sender is Player) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Click a name to check punishments, or use ", NamedTextColor.GRAY))
                        .append(Component.text("/dupeip <ip>", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand("/dupeip "))
                            .hoverEvent(HoverEvent.showText(Component.text("Check all accounts using an IP", NamedTextColor.YELLOW))))
                        .append(Component.text(" to check by IP.", NamedTextColor.GRAY))
                        .build()
                )
            }

        } catch (e: Exception) {
            sender.sendMessage(
                Component.text("An error occurred while retrieving alt accounts.", NamedTextColor.RED)
            )
            radium.logger.error("Error in /alts command for target ${target}: ${e.message}", e)
        }
    }
}
