package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_predictions")
data class ScanPredictionEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val label: String,
    val labelCode: String,
    val confidence: Float,
    val rank: Int,
    val concernBand: String,
    val createdAt: Long,
)
