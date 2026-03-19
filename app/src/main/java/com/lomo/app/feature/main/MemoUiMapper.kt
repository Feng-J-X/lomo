package com.lomo.app.feature.main

import android.net.Uri
import com.lomo.domain.model.Memo
import com.lomo.ui.component.card.buildMemoCardCollapsedSummary
import com.lomo.ui.component.card.shouldShowMemoCardExpand
import com.lomo.ui.component.markdown.ImmutableNode
import com.lomo.ui.component.markdown.MarkdownKnownTagFilter
import com.lomo.ui.component.markdown.MarkdownParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class MemoUiMapper
    @Inject
    constructor() {
        suspend fun mapToUiModels(
            memos: List<Memo>,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
            prioritizedMemoIds: Set<String> = emptySet(),
        ): List<MemoUiModel> =
            withContext(Dispatchers.Default) {
                if (memos.isEmpty()) {
                    return@withContext emptyList()
                }

                val prioritizedIds =
                    if (prioritizedMemoIds.isNotEmpty()) {
                        prioritizedMemoIds
                    } else {
                        memos
                            .asSequence()
                            .take(DEFAULT_PRIORITY_WINDOW_SIZE)
                            .map { it.id }
                            .toSet()
                    }

                memos.map { memo ->
                    mapToUiModel(
                        memo = memo,
                        rootPath = rootPath,
                        imagePath = imagePath,
                        imageMap = imageMap,
                        precomputeMarkdown = memo.id in prioritizedIds,
                    )
                }
            }

        fun mapToUiModel(
            memo: Memo,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
            precomputeMarkdown: Boolean = true,
            existingNode: ImmutableNode? = null,
            existingProcessedContent: String? = null,
        ): MemoUiModel {
            val processedContent = buildProcessedContent(memo.content, rootPath, imagePath, imageMap)
            val canReuseExistingNode =
                existingNode != null &&
                    existingProcessedContent != null &&
                    existingProcessedContent == processedContent
            val parsedNode =
                when {
                    canReuseExistingNode -> existingNode
                    precomputeMarkdown -> MarkdownKnownTagFilter.eraseKnownTags(MarkdownParser.parse(processedContent), memo.tags)
                    else -> null
                }
            val imageUrls = extractImageUrls(processedContent)
            val shouldShowExpand = shouldShowMemoCardExpand(memo.content)
            val collapsedSummary = buildMemoCardCollapsedSummary(memo.content, memo.tags)

            return MemoUiModel(
                memo = memo,
                processedContent = processedContent,
                markdownNode = parsedNode,
                tags = memo.tags.toImmutableList(),
                imageUrls = imageUrls,
                shouldShowExpand = shouldShowExpand,
                collapsedSummary = collapsedSummary,
            )
        }

        private fun buildProcessedContent(
            content: String,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
        ): String {
            var resolvedContent = content

            resolvedContent =
                WIKI_IMAGE_REGEX.replace(resolvedContent) { match ->
                    val path = sanitizeWikiImagePath(match.groupValues[1])
                    val resolved =
                        resolveImageModel(path, isWikiStyle = true, rootPath = rootPath, imagePath = imagePath, imageMap = imageMap)
                    val finalUrl = (resolved as? File)?.absolutePath ?: resolved.toString()
                    "![]($finalUrl)"
                }

            resolvedContent =
                MARKDOWN_IMAGE_REGEX.replace(resolvedContent) { match ->
                    val alt = match.groupValues[1]
                    val path = match.groupValues[2]

                    if (AUDIO_EXTENSIONS.any { path.lowercase().endsWith(it) }) {
                        match.value
                    } else {
                        val resolved =
                            resolveImageModel(
                                imageUrl = path,
                                isWikiStyle = false,
                                rootPath = rootPath,
                                imagePath = imagePath,
                                imageMap = imageMap,
                            )
                        val finalUrl = (resolved as? File)?.absolutePath ?: resolved.toString()
                        "![$alt]($finalUrl)"
                    }
                }

            return resolvedContent
        }

        private fun resolveImageModel(
            imageUrl: String,
            isWikiStyle: Boolean,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
        ): Any {
            val normalizedImageUrl = normalizeImageUrl(imageUrl)
            findCachedImageUri(normalizedImageUrl, imageMap)?.let { return it }

            if (isAbsoluteOrRemoteImageUrl(normalizedImageUrl)) {
                return normalizedImageUrl
            }

            val relativePath = stripLeadingCurrentDir(normalizedImageUrl)
            val candidateBasePaths = buildCandidateBasePaths(isWikiStyle, rootPath, imagePath, relativePath)
            candidateBasePaths.forEach { basePath ->
                if (basePath.startsWith("content://")) {
                    return normalizedImageUrl
                }

                val normalizedBasePath =
                    if (basePath.startsWith("file://")) {
                        parseUriPath(basePath) ?: basePath
                    } else {
                        basePath
                    }
                val resolvedFile = resolveRelativeFile(normalizedBasePath, relativePath)
                if (resolvedFile.exists()) {
                    return resolvedFile
                }
            }

            candidateBasePaths.firstOrNull()?.let { basePath ->
                if (!basePath.startsWith("content://")) {
                    val normalizedBasePath =
                        if (basePath.startsWith("file://")) {
                            parseUriPath(basePath) ?: basePath
                        } else {
                            basePath
                        }
                    return resolveRelativeFile(normalizedBasePath, relativePath)
                }
            }
            return normalizedImageUrl
        }

        private fun buildCandidateBasePaths(
            isWikiStyle: Boolean,
            rootPath: String?,
            imagePath: String?,
            relativePath: String,
        ): List<String> {
            val candidates = LinkedHashSet<String>()

            fun addBasePath(path: String?) {
                val value = path?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    candidates += value
                }
            }

            if (isWikiStyle) {
                addBasePath(imagePath)
                addBasePath(rootPath)
                return candidates.toList()
            }

            if (looksLikeManagedImageFilename(relativePath)) {
                addBasePath(imagePath)
                addBasePath(rootPath)
            } else {
                addBasePath(rootPath)
                addBasePath(imagePath)
            }
            return candidates.toList()
        }

        private fun findCachedImageUri(
            imageUrl: String,
            imageMap: Map<String, Uri>,
        ): Uri? {
            if (imageMap.isEmpty()) return null
            val candidates = buildImageMapCandidates(imageUrl)
            return candidates.firstNotNullOfOrNull { key -> imageMap[key] }
        }

        private fun buildImageMapCandidates(imageUrl: String): List<String> {
            val candidates = LinkedHashSet<String>()

            fun addCandidate(raw: String?) {
                val value = raw?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    candidates.add(value)
                }
            }

            fun addPathForms(raw: String?) {
                val normalized = normalizeImageUrl(raw.orEmpty())
                if (normalized.isBlank()) return
                addCandidate(normalized)
                addCandidate(decodeUrlComponent(normalized))
                val noQuery = normalized.substringBefore('?').substringBefore('#')
                addCandidate(noQuery)
                addCandidate(decodeUrlComponent(noQuery))
                val stripped = stripLeadingRelativeSegments(noQuery)
                addCandidate(stripped)
                val basename = stripped.substringAfterLast('/')
                addCandidate(basename)
                addCandidate(decodeUrlComponent(basename))

                if (normalized.startsWith("file://") || normalized.startsWith("content://")) {
                    addCandidate(extractLastPathSegment(normalized))
                }
            }

            addPathForms(imageUrl)
            return candidates.toList()
        }

        private fun sanitizeWikiImagePath(rawPath: String): String = rawPath.substringBefore('|').trim()

        private fun normalizeImageUrl(raw: String): String =
            raw
                .trim()
                .removeSurrounding("<", ">")
                .replace('\\', '/')

        private fun stripLeadingCurrentDir(path: String): String {
            var result = path
            while (result.startsWith("./")) {
                result = result.removePrefix("./")
            }
            return result
        }

        private fun stripLeadingRelativeSegments(path: String): String {
            var result = stripLeadingCurrentDir(path)
            while (result.startsWith("../")) {
                result = result.removePrefix("../")
            }
            return result.trimStart('/')
        }

        private fun resolveRelativeFile(
            basePath: String,
            relativePath: String,
        ): File {
            var base = File(basePath)
            var path = relativePath

            while (path.startsWith("../")) {
                base = base.parentFile ?: base
                path = path.removePrefix("../")
            }
            path = stripLeadingCurrentDir(path)
            return File(base, path)
        }

        private fun isAbsoluteOrRemoteImageUrl(imageUrl: String): Boolean {
            val lower = imageUrl.lowercase()
            return lower.startsWith("/") ||
                lower.startsWith("content://") ||
                lower.startsWith("file://") ||
                lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("data:image/")
        }

        private fun decodeUrlComponent(value: String): String =
            runCatching {
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }.getOrDefault(value)

        private fun parseUriPath(value: String): String? =
            runCatching {
                URI(value).path
            }.getOrNull()

        private fun extractLastPathSegment(value: String): String? =
            parseUriPath(value)
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }

        private fun looksLikeManagedImageFilename(path: String): Boolean {
            val candidate = path.substringAfterLast('/').lowercase()
            return candidate.matches(Regex("img_\\d+\\.(png|jpg|jpeg|gif|webp)"))
        }

        private fun extractImageUrls(content: String): ImmutableList<String> {
            val imageUrls = mutableListOf<String>()
            EXTRACT_IMAGE_URL_REGEX.findAll(content).forEach { match ->
                val url = match.groupValues[1]
                if (url.isNotBlank()) {
                    imageUrls.add(url)
                }
            }
            return imageUrls.toImmutableList()
        }

        private companion object {
            private const val DEFAULT_PRIORITY_WINDOW_SIZE = 20
            private val WIKI_IMAGE_REGEX = Regex("!\\[\\[(.*?)\\]\\]")
            private val MARKDOWN_IMAGE_REGEX = Regex("!\\[(.*?)\\]\\((.*?)\\)")
            private val EXTRACT_IMAGE_URL_REGEX = Regex("!\\[.*?\\]\\((.*?)\\)")
            private val AUDIO_EXTENSIONS = setOf(".m4a", ".mp3", ".aac", ".wav")
        }
    }
