package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class StaffModeCommand(private val plugin: LobbyPlugin) : Command("staffmode", "sm") {
    
    init {
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    PermissionCache.hasPermissionCached(sender, "radium.staff") ||
                    PermissionCache.hasPermissionCached(sender, "hub.staff.mode") ||
                    PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.staff.mode") {
                // Toggle staff mode - let the manager handle all messages
                plugin.staffModeManager.toggleStaffMode(player)
            }
        }
    }
}
