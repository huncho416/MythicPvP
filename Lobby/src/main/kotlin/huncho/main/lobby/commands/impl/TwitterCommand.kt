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

class TwitterCommand(private val plugin: LobbyPlugin) : Command("twitter") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            
            // Twitter account information - No permission required as this should be public
            val twitterHandle = "@MythicPvP" // TODO: Replace with actual Twitter handle
            val twitterURL = "https://twitter.com/MythicPvP"
            
            player.sendMessage("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
            player.sendMessage("")
            player.sendMessage("                     §b§lMYTHIC PVP TWITTER")
            player.sendMessage("")
            player.sendMessage("         §7Follow us on Twitter for the latest updates!")
            player.sendMessage("")
            player.sendMessage("              §f• Server announcements")
            player.sendMessage("              §f• Update notifications")
            player.sendMessage("              §f• Event information")
            player.sendMessage("              §f• Community highlights")
            player.sendMessage("              §f• Behind-the-scenes content")
            player.sendMessage("")
            
            // Create clickable link
            val clickableLink = Component.text("                     §b§l» FOLLOW US «")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(twitterURL))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open Twitter profile", NamedTextColor.GRAY)))
            
            player.sendMessage(clickableLink)
            player.sendMessage("")
            player.sendMessage("              §7Handle: §f$twitterHandle")
            player.sendMessage("              §7URL: §f$twitterURL")
            player.sendMessage("")
            player.sendMessage("§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        }
    }
}
