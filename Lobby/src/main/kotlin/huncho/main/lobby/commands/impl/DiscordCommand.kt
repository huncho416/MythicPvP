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

class DiscordCommand(private val plugin: LobbyPlugin) : Command("discord") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            
            // Discord server information - No permission required as this should be public
            val discordInvite = "https://discord.gg/mythicpvp" // TODO: Replace with actual Discord invite
            
            player.sendMessage("§d§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
            player.sendMessage("")
            player.sendMessage("                    §d§lMYTHIC PVP DISCORD")
            player.sendMessage("")
            player.sendMessage("        §7Join our Discord community to stay connected!")
            player.sendMessage("")
            player.sendMessage("              §f• Chat with players")
            player.sendMessage("              §f• Get support")
            player.sendMessage("              §f• Stay updated on news")
            player.sendMessage("              §f• Participate in events")
            player.sendMessage("              §f• Report bugs & suggestions")
            player.sendMessage("")
            
            // Create clickable link
            val clickableLink = Component.text("                    §d§l» CLICK TO JOIN «")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(discordInvite))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open Discord invite", NamedTextColor.GRAY)))
            
            player.sendMessage(clickableLink)
            player.sendMessage("")
            player.sendMessage("              §7Link: §f$discordInvite")
            player.sendMessage("")
            player.sendMessage("§d§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        }
    }
}
