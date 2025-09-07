package radium.backend.commands

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import radium.backend.Radium
import radium.backend.maintenance.MaintenanceManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Command to manage maintenance mode
 */
@Command("maintenance")
@CommandPermission("radium.command.maintenance")
@Description("Manage maintenance mode")
class Maintenance(private val radium: Radium) {
    
    @Subcommand("on")
    @Description("Enable global maintenance mode")
    fun enableGlobal(actor: VelocityCommandActor) {
            radium.scope.launch {
                try {
                    val enabled = radium.maintenanceManager.toggleGlobalMaintenance(actor.name())
                    if (enabled) {
                        actor.reply(Component.text("Global maintenance mode enabled!", NamedTextColor.GREEN))
                        actor.reply(Component.text("All non-staff players have been kicked.", NamedTextColor.GRAY))
                    } else {
                        actor.reply(Component.text("Global maintenance mode was already enabled.", NamedTextColor.YELLOW))
                    }
                } catch (e: Exception) {
                    actor.reply(Component.text("Error enabling maintenance: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in maintenance enable command", e)
                }
            }
        }
        
        @Subcommand("off")
        @Description("Disable global maintenance mode")
        fun disableGlobal(actor: VelocityCommandActor) {
            radium.scope.launch {
                try {
                    val disabled = !radium.maintenanceManager.toggleGlobalMaintenance(actor.name())
                    if (disabled) {
                        actor.reply(Component.text("Global maintenance mode disabled!", NamedTextColor.GREEN))
                        actor.reply(Component.text("Players can now join the network.", NamedTextColor.GRAY))
                    } else {
                        actor.reply(Component.text("Global maintenance mode was already disabled.", NamedTextColor.YELLOW))
                    }
                } catch (e: Exception) {
                    actor.reply(Component.text("Error disabling maintenance: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in maintenance disable command", e)
                }
            }
        }
        
        @Subcommand("server")
        @Description("Toggle maintenance for a specific server")
        fun toggleServer(actor: VelocityCommandActor, serverName: String, state: String) {
            if (state != "on" && state != "off") {
                actor.reply(Component.text("Usage: /maintenance server <server> <on|off>", NamedTextColor.RED))
                return
            }
            
            radium.scope.launch {
                try {
                    // Check if server exists
                    val server = radium.server.getServer(serverName).orElse(null)
                    if (server == null) {
                        actor.reply(Component.text("Server '$serverName' not found!", NamedTextColor.RED))
                        return@launch
                    }
                    
                    val enabled = if (state == "on") {
                        radium.maintenanceManager.toggleServerMaintenance(serverName, actor.name())
                    } else {
                        !radium.maintenanceManager.toggleServerMaintenance(serverName, actor.name())
                    }
                    
                    if (state == "on" && enabled) {
                        actor.reply(Component.text("Maintenance enabled for $serverName!", NamedTextColor.GREEN))
                        actor.reply(Component.text("Non-staff players have been moved or kicked.", NamedTextColor.GRAY))
                    } else if (state == "off" && !enabled) {
                        actor.reply(Component.text("Maintenance disabled for $serverName!", NamedTextColor.GREEN))
                        actor.reply(Component.text("Players can now join $serverName.", NamedTextColor.GRAY))
                    } else {
                        actor.reply(Component.text("Maintenance for $serverName was already ${state}.", NamedTextColor.YELLOW))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error toggling server maintenance: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in maintenance server command", e)
                }
            }
        }
        
        @Subcommand("status")
        @Description("Check maintenance status")
        fun status(actor: VelocityCommandActor) {
            radium.scope.launch {
                try {
                    val globalStatus = if (MaintenanceManager.isGlobalMaintenanceEnabled()) "&cEnabled" else "&aDisabled"
                    
                    actor.reply(Component.text("Maintenance Status:", NamedTextColor.GOLD))
                    actor.reply(Component.text("  Global: $globalStatus", NamedTextColor.GRAY))
                    
                    // Check individual servers
                    val serversInMaintenance = mutableListOf<String>()
                    radium.server.allServers.forEach { server ->
                        if (MaintenanceManager.isServerMaintenanceEnabled(server.serverInfo.name)) {
                            serversInMaintenance.add(server.serverInfo.name)
                        }
                    }
                    
                    if (serversInMaintenance.isNotEmpty()) {
                        actor.reply(Component.text("  Servers in maintenance: ${serversInMaintenance.joinToString(", ")}", NamedTextColor.GRAY))
                    } else {
                        actor.reply(Component.text("  No servers in maintenance", NamedTextColor.GRAY))
                    }
                    
                } catch (e: Exception) {
                    actor.reply(Component.text("Error checking maintenance status: ${e.message}", NamedTextColor.RED))
                    radium.logger.error("Error in maintenance status command", e)
                }
            }
        }
    
    @CommandPermission("radium.command.maintenance")
    fun defaultCommand(actor: VelocityCommandActor) {
        actor.reply(Component.text("Maintenance Commands:", NamedTextColor.GOLD))
        actor.reply(Component.text("  /maintenance on - Enable global maintenance", NamedTextColor.GRAY))
        actor.reply(Component.text("  /maintenance off - Disable global maintenance", NamedTextColor.GRAY))
        actor.reply(Component.text("  /maintenance server <server> <on|off> - Toggle server maintenance", NamedTextColor.GRAY))
        actor.reply(Component.text("  /maintenance status - Check maintenance status", NamedTextColor.GRAY))
    }
}
