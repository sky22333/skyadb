package com.sky22333.skyadb.ui.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastbootPartitionTargetTest {
    @Test fun respectsExplicitSlotsWithoutDuplicatingSuffixes() {
        assertEquals("boot", fastbootPartitionTarget("boot", ""))
        assertEquals("boot_a", fastbootPartitionTarget("boot", "a"))
        assertEquals("boot_b", fastbootPartitionTarget("boot_b", ""))
        assertNull(fastbootPartitionTarget("boot_a", "b"))
        assertNull(fastbootPartitionTarget("boot image", ""))
        assertNull(fastbootPartitionTarget("boot", "x"))
    }
}
