package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val mood: Int, // 1-5
    val createdAt: Long,
    val updatedAt: Long,
)
