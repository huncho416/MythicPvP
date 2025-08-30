package huncho.main.lobby.commands

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.commands.impl.*
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command

class CommandManager(private val plugin: LobbyPlugin) {
    
    fun registerAllCommands() {
        try {
            // Register command classes
            registerCommands()
            
            LobbyPlugin.logger.info("All commands registered successfully!")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to register commands", e)
            throw e
        }
    }
    
    private fun registerCommands() {
        // Core commands
        registerMinestomCommand(SpawnCommand(plugin))
        registerMinestomCommand(SetSpawnCommand(plugin))
        registerMinestomCommand(FlyCommand(plugin))
        registerMinestomCommand(BuildCommand(plugin))
        
        // Queue commands
        registerMinestomCommand(JoinQueueCommand(plugin))
        registerMinestomCommand(LeaveQueueCommand(plugin))
        registerMinestomCommand(PauseQueueCommand(plugin))
        
        // Punishment commands (using proper Radium API)
        LobbyPlugin.logger.info("Registering punishment commands...")
        registerMinestomCommand(BanCommand(plugin))
        registerMinestomCommand(TempBanCommand(plugin))
        registerMinestomCommand(UnbanCommand(plugin))
        registerMinestomCommand(MuteCommand(plugin))
        registerMinestomCommand(UnmuteCommand(plugin))
        registerMinestomCommand(KickCommand(plugin))
        registerMinestomCommand(WarnCommand(plugin))
        registerMinestomCommand(BlacklistCommand(plugin))
        registerMinestomCommand(UnblacklistCommand(plugin))
        registerMinestomCommand(CheckPunishmentsCommand(plugin))
        LobbyPlugin.logger.info("Punishment commands registration completed")
        
        // Schematic commands
        plugin.schematicManager.schematicService?.let { service ->
            registerMinestomCommand(SchematicCommand(service, plugin.radiumIntegration))
        }
        
        // Vanish testing command (admin only)
        registerMinestomCommand(VanishTestCommand(plugin))
        
        // Feature commands (will be created)
        // registerMinestomCommand(PlayerVisibilityCommand(plugin))
        // registerMinestomCommand(ScoreboardCommand(plugin))
        // registerMinestomCommand(SoundCommand(plugin))
    }
    
    private fun registerMinestomCommand(command: Command) {
        try {
            MinecraftServer.getCommandManager().register(command)
            LobbyPlugin.logger.info("Successfully registered command: ${command.name}")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to register command: ${command.name}", e)
        }
    }
}
