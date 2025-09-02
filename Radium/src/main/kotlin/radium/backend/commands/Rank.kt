package radium.backend.commands

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import radium.backend.Radium
import radium.backend.annotations.RankList
import radium.backend.annotations.ColorList
import radium.backend.player.TabListManager


@Command("rank", "ranks")
@CommandPermission("radium.staff")
class Rank(private val radium: Radium) {

    private val rankManager = radium.rankManager


    @Command("rank", "ranks")
    @CommandPermission("radium.rank.use")
    fun rankUsage(actor: Player) {
        // Create a formatted help message like /perm does
        val helpText = """
            &e&l--- Rank Management ---
            &e/rank create <name> &7- Create a new rank
            &e/rank delete <name> &7- Delete a rank
            &e/rank setprefix <rank> <prefix> &7- Set rank prefix
            &e/rank setsuffix <rank> <suffix> &7- Set rank suffix
            &e/rank settabprefix <rank> <prefix> &7- Set tab prefix
            &e/rank settabsuffix <rank> <suffix> &7- Set tab suffix
            &e/rank setweight <rank> <weight> &7- Set rank weight
            &e/rank permission add <rank> <permission> &7- Add permission
            &e/rank permission remove <rank> <permission> &7- Remove permission
            &e/rank inherit <rank> <inherit_rank> &7- Add inheritance
            &e/rank info <rank> &7- View rank information
            &e/rank list &7- List all ranks
        """.trimIndent()
        
        // Send each line as a separate message with proper color parsing
        helpText.lines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    val component = TabListManager.safeParseColoredText(line)
                    actor.sendMessage(component)
                } catch (e: Exception) {
                    // Fallback: send as plain text
                    actor.sendMessage(Component.text(line.replace("&", "§")))
                }
            }
        }
    }

    @Subcommand("create")
    @CommandPermission("radium.rank.create")
    suspend fun createRank(
        actor: Player,
        @Optional name: String?
    ) {
        try {
            if (name.isNullOrEmpty()) {
                val usageMessage = radium.yamlFactory.getMessage("rank.create.usage") ?: "&cUsage: /rank create <name>"
                actor.sendMessage(TabListManager.safeParseColoredText(usageMessage))
                return
            }

            // Create rank with default values
            val rank = rankManager.createRank(name, "&7", 0)
            val successMessage = radium.yamlFactory.getMessage("rank.create.success") ?: "&aCreated rank '{rank}' successfully!"
            actor.sendMessage(TabListManager.safeParseColoredText(successMessage.replace("{rank}", rank.name)))
        } catch (e: Exception) {
            val failMessage = radium.yamlFactory.getMessage("general.failed_operation") ?: "&cFailed to {operation}: {message}"
            actor.sendMessage(TabListManager.safeParseColoredText(
                failMessage.replace("{operation}", "create rank").replace("{message}", e.message.toString())
            ))
        }
    }

    @Subcommand("delete")
    @CommandPermission("radium.rank.delete")
    suspend fun deleteRank(
        actor: Player,
        @Optional @RankList name: String?
    ) {
        try {
            if (name.isNullOrEmpty()) {
                val usageMessage = radium.yamlFactory.getMessage("rank.delete.usage") ?: "&cUsage: /rank delete <name>"
                actor.sendMessage(TabListManager.safeParseColoredText(usageMessage))
                return
            }

            val success = rankManager.deleteRank(name)
            if (success) {
                val successMessage = radium.yamlFactory.getMessage("rank.delete.success") ?: "&aDeleted rank '{rank}' successfully!"
                actor.sendMessage(TabListManager.safeParseColoredText(successMessage.replace("{rank}", name)))
            } else {
                val notFoundMessage = radium.yamlFactory.getMessage("rank.delete.not_found") ?: "&cRank '{rank}' not found!"
                actor.sendMessage(TabListManager.safeParseColoredText(notFoundMessage.replace("{rank}", name)))
            }
        } catch (e: Exception) {
            val failMessage = radium.yamlFactory.getMessage("general.failed_operation") ?: "&cFailed to {operation}: {message}"
            actor.sendMessage(TabListManager.safeParseColoredText(
                failMessage.replace("{operation}", "delete rank").replace("{message}", e.message.toString())
            ))
        }
    }

    @Subcommand("setprefix")
    @CommandPermission("radium.rank.setprefix")
    suspend fun setPrefix(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional prefix: String?
    ){
        try {
            if (name.isNullOrEmpty() || prefix.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setprefix.usage"))
                return
            }

            val success = rankManager.updateRank(name) { rank ->
                rank.copy(prefix = prefix)
            }

            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setprefix.success",
                    "rank" to name,
                    "prefix" to prefix
                ))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "set prefix",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("setsuffix")
    @CommandPermission("radium.rank.setsuffix")
    suspend fun setSuffix(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional suffix: String?
    ){
        try {
            if (name.isNullOrEmpty() || suffix.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setsuffix.usage"))
                return
            }

            val success = rankManager.setRankSuffix(name, suffix)

            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setsuffix.success",
                    "rank" to name,
                    "suffix" to suffix
                ))
                
                // Update tab lists for all players with this rank
                GlobalScope.launch {
                    radium.tabListManager.updateAllPlayersTabList()
                }
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "set suffix",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("settabprefix")
    @CommandPermission("radium.rank.settabprefix")
    suspend fun setTabPrefix(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional tabPrefix: String?
    ){
        try {
            if (name.isNullOrEmpty() || tabPrefix.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.settabprefix.usage"))
                return
            }

            val success = rankManager.setRankTabPrefix(name, tabPrefix)

            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.settabprefix.success",
                    "rank" to name,
                    "prefix" to tabPrefix
                ))
                
                // Update tab lists for all players with this rank
                GlobalScope.launch {
                    radium.tabListManager.updateAllPlayersTabList()
                }
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "set tab prefix",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("settabsuffix")
    @CommandPermission("radium.rank.settabsuffix")
    suspend fun setTabSuffix(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional tabSuffix: String?
    ){
        try {
            if (name.isNullOrEmpty() || tabSuffix.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.settabsuffix.usage"))
                return
            }

            val success = rankManager.setRankTabSuffix(name, tabSuffix)

            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.settabsuffix.success",
                    "rank" to name,
                    "suffix" to tabSuffix
                ))
                
                // Update tab lists for all players with this rank
                GlobalScope.launch {
                    radium.tabListManager.updateAllPlayersTabList()
                }
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "set tab suffix",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("setweight")
    @CommandPermission("radium.rank.setweight")
    suspend fun setWeight(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional weight: Int
    ){
        try {
            if (name.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setweight.usage"))
                return
            }

            val success = rankManager.updateRank(name) { rank ->
                rank.copy(weight = weight)
            }

            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.setweight.success",
                    "rank" to name,
                    "weight" to weight.toString()
                ))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "set weight",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("permission add")
    @CommandPermission("radium.rank.permission.add")
    suspend fun addPermission(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional permission: String?
    ){
        try {
            if (name.isNullOrEmpty() || permission.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.permission.add.usage"))
                return
            }

            val success = rankManager.addPermissionToRank(name, permission)
            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.permission.add.success",
                    "permission" to permission,
                    "rank" to name
                ))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "add permission",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("permission remove <name> <permission>")
    @CommandPermission("radium.rank.permission.remove")
    suspend fun removePermission(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional permission: String?
    ) {

        if (name.isNullOrEmpty() || permission.isNullOrEmpty()) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.permission.remove.usage"))
            return
        }

        try {
            val success = rankManager.removePermissionFromRank(name, permission)
            if (success) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.permission.remove.success",
                    "permission" to permission,
                    "rank" to name
                ))
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.permission.remove.not_found"))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "remove permission",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("inherit <name> <inherit>")
    @CommandPermission("radium.rank.inherit")
    suspend fun toggleInheritance(
        actor: Player,
        @Optional @RankList name: String?,
        @Optional @RankList inherit: String?
    ) {
        try {
            if (name.isNullOrEmpty() || inherit.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.inherit.usage"))
                return
            }

            // Get the rank to check if it already inherits from the specified rank
            val rank = rankManager.getRank(name)
            if (rank == null) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
                return
            }

            val alreadyInherits = rank.inherits.contains(inherit)

            // Toggle inheritance based on current state
            val success = if (alreadyInherits) {
                // Already inherits, so remove it
                val removed = rankManager.removeInheritedRank(name, inherit)
                if (removed) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.inherit.remove_success",
                        "rank" to name,
                        "inherit" to inherit
                    ))
                } else {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.inherit.failed",
                        "operation" to "remove",
                        "reason" to "Rank not found or no inheritance relationship exists."
                    ))
                }
                removed
            } else {
                // Doesn't inherit yet, so add it
                val added = rankManager.addInheritedRank(name, inherit)
                if (added) {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.inherit.add_success",
                        "rank" to name,
                        "inherit" to inherit
                    ))
                } else {
                    actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.inherit.failed",
                        "operation" to "add",
                        "reason" to "One of the ranks was not found."
                    ))
                }
                added
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "toggle inheritance",
                "message" to e.message.toString()
            ))
        }
    }


    @Subcommand("info <name>")
    @CommandPermission("radium.rank.info")
    suspend fun getRankInfo(
        actor: Player,
        @Optional @RankList name: String?
    )  {
        try {
            if (name.isNullOrEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.info.usage"))
                return
            }

            val rank = rankManager.getRank(name)
            if (rank != null) {
                val directInherits = rank.inherits
                val allInherits = rankManager.getAllInheritedRanks(name)

                // Get all permissions and their sources
                val permissionMap = mutableMapOf<String, String?>() // permission -> source rank (null for own rank)

                // Add own permissions
                rank.permissions.forEach { perm ->
                    permissionMap[perm] = null // null means it's from own rank
                }

                // Add inherited permissions with proper source tracking
                // We need to trace through the inheritance hierarchy to find the actual source of each permission
                suspend fun addPermissionsFromRank(rankName: String, visited: MutableSet<String> = mutableSetOf()) {
                    if (rankName in visited) return // Prevent infinite loops
                    visited.add(rankName)
                    
                    val inheritedRank = rankManager.getRank(rankName) ?: return
                    
                    // Add permissions from this inherited rank
                    inheritedRank.permissions.forEach { perm ->
                        if (!permissionMap.containsKey(perm)) {
                            permissionMap[perm] = rankName
                        }
                    }
                    
                    // Recursively add permissions from ranks this rank inherits
                    inheritedRank.inherits.forEach { nestedInheritedRankName ->
                        addPermissionsFromRank(nestedInheritedRankName, visited)
                    }
                }

                // Add permissions from all inherited ranks
                directInherits.forEach { inheritedRankName ->
                    addPermissionsFromRank(inheritedRankName)
                }

                // Build the component for display
                val component = Component.text()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.header", "rank" to rank.name))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.prefix", "prefix" to rank.prefix))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.suffix", "suffix" to (rank.suffix ?: "None")))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.tabprefix", "tabprefix" to (rank.tabPrefix ?: "Uses regular prefix")))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.tabsuffix", "tabsuffix" to (rank.tabSuffix ?: "None")))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.weight", "weight" to rank.weight.toString()))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.inherits",
                        "inherits" to if (directInherits.isEmpty()) "None" else directInherits.joinToString(", ")
                    ))
                    .appendNewline()
                    .append(radium.yamlFactory.getMessageComponent("rank.info.permissions"))
                    .appendNewline()

                // Display permissions in requested format
                if (permissionMap.isEmpty()) {
                    component.append(radium.yamlFactory.getMessageComponent("rank.info.none"))
                } else {
                    // First show own permissions
                    permissionMap.entries.filter { it.value == null }.forEach { (perm, _) ->
                        component.append(radium.yamlFactory.getMessageComponent("rank.info.permission", "permission" to perm))
                            .appendNewline()
                    }

                    // Then show inherited permissions with source
                    permissionMap.entries.filter { it.value != null }.forEach { (perm, source) ->
                        component.append(radium.yamlFactory.getMessageComponent("rank.info.inherited_permission",
                            "permission" to perm,
                            "source" to source!!
                        ))
                        .appendNewline()
                    }
                }

                actor.sendMessage(component.build())
            } else {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("general.rank_not_found", "rank" to name))
            }
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "get rank info",
                "message" to e.message.toString()
            ))
        }
    }

    @Subcommand("list")
    @CommandPermission("radium.rank.list")
    suspend fun listRanks(actor: Player
    ) {
        try {
            val ranks = rankManager.listRanksByWeight()
            if (ranks.isEmpty()) {
                actor.sendMessage(radium.yamlFactory.getMessageComponent("rank.list.none"))
                return
            }

            val component = Component.text()
                .append(radium.yamlFactory.getMessageComponent("rank.list.header"))
                .appendNewline()

            ranks.forEach { rank ->
                component.append(radium.yamlFactory.getMessageComponent("rank.list.entry",
                    "name" to rank.name,
                    "weight" to rank.weight.toString(),
                    "prefix" to rank.prefix
                ))
                .appendNewline()
            }

            actor.sendMessage(component.build())
        } catch (e: Exception) {
            actor.sendMessage(radium.yamlFactory.getMessageComponent("general.failed_operation",
                "operation" to "list ranks",
                "message" to e.message.toString()
            ))
        }
    }
}
