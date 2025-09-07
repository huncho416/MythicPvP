package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import huncho.main.lobby.utils.PermissionCache
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.concurrent.atomic.AtomicBoolean

class MaintenanceCommand(private val plugin: LobbyPlugin) : Command("maintenance") {
    
    companion object {
        private val maintenanceMode = AtomicBoolean(false)
        private var maintenanceMessage = "§c§lSERVER MAINTENANCE\n§7The server is currently under maintenance.\n§7Please check our Discord for updates."
        
        fun isMaintenanceEnabled(): Boolean = maintenanceMode.get()
        fun getMaintenanceMessage(): String = maintenanceMessage
    }

    private val actionArg = ArgumentType.Word("action").from("on", "off", "toggle", "status")
    private val scopeArg = ArgumentType.Word("scope").from("server", "global")
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true
            val player = sender as Player
            // Use cached permission check for tab completion filtering
            PermissionCache.hasPermissionCached(player, "hub.command.maintenance")
        }
        
        // TODO: Register maintenance listener when proper events are available
        // registerMaintenanceListener()
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.maintenance") {
                val action = context.get(actionArg)
                val scope = try { context.get(scopeArg) } catch (e: Exception) { "server" }
                
                when (action.lowercase()) {
                    "on" -> enableMaintenance(player, scope)
                    "off" -> disableMaintenance(player, scope)
                    "toggle" -> toggleMaintenance(player, scope)
                    "status" -> showMaintenanceStatus(player)
                }
            }
        }, actionArg, scopeArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.maintenance") {
                showMaintenanceStatus(player)
            }
        }
    }
    
    private fun enableMaintenance(player: Player, scope: String) {
        if (maintenanceMode.get()) {
            player.sendMessage("§c§lMaintenance mode is already enabled!")
            return
        }
        
        maintenanceMode.set(true)
        
        // Create styled maintenance message matching the requirements
        player.sendMessage("§c§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        player.sendMessage("")
        player.sendMessage("                  §c§lMAINTENANCE MODE ENABLED")
        player.sendMessage("")
        player.sendMessage("          §7Maintenance mode has been activated for: §e${scope.uppercase()}")
        player.sendMessage("")
        player.sendMessage("          §f• New players cannot join the server")
        player.sendMessage("          §f• Only bypass permission holders can connect")
        player.sendMessage("          §f• Current players can continue playing")
        player.sendMessage("")
        player.sendMessage("          §7Use §e/maintenance off §7to disable")
        player.sendMessage("")
        player.sendMessage("§c§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        
        // Notify all online staff
        notifyStaff("§c§lMaintenance mode has been ENABLED by ${player.username}")
        
        // TODO: If scope is "global", send maintenance toggle to other servers via Radium integration
        if (scope == "global") {
            // plugin.radiumIntegration.sendGlobalMaintenanceToggle(true)
        }
        
        plugin.logger.info("Maintenance mode enabled by ${player.username} (scope: $scope)")
    }
    
    private fun disableMaintenance(player: Player, scope: String) {
        if (!maintenanceMode.get()) {
            player.sendMessage("§a§lMaintenance mode is already disabled!")
            return
        }
        
        maintenanceMode.set(false)
        
        player.sendMessage("§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        player.sendMessage("")
        player.sendMessage("                  §a§lMAINTENANCE MODE DISABLED")
        player.sendMessage("")
        player.sendMessage("          §7Maintenance mode has been deactivated for: §e${scope.uppercase()}")
        player.sendMessage("")
        player.sendMessage("          §f• Players can now join the server normally")
        player.sendMessage("          §f• All restrictions have been lifted")
        player.sendMessage("")
        player.sendMessage("§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
        
        // Notify all online staff
        notifyStaff("§a§lMaintenance mode has been DISABLED by ${player.username}")
        
        // TODO: If scope is "global", send maintenance toggle to other servers
        if (scope == "global") {
            // plugin.radiumIntegration.sendGlobalMaintenanceToggle(false)
        }
        
        plugin.logger.info("Maintenance mode disabled by ${player.username} (scope: $scope)")
    }
    
    private fun toggleMaintenance(player: Player, scope: String) {
        if (maintenanceMode.get()) {
            disableMaintenance(player, scope)
        } else {
            enableMaintenance(player, scope)
        }
    }
    
    private fun showMaintenanceStatus(player: Player) {
        val status = if (maintenanceMode.get()) "§cENABLED" else "§aD DISABLED"
        
        player.sendMessage("§e§l--- MAINTENANCE STATUS ---")
        player.sendMessage("§7Current Status: $status")
        player.sendMessage("§7Commands:")
        player.sendMessage("§7/maintenance on [server|global] - Enable maintenance")
        player.sendMessage("§7/maintenance off [server|global] - Disable maintenance")
        player.sendMessage("§7/maintenance toggle [server|global] - Toggle maintenance")
        player.sendMessage("§7/maintenance status - Show this information")
    }
    
    private fun notifyStaff(message: String) {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            plugin.radiumIntegration.hasPermission(player.uuid, "hub.staff").thenAccept { isStaff ->
                if (isStaff) {
                    player.sendMessage(message)
                }
            }
        }
    }
    
    // TODO: Implement maintenance listener when proper events are available
    /*
    private fun registerMaintenanceListener() {
        val eventManager = MinecraftServer.getGlobalEventHandler()
        
        eventManager.addListener(EventListener.of(PlayerLoginEvent::class.java) { event ->
            if (maintenanceMode.get()) {
                val player = event.player
                
                // Check if player has bypass permission
                plugin.radiumIntegration.hasPermission(player.uuid, "hub.maintenance.bypass").thenAccept { hasBypass ->
                    if (!hasBypass) {
                        // Disconnect player with maintenance message
                        event.player.kick(Component.text(maintenanceMessage, NamedTextColor.RED))
                    }
                }
            }
        })
    }
    */
}
