package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class JoinGroupRequest(val groupId: String, val userId: String)