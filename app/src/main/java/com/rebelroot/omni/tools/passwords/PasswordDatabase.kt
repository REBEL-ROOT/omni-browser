package com.rebelroot.omni.tools.passwords

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID

@Entity(tableName = "password_entries")
data class PasswordEntry(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "password")
    val password: String,
    @ColumnInfo(name = "label")
    val label: String = "",
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface PasswordDao {
    @Query("SELECT * FROM password_entries ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<PasswordEntry>>

    @Query(
        """
        SELECT * FROM password_entries
        WHERE lower(domain) LIKE '%' || lower(:query) || '%'
           OR lower(username) LIKE '%' || lower(:query) || '%'
           OR lower(label) LIKE '%' || lower(:query) || '%'
        ORDER BY updated_at DESC
        """
    )
    fun searchFlow(query: String): Flow<List<PasswordEntry>>

    @Query(
        """
        SELECT * FROM password_entries
        WHERE lower(domain) = lower(:domain)
           OR lower(domain) LIKE '%' || lower(:domain) || '%'
           OR lower(:domain) LIKE '%' || lower(domain) || '%'
        ORDER BY updated_at DESC
        """
    )
    suspend fun getByDomain(domain: String): List<PasswordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long

    @Update
    suspend fun update(entry: PasswordEntry): Int

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PasswordEntry>): List<Long>

    @Query("DELETE FROM password_entries")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM password_entries WHERE updated_at >= :cutoff")
    suspend fun deleteSince(cutoff: Long): Int
}

@Database(entities = [PasswordEntry::class], version = 1, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao

    companion object {
        private const val DATABASE_NAME = "password_vault.db"

        fun create(context: Context, passphraseBytes: ByteArray): PasswordDatabase {
            System.loadLibrary("sqlcipher")
            val supportFactory = SupportOpenHelperFactory(passphraseBytes)
            return Room.databaseBuilder(
                context.applicationContext,
                PasswordDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(supportFactory)
                .build()
        }
    }
}
