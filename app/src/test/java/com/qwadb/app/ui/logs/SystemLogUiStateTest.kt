package com.qwadb.app.ui.logs

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemLogUiStateTest {
    @Test
    fun filteredLogs_filtersByLevelAndKeyword() {
        val state = SystemLogUiState(
            level = "E",
            query = "Crash",
            logs = listOf(
                "05-29 17:00:00.000 100 100 I App: Started",
                "05-29 17:00:01.000 100 100 E App: Crash happened",
                "05-29 17:00:02.000 100 100 E App: Other error",
            ),
        )

        assertEquals(
            listOf("05-29 17:00:01.000 100 100 E App: Crash happened"),
            state.filteredLogs,
        )
    }
}
