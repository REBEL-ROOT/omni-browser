package com.rebelroot.omni.tools.locker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.InputStream
import java.util.UUID

class PrivateLockerManager(private val context: Context) {

    companion object {
        private const val TAG = "PrivateLockerManager"
        private val DB_PASSPHRASE = "omni_secure_database_passphrase_bytes".toByteArray()
    }

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val lockerDir = File(context.filesDir, "locker").apply {
        if (!exists()) {
            mkdirs()
            // Drop a .nomedia file so Android system scraper ignore locker assets
            try {
                File(this, ".nomedia").createNewFile()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create .nomedia file", e)
            }
        }
    }

    // Encrypted SQLCipher Room Database instance
    private val database: LockerDatabase by lazy {
        System.loadLibrary("sqlcipher")
        val supportFactory = SupportOpenHelperFactory(DB_PASSPHRASE)
        Room.databaseBuilder(
            context.applicationContext,
            LockerDatabase::class.java,
            "locker.db"
        )
            .openHelperFactory(supportFactory)
            .build()
    }

    /**
     * Retrieves flow of indexed private files
     */
    fun getSecureFiles(): Flow<List<LockerFile>> {
        return database.fileDao().getAllFiles()
    }

    /**
     * Helper to get category subfolder name for sorting locker items
     */
    private fun getSubfolderForMimeType(mimeType: String, filename: String): String {
        val mime = mimeType.lowercase()
        val name = filename.lowercase()
        return when {
            mime.startsWith("image") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> "images"
            mime.startsWith("video") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".avi") || name.endsWith(".3gp") -> "videos"
            mime.equals("application/pdf") || mime.contains("msword") || mime.contains("officedocument") || name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx") -> "docs"
            mime.equals("application/epub+zip") || name.endsWith(".epub") -> "epub"
            mime.startsWith("text") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".xml") -> "txt"
            else -> "others"
        }
    }

    /**
     * Find the location of an encrypted file inside the locker sandbox.
     * Checks subfolders first, then falls back to the root lockerDir.
     */
    fun getFileLocation(secureId: String): File {
        val subDirs = listOf("videos", "music", "images", "documents", "docs", "epub", "txt", "others")
        for (subDir in subDirs) {
            val file = File(File(lockerDir, subDir), secureId)
            if (file.exists()) {
                return file
            }
        }
        return File(lockerDir, secureId)
    }

    /**
     * Encrypts and saves a Uri resource (e.g. download or scan) into the secure sandbox.
     */
    suspend fun saveUriToLocker(uri: Uri, originalName: String, mimeType: String): String {
        val secureId = UUID.randomUUID().toString()
        val subDirName = getSubfolderForMimeType(mimeType, originalName)
        val subDir = File(lockerDir, subDirName).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val targetFile = File(subDir, secureId)

        // CRITICAL gotcha: EncryptedFile builder will crash if target already exists.
        // We delete it beforehand to ensure absolute safety.
        if (targetFile.exists()) {
            targetFile.delete()
        }

        try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                targetFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                encryptedFile.openFileOutput().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Write record metadata to SQLCipher database
            val fileSize = targetFile.length()
            val fileRecord = LockerFile(
                id = secureId,
                displayName = originalName,
                mimeType = mimeType,
                sizeBytes = fileSize,
                createdAt = System.currentTimeMillis()
            )
            database.fileDao().insert(fileRecord)
            Log.i(TAG, "File encrypted and saved to sandbox: $secureId ($originalName) under $subDirName")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write encrypted file to locker sandbox", e)
            throw e
        }
        return secureId
    }

    /**
     * Decrypts and retrieves the InputStream for a secure file, enabling in-memory viewing.
     */
    fun decryptFileStream(secureId: String): InputStream {
        val targetFile = getFileLocation(secureId)
        val encryptedFile = EncryptedFile.Builder(
            context,
            targetFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return encryptedFile.openFileInput()
    }

    /**
     * Decrypts a secure file and temporarily copies it to cache directory to open via external intent (e.g., pdf reader)
     */
    fun decryptToCacheFile(secureId: String, displayName: String): File {
        val cacheFile = File(context.cacheDir, displayName)
        if (cacheFile.exists()) cacheFile.delete()

        decryptFileStream(secureId).use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return cacheFile
    }

    /**
     * Deletes a secure file from the disk sandbox and indexes.
     */
    suspend fun deleteFile(secureId: String) {
        try {
            val fileRecord = database.fileDao().getFileById(secureId)
            fileRecord?.let {
                val cacheFile = File(context.cacheDir, it.displayName)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                    Log.d(TAG, "Deleted decrypted cache file on deletion: ${it.displayName}")
                }
            }
            val targetFile = getFileLocation(secureId)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            database.fileDao().deleteById(secureId)
            Log.i(TAG, "Secure file deleted successfully: $secureId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete secure file: $secureId", e)
            throw e
        }
    }

    /**
     * Deletes all decrypted cache files that were temporarily created to open secure files.
     */
    suspend fun clearDecryptedCache() {
        try {
            val files = database.fileDao().getAllFilesList()
            for (fileRecord in files) {
                val cacheFile = File(context.cacheDir, fileRecord.displayName)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                    Log.d(TAG, "Cleared cached decrypted file: ${fileRecord.displayName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing decrypted cache", e)
        }
    }
}
