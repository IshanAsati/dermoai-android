package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.UserProfileDetailsEntity

@Dao
interface UserProfileDetailsDao {
    @Query("SELECT * FROM user_profile_details WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): UserProfileDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: UserProfileDetailsEntity)
}
