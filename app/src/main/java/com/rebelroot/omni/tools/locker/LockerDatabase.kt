/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.tools.locker

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
    suspend fun insert(file: LockerFile): Long

    @Query("DELETE FROM locker_files WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM locker_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): LockerFile?

    @Query("SELECT * FROM locker_files")
    suspend fun getAllFilesList(): List<LockerFile>
}

@Database(entities = [LockerFile::class], version = 1, exportSchema = false)
abstract class LockerDatabase : RoomDatabase() {
    abstract fun fileDao(): LockerFileDao
}
