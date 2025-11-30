package com.moynext.yt_explode.channels

data class Username(
    val value: String
) {
    override fun toString(): String = value
    
    companion object {
        fun fromString(username: Any): Username {
            return when (username) {
                is Username -> username
                is String -> Username(username)
                else -> Username(username.toString())
            }
        }
    }
}
