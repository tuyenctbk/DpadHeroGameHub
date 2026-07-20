package com.tdpham.games.common.profile

import java.util.UUID

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var avatarColor: Int,
    var avatarId: Int = 0, // 0: Initial, 1..N: Predefined icons
    val createdAt: Long = System.currentTimeMillis(),
    var pin: String? = null
)
