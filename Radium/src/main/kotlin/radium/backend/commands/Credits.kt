package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor
import java.text.NumberFormat
import java.util.*

/**
 * Command to manage credits currency system
 */
@Command("credits")
@Description("Manage credits currency")
class Credits(private val radium: Radium) {
    
    @Subcommand("add")
    @CommandPermission("radium.credits.admin")
    @Description("Add credits to a player")
    fun add(actor: VelocityCommandActor, playerName: String, amount: Long) {
        if (amount <= 0) {
            actor.reply(Component.text("Amount must be positive!", NamedTextColor.RED))
            return
        }
        
        radium.scope.launch {
                try {
                    val targetPlayer = radium.server.getPlayer(playerName).orElse(null)
                    val targetUuid = if (targetPlayer != null) {
                        targetPlayer.uniqueId
                    } else {
                        // Try to get UUID from profile
                        val profile = radium.connectionHandler.findPlayerProfile(playerName)
                        if (profile != null) {
                            profile.uuid
                        } else {
                            actor.reply(Component.text("Player '$playerName' not found!", NamedTextColor.RED))
                            return@launch
                        }
                    }
                    
                    val success = radium.creditsManager.addCredits(targetUuid, amount)
                    if (success) {
                        val newBalance = radium.creditsManager.getCredits(targetUuid)
                        val formattedAmount = NumberFormat.getNumberInstance().format(amount)
                        val formattedBalance = NumberFormat.getNumberInstance().format(newBalance)
                        
                        actor.reply(Component.text("Added $formattedAmount credits to $playerName!", NamedTextColor.GREEN))
                        actor.reply(Component.text("New balance: $formattedBalance credits", NamedTextColor.GRAY))
                        
                        // Notify target player if online
                        targetPlayer?.sendMessage(
                            Component.text("You received $formattedAmount credits from ${actor.name()}!", NamedTextColor.GREEN)
                        )
                        
                    } else {
                        actor.reply(Component.text("Failed to add credits!", NamedTextColor.RED))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error adding credits: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in credits add command", e)
                }
            }
        }
        
        @Subcommand("set")
        @CommandPermission("radium.credits.admin")
        @Description("Set a player's credits")
        fun set(actor: VelocityCommandActor, playerName: String, amount: Long) {
            if (amount < 0) {
                actor.reply(Component.text("Amount cannot be negative!", NamedTextColor.RED))
                return
            }
            
            radium.scope.launch {
                try {
                    val targetPlayer = radium.server.getPlayer(playerName).orElse(null)
                    val targetUuid = if (targetPlayer != null) {
                        targetPlayer.uniqueId
                    } else {
                        val profile = radium.connectionHandler.findPlayerProfile(playerName)
                        if (profile != null) {
                            profile.uuid
                        } else {
                            actor.reply(Component.text("Player '$playerName' not found!", NamedTextColor.RED))
                            return@launch
                        }
                    }
                    
                    val success = radium.creditsManager.setCredits(targetUuid, amount)
                    if (success) {
                        val formattedAmount = NumberFormat.getNumberInstance().format(amount)
                        
                        actor.reply(Component.text("Set $playerName's credits to $formattedAmount!", NamedTextColor.GREEN))
                        
                        // Notify target player if online
                        targetPlayer?.sendMessage(
                            Component.text("Your credits were set to $formattedAmount by ${actor.name()}!", NamedTextColor.YELLOW)
                        )
                        
                    } else {
                        actor.reply(Component.text("Failed to set credits!", NamedTextColor.RED))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error setting credits: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in credits set command", e)
                }
            }
        }
        
        @Subcommand("remove")
        @CommandPermission("radium.credits.admin")
        @Description("Remove credits from a player")
        fun remove(actor: VelocityCommandActor, playerName: String, amount: Long) {
            if (amount <= 0) {
                actor.reply(Component.text("Amount must be positive!", NamedTextColor.RED))
                return
            }
            
            radium.scope.launch {
                try {
                    val targetPlayer = radium.server.getPlayer(playerName).orElse(null)
                    val targetUuid = if (targetPlayer != null) {
                        targetPlayer.uniqueId
                    } else {
                        val profile = radium.connectionHandler.findPlayerProfile(playerName)
                        if (profile != null) {
                            profile.uuid
                        } else {
                            actor.reply(Component.text("Player '$playerName' not found!", NamedTextColor.RED))
                            return@launch
                        }
                    }
                    
                    val currentBalance = radium.creditsManager.getCredits(targetUuid)
                    if (currentBalance < amount) {
                        val formattedBalance = NumberFormat.getNumberInstance().format(currentBalance)
                        actor.reply(Component.text("$playerName only has $formattedBalance credits!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    val success = radium.creditsManager.removeCredits(targetUuid, amount)
                    if (success) {
                        val newBalance = radium.creditsManager.getCredits(targetUuid)
                        val formattedAmount = NumberFormat.getNumberInstance().format(amount)
                        val formattedBalance = NumberFormat.getNumberInstance().format(newBalance)
                        
                        actor.reply(Component.text("Removed $formattedAmount credits from $playerName!", NamedTextColor.GREEN))
                        actor.reply(Component.text("New balance: $formattedBalance credits", NamedTextColor.GRAY))
                        
                        // Notify target player if online
                        targetPlayer?.sendMessage(
                            Component.text("$formattedAmount credits were removed by ${actor.name()}!", NamedTextColor.RED)
                        )
                        
                    } else {
                        actor.reply(Component.text("Failed to remove credits!", NamedTextColor.RED))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error removing credits: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in credits remove command", e)
                }
            }
        }
        
        @Subcommand("bal", "balance")
        @CommandPermission("radium.credits.use")
        @Description("Check credits balance")
        fun balance(actor: VelocityCommandActor, playerName: String? = null) {
            radium.scope.launch {
                try {
                    if (playerName != null) {
                        // Check another player's balance (assume permission check would be done elsewhere or via annotations)
                        // For now, allow the command to proceed
                        
                        val targetPlayer = radium.server.getPlayer(playerName).orElse(null)
                        val targetUuid = if (targetPlayer != null) {
                            targetPlayer.uniqueId
                        } else {
                            val profile = radium.connectionHandler.findPlayerProfile(playerName)
                            if (profile != null) {
                                profile.uuid
                            } else {
                                actor.reply(Component.text("Player '$playerName' not found!", NamedTextColor.RED))
                                return@launch
                            }
                        }
                        
                        val balance = radium.creditsManager.getCredits(targetUuid)
                        val formattedBalance = NumberFormat.getNumberInstance().format(balance)
                        
                        actor.reply(Component.text("$playerName has $formattedBalance credits", NamedTextColor.GOLD))
                        
                    } else {
                        // Check own balance
                        if (!actor.isPlayer) {
                            actor.reply(Component.text("Console must specify a player name!", NamedTextColor.RED))
                            return@launch
                        }
                        
                        val player = actor.asPlayer()
                        val balance = radium.creditsManager.getCredits(player?.uniqueId ?: return@launch)
                        val formattedBalance = NumberFormat.getNumberInstance().format(balance)
                        
                        actor.reply(Component.text("You have $formattedBalance credits", NamedTextColor.GOLD))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error checking balance: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in credits balance command", e)
                }
            }
        }
    
    fun defaultCommand(actor: VelocityCommandActor) {
        actor.reply(Component.text("Credits Commands:", NamedTextColor.GOLD))
        actor.reply(Component.text("  /credits bal [player] - Check balance", NamedTextColor.GRAY))
        // Show admin commands if available (permission checking done via annotations)
        actor.reply(Component.text("  /credits add <player> <amount> - Add credits", NamedTextColor.GRAY))
        actor.reply(Component.text("  /credits set <player> <amount> - Set credits", NamedTextColor.GRAY))
        actor.reply(Component.text("  /credits remove <player> <amount> - Remove credits", NamedTextColor.GRAY))
    }
}
