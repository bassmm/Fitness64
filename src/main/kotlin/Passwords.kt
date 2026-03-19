package com.comp2850

import org.mindrot.jbcrypt.BCrypt

object Passwords {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    fun verify(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)
}
