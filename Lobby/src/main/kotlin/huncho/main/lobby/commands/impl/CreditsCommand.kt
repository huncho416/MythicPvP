package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.MessageUtils.hasPermissionAsync
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Credits Command - Manages the credits currency system
 */
class CreditsCommand(private val plugin: LobbyPlugin) : Command("credits") {
    
    private val actionArg = ArgumentType.Word("action").from("add", "set", "remove", "bal", "balance")
    private val playerArg = ArgumentType.Word("player")
    private val amountArg = ArgumentType.Integer("amount")
    
    init {
        // /credits bal - show own balance
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            val action = context.get(actionArg)
            
            when (action.lowercase()) {
                "bal", "balance" -> {
                    GlobalScope.launch {
                        try {
                            val balance = plugin.creditsManager.getBalance(player.uuid)
                            player.sendMessage("§6§lCredits Balance")
                            player.sendMessage("§7Your balance: §e$balance credits")
                        } catch (e: Exception) {
                            player.sendMessage("§c§lError!")
                            player.sendMessage("§7Failed to get balance: ${e.message}")
                        }
                    }
                }
                else -> {
                    player.sendMessage("§c§lUsage:")
                    player.sendMessage("§7/credits bal - Check your balance")
                }
            }
        }, actionArg)
        
        // /credits <action> <player> <amount>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            val action = context.get(actionArg)
            val targetName = context.get(playerArg)
            val amount = context.get(amountArg)
            
            when (action.lowercase()) {
                "add" -> {
                    player.checkPermissionAndExecute("hub.command.credits.add") {
                        GlobalScope.launch {
                            try {
                                val result = plugin.creditsManager.addCredits(targetName, amount)
                                if (result.isSuccess) {
                                    val newBalance = result.getOrNull() ?: 0
                                    player.sendMessage("§a§lCredits Added!")
                                    player.sendMessage("§7Added §e$amount credits §7to §f$targetName")
                                    player.sendMessage("§7New balance: §e$newBalance credits")
                                } else {
                                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                    player.sendMessage("§c§lError!")
                                    player.sendMessage("§7$error")
                                }
                            } catch (e: Exception) {
                                player.sendMessage("§c§lError!")
                                player.sendMessage("§7Failed to add credits: ${e.message}")
                            }
                        }
                    }
                }
                
                "set" -> {
                    player.checkPermissionAndExecute("hub.command.credits.set") {
                        GlobalScope.launch {
                            try {
                                val result = plugin.creditsManager.setCredits(targetName, amount)
                                if (result.isSuccess) {
                                    player.sendMessage("§a§lCredits Set!")
                                    player.sendMessage("§7Set §f$targetName's §7balance to §e$amount credits")
                                } else {
                                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                    player.sendMessage("§c§lError!")
                                    player.sendMessage("§7$error")
                                }
                            } catch (e: Exception) {
                                player.sendMessage("§c§lError!")
                                player.sendMessage("§7Failed to set credits: ${e.message}")
                            }
                        }
                    }
                }
                
                "remove" -> {
                    player.checkPermissionAndExecute("hub.command.credits.remove") {
                        GlobalScope.launch {
                            try {
                                val result = plugin.creditsManager.removeCredits(targetName, amount)
                                if (result.isSuccess) {
                                    val newBalance = result.getOrNull() ?: 0
                                    player.sendMessage("§c§lCredits Removed!")
                                    player.sendMessage("§7Removed §e$amount credits §7from §f$targetName")
                                    player.sendMessage("§7New balance: §e$newBalance credits")
                                } else {
                                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                    player.sendMessage("§c§lError!")
                                    player.sendMessage("§7$error")
                                }
                            } catch (e: Exception) {
                                player.sendMessage("§c§lError!")
                                player.sendMessage("§7Failed to remove credits: ${e.message}")
                            }
                        }
                    }
                }
                
                else -> {
                    player.sendMessage("§c§lUsage:")
                    player.sendMessage("§7/credits add <player> <amount>")
                    player.sendMessage("§7/credits set <player> <amount>")
                    player.sendMessage("§7/credits remove <player> <amount>")
                }
            }
        }, actionArg, playerArg, amountArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.sendMessage("§6§lCredits Commands:")
            player.sendMessage("§7/credits bal - Check your balance")
            player.hasPermissionAsync("hub.command.credits.admin") { hasAdminPermission ->
                if (hasAdminPermission) {
                    player.sendMessage("§7/credits add <player> <amount> - Add credits")
                    player.sendMessage("§7/credits set <player> <amount> - Set credits")
                    player.sendMessage("§7/credits remove <player> <amount> - Remove credits")
                }
            }
        }
    }
}
