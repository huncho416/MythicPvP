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
import radium.backend.Radium
import radium.backend.punishment.alts.BanEvasionManager
import java.net.InetAddress

/**
 * Command to view all players who have used a specific IP address
 * Follows the pattern of other punishment commands
 */
@Command("dupeip")
@CommandPermission("radium.command.dupeip")
class DupeIp(private val radium: Radium) {

    @Command("dupeip <ip>")
    suspend fun onDupeIpCommand(
        sender: CommandSource,
        ip: String
    ) {
        try {
            // Validate IP address format
            if (!isValidIpAddress(ip)) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Invalid IP address format: ", NamedTextColor.RED))
                        .append(Component.text(ip, NamedTextColor.YELLOW))
                        .build()
                )
                return
            }

            // Get all accounts using this IP
            val accounts = radium.banEvasionManager.getPlayersByIp(ip)
            
            if (accounts.isEmpty()) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("No accounts found using IP ", NamedTextColor.GRAY))
                        .append(Component.text(ip, NamedTextColor.YELLOW))
                        .append(Component.text(".", NamedTextColor.GRAY))
                        .build()
                )
                return
            }

            // Send header
            sender.sendMessage(
                Component.text()
                    .append(Component.text("Accounts using IP ", NamedTextColor.GRAY))
                    .append(Component.text(ip, NamedTextColor.YELLOW))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(accounts.size.toString(), NamedTextColor.WHITE))
                    .append(Component.text(" found):", NamedTextColor.GRAY))
                    .build()
            )

            // Group by status for better organization
            val accountsByStatus = accounts.groupBy { it.status }
            
            // Display online accounts first
            accountsByStatus[BanEvasionManager.AltStatus.ONLINE]?.let { onlineAccounts ->
                if (onlineAccounts.isNotEmpty()) {
                    sender.sendMessage(
                        Component.text("Online:", NamedTextColor.GREEN)
                    )
                    onlineAccounts.forEach { account ->
                        sendAccountInfo(sender, account)
                    }
                }
            }
            
            // Then banned accounts
            accountsByStatus[BanEvasionManager.AltStatus.BANNED]?.let { bannedAccounts ->
                if (bannedAccounts.isNotEmpty()) {
                    sender.sendMessage(
                        Component.text("Banned:", NamedTextColor.RED)
                    )
                    bannedAccounts.forEach { account ->
                        sendAccountInfo(sender, account)
                    }
                }
            }
            
            // Finally offline accounts
            accountsByStatus[BanEvasionManager.AltStatus.OFFLINE]?.let { offlineAccounts ->
                if (offlineAccounts.isNotEmpty()) {
                    sender.sendMessage(
                        Component.text("Offline:", NamedTextColor.GRAY)
                    )
                    offlineAccounts.forEach { account ->
                        sendAccountInfo(sender, account)
                    }
                }
            }

            // Footer with additional actions
            if (sender is Player) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text("Click a name to check punishments, or use ", NamedTextColor.GRAY))
                        .append(Component.text("/alts <player>", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand("/alts "))
                            .hoverEvent(HoverEvent.showText(Component.text("Check alt accounts of a player", NamedTextColor.YELLOW))))
                        .append(Component.text(" to check by player.", NamedTextColor.GRAY))
                        .build()
                )
            }

        } catch (e: Exception) {
            sender.sendMessage(
                Component.text("An error occurred while retrieving accounts for this IP.", NamedTextColor.RED)
            )
            radium.logger.error("Error in /dupeip command for IP ${ip}: ${e.message}", e)
        }
    }

    private fun sendAccountInfo(sender: CommandSource, account: BanEvasionManager.AltAccount) {
        val statusComponent = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(account.status.displayName, account.status.color))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .build()

        val nameComponent = Component.text(account.username, NamedTextColor.WHITE)
            .clickEvent(ClickEvent.suggestCommand("/checkpunishments ${account.username}"))
            .hoverEvent(HoverEvent.showText(
                Component.text()
                    .append(Component.text("UUID: ", NamedTextColor.GRAY))
                    .append(Component.text(account.uuid.toString(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Last seen: ", NamedTextColor.GRAY))
                    .append(Component.text(account.lastSeen.toString(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Click to check punishments", NamedTextColor.YELLOW))
                    .build()
            ))

        val timeComponent = Component.text(
            formatTimeAgo(account.lastSeen),
            NamedTextColor.DARK_GRAY
        )

        sender.sendMessage(
            Component.text()
                .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                .append(statusComponent)
                .append(Component.space())
                .append(nameComponent)
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(timeComponent)
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
                .build()
        )
    }

    /**
     * Validates if the given string is a valid IP address
     */
    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip)
            // Additional validation for IPv4 format
            val parts = ip.split(".")
            if (parts.size == 4) {
                parts.all { part ->
                    try {
                        val num = part.toInt()
                        num in 0..255
                    } catch (e: NumberFormatException) {
                        false
                    }
                }
            } else {
                // Could be IPv6 or other format, let InetAddress handle validation
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format a timestamp as "X time ago"
     */
    private fun formatTimeAgo(instant: java.time.Instant): String {
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        
        return when {
            duration.toDays() > 0 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "${duration.seconds}s ago"
        }
    }
}
