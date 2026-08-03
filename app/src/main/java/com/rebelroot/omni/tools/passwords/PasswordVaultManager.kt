package com.rebelroot.omni.tools.passwords

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

class PasswordVaultManager(
    context: Context,
    passphraseBytes: ByteArray
) {
    private val database: PasswordDatabase = PasswordDatabase.create(context, passphraseBytes)
    private val dao: PasswordDao = database.passwordDao()

    fun getAllPasswords(): Flow<List<PasswordEntry>> = dao.getAllFlow()

    fun searchPasswordsFlow(query: String): Flow<List<PasswordEntry>> = dao.searchFlow(query)

    suspend fun addPassword(entry: PasswordEntry) {
        dao.insert(entry)
    }

    suspend fun updatePassword(entry: PasswordEntry) {
        dao.update(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePassword(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAllPasswords() {
        dao.deleteAll()
    }

    suspend fun deletePasswordsSince(cutoffTime: Long) {
        dao.deleteSince(cutoffTime)
    }

    suspend fun getPasswordsForDomain(domain: String): List<PasswordEntry> {
        return dao.getByDomain(domain)
    }

    suspend fun searchPasswords(query: String): List<PasswordEntry> {
        return searchPasswordsFlow(query).first()
    }

    suspend fun importAll(entries: List<PasswordEntry>) {
        dao.insertAll(entries)
    }

    suspend fun exportAll(): List<PasswordEntry> {
        return dao.getAllFlow().first()
    }

    fun close() {
        database.close()
    }
}