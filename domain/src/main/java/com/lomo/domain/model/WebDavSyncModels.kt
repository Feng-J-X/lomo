package com.lomo.domain.model

enum class WebDavProvider {
    NUTSTORE,
    NEXTCLOUD,
    CUSTOM,
}

enum class WebDavSyncDirection {
    NONE,
    UPLOAD,
    DOWNLOAD,
    DELETE_LOCAL,
    DELETE_REMOTE,
}

enum class WebDavSyncReason {
    UNCHANGED,
    LOCAL_ONLY,
    REMOTE_ONLY,
    LOCAL_NEWER,
    REMOTE_NEWER,
    LOCAL_DELETED,
    REMOTE_DELETED,
    SAME_TIMESTAMP,
}

data class WebDavSyncOutcome(
    val path: String,
    val direction: WebDavSyncDirection,
    val reason: WebDavSyncReason,
)

data class WebDavSyncStatus(
    val remoteFileCount: Int,
    val localFileCount: Int,
    val pendingChanges: Int,
    val lastSyncTime: Long?,
)

sealed interface WebDavSyncResult {
    data class Success(
        val message: String,
        val outcomes: List<WebDavSyncOutcome> = emptyList(),
    ) : WebDavSyncResult

    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val outcomes: List<WebDavSyncOutcome> = emptyList(),
    ) : WebDavSyncResult

    data object NotConfigured : WebDavSyncResult
}

sealed interface WebDavSyncState {
    data object Idle : WebDavSyncState

    data object Initializing : WebDavSyncState

    data object Connecting : WebDavSyncState

    data object Listing : WebDavSyncState

    data object Uploading : WebDavSyncState

    data object Downloading : WebDavSyncState

    data object Deleting : WebDavSyncState

    data class Success(
        val timestamp: Long,
        val summary: String,
    ) : WebDavSyncState

    data class Error(
        val message: String,
        val timestamp: Long,
    ) : WebDavSyncState

    data object NotConfigured : WebDavSyncState
}
