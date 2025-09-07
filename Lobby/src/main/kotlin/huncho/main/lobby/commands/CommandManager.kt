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
            
            // Commands registration complete
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
        
        // Nametag commands
        registerMinestomCommand(NametagCommand(plugin))
        
        // Reports and requests commands
        registerMinestomCommand(ReportCommand(plugin))
        registerMinestomCommand(RequestCommand(plugin))
        registerMinestomCommand(ReportsCommand(plugin))
        
        // Messaging commands
        registerMinestomCommand(MessageCommand(plugin))
        registerMinestomCommand(ReplyCommand(plugin))
        registerMinestomCommand(MessageCommand(plugin))
        registerMinestomCommand(ReplyCommand(plugin))
        
        // Messaging commands
        registerMinestomCommand(MessageCommand(plugin))
        registerMinestomCommand(ReplyCommand(plugin))
        
        // New feature commands
        registerMinestomCommand(AdminChatCommand(plugin))
        registerMinestomCommand(FreezeCommand(plugin))
        registerMinestomCommand(UnfreezeCommand(plugin))
        registerMinestomCommand(PanicCommand(plugin))
        registerMinestomCommand(UnpanicCommand(plugin))
        registerMinestomCommand(PanicStatusCommand(plugin))
        registerMinestomCommand(CreditsCommand(plugin))
        registerMinestomCommand(GiveCommand(plugin))
        registerMinestomCommand(HealCommand(plugin))
        registerMinestomCommand(FeedCommand(plugin))
        registerMinestomCommand(StaffModeCommand(plugin))
        
        // Additional utility commands
        registerMinestomCommand(RenameCommand(plugin))
        registerMinestomCommand(MoreCommand(plugin))
        registerMinestomCommand(AlertCommand(plugin))
        registerMinestomCommand(BroadcastCommand(plugin))
        registerMinestomCommand(StaffListCommand(plugin))
        registerMinestomCommand(ClearCommand(plugin))
        
        // Player management commands
        registerMinestomCommand(SkullCommand(plugin))
        registerMinestomCommand(SudoCommand(plugin))
        registerMinestomCommand(GodCommand(plugin))
        
        // Teleportation commands
        registerMinestomCommand(TeleportCommand(plugin))
        registerMinestomCommand(TeleportHereCommand(plugin))
        registerMinestomCommand(TeleportWorldCommand(plugin))
        registerMinestomCommand(TeleportPositionCommand(plugin))
        
        // Information commands
        registerMinestomCommand(DiscordCommand(plugin))
        registerMinestomCommand(TwitterCommand(plugin))
        registerMinestomCommand(StoreCommand(plugin))
        
        // Server management commands
        registerMinestomCommand(MaintenanceCommand(plugin))

        // Register new separate commands for staff management
        registerMinestomCommand(ResolveReportCommand(plugin))
        registerMinestomCommand(DismissReportCommand(plugin))
        registerMinestomCommand(CancelRequestCommand(plugin))
        registerMinestomCommand(CompleteRequestCommand(plugin))
    }
    
    private fun registerMinestomCommand(command: Command) {
        try {
            // Note: Tab completion filtering for Minestom commands is complex to implement
            // due to the asynchronous nature of our permission system.
            // The permission checks are handled in each command's execution via checkPermissionAndExecute
            // which provides security even if commands appear in tab completion.
            
            MinecraftServer.getCommandManager().register(command)
            // Command registered
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to register command: ${command.name}", e)
        }
    }
}
