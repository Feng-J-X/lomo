package com.lomo.ui.component.markdown

import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

object MarkdownKnownTagFilter {
    fun eraseKnownTags(
        root: ImmutableNode,
        tags: Iterable<String>,
    ): ImmutableNode {
        val tagPatterns = buildTagPatterns(tags)
        if (tagPatterns.isEmpty()) return root

        stripKnownTags(root.node, tagPatterns)
        trimParagraphEdges(root.node)
        pruneEmptyParagraphs(root.node)
        return root
    }

    fun stripInlineTags(
        input: String,
        tags: Iterable<String>,
    ): String {
        val tagPatterns = buildTagPatterns(tags)
        return eraseTagsInText(input = input, tagPatterns = tagPatterns)
    }

    private fun buildTagPatterns(tags: Iterable<String>): List<Regex> =
        tags
            .asSequence()
            .map { it.trim().trimStart('#').trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .map(::createTagPattern)
            .toList()

    private fun stripKnownTags(
        node: Node,
        tagPatterns: List<Regex>,
    ) {
        if (shouldSkipNode(node)) return

        if (node is Text) {
            node.literal = eraseTagsInText(node.literal.orEmpty(), tagPatterns)
            return
        }

        var child = node.firstChild
        while (child != null) {
            val next = child.next
            stripKnownTags(child, tagPatterns)
            child = next
        }
    }

    private fun shouldSkipNode(node: Node): Boolean =
        node is Heading ||
            node is Link ||
            node is FencedCodeBlock ||
            node is IndentedCodeBlock ||
            node is Code

    private fun eraseTagsInText(
        input: String,
        tagPatterns: List<Regex>,
    ): String {
        if (tagPatterns.isEmpty() || '#' !in input) return input

        var stripped = input
        var changed = false

        tagPatterns.forEach { pattern ->
            val updated =
                stripped.replace(pattern) { match ->
                    changed = true
                    match.groupValues[1]
                }
            stripped = updated
        }

        if (!changed) return input

        return stripped
            .replace(HORIZONTAL_WHITESPACE_REGEX, " ")
            .replace(SPACE_BEFORE_PUNCTUATION_REGEX, "")
            .replace(SPACE_BEFORE_NEWLINE_REGEX, "\n")
            .replace(LEADING_EMPTY_LINES_REGEX, "")
    }

    private fun createTagPattern(tag: String): Regex =
        Regex("(^|\\s)#${Regex.escape(tag)}(?:/)?(?=\\s|$|[.,!?;:，。！？；：、)\\]}】）])")

    private fun trimParagraphEdges(root: Node) {
        traverse(root) { node ->
            if (node is Paragraph) {
                trimParagraphEdge(node, fromStart = true)
                trimParagraphEdge(node, fromStart = false)
            }
        }
    }

    private fun trimParagraphEdge(
        paragraph: Paragraph,
        fromStart: Boolean,
    ) {
        var cursor: Node? = if (fromStart) paragraph.firstChild else paragraph.lastChild
        while (cursor != null && isParagraphEdgeJunk(cursor)) {
            val next = if (fromStart) cursor.next else cursor.previous
            cursor.unlink()
            cursor = next
        }
    }

    private fun isParagraphEdgeJunk(node: Node): Boolean =
        node is SoftLineBreak ||
            node is HardLineBreak ||
            (node is Text && node.literal.orEmpty().isBlank())

    private fun pruneEmptyParagraphs(root: Node) {
        val emptyParagraphs = mutableListOf<Paragraph>()
        traverse(root) { node ->
            if (node is Paragraph && !hasRenderableContent(node)) {
                emptyParagraphs += node
            }
        }
        emptyParagraphs.forEach { it.unlink() }
    }

    private fun hasRenderableContent(node: Node): Boolean =
        when (node) {
            is Text -> {
                node.literal?.any { !it.isWhitespace() } == true
            }

            is SoftLineBreak, is HardLineBreak -> {
                false
            }

            is Code -> {
                !node.literal.isNullOrBlank()
            }

            is FencedCodeBlock -> {
                !node.literal.isNullOrBlank()
            }

            is IndentedCodeBlock -> {
                !node.literal.isNullOrBlank()
            }

            is Image -> {
                true
            }

            else -> {
                var child = node.firstChild
                while (child != null) {
                    if (hasRenderableContent(child)) {
                        return true
                    }
                    child = child.next
                }
                false
            }
        }

    private fun traverse(
        root: Node,
        visit: (Node) -> Unit,
    ) {
        visit(root)
        var child = root.firstChild
        while (child != null) {
            val next = child.next
            traverse(child, visit)
            child = next
        }
    }

    private val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t]{2,}")
    private val SPACE_BEFORE_PUNCTUATION_REGEX = Regex("[ \\t]+(?=[.,!?;:，。！？；：、)\\]}】）])")
    private val SPACE_BEFORE_NEWLINE_REGEX = Regex("[ \\t]+(?=\\n)")
    private val LEADING_EMPTY_LINES_REGEX = Regex("^(?:[ \\t]*\\n)+")
}
