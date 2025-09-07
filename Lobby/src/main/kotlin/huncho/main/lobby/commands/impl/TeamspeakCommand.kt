package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

class TeamspeakCommand(private val plugin: LobbyPlugin) : Command("teamspeak", "ts") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            
            // TeamSpeak server information - No permission required as this should be public
            val teamspeakIP = "ts.mythicpvp.com" // TODO: Replace with actual TeamSpeak server
            
            player.sendMessage("§9§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
            player.sendMessage("")
            player.sendMessage("                   §9§lMYTHIC PVP TEAMSPEAK")
            player.sendMessage("")
            player.sendMessage("       §7Join our TeamSpeak server for voice communication!")
            player.sendMessage("")
            player.sendMessage("              §f• Voice chat with players")
            player.sendMessage("              §f• Talk to staff members")
            player.sendMessage("              §f• Coordinate in teams")
            player.sendMessage("              §f• Join community channels")
            player.sendMessage("              §f• Get real-time support")
            player.sendMessage("")
            
            // Create clickable link
            val clickableLink = Component.text("                    §9§l» CLICK TO CONNECT «")
                .color(NamedTextColor.BLUE)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("ts3server://$teamspeakIP"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to connect to TeamSpeak", NamedTextColor.GRAY)))
            
            player.sendMessage(clickableLink)
            player.sendMessage("")
            player.sendMessage("              §7IP: §f$teamspeakIP")
            player.sendMessage("              §7Port: §f9987 §8(default)")
            player.sendMessage("")
            player.sendMessage("§9§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        }
    }
}
