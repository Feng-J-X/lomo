package com.lomo.app.feature.settings

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lomo.app.R
import com.lomo.app.feature.lanshare.LanSharePairingCodePolicy
import com.lomo.domain.model.SyncEngineState
import com.lomo.domain.model.WebDavSyncState

object SettingsErrorPresenter {
    @Composable
    fun gitSyncNowSubtitle(
        state: SyncEngineState,
        lastSyncTime: Long,
        localizeError: (String) -> String = { it },
    ): String =
        when (state) {
            is SyncEngineState.Syncing.Pulling -> stringResource(R.string.settings_git_sync_status_pulling)
            is SyncEngineState.Syncing.Committing -> stringResource(R.string.settings_git_sync_status_committing)
            is SyncEngineState.Syncing.Pushing -> stringResource(R.string.settings_git_sync_status_pushing)
            is SyncEngineState.Initializing -> stringResource(R.string.settings_git_sync_status_initializing)
            is SyncEngineState.Error -> stringResource(R.string.settings_git_sync_status_error, localizeError(state.message))
            is SyncEngineState.NotConfigured -> stringResource(R.string.settings_git_sync_status_not_configured)
            else -> relativeSyncSubtitle(lastSyncTime, R.string.settings_git_sync_now_subtitle, R.string.settings_git_sync_never)
        }

    @Composable
    fun webDavSyncNowSubtitle(
        state: WebDavSyncState,
        lastSyncTime: Long,
    ): String =
        when (state) {
            WebDavSyncState.Connecting -> stringResource(R.string.settings_webdav_sync_status_connecting)
            WebDavSyncState.Listing -> stringResource(R.string.settings_webdav_sync_status_listing)
            WebDavSyncState.Uploading -> stringResource(R.string.settings_webdav_sync_status_uploading)
            WebDavSyncState.Downloading -> stringResource(R.string.settings_webdav_sync_status_downloading)
            WebDavSyncState.Deleting -> stringResource(R.string.settings_webdav_sync_status_deleting)
            WebDavSyncState.Initializing -> stringResource(R.string.settings_webdav_sync_status_initializing)
            is WebDavSyncState.Error -> stringResource(R.string.settings_webdav_sync_status_error, state.message)
            WebDavSyncState.NotConfigured -> stringResource(R.string.settings_webdav_sync_status_not_configured)
            else -> relativeSyncSubtitle(lastSyncTime, R.string.settings_webdav_sync_now_subtitle, R.string.settings_webdav_sync_never)
        }

    @Composable
    fun pairingCodeMessage(raw: String): String =
        when (LanSharePairingCodePolicy.userMessageKey(raw)) {
            LanSharePairingCodePolicy.UserMessageKey.INVALID_PAIRING_CODE -> stringResource(R.string.share_error_invalid_pairing_code)
            LanSharePairingCodePolicy.UserMessageKey.UNKNOWN -> stringResource(R.string.share_error_unknown)
        }

    @Composable
    private fun relativeSyncSubtitle(
        lastSyncTime: Long,
        syncedResId: Int,
        neverResId: Int,
    ): String {
        if (lastSyncTime <= 0) return stringResource(neverResId)
        val relative =
            DateUtils
                .getRelativeTimeSpanString(
                    lastSyncTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
        return stringResource(syncedResId, relative)
    }
}
