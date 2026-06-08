package com.rebelroot.omni.tools.locker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locker_files")
data class LockerFile(
    @PrimaryKey val id: String,          // Secure random UUID representing the encrypted filename on disk
    val displayName: String,             // Original human-readable filename
    val mimeType: String,                // Original file content type (e.g. application/pdf, image/png)
    val sizeBytes: Long,                 // File size in bytes
    val createdAt: Long                  // Insertion timestamp
)
