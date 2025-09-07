package radium.backend.annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class OnlinePlayersWithPermission(val permission: String)
