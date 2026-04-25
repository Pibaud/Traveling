package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class JoinGroupRequest(val groupId: String, val userId: String)