package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class TeleportWorldCommand(private val plugin: LobbyPlugin) : Command("teleportworld", "tpworld") {
    
    private val worldArg = ArgumentType.Word("world")
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    // Use cached permission check for tab completion filtering
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "hub.command.teleportworld") ||
                    huncho.main.lobby.utils.PermissionCache.hasPermissionCached(sender, "lobby.admin")
                }
                else -> true // Allow console
            }
        }
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("§cThis command can only be used by players!")
                return@addSyntax
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleportworld") {
                val worldName = context.get(worldArg)
                
                // Find the world/instance
                val instance = MinecraftServer.getInstanceManager().getInstances()
                    .find { it.getDimensionName().equals(worldName, ignoreCase = true) }
                
                if (instance == null) {
                    player.sendMessage("§cWorld '$worldName' not found!")
                    player.sendMessage("§7Available worlds:")
                    MinecraftServer.getInstanceManager().getInstances().forEach { inst ->
                        player.sendMessage("§7- ${inst.getDimensionName()}")
                    }
                    return@checkPermissionAndExecute
                }
                
                if (player.instance == instance) {
                    player.sendMessage("§cYou are already in world '$worldName'!")
                    return@checkPermissionAndExecute
                }
                
                // Get spawn position for the world (default to 0, 65, 0)
                val spawnPos = net.minestom.server.coordinate.Pos(0.0, 65.0, 0.0)
                
                // Teleport to world
                player.setInstance(instance, spawnPos).thenRun {
                    player.sendMessage("§a§lTeleported!")
                    player.sendMessage("§7You have been teleported to world: §e$worldName")
                }
                
                plugin.logger.info("${player.username} teleported to world $worldName")
            }
        }, worldArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.teleportworld") {
                player.sendMessage("§c§lUsage:")
                player.sendMessage("§7/teleportworld <world>")
                player.sendMessage("§7/tpworld <world>")
                player.sendMessage("§7Teleport to a specific world/dimension")
                
                player.sendMessage("§7Available worlds:")
                MinecraftServer.getInstanceManager().getInstances().forEach { instance ->
                    val worldName = instance.getDimensionName()
                    player.sendMessage("§7- §e$worldName")
                }
            }
        }
    }
}
