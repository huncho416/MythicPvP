package radium.backend.maintenance

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import radium.backend.Radium
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages maintenance mode for the network
 */
class MaintenanceManager(private val radium: Radium) {
    
    companion object {
        private val globalMaintenance = AtomicBoolean(false)
        private val serverMaintenance = mutableMapOf<String, Boolean>()
        
        fun isGlobalMaintenanceEnabled(): Boolean {
            return globalMaintenance.get()
        }
        
        fun isServerMaintenanceEnabled(serverName: String): Boolean {
            return serverMaintenance[serverName] ?: false
        }
        
        fun setGlobalMaintenance(enabled: Boolean) {
            globalMaintenance.set(enabled)
        }
        
        fun setServerMaintenance(serverName: String, enabled: Boolean) {
            if (enabled) {
                serverMaintenance[serverName] = true
            } else {
                serverMaintenance.remove(serverName)
            }
        }
    }
    
    /**
     * Toggle global maintenance mode
     */
    fun toggleGlobalMaintenance(staffName: String): Boolean {
        val newState = !isGlobalMaintenanceEnabled()
        setGlobalMaintenance(newState)
        
        // Broadcast maintenance status change
        broadcastMaintenanceChange("global", newState, staffName)
        
        if (newState) {
            // Kick non-staff players when enabling maintenance
            kickNonStaffPlayers("Global maintenance mode has been enabled.")
        }
        
        radium.logger.info("$staffName ${if (newState) "enabled" else "disabled"} global maintenance mode")
        return newState
    }
    
    /**
     * Toggle server-specific maintenance mode
     */
    fun toggleServerMaintenance(serverName: String, staffName: String): Boolean {
        val newState = !isServerMaintenanceEnabled(serverName)
        setServerMaintenance(serverName, newState)
        
        // Broadcast maintenance status change
        broadcastMaintenanceChange(serverName, newState, staffName)
        
        if (newState) {
            // Kick non-staff players from specific server
            kickNonStaffPlayersFromServer(serverName, "Maintenance mode has been enabled for this server.")
        }
        
        radium.logger.info("$staffName ${if (newState) "enabled" else "disabled"} maintenance mode for $serverName")
        return newState
    }
    
    /**
     * Check if a player can join during maintenance
     */
    fun canPlayerJoin(player: com.velocitypowered.api.proxy.Player, serverName: String?): Boolean {
        // Staff can always join
        if (player.hasPermission("radium.maintenance.bypass") || player.hasPermission("radium.staff")) {
            return true
        }
        
        // Check global maintenance
        if (isGlobalMaintenanceEnabled()) {
            return false
        }
        
        // Check server-specific maintenance
        if (serverName != null && isServerMaintenanceEnabled(serverName)) {
            return false
        }
        
        return true
    }
    
    /**
     * Get maintenance kick message
     */
    fun getMaintenanceKickMessage(isGlobal: Boolean, serverName: String? = null): Component {
        return if (isGlobal) {
            LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "&c&lMYTHICPVP NETWORK\n" +
                "&c&lMAINTENANCE MODE\n" +
                "\n" +
                "&7The network is currently undergoing maintenance.\n" +
                "&7Please check back later!\n" +
                "\n" +
                "&7Discord: &bdiscord.mythicpvp.com\n" +
                "&7Website: &bmythicpvp.com\n" +
                "\n" +
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            )
        } else {
            LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "&c&l${serverName?.uppercase() ?: "SERVER"} MAINTENANCE\n" +
                "\n" +
                "&7This server is currently undergoing maintenance.\n" +
                "&7Please try another server or check back later!\n" +
                "\n" +
                "&7Discord: &bdiscord.mythicpvp.com\n" +
                "&7Website: &bmythicpvp.com\n" +
                "\n" +
                "&c&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            )
        }
    }
    
    /**
     * Broadcast maintenance status change to staff
     */
    private fun broadcastMaintenanceChange(target: String, enabled: Boolean, staffName: String) {
        radium.scope.launch {
            val action = if (enabled) "enabled" else "disabled"
            val message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&e$staffName $action maintenance mode for $target"
            )
            
            radium.server.allPlayers.forEach { player ->
                if (player.hasPermission("radium.staff")) {
                    player.sendMessage(message)
                }
            }
        }
    }
    
    /**
     * Kick non-staff players when maintenance is enabled
     */
    private fun kickNonStaffPlayers(reason: String) {
        radium.scope.launch {
            val kickMessage = getMaintenanceKickMessage(true)
            
            radium.server.allPlayers.forEach { player ->
                if (!player.hasPermission("radium.maintenance.bypass") && !player.hasPermission("radium.staff")) {
                    player.disconnect(kickMessage)
                }
            }
        }
    }
    
    /**
     * Kick non-staff players from specific server
     */
    private fun kickNonStaffPlayersFromServer(serverName: String, reason: String) {
        radium.scope.launch {
            val kickMessage = getMaintenanceKickMessage(false, serverName)
            
            radium.server.allPlayers.forEach { player ->
                val currentServer = player.currentServer.orElse(null)
                if (currentServer?.serverInfo?.name == serverName) {
                    if (!player.hasPermission("radium.maintenance.bypass") && !player.hasPermission("radium.staff")) {
                        // Try to send them to lobby, if that fails, disconnect them
                        val lobbyServer = radium.server.getServer("lobby").orElse(null)
                        if (lobbyServer != null && lobbyServer.serverInfo.name != serverName) {
                            player.createConnectionRequest(lobbyServer).fireAndForget()
                            player.sendMessage(Component.text("$serverName is now in maintenance mode. You've been moved to lobby.", NamedTextColor.YELLOW))
                        } else {
                            player.disconnect(kickMessage)
                        }
                    }
                }
            }
        }
    }
}
