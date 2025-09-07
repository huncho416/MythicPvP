# MythicPvP Permissions & Commands Documentation

This document lists all permissions and commands available in the MythicPvP network, organized by plugin and functionality.

---

## Table of Contents

1. [Player Commands (No Permission Required)](#player-commands-no-permission-required)
2. [Radium Backend Permissions](#radium-backend-permissions)
3. [Lobby Server Permissions](#lobby-server-permissions)
4. [Default Rank Permissions](#default-rank-permissions)
5. [Velocity Core Permissions](#velocity-core-permissions)

---

## Player Commands (No Permission Required)

These commands are available to all players without any special permissions:

### General Commands
- `/spawn` - Teleport to spawn location
- `/report <player> <reason>` - Report a player for rule violations
- `/request <message>` - Submit a request to staff

### Social Commands
- All players can use basic chat functionality (unless chat is disabled)

---

## Radium Backend Permissions

### Core Staff Permissions
- `radium.staff` - Basic staff permission (allows bypassing maintenance, seeing staff messages, etc.)
- `radium.admin.*` - Admin wildcard permission
- `radium.mod.*` - Moderator wildcard permission  
- `radium.helper.*` - Helper wildcard permission
- `radium.vip.*` - VIP wildcard permission

### Report & Request System
#### Player Permissions (No permissions required)
- **Commands:**
  - `/report <player> <reason>` - No permission required
  - `/request <message>` - No permission required

#### Staff Permissions
- `radium.reports.view` - View reports for players (`/reports <player>`)
- `radium.reports.resolve` - Resolve reports (`/report resolve <player> [resolution]`)
- `radium.reports.dismiss` - Dismiss reports (`/report dismiss <player> [reason]`)
- `radium.reports.stats` - View report statistics (`/reports stats`)
- `radium.reports.admin` - Advanced report management (`/reports cleanup`)
- `radium.request.complete` - Complete requests (`/request complete <player> [response]`)
- `radium.request.cancel` - Cancel requests (`/request cancel <player> [reason]`)

### Punishment System
#### Core Punishment Permissions
- `radium.punish.ban` - Ban players (`/ban <player> [duration] <reason> [-s] [-c]`)
- `radium.punish.tempban` - Temporary ban players
- `radium.punish.ipban` - IP ban players (`/ipban <player> [duration] <reason> [-s] [-c]`)
- `radium.punish.unban` - Unban players (`/unban <player> [-s]`)
- `radium.punish.mute` - Mute players (`/mute <player> [duration] <reason> [-s]`)
- `radium.punish.unmute` - Unmute players (`/unmute <player> [-s]`)
- `radium.punish.kick` - Kick players (`/kick <player> <reason> [-s]`)
- `radium.punish.warn` - Warn players (`/warn <player> <reason> [-s]`)
- `radium.punish.blacklist` - Blacklist players (`/blacklist <player> <reason> [-s] [-c]`)
- `radium.punish.unblacklist` - Remove blacklist (`/unblacklist <player> [-s]`)

#### Punishment Modifiers
- `radium.punish.silent` - Execute silent punishments ([-s] flag)
- `radium.punish.clearinventory` - Clear inventory on ban/blacklist ([-c] flag)
- `radium.punish.self` - Punish yourself (normally blocked)

#### Punishment Info & Management
- `radium.punish.check` - Check player punishments (`/checkpunishments <player>`)
- `radium.punish.info` - View detailed punishment info (`/punishmentinfo <id>`)
- `radium.command.alts` - Check player alts (`/alts <player>`)
- `radium.command.dupeip` - Check duplicate IPs (`/dupeip <player>`)
- `radium.banevasion.alerts` - Receive ban evasion alerts

### Rank & Permission Management
- `radium.rank.use` - Use rank commands (`/rank`)
- `radium.permission.use` - Use permission commands (`/permission`)
- `radium.permission.add` - Add permissions to players
- `radium.permission.remove` - Remove permissions from players

### Vanish System
- `radium.vanish.owner` - Owner level vanish (see all staff)
- `radium.vanish.admin` - Admin level vanish
- `radium.vanish.moderator` - Moderator level vanish
- `radium.vanish.helper` - Helper level vanish
- `radium.vanish.see` - See vanished players regardless of rank

### Staff Communication
- `radium.staffchat.use` - Use staff chat (`/staffchat`, `/sc`)
- `radium.staffchat.hide` - Hide/show staff chat messages

### Teleportation
- `radium.teleport.use` - Basic teleport (`/teleport <player>`)
- `radium.teleport.here` - Teleport players to you (`/teleporthere <player>`)
- `radium.teleport.world` - Teleport to worlds (`/teleportworld <world>`)
- `radium.teleport.position` - Teleport to coordinates (`/teleportposition <x> <y> <z>`)

### Utility Commands
- `radium.sudo.use` - Force players to run commands (`/sudo <player> <command>`)
- `radium.freeze.use` - Freeze players (`/freeze <player>`)
- `radium.unfreeze.use` - Unfreeze players (`/unfreeze <player>`)
- `radium.panic.use` - Enable panic mode (`/panic`)
- `radium.unpanic.use` - Disable panic mode (`/unpanic`)
- `radium.skull.use` - Get player skulls (`/skull <player>`)
- `radium.rename.use` - Rename items (`/rename <name>`)

### Chat & Maintenance
- `radium.chat.bypass` - Bypass chat restrictions when muted
- `radium.maintenance.bypass` - Bypass maintenance mode

### Administration
- `radium.punishment.admin` - Access punishment admin commands (`/punishmentadmin`)
- `radium.admin.stats` - View punishment statistics
- `radium.admin.queue` - Manage punishment queue
- `radium.admin.cache` - Manage cache operations
- `radium.admin.cleanup` - Cleanup old punishments
- `radium.admin.test` - Run system tests

---

## Lobby Server Permissions

### Basic Player Permissions
- `lobby.chat` - Send chat messages (when chat is enabled)
- `hub.fly` - Fly in lobby (auto-granted on join if player has permission)

### Staff Mode & Management
- `hub.staff.mode` - Toggle staff mode (`/staffmode`, `/sm`)
- `radium.staff` - Basic staff functionality (required for most staff features)

### Protection Bypasses
- `lobby.admin` - Bypass all lobby protections and restrictions
- `lobby.bypass.protection` - Bypass inventory/item protection
- `lobby.bypass.chat` - Bypass chat restrictions
- `hub.interact.bypass` - Bypass interaction protection
- `hub.command.build` - Build/break blocks in lobby
- `hub.maintenance.bypass` - Bypass maintenance mode

### Hub Staff Commands
All hub commands require their respective permissions:

#### Communication
- `hub.command.adminchat` - Admin chat functionality
- `hub.command.broadcast` - Broadcast messages
- `hub.command.alert` - Send alerts

#### Player Management  
- `hub.command.freeze` - Freeze players
- `hub.command.unfreeze` - Unfreeze players
- `hub.command.panic` - Panic mode
- `hub.command.unpanic` - Disable panic
- `hub.command.invsee` - View player inventories
- `hub.command.sudo` - Force command execution
- `hub.command.stafflist` - View staff list

#### Punishment Commands (Hub variants)
- `hub.command.ban` - Ban players from hub
- `hub.command.tempban` - Temporary ban from hub
- `hub.command.unban` - Unban from hub
- `hub.command.mute` - Mute players in hub
- `hub.command.unmute` - Unmute players in hub
- `hub.command.kick` - Kick from hub
- `hub.command.warn` - Warn players in hub
- `hub.command.blacklist` - Blacklist from hub
- `hub.command.unblacklist` - Remove blacklist from hub
- `hub.command.checkpunishments` - Check punishments in hub

#### Teleportation (Hub)
- `hub.command.teleport` - Teleport to players
- `hub.command.teleporthere` - Teleport players to you
- `hub.command.teleportworld` - Teleport to worlds
- `hub.command.teleportposition` - Teleport to coordinates

#### Utility Commands (Hub)
- `hub.command.god` - God mode
- `hub.command.give` - Give items
- `hub.command.heal` - Heal players
- `hub.command.feed` - Feed players
- `hub.command.skull` - Get player skulls
- `hub.command.clear` - Clear inventories
- `hub.command.more` - Get more items
- `hub.command.nametag` - Modify nametags
- `hub.command.rename` - Rename items
- `hub.command.maintenance` - Toggle maintenance
- `hub.command.credits` - Credits command

#### Management Commands
- `hub.command.reports` - Manage reports in hub
- `hub.command.schematic` - Schematic management
- `hub.update` - Receive update notifications
- `hub.staff` - General staff permissions in hub

---

## Default Rank Permissions

### OWNER Rank
- **Permissions:** `radium.*` (all permissions)
- **Weight:** 1000
- **Prefix:** `&4[OWNER] &c`
- **Color:** `&f` (white)

### ADMIN Rank  
- **Permissions:** `radium.admin.*`
- **Weight:** 900
- **Prefix:** `&c[ADMIN] &c`
- **Color:** `&c` (red)

### MOD Rank
- **Permissions:** `radium.mod.*`
- **Weight:** 800
- **Prefix:** `&9[MOD] &9`
- **Color:** `&9` (blue)

### HELPER Rank
- **Permissions:** `radium.helper.*`
- **Weight:** 700
- **Prefix:** `&a[HELPER] &a`
- **Color:** `&a` (green)

### VIP Rank
- **Permissions:** `radium.vip.*`
- **Weight:** 100
- **Prefix:** `&6[VIP] &6`
- **Color:** `&6` (gold)

### DEFAULT Rank
- **Permissions:** None (empty list)
- **Weight:** 0
- **Prefix:** None
- **Color:** `&f` (white)

---

## Velocity Core Permissions

These permissions are for Velocity proxy administration:

### Core Velocity Commands
- `velocity.command.velocity` - Core Velocity commands
- `velocity.command.server` - Server switching commands
- `velocity.command.glist` - Global player list
- `velocity.command.send` - Send players to servers
- `velocity.command.shutdown` - Shutdown proxy
- `velocity.command.reload` - Reload proxy configuration

### Communication
- `velocity.command.message` - Private messaging
- `velocity.command.reply` - Reply to messages  
- `velocity.command.broadcast` - Broadcast messages

### Access Control
- `velocity.maintenance.bypass` - Bypass maintenance mode
- `velocity.player.count.bypass` - Bypass player limits
- `velocity.whitelist.bypass` - Bypass whitelist
- `velocity.player.list` - See all players

### Administration
- `velocity.command.debug` - Debug commands
- `velocity.command.plugins` - Plugin management
- `velocity.heap` - Memory heap access

---

## Important Notes

### Wildcard Permissions
- `*` - All permissions (use with caution)
- `radium.*` - All Radium permissions
- `radium.admin.*` - All admin permissions
- `radium.mod.*` - All moderator permissions
- `radium.helper.*` - All helper permissions
- `radium.vip.*` - All VIP permissions

### Permission Inheritance
- Ranks can inherit permissions from other ranks
- Higher weight ranks typically have more permissions
- Admin bypass permissions work across most systems

### Command Aliases
Many commands have aliases for convenience:
- `/staffmode` = `/sm`
- `/staffchat` = `/sc`
- `/rank` = `/ranks`
- `/permission` = `/perms` = `/perm`

### Tab Completion
- Staff commands only appear in tab completion for players with appropriate permissions
- This prevents regular players from seeing staff-only command syntax

---

**Last Updated:** December 2024  
**Network:** MythicPvP  
**Plugins:** Radium Backend, Lobby Server
