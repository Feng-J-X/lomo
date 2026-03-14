package com.lomo.domain.model

enum class SyncConflictResolutionChoice {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

data class SyncConflictFile(
    val relativePath: String,
    val localContent: String?,
    val remoteContent: String?,
    val isBinary: Boolean,
)

data class SyncConflictSet(
    val source: SyncBackendType,
    val files: List<SyncConflictFile>,
    val timestamp: Long,
)

data class SyncConflictResolution(
    val perFileChoices: Map<String, SyncConflictResolutionChoice>,
)
