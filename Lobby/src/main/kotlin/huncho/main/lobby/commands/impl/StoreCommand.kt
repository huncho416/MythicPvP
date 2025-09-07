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

class StoreCommand(private val plugin: LobbyPlugin) : Command("store", "shop", "buy") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            
            // Store information - No permission required as this should be public
            val storeURL = "https://store.mythicpvp.com" // TODO: Replace with actual store URL
            
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
            player.sendMessage("")
            player.sendMessage("                     §6§lMYTHIC PVP STORE")
            player.sendMessage("")
            player.sendMessage("            §7Support the server and get amazing perks!")
            player.sendMessage("")
            player.sendMessage("              §f• Premium ranks & permissions")
            player.sendMessage("              §f• Exclusive cosmetics & pets")
            player.sendMessage("              §f• Crate keys & mystery boxes")
            player.sendMessage("              §f• Server boosts & multipliers")
            player.sendMessage("              §f• Custom items & tools")
            player.sendMessage("              §f• VIP features & privileges")
            player.sendMessage("")
            
            // Create clickable link
            val clickableLink = Component.text("                    §6§l» VISIT STORE «")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(storeURL))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open the store", NamedTextColor.GRAY)))
            
            player.sendMessage(clickableLink)
            player.sendMessage("")
            player.sendMessage("              §7URL: §f$storeURL")
            player.sendMessage("              §7Secure payments via PayPal & Stripe")
            player.sendMessage("")
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
            
            // Log store command usage
            plugin.logger.info("${player.username} viewed store information")
        }
    }
}
