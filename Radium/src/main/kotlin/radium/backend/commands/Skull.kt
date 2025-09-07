package radium.backend.commands

import radium.backend.Radium
import revxrsal.commands.annotation.*
import revxrsal.commands.velocity.annotation.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import revxrsal.commands.velocity.actor.VelocityCommandActor
import kotlinx.coroutines.launch

@Command("skull", "head")
class Skull(private val radium: Radium) {
    
    companion object {
        val SKULL_CHANNEL = MinecraftChannelIdentifier.create("radium", "skullcommand")
    }

    @Command("skull", "head")
    @CommandPermission("radium.command.skull")
    @Description("Gets player head/skull using Minecraft-Heads.com integration")
    fun skull(actor: VelocityCommandActor, headName: String) {
        if (!actor.isPlayer) {
            actor.reply(Component.text("Only players can use this command!", NamedTextColor.RED))
            return
        }
        
        radium.scope.launch {
            try {
                // First try to get from minecraft-heads.com
                val headData = radium.minecraftHeadsManager.getHeadByName(headName).get()
                
        val player = actor.asPlayer()
        val currentServer = player?.currentServer?.orElse(null)
                if (currentServer == null) {
                    actor.reply(Component.text("You must be connected to a server!", NamedTextColor.RED))
                    return@launch
                }
                
                if (headData != null) {
                    // Send skull command with minecraft-heads data
                    val message = "skull:${player?.uniqueId}:${headData.name}:${headData.value}:${headData.signature ?: ""}"
                    currentServer.sendPluginMessage(SKULL_CHANNEL, message.toByteArray())
                    actor.reply(Component.text("Gave you head: ${headData.name}", NamedTextColor.GREEN))
                } else {
                    // Fallback to player head
                    val message = "skull:${player?.uniqueId}:$headName:::"
                    currentServer.sendPluginMessage(SKULL_CHANNEL, message.toByteArray())
                    actor.reply(Component.text("Gave you head: $headName", NamedTextColor.GREEN))
                }
            } catch (e: Exception) {
                actor.reply(Component.text("Failed to get skull: ${e.message}", NamedTextColor.RED))
                radium.logger.error("Error processing skull command", e)
            }
        }
    }
}
