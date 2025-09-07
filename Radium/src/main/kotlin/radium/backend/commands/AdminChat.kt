package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Admin chat channel for admin staff and above
 */
@Command("adminchat", "ac")
@CommandPermission("radium.adminchat")
@Description("Admin chat channel")
class AdminChat(private val radium: Radium) {
    
    companion object {
        private val adminChatToggle = ConcurrentHashMap<UUID, Boolean>()
        
        fun isAdminChatEnabled(uuid: UUID): Boolean {
            return adminChatToggle[uuid] ?: false
        }
        
        fun setAdminChatEnabled(uuid: UUID, enabled: Boolean) {
            adminChatToggle[uuid] = enabled
        }
    }
    
    @Subcommand("toggle")
    @Description("Toggle admin chat mode")
    fun toggle(actor: VelocityCommandActor) {
            if (!actor.isPlayer) {
                actor.reply(Component.text("Console cannot toggle admin chat mode!", NamedTextColor.RED))
                return
            }
            
            val player = actor.asPlayer()
            val currentState = isAdminChatEnabled(player?.uniqueId ?: return)
            val newState = !currentState
            
            setAdminChatEnabled(player.uniqueId, newState)
            
            if (newState) {
                actor.reply(Component.text("Admin chat mode enabled! All messages will be sent to admin chat.", NamedTextColor.GREEN))
            } else {
                actor.reply(Component.text("Admin chat mode disabled! Messages will be sent normally.", NamedTextColor.YELLOW))
            }
        }
        
    @Command("adminchat <message>", "ac <message>")
    @Description("Send a message to admin chat")
    fun sendAdminMessage(actor: VelocityCommandActor, message: String) {
        sendMessage(actor, message)
    }
    
    @Subcommand("list")
    @Description("List online admin staff")
    fun list(actor: VelocityCommandActor) {
            radium.scope.launch {
                try {
                    val adminPlayers = mutableListOf<String>()
                    
                    radium.server.allPlayers.forEach { player ->
                        if (player.hasPermission("radium.adminchat")) {
                            val profile = radium.connectionHandler.getPlayerProfile(player.uniqueId, player.username)
                            val rank = if (profile != null) {
                                profile.getHighestRankCached(radium.rankManager)
                            } else null
                            
                            val prefix = rank?.prefix?.replace("&", "§") ?: ""
                            val suffix = rank?.suffix?.replace("&", "§") ?: ""
                            val toggleState = if (isAdminChatEnabled(player.uniqueId)) " §a[AC]" else ""
                            
                            adminPlayers.add("  §7- $prefix${player.username}$suffix$toggleState")
                        }
                    }
                    
                    val component = Component.text()
                        .append(Component.text("Online Admin Staff (${adminPlayers.size}):", NamedTextColor.RED))
                        .append(Component.newline())
                    
                    if (adminPlayers.isEmpty()) {
                        component.append(Component.text("  No admin staff are currently online.", NamedTextColor.GRAY))
                    } else {
                        adminPlayers.forEach { adminInfo ->
                            component.append(LegacyComponentSerializer.legacyAmpersand().deserialize(adminInfo))
                            component.append(Component.newline())
                        }
                    }
                    
                    actor.reply(component.build())
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Failed to list admin staff: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in admin chat list command", e)
                }
            }
        }
    
    fun sendMessage(actor: VelocityCommandActor, message: String) {
        radium.scope.launch {
            try {
                val senderProfile = if (actor.isPlayer) {
                    radium.connectionHandler.getPlayerProfile(actor.asPlayer()?.uniqueId ?: return@launch, actor.asPlayer()?.username ?: return@launch)
                } else null
                
                val senderRank = if (senderProfile != null) {
                    senderProfile.getHighestRankCached(radium.rankManager)
                } else null
                
                // Clean up rank prefix/suffix - remove any invalid characters and ensure proper format
                val senderPrefix = senderRank?.prefix?.let { prefix ->
                    // Remove any non-printable characters and normalize color codes
                    prefix.replace("∩┐╜", "&").replace("§", "&").trim()
                } ?: ""
                val senderSuffix = senderRank?.suffix?.let { suffix ->
                    // Remove any non-printable characters and normalize color codes  
                    suffix.replace("∩┐╜", "&").replace("§", "&").trim()
                } ?: ""
                
                // Format the admin chat message using & codes for the serializer
                val formattedMessage = if (actor.isPlayer) {
                    "&c[AC] $senderPrefix${actor.name()}$senderSuffix&f: $message"
                } else {
                    "&c[AC] &4[CONSOLE]&f: $message"
                }
                
                val chatComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(formattedMessage)
                
                // Send to all players with admin chat permission
                var recipientCount = 0
                radium.server.allPlayers.forEach { player ->
                    if (player.hasPermission("radium.adminchat")) {
                        player.sendMessage(chatComponent)
                        recipientCount++
                    }
                }
                
                // Log admin chat message
                radium.logger.info("[AdminChat] ${actor.name()}: $message")
                
                // Confirm to sender if no recipients
                if (recipientCount == 0) {
                    actor.reply(Component.text("No admin staff are currently online to receive your message.", NamedTextColor.YELLOW))
                }
                
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to send admin chat message: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error in admin chat command", e)
            }
        }
    }
}
