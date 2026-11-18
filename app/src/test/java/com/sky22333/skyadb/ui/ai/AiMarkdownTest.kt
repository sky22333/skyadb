package com.sky22333.skyadb.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiMarkdownTest {
    @Test fun insertsOnlyMissingBoundaryBeforeTable() {
        val source = "当前连接状态\n| 项目 | 结果 |\n|---|---|\n| ADB | 无 |"
        assertEquals("当前连接状态\n\n| 项目 | 结果 |\n|---|---|\n| ADB | 无 |", separateMarkdownTables(source))
        assertEquals("说明\n\n| 名称 |\n|---|\n| 设备 |", separateMarkdownTables("说明\n| 名称 |\n|---|\n| 设备 |"))
        assertEquals("说明\n\n名称 | 结果\n--- | :---:\n设备 | 无", separateMarkdownTables("说明\n名称 | 结果\n--- | :---:\n设备 | 无"))
    }
    @Test fun keepsValidMarkdownAndCodeUnchanged() {
        val inputs = listOf(
            "说明\n\n| 名称 | 结果 |\n|---|---|\n|设备|无|",
            "```md\n说明\n| 名称 | 结果 |\n|---|---|\n```",
            "~~~\n说明\n| 名称 | 结果 |\n|---|---|\n~~~",
            "说明\n    | 名称 | 结果 |\n    |---|---|",
            "> 说明\n> | 名称 | 结果 |\n> |---|---|",
            "普通文字 | 状态\n并非表格 | ---",
            "说明\n| 名称 | 结果 |\n|---|---|---|",
        )
        inputs.forEach { assertEquals(it, separateMarkdownTables(it)) }
    }
    @Test fun handlesEscapedAndCodeSpanPipesAndRepeatedUpdates() {
        val source = "说明\n| `a|b` | x\\|y |\n|---|---|\n| a | b |"
        val expected = "说明\n\n| `a|b` | x\\|y |\n|---|---|\n| a | b |"
        assertEquals(expected, separateMarkdownTables(source))
        assertEquals(expected, separateMarkdownTables(separateMarkdownTables(source)))
        assertEquals("说明\n| A | B |\n|---", separateMarkdownTables("说明\n| A | B |\n|---"))
    }
}
