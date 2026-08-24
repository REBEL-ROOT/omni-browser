package com.rebelroot.omni.sync.conflict

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.ApplyResult
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.model.SyncOperation
import com.rebelroot.omni.sync.storage.IngestResult
import com.rebelroot.omni.sync.storage.SyncStorage

data class ConflictResolutionResult(
    val applied: Boolean,
    val opId: String,
    val entityId: String,
    val details: String
)

class ConflictEngine(
    private val adapter: BookmarkAdapter,
    private val storage: SyncStorage
) {

    @Synchronized
    fun processIncomingOperation(
        collection: BookmarkCollection,
        op: SyncOperation
    ): ConflictResolutionResult {
        val eligibility = storage.checkIncomingEligibility(op)
        if (eligibility != IngestResult.APPLIED) {
            return ConflictResolutionResult(
                applied = false,
                opId = op.opId,
                entityId = op.entityId,
                details = "Skipped due to storage eligibility: $eligibility"
            )
        }

        val applyResult = adapter.applyRemoteOperation(collection, op)

        return when (applyResult) {
            is ApplyResult.Applied -> {
                storage.markIncomingApplied(op)
                ConflictResolutionResult(
                    applied = true,
                    opId = op.opId,
                    entityId = op.entityId,
                    details = "Successfully applied ${op.opType}"
                )
            }
            is ApplyResult.Rejected -> {
                storage.quarantineInvalidRecord(op.opId, op.toString(), applyResult.reason)
                ConflictResolutionResult(
                    applied = false,
                    opId = op.opId,
                    entityId = op.entityId,
                    details = "Rejected: ${applyResult.reason}"
                )
            }
            is ApplyResult.Quarantined -> {
                storage.quarantineInvalidRecord(op.opId, op.toString(), applyResult.reason)
                ConflictResolutionResult(
                    applied = false,
                    opId = op.opId,
                    entityId = op.entityId,
                    details = "Quarantined: ${applyResult.reason}"
                )
            }
        }
    }
}
