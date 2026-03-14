package com.lomo.domain.model

import com.lomo.domain.model.SimpleLineDiff.DiffOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleLineDiffTest {
    @Test
    fun `identical texts produce no hunks`() {
        val hunks = SimpleLineDiff.diff("hello\nworld", "hello\nworld")
        assertTrue(hunks.isEmpty())
    }

    @Test
    fun `empty old text shows all inserts`() {
        val hunks = SimpleLineDiff.diff("", "a\nb")
        val lines = hunks.flatMap { it.lines }
        assertTrue(lines.all { it.op == DiffOp.INSERT || it.op == DiffOp.DELETE })
    }

    @Test
    fun `empty new text shows all deletes`() {
        val hunks = SimpleLineDiff.diff("a\nb", "")
        val lines = hunks.flatMap { it.lines }
        assertTrue(lines.any { it.op == DiffOp.DELETE })
    }

    @Test
    fun `single line change produces one hunk with delete and insert`() {
        val hunks = SimpleLineDiff.diff("hello", "world")
        assertEquals(1, hunks.size)
        val lines = hunks[0].lines
        assertTrue(lines.any { it.op == DiffOp.DELETE && it.text == "hello" })
        assertTrue(lines.any { it.op == DiffOp.INSERT && it.text == "world" })
    }

    @Test
    fun `addition in middle produces correct diff`() {
        val old = "a\nb\nc"
        val new = "a\nb\nnew\nc"
        val hunks = SimpleLineDiff.diff(old, new)
        val inserts = hunks.flatMap { it.lines }.filter { it.op == DiffOp.INSERT }
        assertEquals(1, inserts.size)
        assertEquals("new", inserts[0].text)
    }

    @Test
    fun `line numbers are correct`() {
        val hunks = SimpleLineDiff.diff("a\nb\nc", "a\nx\nc")
        val lines = hunks.flatMap { it.lines }
        val deletedLine = lines.first { it.op == DiffOp.DELETE }
        val insertedLine = lines.first { it.op == DiffOp.INSERT }
        assertEquals(2, deletedLine.oldLineNumber)
        assertEquals(2, insertedLine.newLineNumber)
    }

    @Test
    fun `distant changes produce separate hunks`() {
        // 10 equal lines, 1 change, 10 equal lines, 1 change
        val oldLines =
            (1..10).map { "line$it" } + listOf("old1") +
                (12..21).map { "line$it" } + listOf("old2")
        val newLines =
            (1..10).map { "line$it" } + listOf("new1") +
                (12..21).map { "line$it" } + listOf("new2")
        val hunks = SimpleLineDiff.diff(oldLines.joinToString("\n"), newLines.joinToString("\n"))
        assertEquals(2, hunks.size)
    }
}
