package com.sky22333.skyadb.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevicePropertiesTest {
    @Test fun parsesPropertyDumpAndIgnoresMalformedLines() {
        val properties = parseDeviceProperties("""
            [ro.product.brand]: [Google]
            [ro.build.display.id]: [build: [123]]
            [ro.build.version.security_patch]: []
            malformed line
            [missing end]: [value
        """.trimIndent())
        assertEquals("Google", properties["ro.product.brand"])
        assertEquals("build: [123]", properties["ro.build.display.id"])
        assertEquals("", properties["ro.build.version.security_patch"])
        assertFalse(properties.containsKey("missing end"))
    }
}
