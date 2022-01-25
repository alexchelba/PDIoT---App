package com.specknet.pdiotapp.userModel

/**
 * this class is to model the structure of the SQL table
 */
data class UserModel(val id: Int = -1, val username: String = "", val email: String = "", val password: String = "",
val isLogged : Boolean = false)
