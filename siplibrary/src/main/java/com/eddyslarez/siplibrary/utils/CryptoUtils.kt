package com.eddyslarez.siplibrary.utils

import java.security.MessageDigest

/**
 * Crypto utilities for SIP authentication
 * 
 * @author Eddys Larez
 */

/**
 * Computes MD5 hash of a string input
 */
fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}