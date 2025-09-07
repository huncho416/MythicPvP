package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

class GodCommand(private val plugin: LobbyPlugin) : Command("god", "godmode") {
    
    companion object {
        private val godModePlayers = ConcurrentHashMap<String, Boolean>()
        
        fun isInGodMode(player: Player): Boolean {
            return godModePlayers[player.uuid.toString()] ?: false
        }
    }
    
    private val playerArg = ArgumentType.Word("player")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.god.self") ||
            PermissionCache.hasPermissionCached(sender as Player, "hub.command.god.others")
        }
        
        // TODO: Register god mode protection listener when proper events are available
        // registerGodModeListener()
        
        // Command with optional player argument
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.god") {
                val targetName = context.get(playerArg)
                val target = MinecraftServer.getConnectionManager().onlinePlayers.find { 
                    it.username.equals(targetName, ignoreCase = true) 
                }
                
                if (target == null) {
                    player.sendMessage("§c§lPlayer Not Found!")
                    player.sendMessage("§7Player §f$targetName §7is not online.")
                    return@checkPermissionAndExecute
                }
                
                if (target != player) {
                    // TODO: Check permission when Radium integration is available
                    // if (!player.hasPermission("hub.command.god.others")) {
                        player.sendMessage("§c§lNo Permission!")
                        player.sendMessage("§7You don't have permission to toggle god mode for other players.")
                        return@checkPermissionAndExecute
                    // }
                }
                
                toggleGodMode(player, target)
            }
        }, playerArg)
        
        // Command without arguments (self)
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.god") {
                toggleGodMode(player, player)
            }
        }
    }
    
    private fun toggleGodMode(executor: Player, target: Player) {
        val currentState = isInGodMode(target)
        val newState = !currentState
        
        godModePlayers[target.uuid.toString()] = newState
        
        if (newState) {
            // Enable god mode
            target.sendMessage("§a§lGod Mode Enabled!")
            target.sendMessage("§7You are now invulnerable to damage.")
            
            if (executor != target) {
                executor.sendMessage("§a§lGod Mode Enabled!")
                executor.sendMessage("§7Enabled god mode for §f${target.username}§7.")
            }
            
            plugin.logger.info("God mode enabled for ${target.username} by ${executor.username}")
        } else {
            // Disable god mode
            target.sendMessage("§c§lGod Mode Disabled!")
            target.sendMessage("§7You are now vulnerable to damage.")
            
            if (executor != target) {
                executor.sendMessage("§c§lGod Mode Disabled!")
                executor.sendMessage("§7Disabled god mode for §f${target.username}§7.")
            }
            
            plugin.logger.info("God mode disabled for ${target.username} by ${executor.username}")
        }
    }
}
