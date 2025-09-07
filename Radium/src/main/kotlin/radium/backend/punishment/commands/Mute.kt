package radium.backend.punishment.commands

import com.velocitypowered.api.proxy.Player
import kotlinx.coroutines.launch
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Flag
import revxrsal.commands.annotation.Optional
import revxrsal.commands.velocity.annotation.CommandPermission
import radium.backend.Radium
import radium.backend.annotations.OnlinePlayers
import radium.backend.punishment.models.PunishmentType
import radium.backend.util.DurationParser

@Command("mute", "unmute")
class Mute(private val radium: Radium) {

    @Command("mute <target> <reason>")
    @CommandPermission("radium.punish.mute")
    fun mute(
        actor: Player,
        @OnlinePlayers target: String,
        reason: String,
        @Flag("s") silent: Boolean = false
    ) {
        // Check for both old and new permission formats for lobby compatibility
        if (!actor.hasPermission("radium.punish.mute") && !actor.hasPermission("radium.command.mute")) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.no_permission"))
            return
        }
        
        if (target.isEmpty() || reason.isEmpty()) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("commands.mute.usage"))
            return
        }

        // Parse the reason to extract duration and actual reason
        // Format can be: "<duration> <reason>" or just "<reason>" (permanent mute)
        // Duration examples: "1h", "30m", "1d", "perm", "permanent"
        val parts = reason.split(" ", limit = 2)
        val firstPart = parts.getOrNull(0) ?: ""
        val secondPart = parts.getOrNull(1)
        
        val finalDuration: Long?
        val finalReason: String
        
        when {
            firstPart.equals("perm", ignoreCase = true) || firstPart.equals("permanent", ignoreCase = true) -> {
                // Permanent mute
                finalDuration = null
                finalReason = secondPart ?: "No reason provided"
            }
            firstPart.matches(Regex("\\d+[smhdwy]")) -> {
                // Valid duration format
                finalDuration = DurationParser.parseToMillis(firstPart)
                finalReason = secondPart ?: "No reason provided"
            }
            else -> {
                // No duration specified - treat entire input as reason and make it permanent
                finalDuration = null
                finalReason = reason
            }
        }

        if (silent && !actor.hasPermission("radium.punish.silent")) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.no_silent_permission"))
            return
        }

        radium.scope.launch {
            try {
                // Try to find player online first
                val targetPlayer = radium.server.getPlayer(target).orElse(null)

                val (targetId, targetName, targetIp) = if (targetPlayer != null) {
                    Triple(
                        targetPlayer.uniqueId.toString(),
                        targetPlayer.username,
                        targetPlayer.remoteAddress.address.hostAddress
                    )
                } else {
                    // Look up offline player
                    val profile = radium.connectionHandler.findPlayerProfile(target)
                    if (profile != null) {
                        Triple(profile.uuid.toString(), profile.username, null)
                    } else {
                        actor.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "punishments.player_not_found",
                                "player" to target
                            )
                        )
                        return@launch
                    }
                }

                // Prevent self-punishment
                if (targetId == actor.uniqueId.toString() && !actor.hasPermission("radium.punish.self")) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.cannot_punish_self"))
                    return@launch
                }

                val success = radium.punishmentManager.issuePunishment(
                    target = targetPlayer,
                    targetId = targetId,
                    targetName = targetName,
                    targetIp = targetIp,
                    type = PunishmentType.MUTE,
                    reason = finalReason,
                    staff = actor,
                    duration = finalDuration?.toString(),
                    silent = silent,
                    clearInventory = false
                )

                if (success) {
                    // Send appropriate success message based on duration
                    val messageKey = if (finalDuration == null) {
                        "punishments.mute.success_permanent"
                    } else {
                        "punishments.mute.success_temporary"
                    }
                    
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            messageKey,
                            "target" to targetName,
                            "reason" to finalReason,
                            "duration" to (finalDuration?.let { "${it}ms" } ?: "permanent")
                        )
                    )

                    // Notify the muted player if online
                    targetPlayer?.let { player ->
                        val muteMessageKey = if (finalDuration == null) {
                            "punishments.player.muted_permanent"
                        } else {
                            "punishments.player.muted"
                        }
                        
                        val muteMessage = if (finalDuration == null) {
                            radium.yamlFactory.getMessageComponent(
                                muteMessageKey,
                                "reason" to finalReason,
                                "staff" to actor.username
                            )
                        } else {
                            val expiresAt = java.time.Instant.now().plusMillis(finalDuration)
                            radium.yamlFactory.getMessageComponent(
                                muteMessageKey,
                                "reason" to finalReason,
                                "staff" to actor.username,
                                "duration" to formatDuration(finalDuration),
                                "expires" to expiresAt.toString()
                            )
                        }
                        player.sendMessage(muteMessage)
                    }
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing mute command: ${e.message}", e)
                actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.error_occurred"))
            }
        }
    }

    @Command("mute <target> <duration> <reason>")
    @CommandPermission("radium.punish.mute")
    fun muteWithDuration(
        actor: Player,
        @OnlinePlayers target: String,
        duration: String,
        reason: String,
        @Flag("s") silent: Boolean = false
    ) {
        // Combine duration and reason and call the main mute function
        val combinedReason = "$duration $reason"
        mute(actor, target, combinedReason, silent)
    }

    @Command("unmute <target> <reason>")
    @CommandPermission("radium.punish.unmute")
    fun unmute(
        actor: Player,
        target: String,
        reason: String,
        @Flag("s") silent: Boolean = false
    ) {
        // Check for both old and new permission formats for lobby compatibility
        if (!actor.hasPermission("radium.punish.unmute") && !actor.hasPermission("radium.command.unmute")) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.no_permission"))
            return
        }
        
        if (target.isEmpty() || reason.isEmpty()) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("commands.unmute.usage"))
            return
        }

        if (silent && !actor.hasPermission("radium.punish.silent")) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.no_silent_permission"))
            return
        }

        radium.scope.launch {
            try {
                // Look up player UUID
                val profile = radium.connectionHandler.findPlayerProfile(target)
                if (profile == null) {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "punishments.player_not_found",
                            "player" to target
                        )
                    )
                    return@launch
                }

                val success = radium.punishmentManager.revokePunishment(
                    targetId = profile.uuid.toString(),
                    targetName = profile.username,
                    type = PunishmentType.MUTE,
                    reason = reason,
                    staff = actor,
                    silent = silent
                )

                if (success) {
                    actor.sendMessage(
                        radium.yamlFactory.getMessageComponent(
                            "punishments.unmute.success",
                            "target" to profile.username,
                            "reason" to reason
                        )
                    )

                    // Notify the unmuted player if online
                    radium.server.getPlayer(profile.username).ifPresent { player ->
                        player.sendMessage(
                            radium.yamlFactory.getMessageComponent(
                                "punishments.player.unmuted",
                                "reason" to reason
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                radium.logger.error("Error executing unmute command: ${e.message}", e)
                actor.sendMessage(radium.yamlFactory.getMessageComponent("punishments.error_occurred"))
            }
        }
    }

    /**
     * Parse duration and reason from command arguments
     */
    private fun parseDurationAndReason(duration: String?, reason: String): Pair<String?, String> {
        return if (duration != null) {
            Pair(duration, reason)
        } else {
            val reasonParts = reason.split(" ", limit = 2)
            if (reasonParts.size >= 2 && isDurationString(reasonParts[0])) {
                Pair(reasonParts[0], reasonParts[1])
            } else {
                Pair(null, reason)
            }
        }
    }

    private fun isDurationString(str: String): Boolean {
        return str.matches(Regex("^\\d+[smhdwy]|perm|permanent$", RegexOption.IGNORE_CASE))
    }

    /**
     * Format duration from milliseconds to human readable string
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
