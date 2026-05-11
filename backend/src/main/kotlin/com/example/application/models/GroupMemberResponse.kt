import kotlinx.serialization.Serializable

@Serializable
data class GroupMemberResponse(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val role: String
)