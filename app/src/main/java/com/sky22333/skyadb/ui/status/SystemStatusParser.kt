package com.sky22333.skyadb.ui.status

import kotlin.math.max

data class SystemProcess(
    val pid: Int,
    val name: String,
)

data class SystemStatusSample(
    val cpuTotalTicks: Long = 0,
    val cpuIdleTicks: Long = 0,
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0,
)

data class SystemStatusMetrics(
    val batteryLevel: Int? = null,
    val batteryTemperatureCelsius: Double? = null,
    val batteryStatus: Int? = null,
    val batteryPowered: Boolean = false,
    val memoryTotalBytes: Long? = null,
    val memoryUsedBytes: Long? = null,
    val cpuUsagePercent: Double? = null,
    val storageTotalBytes: Long? = null,
    val storageUsedBytes: Long? = null,
    val brightness: Int? = null,
    val autoBrightness: Boolean? = null,
    val currentApp: String? = null,
    val processes: List<SystemProcess> = emptyList(),
    val rxBytesPerSecond: Double = 0.0,
    val txBytesPerSecond: Double = 0.0,
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0,
)

data class ParsedSystemStatus(
    val metrics: SystemStatusMetrics,
    val sample: SystemStatusSample,
)

object SystemStatusParser {
    private const val BatteryMarker = "__SKY_STATUS_BATTERY__"
    private const val MemoryMarker = "__SKY_STATUS_MEMORY__"
    private const val CpuMarker = "__SKY_STATUS_CPU__"
    private const val DiskMarker = "__SKY_STATUS_DISK__"
    private const val NetworkMarker = "__SKY_STATUS_NETWORK__"
    private const val BrightnessMarker = "__SKY_STATUS_BRIGHTNESS__"
    private const val BrightnessModeMarker = "__SKY_STATUS_BRIGHTNESS_MODE__"
    private const val FocusMarker = "__SKY_STATUS_FOCUS__"
    private const val ProcessesMarker = "__SKY_STATUS_PROCESSES__"

    fun buildCommand(): String =
        """
        echo '$BatteryMarker'
        dumpsys battery 2>/dev/null
        echo '$MemoryMarker'
        cat /proc/meminfo 2>/dev/null
        echo '$CpuMarker'
        head -n 2 /proc/stat 2>/dev/null
        echo '$DiskMarker'
        df -k /data 2>/dev/null
        echo '$NetworkMarker'
        cat /proc/net/dev 2>/dev/null
        echo '$BrightnessMarker'
        settings get system screen_brightness 2>/dev/null
        echo '$BrightnessModeMarker'
        settings get system screen_brightness_mode 2>/dev/null
        echo '$FocusMarker'
        dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'
        echo '$ProcessesMarker'
        (ps -A -o PID,NAME 2>/dev/null || ps 2>/dev/null) | head -n 20
        """.trimIndent()

    fun parse(
        raw: String,
        previous: SystemStatusSample? = null,
        elapsedMillis: Long = 0,
    ): ParsedSystemStatus {
        val sections = sections(raw)
        val battery = parseBattery(sections[BatteryMarker].orEmpty())
        val memory = parseMemory(sections[MemoryMarker].orEmpty())
        val cpu = parseCpu(sections[CpuMarker].orEmpty())
        val storage = parseStorage(sections[DiskMarker].orEmpty())
        val network = parseNetwork(sections[NetworkMarker].orEmpty())
        val brightness = sections[BrightnessMarker]?.trim()?.toIntOrNull()
        val brightnessMode = sections[BrightnessModeMarker]?.trim()?.toIntOrNull()
        val focus = parseCurrentApp(sections[FocusMarker].orEmpty())
        val processes = parseProcesses(sections[ProcessesMarker].orEmpty())

        val cpuUsage = if (
            previous != null &&
                cpu.totalTicks > previous.cpuTotalTicks &&
                cpu.idleTicks >= previous.cpuIdleTicks
        ) {
            val totalDelta = cpu.totalTicks - previous.cpuTotalTicks
            val idleDelta = cpu.idleTicks - previous.cpuIdleTicks
            ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0).coerceIn(0.0, 100.0)
        } else {
            null
        }

        val elapsedSeconds = max(elapsedMillis, 0L) / 1000.0
        val hasNetworkDelta =
            previous != null &&
                elapsedSeconds > 0.0 &&
                network.rxBytes >= previous.networkRxBytes &&
                network.txBytes >= previous.networkTxBytes
        val rxSpeed = if (hasNetworkDelta) {
            (network.rxBytes - previous.networkRxBytes) / elapsedSeconds
        } else {
            0.0
        }
        val txSpeed = if (hasNetworkDelta) {
            (network.txBytes - previous.networkTxBytes) / elapsedSeconds
        } else {
            0.0
        }

        return ParsedSystemStatus(
            metrics =
                SystemStatusMetrics(
                    batteryLevel = battery.level,
                    batteryTemperatureCelsius = battery.temperatureCelsius,
                    batteryStatus = battery.status,
                    batteryPowered = battery.powered,
                    memoryTotalBytes = memory.totalBytes,
                    memoryUsedBytes = memory.usedBytes,
                    cpuUsagePercent = cpuUsage,
                    storageTotalBytes = storage.first,
                    storageUsedBytes = storage.second,
                    brightness = brightness,
                    autoBrightness = brightnessMode?.let { it == 1 },
                    currentApp = focus,
                    processes = processes,
                    rxBytesPerSecond = rxSpeed,
                    txBytesPerSecond = txSpeed,
                    networkRxBytes = network.rxBytes,
                    networkTxBytes = network.txBytes,
                ),
            sample =
                SystemStatusSample(
                    cpuTotalTicks = cpu.totalTicks,
                    cpuIdleTicks = cpu.idleTicks,
                    networkRxBytes = network.rxBytes,
                    networkTxBytes = network.txBytes,
                ),
        )
    }

    private fun sections(raw: String): Map<String, String> {
        val markers = setOf(
            BatteryMarker,
            MemoryMarker,
            CpuMarker,
            DiskMarker,
            NetworkMarker,
            BrightnessMarker,
            BrightnessModeMarker,
            FocusMarker,
            ProcessesMarker,
        )
        val result = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null
        raw.lineSequence().forEach { line ->
            val marker = line.trim()
            if (marker in markers) {
                current = StringBuilder().also { result[marker] = it }
            } else {
                current?.appendLine(line)
            }
        }
        return result.mapValues { (_, value) -> value.toString() }
    }

    private data class Battery(
        val level: Int?,
        val temperatureCelsius: Double?,
        val status: Int?,
        val powered: Boolean,
    )

    private fun parseBattery(raw: String): Battery {
        val values = raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
            .toMap()
        return Battery(
            level = values["level"]?.toIntOrNull()?.takeIf { it in 0..100 },
            temperatureCelsius = values["temperature"]?.toDoubleOrNull()?.div(10.0),
            status = values["status"]?.toIntOrNull(),
            powered =
                values["ac powered"] == "true" ||
                    values["usb powered"] == "true" ||
                    values["wireless powered"] == "true" ||
                    values["dock powered"] == "true",
        )
    }

    private data class Memory(val totalBytes: Long?, val usedBytes: Long?)

    private fun parseMemory(raw: String): Memory {
        val values = raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .toMap()
        val total = values["MemTotal"]?.substringBefore(' ')?.toLongOrNull()?.times(1024)
        val available = values["MemAvailable"]?.substringBefore(' ')?.toLongOrNull()?.times(1024)
        return Memory(
            totalBytes = total,
            usedBytes = if (total != null && available != null) (total - available).coerceAtLeast(0) else null,
        )
    }

    private data class Cpu(val totalTicks: Long, val idleTicks: Long)

    private fun parseCpu(raw: String): Cpu {
        val fields = raw.lineSequence()
            .firstOrNull { it.trimStart().startsWith("cpu ") }
            ?.trim()
            ?.substringAfter("cpu")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty()
        if (fields.isEmpty()) return Cpu(0, 0)
        val idle = fields.getOrElse(3) { 0 } + fields.getOrElse(4) { 0 }
        return Cpu(totalTicks = fields.sum(), idleTicks = idle)
    }

    private fun parseStorage(raw: String): Pair<Long?, Long?> {
        val columns = raw.lineSequence()
            .drop(1)
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull {
                it.size >= 4 &&
                    it[1].toLongOrNull() != null &&
                    it[2].toLongOrNull() != null
            }
            ?: return null to null
        val total = columns.getOrNull(1)?.toLongOrNull()?.times(1024)
        val used = columns.getOrNull(2)?.toLongOrNull()?.times(1024)
        return total to used
    }

    private data class Network(val rxBytes: Long, val txBytes: Long)

    private fun parseNetwork(raw: String): Network {
        var rx = 0L
        var tx = 0L
        raw.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val interfaceName = line.substring(0, separator).trim()
            if (interfaceName == "lo" || interfaceName.isEmpty()) return@forEach
            val fields = line.substring(separator + 1).trim().split(Regex("\\s+"))
            if (fields.size >= 9) {
                rx += fields[0].toLongOrNull() ?: 0
                tx += fields[8].toLongOrNull() ?: 0
            }
        }
        return Network(rx, tx)
    }

    private fun parseCurrentApp(raw: String): String? {
        val focusLine = raw.lineSequence().firstOrNull { it.contains("mCurrentFocus") }
            ?: raw.lineSequence().firstOrNull { it.contains("mFocusedApp") }
            ?: return null
        return Regex("([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)")
            .find(focusLine)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun parseProcesses(raw: String): List<SystemProcess> =
        raw.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val trimmed = line.trim()
                val separator = trimmed.indexOfFirst(Char::isWhitespace)
                if (separator <= 0) return@mapNotNull null
                val pid = trimmed.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
                val name = trimmed.substring(separator).trim()
                name.takeIf(String::isNotBlank)?.let { SystemProcess(pid, it) }
            }
            .take(20)
            .toList()
}
