package com.omni.browser.tools.locker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LockerFileDao {
    @Query("SELECT * FROM locker_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<LockerFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: LockerFile)

    @Query("DELETE FROM locker_files WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [LockerFile::class], version = 1, exportSchema = false)
abstract class LockerDatabase : RoomDatabase() {
    abstract fun fileDao(): LockerFileDao
}
