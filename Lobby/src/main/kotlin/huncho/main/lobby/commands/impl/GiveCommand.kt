package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Give Command - Gives items to players
 */
class GiveCommand(private val plugin: LobbyPlugin) : Command("give") {
    
    private val playerArg = ArgumentType.Word("player")
    private val itemArg = ArgumentType.Word("item")
    private val amountArg = ArgumentType.Integer("amount").setDefaultValue(1)
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.give")
        }
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.give") {
                val targetName = context.get(playerArg)
                val itemName = context.get(itemArg)
                val amount = context.get(amountArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.giveItem(player, targetName, itemName, amount)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lItem Given!")
                            player.sendMessage("§7Gave §e$amount §f$itemName §7to §f$targetName")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to give item: ${e.message}")
                        plugin.logger.error("Error giving item", e)
                    }
                }
            }
        }, playerArg, itemArg, amountArg)
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.give") {
                val targetName = context.get(playerArg)
                val itemName = context.get(itemArg)
                
                GlobalScope.launch {
                    try {
                        val result = plugin.adminCommandManager.giveItem(player, targetName, itemName, 1)
                        
                        if (result.isSuccess) {
                            player.sendMessage("§a§lItem Given!")
                            player.sendMessage("§7Gave §e1 §f$itemName §7to §f$targetName")
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7$error")
                        }
                        
                    } catch (e: Exception) {
                        player.sendMessage("§c§lError!")
                        player.sendMessage("§7Failed to give item: ${e.message}")
                        plugin.logger.error("Error giving item", e)
                    }
                }
            }
        }, playerArg, itemArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.give") {
                player.sendMessage("§6§lUsage:")
                player.sendMessage("§7/give <player> <item> [amount]")
                player.sendMessage("§7Example: /give Player123 diamond_sword 1")
            }
        }
    }
}
