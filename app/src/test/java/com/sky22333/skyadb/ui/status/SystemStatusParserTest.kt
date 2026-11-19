package com.sky22333.skyadb.ui.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemStatusParserTest {
    @Test
    fun parsesBatteryMemoryStorageNetworkAndProcesses() {
        val parsed = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_BATTERY__
                AC powered: true
                USB powered: false
                status: 2
                level: 86
                voltage: 4000
                temperature: 320
                __SKY_STATUS_MEMORY__
                MemTotal:        8000000 kB
                MemAvailable:    3000000 kB
                __SKY_STATUS_CPU__
                cpu  100 0 50 850 0 0 0 0 0 0
                cpu0 100 0 50 850 0 0 0 0 0 0
                __SKY_STATUS_DISK__
                Filesystem 1K-blocks Used Available Use% Mounted on
                /dev/block/dm-0 1000000 400000 600000 40% /data
                __SKY_STATUS_NETWORK__
                Inter-| Receive | Transmit
                 wlan0: 1000 0 0 0 0 0 0 0 500 0 0 0 0 0 0 0
                __SKY_STATUS_BRIGHTNESS__
                120
                __SKY_STATUS_BRIGHTNESS_MODE__
                1
                __SKY_STATUS_FOCUS__
                mCurrentFocus=Window{abcdef u0 com.example.app/.MainActivity}
                __SKY_STATUS_PROCESSES__
                  PID NAME
                  123 com.example.app
                  456 system_server
                """.trimIndent(),
        )

        assertEquals(86, parsed.metrics.batteryLevel)
        assertEquals(32.0, parsed.metrics.batteryTemperatureCelsius ?: 0.0, 0.001)
        assertEquals(2, parsed.metrics.batteryStatus)
        assertEquals(true, parsed.metrics.batteryPowered)
        assertEquals(8_000_000L * 1024, parsed.metrics.memoryTotalBytes)
        assertEquals(5_000_000L * 1024, parsed.metrics.memoryUsedBytes)
        assertNull(parsed.metrics.cpuUsagePercent)
        assertEquals(1_000_000L * 1024, parsed.metrics.storageTotalBytes)
        assertEquals(400_000L * 1024, parsed.metrics.storageUsedBytes)
        assertEquals(120, parsed.metrics.brightness)
        assertEquals(true, parsed.metrics.autoBrightness)
        assertEquals("com.example.app/.MainActivity", parsed.metrics.currentApp)
        assertEquals(listOf("com.example.app", "system_server"), parsed.metrics.processes.map { it.name })
        assertEquals(1000L, parsed.metrics.networkRxBytes)
        assertEquals(500L, parsed.metrics.networkTxBytes)
    }

    @Test
    fun computesCpuUsageAndNetworkSpeedAgainstPreviousSample() {
        val first = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_CPU__
                cpu  100 0 50 850 0 0 0 0 0 0
                __SKY_STATUS_NETWORK__
                 wlan0: 1000 0 0 0 0 0 0 0 500 0 0 0 0 0 0 0
                """.trimIndent(),
        )
        val second = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_CPU__
                cpu  150 0 80 870 0 0 0 0 0 0
                __SKY_STATUS_NETWORK__
                 wlan0: 3000 0 0 0 0 0 0 0 1500 0 0 0 0 0 0 0
                """.trimIndent(),
            previous = first.sample,
            elapsedMillis = 2_000,
        )

        assertEquals(80.0, second.metrics.cpuUsagePercent ?: 0.0, 0.001)
        assertEquals(1000.0, second.metrics.rxBytesPerSecond, 0.001)
        assertEquals(500.0, second.metrics.txBytesPerSecond, 0.001)
    }

    @Test
    fun usesActiveNetworkInterfaceInsteadOfSummingEveryInterface() {
        val parsed = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_NETWORK__
                 wlan0: 1000 0 0 0 0 0 0 0 500 0 0 0 0 0 0 0
                 rmnet0: 9000 0 0 0 0 0 0 0 4000 0 0 0 0 0 0 0
                __SKY_STATUS_NETWORK_IFACE__
                wlan0
                """.trimIndent(),
        )

        assertEquals("wlan0", parsed.metrics.networkInterface)
        assertEquals(1000L, parsed.metrics.networkRxBytes)
        assertEquals(500L, parsed.metrics.networkTxBytes)
    }

    @Test
    fun readsBacklightRangeFromSysfs() {
        val parsed = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_BRIGHTNESS__
                128
                __SKY_STATUS_BRIGHTNESS_MODE__
                1
                __SKY_STATUS_BACKLIGHT__
                512|2047|backlight
                """.trimIndent(),
        )

        assertEquals(512, parsed.metrics.backlightCurrent)
        assertEquals(2047, parsed.metrics.backlightMax)
        assertEquals("backlight", parsed.metrics.backlightName)
        assertEquals(true, parsed.metrics.autoBrightness)
    }

    @Test
    fun computesPerProcessCpuIoAndUidNetworkRates() {
        val first = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_CPU__
                cpu  100 0 50 850 0 0 0 0 0 0
                __SKY_STATUS_PROCESSES__
                PID UID RSS NAME
                123 10123 2048 com.example.app
                __SKY_STATUS_PROCESS_STATS__
                123|10|5|1000|500
                __SKY_STATUS_UID_NETWORK__
                ident=[{type=0, networkId=100}] uid=10123 set=DEFAULT tag=0x0
                st=1 rb=1000 rp=10 tb=500 tp=5
                """.trimIndent(),
        )
        val second = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_CPU__
                cpu  150 0 80 870 0 0 0 0 0 0
                __SKY_STATUS_PROCESSES__
                PID UID RSS NAME
                123 10123 2048 com.example.app
                __SKY_STATUS_PROCESS_STATS__
                123|30|10|3000|1500
                __SKY_STATUS_UID_NETWORK__
                ident=[{type=0, networkId=100}] uid=10123 set=DEFAULT tag=0x0
                st=1 rb=3000 rp=20 tb=1500 tp=10
                """.trimIndent(),
            previous = first.sample,
            elapsedMillis = 1_000,
        )

        val process = second.metrics.processes.single()
        assertEquals(25.0, process.cpuPercent, 0.001)
        assertEquals(2_097_152L, process.memoryBytes)
        assertEquals(2_000.0, process.ioReadBytesPerSecond, 0.001)
        assertEquals(1_000.0, process.ioWriteBytesPerSecond, 0.001)
        assertEquals(3_000.0, process.ioBytesPerSecond, 0.001)
        assertEquals(2_000.0, process.rxBytesPerSecond, 0.001)
        assertEquals(1_000.0, process.txBytesPerSecond, 0.001)
    }

    @Test
    fun parsesUserColumnWhenUidColumnIsUnavailable() {
        val parsed = SystemStatusParser.parse(
            raw =
                """
                __SKY_STATUS_PROCESSES__
                PID USER RSS NAME
                123 u0_a123 2048 com.example.app
                456 shell 1024 sh
                """.trimIndent(),
        )

        assertEquals(2, parsed.metrics.processes.size)
        assertEquals(10123, parsed.metrics.processes[0].uid)
        assertEquals(2_097_152L, parsed.metrics.processes[0].memoryBytes)
        assertNull(parsed.metrics.processes[1].uid)
        assertEquals(1_048_576L, parsed.metrics.processes[1].memoryBytes)
    }
}
