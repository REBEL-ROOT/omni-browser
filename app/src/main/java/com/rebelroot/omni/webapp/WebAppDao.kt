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

package com.rebelroot.omni.webapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for installed Web Apps.
 */
@Dao
interface WebAppDao {

    @Query("SELECT * FROM web_apps ORDER BY isPinned DESC, lastUsedDate DESC")
    fun getAll(): Flow<List<WebAppEntity>>

    @Query("SELECT * FROM web_apps WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE url = :url AND profileId = :profileId LIMIT 1")
    suspend fun getByUrlAndProfile(url: String, profileId: String): WebAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: WebAppEntity): Long

    @Update
    suspend fun update(app: WebAppEntity)

    @Query("DELETE FROM web_apps WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("UPDATE web_apps SET lastUsedDate = :time, launchCount = launchCount + 1 WHERE id = :id")
    suspend fun recordLaunch(id: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE web_apps SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE web_apps SET iconPath = :iconPath WHERE id = :id")
    suspend fun updateIcon(id: String, iconPath: String)

    @Query("SELECT COUNT(*) FROM web_apps")
    fun getCount(): Flow<Int>
}
