package com.focusgate.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfMissing(sessions: List<SessionEntity>)

    @Update
    fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime ASC")
    fun getAll(): List<SessionEntity>

    @Query(
        "SELECT * FROM sessions WHERE packageName = :packageName " +
            "AND subTargetKey = :subTargetKey AND endTime IS NULL " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun findLatestOpen(packageName: String, subTargetKey: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE startTime BETWEEN :startMs AND :endMs ORDER BY startTime ASC")
    fun getBetween(startMs: Long, endMs: Long): List<SessionEntity>

    @Query("DELETE FROM sessions")
    fun deleteAll()
}
