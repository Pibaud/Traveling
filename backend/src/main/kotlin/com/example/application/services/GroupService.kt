package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.GroupMembers
import com.example.application.Groups
import com.example.application.models.Group
import org.jetbrains.exposed.sql.*

object GroupService {
    suspend fun createNewGroup(name: String, description: String, isPublic: Boolean, tags: List<String>, photoUrl: String, authorId: String): Boolean = dbQuery {
        try {
            val insertedGroupId = Groups.insert {
                it[Groups.name] = name
                it[Groups.description] = description
                it[Groups.isPublic] = isPublic
                it[Groups.tags] = tags.joinToString(",")
                it[Groups.urlGroupPhoto] = photoUrl
                it[Groups.nbMembers] = 1
            } get Groups.id

            GroupMembers.insert {
                it[groupId] = insertedGroupId
                it[userId] = authorId
                it[role] = "ADMIN"
                it[status] = "ACCEPTED"
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getPopularGroups(currentUserId: String?): List<Group> = dbQuery {
        val query = Groups.selectAll().orderBy(Groups.nbMembers to SortOrder.DESC).limit(20)
        query.map { row ->
            val tagsStr = row[Groups.tags] ?: ""
            val isMember = if (currentUserId != null) {
                GroupMembers.select { (GroupMembers.groupId eq row[Groups.id]) and (GroupMembers.userId eq currentUserId) }.count() > 0
            } else false

            val isNotificationEnabled = if (currentUserId != null) {
                GroupMembers.select { (GroupMembers.groupId eq row[Groups.id]) and (GroupMembers.userId eq currentUserId) }
                    .map { it[GroupMembers.shouldNotify] }.singleOrNull() ?: false
            } else false

            Group(
                id = row[Groups.id].toString(),
                name = row[Groups.name],
                description = row[Groups.description] ?: "",
                photoUrl = row[Groups.urlGroupPhoto] ?: "",
                nbMembers = row[Groups.nbMembers],
                nbPosts = row[Groups.nbPosts],
                isPublic = row[Groups.isPublic],
                tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                isMember = isMember,
                isNotificationEnabled = isNotificationEnabled
            )
        }
    }

    suspend fun getMyGroups(userId: String): List<Group> = dbQuery {
        val query = (Groups innerJoin GroupMembers).select { GroupMembers.userId eq userId }.orderBy(GroupMembers.joinedAt to SortOrder.DESC)
        query.map { row ->
            val tagsStr = row[Groups.tags] ?: ""
            val isNotificationEnabled = row[GroupMembers.shouldNotify]

            Group(
                id = row[Groups.id].toString(),
                name = row[Groups.name],
                description = row[Groups.description] ?: "",
                photoUrl = row[Groups.urlGroupPhoto] ?: "",
                nbMembers = row[Groups.nbMembers],
                nbPosts = row[Groups.nbPosts],
                isPublic = row[Groups.isPublic],
                tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                isMember = true,
                isNotificationEnabled = isNotificationEnabled
            )
        }
    }

    suspend fun toggleNotification(groupIdStr: String, userIdStr: String, enable: Boolean): Boolean = dbQuery {
        val groupUuid = java.util.UUID.fromString(groupIdStr)
        GroupMembers.update({ (GroupMembers.groupId eq groupUuid) and (GroupMembers.userId eq userIdStr) }) {
            it[shouldNotify] = enable
        } > 0
    }
}