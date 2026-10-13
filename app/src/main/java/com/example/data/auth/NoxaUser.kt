package com.example.data.auth

/**
 * The signed-in user, independent of which backend produced it.
 *
 * Firebase (when `google-services.json` is present) and the built-in local
 * account store both map onto this shape, so screens and the view model never
 * need to know which one is active.
 */
data class NoxaUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)
