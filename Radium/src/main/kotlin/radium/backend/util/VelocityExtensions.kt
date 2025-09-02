package radium.backend.util

// Re-export Velocity API classes for convenience
import com.velocitypowered.api.proxy.Player as VelocityPlayer
import com.velocitypowered.api.proxy.ProxyServer as VelocityProxyServer
import com.velocitypowered.api.event.connection.DisconnectEvent as VelocityDisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent as VelocityLoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent as VelocityPlayerChatEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent as VelocityServerPostConnectEvent
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent as VelocityPlayerConfigurationEvent
import com.velocitypowered.api.proxy.server.RegisteredServer as VelocityRegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo as VelocityServerInfo
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier as VelocityMinecraftChannelIdentifier
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult as VelocityChatResult
import net.kyori.adventure.text.logger.slf4j.ComponentLogger as VelocityComponentLogger
import net.kyori.adventure.text.minimessage.MiniMessage as VelocityMiniMessage

// Type aliases for cleaner code
typealias Player = VelocityPlayer
typealias ProxyServer = VelocityProxyServer
typealias DisconnectEvent = VelocityDisconnectEvent
typealias LoginEvent = VelocityLoginEvent
typealias PlayerChatEvent = VelocityPlayerChatEvent
typealias ServerPostConnectEvent = VelocityServerPostConnectEvent
typealias PlayerConfigurationEvent = VelocityPlayerConfigurationEvent
typealias RegisteredServer = VelocityRegisteredServer
typealias ServerInfo = VelocityServerInfo
typealias MinecraftChannelIdentifier = VelocityMinecraftChannelIdentifier
typealias ChatResult = VelocityChatResult
typealias ComponentLogger = VelocityComponentLogger
typealias minimessage = VelocityMiniMessage
typealias logger = VelocityComponentLogger

// Logging extensions
fun ComponentLogger.debug(message: String) {
    this.debug(net.kyori.adventure.text.Component.text(message))
}

fun ComponentLogger.info(message: String) {
    this.info(net.kyori.adventure.text.Component.text(message))
}

fun ComponentLogger.warn(message: String) {
    this.warn(net.kyori.adventure.text.Component.text(message))
}

fun ComponentLogger.error(message: String) {
    this.error(net.kyori.adventure.text.Component.text(message))
}

// Utility functions
fun ProxyServer.getPlayer(name: String): Player? {
    return this.getPlayer(name).orElse(null)
}

fun Player.sendMessage(message: String) {
    this.sendMessage(net.kyori.adventure.text.Component.text(message))
}

fun Player.sendMessage(component: net.kyori.adventure.text.Component) {
    this.sendMessage(component)
}
