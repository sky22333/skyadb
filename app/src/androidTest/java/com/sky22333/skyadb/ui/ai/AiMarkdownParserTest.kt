package com.sky22333.skyadb.ui.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.enriched.markdown.parser.MarkdownASTNode
import com.swmansion.enriched.markdown.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMarkdownParserTest {
    @Test fun missingParagraphBoundaryRendersAsTableWithoutChangingSource() {
        val source = "当前连接状态\n| 项目 | 结果 |\n|---|---|\n| ADB | 无 |"
        fun hasTable(text: String): Boolean = Parser.shared.parseMarkdown(text)!!.children.any { it.type == MarkdownASTNode.NodeType.Table }
        assertFalse(hasTable(source))
        assertTrue(hasTable(separateMarkdownTables(source)))
        assertEquals("当前连接状态\n| 项目 | 结果 |\n|---|---|\n| ADB | 无 |", source)
        assertTrue(hasTable(separateMarkdownTables("| 项目 | 结果 |\n|---|---|\n| ADB | 无 |")))
    }
}
