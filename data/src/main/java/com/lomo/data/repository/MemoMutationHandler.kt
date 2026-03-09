package com.lomo.data.repository

import android.net.Uri
import com.lomo.data.local.dao.LocalFileStateDao
import com.lomo.data.local.dao.MemoDao
import com.lomo.data.local.datastore.LomoDataStore
import com.lomo.data.local.entity.LocalFileStateEntity
import com.lomo.data.local.entity.MemoEntity
import com.lomo.data.local.entity.MemoFileOutboxEntity
import com.lomo.data.local.entity.MemoFileOutboxOp
import com.lomo.data.local.entity.MemoFtsEntity
import com.lomo.data.local.entity.TrashMemoEntity
import com.lomo.data.source.MarkdownStorageDataSource
import com.lomo.data.source.MemoDirectoryType
import com.lomo.data.util.MemoLocalDateResolver
import com.lomo.data.util.MemoTextProcessor
import com.lomo.data.util.SearchTokenizer
import com.lomo.domain.model.Memo
import com.lomo.domain.model.StorageFilenameFormats
import com.lomo.domain.model.StorageTimestampFormats
import com.lomo.domain.usecase.MemoIdentityPolicy
import com.lomo.domain.usecase.MemoUpdateAction
import com.lomo.domain.usecase.ResolveMemoUpdateActionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * Handles active memo mutations (save/update). Trash lifecycle is delegated to [MemoTrashMutationHandler].
 */
class MemoMutationHandler
    @Inject
    constructor(
        private val markdownStorageDataSource: MarkdownStorageDataSource,
        private val dao: MemoDao,
        private val localFileStateDao: LocalFileStateDao,
        private val savePlanFactory: MemoSavePlanFactory,
        private val textProcessor: MemoTextProcessor,
        private val dataStore: LomoDataStore,
        private val trashMutationHandler: MemoTrashMutationHandler,
        private val resolveMemoUpdateActionUseCase: ResolveMemoUpdateActionUseCase,
        private val memoIdentityPolicy: MemoIdentityPolicy,
    ) {
        private companion object {
            const val OUTBOX_CLAIM_STALE_MS = 2 * 60_000L
        }

        private data class StorageFormatSettings(
            val filenameFormat: String = StorageFilenameFormats.DEFAULT_PATTERN,
            val timestampFormat: String = StorageTimestampFormats.DEFAULT_PATTERN,
            val isReady: Boolean = false,
        )

        private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val storageFormatSettings =
            combine(
                dataStore.storageFilenameFormat,
                dataStore.storageTimestampFormat,
            ) { filenameFormat, timestampFormat ->
                StorageFormatSettings(
                    filenameFormat = filenameFormat,
                    timestampFormat = timestampFormat,
                    isReady = true,
                )
            }.stateIn(
                scope = settingsScope,
                started = SharingStarted.Eagerly,
                initialValue = StorageFormatSettings(),
            )

        data class SaveDbResult(
            val savePlan: MemoSavePlan,
            val outboxId: Long,
        )

        private suspend fun currentStorageFormatSettings(): StorageFormatSettings {
            val cached = storageFormatSettings.value
            if (cached.isReady) {
                return cached
            }
            return StorageFormatSettings(
                filenameFormat = dataStore.storageFilenameFormat.first(),
                timestampFormat = dataStore.storageTimestampFormat.first(),
                isReady = true,
            )
        }

        suspend fun saveMemo(
            content: String,
            timestamp: Long,
        ) {
            val savePlan = createSavePlan(content, timestamp)
            persistMainMemoEntity(MemoEntity.fromDomain(savePlan.memo))
            flushSavedMemoToFile(savePlan)
        }

        suspend fun saveMemoInDb(
            content: String,
            timestamp: Long,
        ): SaveDbResult {
            val savePlan = createSavePlan(content, timestamp)
            val outboxId =
                dao.persistMemoWithOutbox(
                    memo = MemoEntity.fromDomain(savePlan.memo),
                    outbox = buildCreateOutbox(savePlan),
                )
            return SaveDbResult(savePlan = savePlan, outboxId = outboxId)
        }

        suspend fun flushSavedMemoToFile(savePlan: MemoSavePlan) {
            appendMainMemoContentAndUpdateState(
                filename = savePlan.filename,
                rawContent = savePlan.rawContent,
            )
        }

        suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ) {
            if (resolveMemoUpdateActionUseCase(newContent) == MemoUpdateAction.MOVE_TO_TRASH) {
                trashMutationHandler.moveToTrash(memo)
                return
            }
            if (dao.getMemo(memo.id) == null) return

            if (!flushMemoUpdateToFile(memo, newContent)) return
            val finalUpdatedMemo = buildUpdatedMemo(memo, newContent)
            persistMainMemoEntity(MemoEntity.fromDomain(finalUpdatedMemo))
        }

        suspend fun updateMemoInDb(
            memo: Memo,
            newContent: String,
        ): Long? {
            val sourceMemo = dao.getMemo(memo.id)?.toDomain() ?: return null

            if (resolveMemoUpdateActionUseCase(newContent) == MemoUpdateAction.MOVE_TO_TRASH) {
                return dao.moveMemoToTrashWithOutbox(
                    trashMemo = TrashMemoEntity.fromDomain(sourceMemo.copy(isDeleted = true)),
                    outbox = buildDeleteOutbox(sourceMemo),
                )
            }

            val finalUpdatedMemo = buildUpdatedMemo(sourceMemo, newContent)
            return dao.persistMemoWithOutbox(
                memo = MemoEntity.fromDomain(finalUpdatedMemo),
                outbox = buildUpdateOutbox(sourceMemo, newContent),
            )
        }

        suspend fun flushMemoUpdateToFile(
            memo: Memo,
            newContent: String,
        ): Boolean {
            if (resolveMemoUpdateActionUseCase(newContent) == MemoUpdateAction.MOVE_TO_TRASH) {
                return trashMutationHandler.moveToTrashFileOnly(memo)
            }

            val timeString = formatMemoTime(memo.timestamp)
            val filename = memo.dateKey + ".md"
            val cachedUriString = getMainSafUri(filename)
            val cachedUri = cachedUriString.toPersistedUriOrNull()
            val currentFileContent =
                if (cachedUri != null) {
                    markdownStorageDataSource.readFile(cachedUri)
                        ?: markdownStorageDataSource.readFileIn(MemoDirectoryType.MAIN, filename)
                } else {
                    markdownStorageDataSource.readFileIn(MemoDirectoryType.MAIN, filename)
                }

            if (currentFileContent == null) return false
            val lines = currentFileContent.lines().toMutableList()
            val success =
                textProcessor.replaceMemoBlock(
                    lines,
                    memo.rawContent,
                    memo.timestamp,
                    newContent,
                    timeString,
                    memo.id,
                )
            if (!success) return false

            val savedUri =
                markdownStorageDataSource.saveFileIn(
                    directory = MemoDirectoryType.MAIN,
                    filename = filename,
                    content = lines.joinToString("\n"),
                    append = false,
                )
            val metadata = markdownStorageDataSource.getFileMetadataIn(MemoDirectoryType.MAIN, filename)
            if (metadata != null) {
                upsertMainState(filename, metadata.lastModified, savedUri)
            }
            return true
        }

        suspend fun deleteMemo(memo: Memo) {
            trashMutationHandler.moveToTrash(memo)
        }

        suspend fun deleteMemoInDb(memo: Memo): Long? {
            val sourceMemo = dao.getMemo(memo.id)?.toDomain() ?: return null
            return dao.moveMemoToTrashWithOutbox(
                trashMemo = TrashMemoEntity.fromDomain(sourceMemo.copy(isDeleted = true)),
                outbox = buildDeleteOutbox(sourceMemo),
            )
        }

        suspend fun flushDeleteMemoToFile(memo: Memo): Boolean = trashMutationHandler.moveToTrashFileOnly(memo)

        suspend fun restoreMemo(memo: Memo) {
            trashMutationHandler.restoreFromTrash(memo)
        }

        suspend fun restoreMemoInDb(memo: Memo): Long? {
            val sourceMemo = dao.getTrashMemo(memo.id)?.toDomain() ?: return null
            val restoredMemo = sourceMemo.copy(isDeleted = false)
            return dao.restoreMemoFromTrashWithOutbox(
                memo = MemoEntity.fromDomain(restoredMemo),
                outbox = buildRestoreOutbox(sourceMemo),
            )
        }

        suspend fun flushRestoreMemoToFile(memo: Memo): Boolean = trashMutationHandler.restoreFromTrashFileOnly(memo)

        suspend fun deletePermanently(memo: Memo) {
            trashMutationHandler.deleteFromTrashPermanently(memo)
        }

        suspend fun hasPendingMemoFileOutbox(): Boolean = dao.getMemoFileOutboxCount() > 0

        suspend fun nextMemoFileOutbox(): MemoFileOutboxEntity? {
            val now = System.currentTimeMillis()
            return dao.claimNextMemoFileOutbox(
                claimToken = UUID.randomUUID().toString(),
                claimedAt = now,
                staleBefore = now - OUTBOX_CLAIM_STALE_MS,
            )
        }

        suspend fun acknowledgeMemoFileOutbox(id: Long) {
            dao.deleteMemoFileOutboxById(id)
        }

        suspend fun markMemoFileOutboxFailed(
            id: Long,
            throwable: Throwable?,
        ) {
            dao.markMemoFileOutboxFailed(
                id = id,
                updatedAt = System.currentTimeMillis(),
                lastError = throwable?.message?.take(512),
            )
        }

        suspend fun flushMemoFileOutbox(item: MemoFileOutboxEntity): Boolean =
            when (item.operation) {
                MemoFileOutboxOp.CREATE -> {
                    flushCreateFromOutbox(item)
                }

                MemoFileOutboxOp.UPDATE -> {
                    val newContent = item.newContent ?: return false
                    flushMemoUpdateToFile(outboxSourceMemo(item), newContent)
                }

                MemoFileOutboxOp.DELETE -> {
                    flushDeleteMemoToFile(outboxSourceMemo(item))
                }

                MemoFileOutboxOp.RESTORE -> {
                    flushRestoreMemoToFile(outboxSourceMemo(item))
                }

                else -> {
                    false
                }
            }

        private suspend fun persistMainMemoEntity(entity: MemoEntity) {
            dao.insertMemo(entity)
            dao.replaceTagRefsForMemo(entity)
            val tokenizedContent = SearchTokenizer.tokenize(entity.content)
            dao.insertMemoFts(MemoFtsEntity(entity.id, tokenizedContent))
        }

        private suspend fun createSavePlan(
            content: String,
            timestamp: Long,
        ): MemoSavePlan {
            val settings = currentStorageFormatSettings()
            val filenameFormat = settings.filenameFormat
            val timestampFormat = settings.timestampFormat
            val zoneId = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(timestamp)
            val dateString =
                StorageFilenameFormats
                    .formatter(filenameFormat)
                    .withZone(zoneId)
                    .format(instant)
            val timeString =
                StorageTimestampFormats
                    .formatter(timestampFormat)
                    .withZone(zoneId)
                    .format(instant)
            val baseId = memoIdentityPolicy.buildBaseId(dateString, timeString, content)
            val precomputedCollisionCount =
                dao.countMemoIdCollisions(
                    baseId = baseId,
                    globPattern = "${baseId}_*",
                )
            val precomputedSameTimestampCount = dao.countMemosByIdGlob("${dateString}_${timeString}_*")
            return savePlanFactory.create(
                content = content,
                timestamp = timestamp,
                filenameFormat = filenameFormat,
                timestampFormat = timestampFormat,
                existingFileContent = "",
                precomputedSameTimestampCount = precomputedSameTimestampCount,
                precomputedCollisionCount = precomputedCollisionCount,
            )
        }

        private fun buildCreateOutbox(savePlan: MemoSavePlan): MemoFileOutboxEntity =
            MemoFileOutboxEntity(
                operation = MemoFileOutboxOp.CREATE,
                memoId = savePlan.memo.id,
                memoDate = savePlan.memo.dateKey,
                memoTimestamp = savePlan.memo.timestamp,
                memoRawContent = savePlan.memo.rawContent,
                newContent = savePlan.memo.content,
                createRawContent = savePlan.rawContent,
            )

        private fun buildUpdateOutbox(
            sourceMemo: Memo,
            newContent: String,
        ): MemoFileOutboxEntity =
            MemoFileOutboxEntity(
                operation = MemoFileOutboxOp.UPDATE,
                memoId = sourceMemo.id,
                memoDate = sourceMemo.dateKey,
                memoTimestamp = sourceMemo.timestamp,
                memoRawContent = sourceMemo.rawContent,
                newContent = newContent,
                createRawContent = null,
            )

        private fun buildDeleteOutbox(sourceMemo: Memo): MemoFileOutboxEntity =
            MemoFileOutboxEntity(
                operation = MemoFileOutboxOp.DELETE,
                memoId = sourceMemo.id,
                memoDate = sourceMemo.dateKey,
                memoTimestamp = sourceMemo.timestamp,
                memoRawContent = sourceMemo.rawContent,
                newContent = null,
                createRawContent = null,
            )

        private fun buildRestoreOutbox(sourceMemo: Memo): MemoFileOutboxEntity =
            MemoFileOutboxEntity(
                operation = MemoFileOutboxOp.RESTORE,
                memoId = sourceMemo.id,
                memoDate = sourceMemo.dateKey,
                memoTimestamp = sourceMemo.timestamp,
                memoRawContent = sourceMemo.rawContent,
                newContent = sourceMemo.content,
                createRawContent = null,
            )

        private fun outboxSourceMemo(item: MemoFileOutboxEntity): Memo =
            Memo(
                id = item.memoId,
                timestamp = item.memoTimestamp,
                content = item.newContent.orEmpty(),
                rawContent = item.memoRawContent,
                dateKey = item.memoDate,
                localDate = MemoLocalDateResolver.resolve(item.memoDate),
            )

        private suspend fun flushCreateFromOutbox(item: MemoFileOutboxEntity): Boolean {
            val createRawContent = item.createRawContent ?: return false
            val filename = item.memoDate + ".md"
            appendMainMemoContentAndUpdateState(
                filename = filename,
                rawContent = createRawContent,
            )
            return true
        }

        private suspend fun appendMainMemoContentAndUpdateState(
            filename: String,
            rawContent: String,
        ) {
            val savedUriString = appendMainMemoContent(filename, rawContent)
            upsertMainState(filename, resolveMainFileLastModified(filename), savedUriString)
        }

        private suspend fun appendMainMemoContent(
            filename: String,
            rawContent: String,
        ): String? {
            val cachedUri = getMainSafUri(filename).toPersistedUriOrNull()
            return markdownStorageDataSource.saveFileIn(
                directory = MemoDirectoryType.MAIN,
                filename = filename,
                content = "\n$rawContent",
                append = true,
                uri = cachedUri,
            )
        }

        private suspend fun buildUpdatedMemo(
            memo: Memo,
            newContent: String,
        ): Memo {
            val timeString = formatMemoTime(memo.timestamp)
            val updatedAt = nextUpdatedAt(memo.updatedAt)
            return memo.copy(
                content = newContent,
                updatedAt = updatedAt,
                rawContent = "- $timeString $newContent",
                tags = textProcessor.extractTags(newContent),
                imageUrls = textProcessor.extractImages(newContent),
            )
        }

        private fun nextUpdatedAt(previousUpdatedAt: Long): Long {
            val now = System.currentTimeMillis()
            return if (now > previousUpdatedAt) now else previousUpdatedAt + 1
        }

        private suspend fun formatMemoTime(timestamp: Long): String {
            val timestampFormat = currentStorageFormatSettings().timestampFormat
            return StorageTimestampFormats
                .formatter(timestampFormat)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp))
        }

        private suspend fun getMainSafUri(filename: String): String? = localFileStateDao.getByFilename(filename, false)?.safUri

        private suspend fun resolveMainFileLastModified(filename: String): Long =
            markdownStorageDataSource
                .getFileMetadataIn(MemoDirectoryType.MAIN, filename)
                ?.lastModified
                ?: System.currentTimeMillis()

        private fun String?.toPersistedUriOrNull(): Uri? {
            val value = this ?: return null
            if (!(value.startsWith("content://") || value.startsWith("file://"))) return null
            return Uri.parse(value)
        }

        private suspend fun upsertMainState(
            filename: String,
            lastModified: Long,
            safUri: String? = null,
        ) {
            val existing = localFileStateDao.getByFilename(filename, false)
            localFileStateDao.upsert(
                LocalFileStateEntity(
                    filename = filename,
                    isTrash = false,
                    safUri = safUri ?: existing?.safUri,
                    lastKnownModifiedTime = lastModified,
                ),
            )
        }
    }
