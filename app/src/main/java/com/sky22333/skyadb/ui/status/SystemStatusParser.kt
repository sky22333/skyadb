package com.sky22333.skyadb.ui.status

import kotlin.math.max

data class SystemProcess(
    val pid: Int,
    val name: String,
    val uid: Int? = null,
    val cpuPercent: Double = 0.0,
    val memoryBytes: Long? = null,
    val ioReadBytesPerSecond: Double = 0.0,
    val ioWriteBytesPerSecond: Double = 0.0,
    val rxBytesPerSecond: Double = 0.0,
    val txBytesPerSecond: Double = 0.0,
) {
    val ioBytesPerSecond: Double
        get() = ioReadBytesPerSecond + ioWriteBytesPerSecond
}

data class SystemStatusSample(
    val cpuTotalTicks: Long = 0,
    val cpuIdleTicks: Long = 0,
    val cpuCoreCount: Int = 1,
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0,
    val processCpuTicks: Map<Int, Long> = emptyMap(),
    val processIoReadBytes: Map<Int, Long> = emptyMap(),
    val processIoWriteBytes: Map<Int, Long> = emptyMap(),
    val uidRxBytes: Map<Int, Long> = emptyMap(),
    val uidTxBytes: Map<Int, Long> = emptyMap(),
    val networkInterface: String? = null,
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
    val backlightCurrent: Int? = null,
    val backlightMax: Int? = null,
    val backlightName: String? = null,
    val autoBrightness: Boolean? = null,
    val currentApp: String? = null,
    val processes: List<SystemProcess> = emptyList(),
    val activeNetworkInterface: String? = null,
    val networkInterfaces: List<String> = emptyList(),
    val networkInterface: String? = null,
    val rxBytesPerSecond: Double = 0.0,
    val txBytesPerSecond: Double = 0.0,
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0,
)

data class ParsedSystemStatus(
    val metrics: SystemStatusMetrics,
    val sample: SystemStatusSample,
)

data class ParsedProcessStatus(
    val processes: List<SystemProcess>,
    val sample: SystemStatusSample,
)

object SystemStatusParser {
    private const val BatteryMarker = "__SKY_STATUS_BATTERY__"
    private const val MemoryMarker = "__SKY_STATUS_MEMORY__"
    private const val CpuMarker = "__SKY_STATUS_CPU__"
    private const val DiskMarker = "__SKY_STATUS_DISK__"
    private const val NetworkMarker = "__SKY_STATUS_NETWORK__"
    private const val NetworkInterfaceMarker = "__SKY_STATUS_NETWORK_IFACE__"
    private const val BrightnessMarker = "__SKY_STATUS_BRIGHTNESS__"
    private const val BrightnessModeMarker = "__SKY_STATUS_BRIGHTNESS_MODE__"
    private const val BacklightMarker = "__SKY_STATUS_BACKLIGHT__"
    private const val FocusMarker = "__SKY_STATUS_FOCUS__"
    private const val ProcessesMarker = "__SKY_STATUS_PROCESSES__"
    private const val ProcessStatsMarker = "__SKY_STATUS_PROCESS_STATS__"
    private const val UidNetworkMarker = "__SKY_STATUS_UID_NETWORK__"

    private val UidRegex = Regex("""uid=(-?\d+)""")
    private val TagRegex = Regex("""tag=(0x[0-9a-fA-F]+)""")
    private val BucketRegex = Regex("""st=(\d+)\s+rb=(\d+)\s+rp=(\d+)\s+tb=(\d+)\s+tp=(\d+)""")
    private val LegacyUidNetworkRegex = Regex("""\{uid=(\d+),package=[^}]*\}=(\d+)""")
    private val AppUidRegex = Regex("""u(\d+)_a(\d+)""")

    fun buildCommand(includeProcesses: Boolean = true): String =
        """
        echo '$BatteryMarker'
        dumpsys battery 2>/dev/null
        echo '$MemoryMarker'
        cat /proc/meminfo 2>/dev/null
        echo '$CpuMarker'
        grep '^cpu' /proc/stat 2>/dev/null
        echo '$DiskMarker'
        df -k /data 2>/dev/null
        echo '$NetworkMarker'
        cat /proc/net/dev 2>/dev/null
        echo '$NetworkInterfaceMarker'
        (ip route get 1.1.1.1 2>/dev/null; ip -6 route get 2606:4700:4700::1111 2>/dev/null; ip route 2>/dev/null | grep '^default'; ip -6 route 2>/dev/null | grep '^default') | sed -n 's/.* dev \([^ ]*\).*/\1/p' | head -n 1
        echo '$BrightnessMarker'
        settings get system screen_brightness 2>/dev/null
        echo '$BrightnessModeMarker'
        settings get system screen_brightness_mode 2>/dev/null
        echo '$BacklightMarker'
        backlight_ok=0
        for d in /sys/class/backlight/*; do
          [ -e "${'$'}d/brightness" ] || continue
          current=$(cat "${'$'}d/brightness" 2>/dev/null)
          maximum=$(cat "${'$'}d/max_brightness" 2>/dev/null)
          if [ -n "${'$'}current" ] && [ -n "${'$'}maximum" ]; then
            echo "${'$'}current|${'$'}maximum|${'$'}{d##*/}"
            backlight_ok=1
            break
          fi
        done
        if [ "${'$'}backlight_ok" -ne 1 ] && command -v su >/dev/null 2>&1; then
          su 0 sh -c 'for d in /sys/class/backlight/*; do [ -e "${'$'}d/brightness" ] || continue; current=$(cat "${'$'}d/brightness" 2>/dev/null); maximum=$(cat "${'$'}d/max_brightness" 2>/dev/null); if [ -n "${'$'}current" ] && [ -n "${'$'}maximum" ]; then echo "${'$'}current|${'$'}maximum|${'$'}{d##*/}"; break; fi; done' 2>/dev/null
        fi
        echo '$FocusMarker'
        (dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'; dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' | head -n 5)
        """.trimIndent() + if (includeProcesses) "\n${processCommand()}" else ""

    fun buildProcessCommand(): String = processCommand()

    private val ProcessStatsLoop =
        """
        for p in /proc/[0-9]*; do
          pid=${'$'}{p##*/}
          stat=
          read -r stat 2>/dev/null < "${'$'}p/stat" || continue
          rest=${'$'}{stat##*) }
          set -- ${'$'}rest
          utime=${'$'}{12}
          stime=${'$'}{13}
          read_bytes=0
          write_bytes=0
          while IFS=: read -r key value; do
            case "${'$'}key" in
              read_bytes) read_bytes=${'$'}{value# } ;;
              write_bytes) write_bytes=${'$'}{value# } ;;
            esac
          done 2>/dev/null < "${'$'}p/io"
          echo "${'$'}pid|${'$'}utime|${'$'}stime|${'$'}read_bytes|${'$'}write_bytes"
        done
        """.trimIndent()

    private fun processCommand(): String =
        """
        echo '$CpuMarker'
        grep '^cpu' /proc/stat 2>/dev/null
        echo '$ProcessesMarker'
        ps -A -o PID,UID,RSS,NAME 2>/dev/null || ps -A -o PID,USER,RSS,NAME 2>/dev/null || ps -A -o PID,RSS,NAME 2>/dev/null || ps -A -o PID,NAME 2>/dev/null
        echo '$ProcessStatsMarker'
        process_stats_ok=0
        if command -v su >/dev/null 2>&1; then
          su -c '${ProcessStatsLoop}' 2>/dev/null && process_stats_ok=1
        fi
        if [ "${'$'}process_stats_ok" -ne 1 ]; then
          ${ProcessStatsLoop}
        fi
        echo '$UidNetworkMarker'
        dumpsys netstats detail 2>/dev/null | grep -E 'ident=|st=' | head -n 3000
        """.trimIndent()

    fun parse(
        raw: String,
        previous: SystemStatusSample? = null,
        elapsedMillis: Long = 0,
        preferredNetworkInterface: String? = null,
    ): ParsedSystemStatus {
        val sections = sections(raw)
        val battery = parseBattery(sections[BatteryMarker].orEmpty())
        val memory = parseMemory(sections[MemoryMarker].orEmpty())
        val cpu = parseCpu(sections[CpuMarker].orEmpty())
        val storage = parseStorage(sections[DiskMarker].orEmpty())
        val activeInterface = sections[NetworkInterfaceMarker]
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
        val networkSelection = parseNetwork(
            raw = sections[NetworkMarker].orEmpty(),
            activeInterface = activeInterface,
            preferredInterface = preferredNetworkInterface,
        )
        val backlight = parseBacklight(sections[BacklightMarker].orEmpty())
        val brightness = sections[BrightnessMarker]?.trim()?.toIntOrNull()
        val brightnessMode = sections[BrightnessModeMarker]?.trim()?.toIntOrNull()
        val focus = parseCurrentApp(sections[FocusMarker].orEmpty())
        val processMetadata = parseProcessMetadata(sections[ProcessesMarker].orEmpty())
        val processStats = parseProcessStats(sections[ProcessStatsMarker].orEmpty())
        val uidNetwork = parseUidNetwork(sections[UidNetworkMarker].orEmpty())

        val totalDelta = if (previous != null && cpu.totalTicks > previous.cpuTotalTicks) {
            cpu.totalTicks - previous.cpuTotalTicks
        } else {
            0L
        }
        val cores = cpu.coreCount.coerceAtLeast(1)
        val cpuUsage = if (totalDelta > 0 && previous != null && cpu.idleTicks >= previous.cpuIdleTicks) {
            val idleDelta = cpu.idleTicks - previous.cpuIdleTicks
            ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0).coerceIn(0.0, 100.0)
        } else {
            null
        }

        val elapsedSeconds = max(elapsedMillis, 1L) / 1000.0
        val hasNetworkDelta =
            previous != null &&
                previous.networkInterface == networkSelection.selectedInterface &&
                networkSelection.network.rxBytes >= previous.networkRxBytes &&
                networkSelection.network.txBytes >= previous.networkTxBytes
        val rxSpeed = if (hasNetworkDelta) {
            (networkSelection.network.rxBytes - previous.networkRxBytes) / elapsedSeconds
        } else {
            0.0
        }
        val txSpeed = if (hasNetworkDelta) {
            (networkSelection.network.txBytes - previous.networkTxBytes) / elapsedSeconds
        } else {
            0.0
        }

        val processes = buildProcessList(
            processMetadata = processMetadata,
            processStats = processStats,
            uidNetwork = uidNetwork,
            totalDelta = totalDelta,
            cores = cores,
            elapsedSeconds = elapsedSeconds,
            previous = previous,
        )

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
                    backlightCurrent = backlight.current,
                    backlightMax = backlight.max,
                    backlightName = backlight.name,
                    autoBrightness = brightnessMode?.let { it == 1 },
                    currentApp = focus,
                    processes = processes,
                    activeNetworkInterface = networkSelection.activeInterface,
                    networkInterfaces = networkSelection.interfaces,
                    networkInterface = networkSelection.selectedInterface,
                    rxBytesPerSecond = rxSpeed,
                    txBytesPerSecond = txSpeed,
                    networkRxBytes = networkSelection.network.rxBytes,
                    networkTxBytes = networkSelection.network.txBytes,
                ),
            sample =
                SystemStatusSample(
                    cpuTotalTicks = cpu.totalTicks,
                    cpuIdleTicks = cpu.idleTicks,
                    cpuCoreCount = cpu.coreCount,
                    networkRxBytes = networkSelection.network.rxBytes,
                    networkTxBytes = networkSelection.network.txBytes,
                    processCpuTicks = processStats.mapValues { it.value.utime + it.value.stime },
                    processIoReadBytes = processStats.mapValues { it.value.readBytes },
                    processIoWriteBytes = processStats.mapValues { it.value.writeBytes },
                    uidRxBytes = uidNetwork.mapValues { it.value.first },
                    uidTxBytes = uidNetwork.mapValues { it.value.second },
                    networkInterface = networkSelection.selectedInterface,
                ),
        )
    }

    fun parseProcesses(
        raw: String,
        previous: SystemStatusSample? = null,
        elapsedMillis: Long = 0,
    ): ParsedProcessStatus {
        val sections = sections(raw)
        val cpu = parseCpu(sections[CpuMarker].orEmpty())
        val processMetadata = parseProcessMetadata(sections[ProcessesMarker].orEmpty())
        val processStats = parseProcessStats(sections[ProcessStatsMarker].orEmpty())
        val uidNetwork = parseUidNetwork(sections[UidNetworkMarker].orEmpty())
        val totalDelta = if (previous != null && cpu.totalTicks > previous.cpuTotalTicks) {
            cpu.totalTicks - previous.cpuTotalTicks
        } else {
            0L
        }
        val cores = cpu.coreCount.coerceAtLeast(1)
        val elapsedSeconds = max(elapsedMillis, 1L) / 1000.0
        return ParsedProcessStatus(
            processes = buildProcessList(
                processMetadata = processMetadata,
                processStats = processStats,
                uidNetwork = uidNetwork,
                totalDelta = totalDelta,
                cores = cores,
                elapsedSeconds = elapsedSeconds,
                previous = previous,
            ),
            sample = SystemStatusSample(
                cpuTotalTicks = cpu.totalTicks,
                cpuIdleTicks = cpu.idleTicks,
                cpuCoreCount = cpu.coreCount,
                processCpuTicks = processStats.mapValues { it.value.utime + it.value.stime },
                processIoReadBytes = processStats.mapValues { it.value.readBytes },
                processIoWriteBytes = processStats.mapValues { it.value.writeBytes },
                uidRxBytes = uidNetwork.mapValues { it.value.first },
                uidTxBytes = uidNetwork.mapValues { it.value.second },
            ),
        )
    }

    private fun buildProcessList(
        processMetadata: List<ProcessMetadata>,
        processStats: Map<Int, ProcessStat>,
        uidNetwork: Map<Int, Pair<Long, Long>>,
        totalDelta: Long,
        cores: Int,
        elapsedSeconds: Double,
        previous: SystemStatusSample?,
    ): List<SystemProcess> = processMetadata.map { metadata ->
        val stats = processStats[metadata.pid]
        val previousTicks = previous?.processCpuTicks?.get(metadata.pid)
        val currentTicks = stats?.let { it.utime + it.stime }
        val processCpu = if (
            previousTicks != null && currentTicks != null && totalDelta > 0 &&
                currentTicks >= previousTicks
        ) {
            ((currentTicks - previousTicks).toDouble() / totalDelta * 100.0 * cores)
                .coerceIn(0.0, 100.0 * cores)
        } else {
            0.0
        }
        val ioReadRate = rate(
            current = stats?.readBytes,
            previousValue = previous?.processIoReadBytes?.get(metadata.pid),
            elapsedSeconds = elapsedSeconds,
        )
        val ioWriteRate = rate(
            current = stats?.writeBytes,
            previousValue = previous?.processIoWriteBytes?.get(metadata.pid),
            elapsedSeconds = elapsedSeconds,
        )
        val uidRx = metadata.uid?.let { uidNetwork[it]?.first }
        val uidTx = metadata.uid?.let { uidNetwork[it]?.second }
        val rxRate = rate(
            current = uidRx,
            previousValue = metadata.uid?.let { previous?.uidRxBytes?.get(it) },
            elapsedSeconds = elapsedSeconds,
        )
        val txRate = rate(
            current = uidTx,
            previousValue = metadata.uid?.let { previous?.uidTxBytes?.get(it) },
            elapsedSeconds = elapsedSeconds,
        )
        SystemProcess(
            pid = metadata.pid,
            name = metadata.name,
            uid = metadata.uid,
            cpuPercent = processCpu,
            memoryBytes = metadata.rssKb?.times(1024L),
            ioReadBytesPerSecond = ioReadRate,
            ioWriteBytesPerSecond = ioWriteRate,
            rxBytesPerSecond = rxRate,
            txBytesPerSecond = txRate,
        )
    }

    private fun rate(current: Long?, previousValue: Long?, elapsedSeconds: Double): Double {
        if (current == null || previousValue == null || elapsedSeconds <= 0.0 || current < previousValue) return 0.0
        return (current - previousValue) / elapsedSeconds
    }

    private fun sections(raw: String): Map<String, String> {
        val markers = setOf(
            BatteryMarker,
            MemoryMarker,
            CpuMarker,
            DiskMarker,
            NetworkMarker,
            NetworkInterfaceMarker,
            BrightnessMarker,
            BrightnessModeMarker,
            BacklightMarker,
            FocusMarker,
            ProcessesMarker,
            ProcessStatsMarker,
            UidNetworkMarker,
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

    private data class Cpu(val totalTicks: Long, val idleTicks: Long, val coreCount: Int)

    private fun parseCpu(raw: String): Cpu {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val fields = lines
            .firstOrNull { it.startsWith("cpu ") }
            ?.substringAfter("cpu")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty()
        if (fields.isEmpty()) return Cpu(0, 0, 1)
        val idle = fields.getOrElse(3) { 0 } + fields.getOrElse(4) { 0 }
        val cores = lines.count { it.startsWith("cpu") && !it.startsWith("cpu ") }.coerceAtLeast(1)
        return Cpu(totalTicks = fields.take(8).sum(), idleTicks = idle, coreCount = cores)
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

    private data class NetworkSelection(
        val network: Network,
        val selectedInterface: String?,
        val activeInterface: String?,
        val interfaces: List<String>,
    )

    private fun parseNetwork(
        raw: String,
        activeInterface: String?,
        preferredInterface: String?,
    ): NetworkSelection {
        val interfaces = LinkedHashMap<String, Network>()
        raw.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val interfaceName = line.substring(0, separator).trim()
            if (interfaceName.isEmpty()) return@forEach
            val fields = line.substring(separator + 1).trim().split(Regex("\\s+"))
            if (fields.size >= 9) {
                interfaces[interfaceName] = Network(
                    rxBytes = fields[0].toLongOrNull() ?: 0,
                    txBytes = fields[8].toLongOrNull() ?: 0,
                )
            }
        }
        val routeInterface = activeInterface
            ?.takeIf { it.isNotBlank() && interfaces.containsKey(it) }
        val selectedName = preferredInterface
            ?.takeIf { it.isNotBlank() && interfaces.containsKey(it) }
            ?: routeInterface
            ?: interfaces.entries
                .filter { isPhysicalInterface(it.key) }
                .maxByOrNull { it.value.rxBytes + it.value.txBytes }
                ?.key
            ?: interfaces.entries
                .filter { it.key != "lo" }
                .maxByOrNull { it.value.rxBytes + it.value.txBytes }
                ?.key
        val visibleInterfaces = buildList {
            listOfNotNull(routeInterface ?: selectedName, selectedName).forEach { name ->
                if (name !in this) add(name)
            }
            interfaces.keys
                .filter { it != "lo" && (isPhysicalInterface(it) || it == preferredInterface) }
                .sorted()
                .forEach { name -> if (name !in this) add(name) }
        }
        return NetworkSelection(
            network = selectedName?.let { interfaces[it] } ?: Network(0, 0),
            selectedInterface = selectedName,
            activeInterface = routeInterface ?: selectedName,
            interfaces = visibleInterfaces,
        )
    }

    private fun isPhysicalInterface(name: String): Boolean {
        val value = name.lowercase()
        if (value == "lo" || value.startsWith("dummy") || value.startsWith("ifb") ||
            value.startsWith("tun") || value.startsWith("tap") || value.startsWith("p2p") ||
            value.startsWith("sit") || value.startsWith("ip6tnl") || value.startsWith("gre") ||
            value.startsWith("erspan") || value.startsWith("v4-")
        ) {
            return false
        }
        return value.startsWith("wlan") || value.startsWith("wifi") || value.startsWith("rmnet") ||
            value.startsWith("eth") || value.startsWith("radio") || value.startsWith("usb") ||
            value.startsWith("ccmni") || value.startsWith("pdp") || value.startsWith("wwan")
    }

    private data class Backlight(val current: Int?, val max: Int?, val name: String?)

    private fun parseBacklight(raw: String): Backlight {
        val parts = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains('|') }
            ?.split('|', limit = 3)
            ?: return Backlight(null, null, null)
        val current = parts.getOrNull(0)?.toIntOrNull()
        val max = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        val name = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        return Backlight(current, max, name)
    }

    private fun parseCurrentApp(raw: String): String? {
        val packageActivity = Regex("([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)")
        return raw.lineSequence()
            .mapNotNull { packageActivity.find(it)?.groupValues?.getOrNull(1) }
            .firstOrNull()
    }

    private data class ProcessMetadata(
        val pid: Int,
        val uid: Int?,
        val rssKb: Long?,
        val name: String,
    )

    private fun parseProcessMetadata(raw: String): List<ProcessMetadata> {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val header = lines.firstOrNull().orEmpty()
        val hasUid = header.contains("UID", ignoreCase = true) || header.contains("USER", ignoreCase = true)
        val hasRss = header.contains("RSS", ignoreCase = true)
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 4)
            val pid = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val uid = if (hasUid) parts.getOrNull(1)?.let(::parseUid) else null
            val rssKb = if (hasRss) parts.getOrNull(2)?.toLongOrNull() else null
            val name = when {
                hasUid && hasRss -> parts.getOrNull(3)
                hasUid -> parts.getOrNull(2)
                else -> parts.getOrNull(1)
            }?.trim().orEmpty()
            name.takeIf { it.isNotEmpty() }?.let { ProcessMetadata(pid, uid, rssKb, it) }
        }.take(MaxProcessEntries)
    }

    private fun parseUid(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val match = AppUidRegex.matchEntire(value) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val appId = 10_000 + (match.groupValues[2].toIntOrNull() ?: return null)
        return userId * 100_000 + appId
    }

    private data class ProcessStat(
        val utime: Long,
        val stime: Long,
        val readBytes: Long,
        val writeBytes: Long,
    )

    private fun parseProcessStats(raw: String): Map<Int, ProcessStat> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 5) return@mapNotNull null
            val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
            pid to ProcessStat(
                utime = parts[1].trim().toLongOrNull() ?: 0L,
                stime = parts[2].trim().toLongOrNull() ?: 0L,
                readBytes = parts[3].trim().toLongOrNull() ?: 0L,
                writeBytes = parts[4].trim().toLongOrNull() ?: 0L,
            )
        }.toMap()

    private fun parseUidNetwork(raw: String): Map<Int, Pair<Long, Long>> {
        val result = HashMap<Int, LongArray>()
        var uid = -1
        var include = false
        var sawIdent = false
        raw.lineSequence().forEach { line ->
            val value = line.trim()
            if (value.startsWith("ident=")) {
                sawIdent = true
                uid = UidRegex.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val tag = TagRegex.find(value)?.groupValues?.get(1)
                include = uid >= 0 && (tag == null || tag.equals("0x0", ignoreCase = true))
                return@forEach
            }
            if (!include) return@forEach
            val match = BucketRegex.find(value) ?: return@forEach
            val rx = match.groupValues[2].toLongOrNull() ?: 0L
            val tx = match.groupValues[4].toLongOrNull() ?: 0L
            val values = result.getOrPut(uid) { LongArray(2) }
            values[0] += rx
            values[1] += tx
        }
        if (!sawIdent) {
            LegacyUidNetworkRegex.findAll(raw).forEach legacy@{ match ->
                val legacyUid = match.groupValues[1].toIntOrNull() ?: return@legacy
                val bytes = match.groupValues[2].toLongOrNull() ?: 0L
                val values = result.getOrPut(legacyUid) { LongArray(2) }
                values[0] += bytes
            }
        }
        return result.mapValues { it.value[0] to it.value[1] }
    }

    private const val MaxProcessEntries = 2000
}
