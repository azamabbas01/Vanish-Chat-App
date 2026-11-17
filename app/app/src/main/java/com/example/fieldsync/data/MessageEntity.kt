package com.example.fieldsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One chat message. Messages are never edited, so they can never conflict.
 * expiresAt is set on THIS phone (send time or arrival time + 30 s), so the
 * vanish timer does not depend on every phone's clock being exact.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val createdAt: Long,   // sender's clock, epoch ms (only used to ignore old history)
    val expiresAt: Long,   // local clock, epoch ms: message is deleted after this
    val version: Int,      // server version (1 once the relay accepted it)
    val dirty: Boolean     // true = not yet delivered to the relay
)
