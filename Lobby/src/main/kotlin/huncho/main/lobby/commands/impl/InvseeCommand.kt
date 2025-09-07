package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

class InvseeCommand(private val plugin: LobbyPlugin) : Command("invsee") {
    
    private val playerArg = ArgumentType.String("player")
    
    init {
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.invsee") {
                val targetName = context.get(playerArg)
                
                // Find target player
                val target = MinecraftServer.getConnectionManager().onlinePlayers
                    .find { it.username.equals(targetName, ignoreCase = true) }
                
                if (target == null) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.invsee.player-not-found", "&cPlayer '{player}' not found or is not online!")
                        .replace("{player}", targetName)
                        .replace("&", "§")
                    player.sendMessage(Component.text(message))
                    return@checkPermissionAndExecute
                }
                
                if (target.uuid == player.uuid) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.invsee.cannot-view-self", "&cYou cannot view your own inventory!")
                        .replace("&", "§")
                    player.sendMessage(Component.text(message))
                    return@checkPermissionAndExecute
                }
                
                // Send opening message first
                val openingMessage = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.invsee.opening", "&aOpening &e{target}'s &ainventory...")
                    .replace("{target}", target.username)
                    .replace("&", "§")
                player.sendMessage(Component.text(openingMessage))
                
                try {
                    // Create a new inventory with 6 rows (54 slots)
                    val inventory = Inventory(InventoryType.CHEST_6_ROW, Component.text("${target.username}'s Inventory"))
                    
                    // Copy the target's inventory contents (exactly like staff mode inspect)
                    for (slot in 0 until target.inventory.size) {
                        val item = target.inventory.getItemStack(slot)
                        if (!item.isAir) {
                            inventory.setItemStack(slot, item)
                        }
                    }
                    
                    // Open the inventory for the player
                    player.openInventory(inventory)
                } catch (e: Exception) {
                    player.sendMessage(Component.text("§cError opening inventory: " + e.message))
                    e.printStackTrace()
                }
            }
        }, playerArg)
    }
}
