package com.qwadb.app.status

import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.adb.KadbManager
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次采集得到的系统状态快照。 */
data class SystemStatus(
    val networkStatus: String = "",
    val batteryStatus: String = "",
    val storageStatus: String = "",
    val cpuUsage: String = "",
    val memoryUsage: String = "",
    val screenBrightness: String = "",
    val systemTime: String = "",
    val appProcesses: List<String> = emptyList(),
    val systemProcesses: List<String> = emptyList(),
    val currentApp: String = "",
    val runningServices: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = this == SystemStatus()
}

/**
 * 系统状态采集器：通过单次 ADB shell 会话（分段标记输出）采集 11 项系统状态指标。
 */
class SystemStatusCollector(
    private val kadbManager: KadbManager = AppServices.kadbManager,
) {
    suspend fun collect(): AdbOperationResult<SystemStatus> = withContext(Dispatchers.IO) {
        when (val result = kadbManager.shell(CombinedCommand)) {
            is AdbOperationResult.Failure -> result
            is AdbOperationResult.Success -> {
                val sections = splitSections(result.data.output)
                fun section(name: String) = sections[name].orEmpty()
                val processes = parseProcesses(section(SectionProcesses))
                AdbOperationResult.Success(
                    SystemStatus(
                        networkStatus = parseNetwork(section(SectionNetwork)),
                        batteryStatus = parseBattery(section(SectionBattery)),
                        storageStatus = parseStorage(section(SectionStorage)),
                        cpuUsage = parseCpu(section(SectionCpu)),
                        memoryUsage = parseMemory(section(SectionMemory)),
                        screenBrightness = parseBrightness(section(SectionBrightness)),
                        systemTime = parseSystemTime(section(SectionTimeZone), section(SectionTime)),
                        appProcesses = processes.app,
                        systemProcesses = processes.system,
                        currentApp = parseCurrentApp(section(SectionActivity)),
                        runningServices = parseServices(section(SectionServices)),
                    ),
                )
            }
        }
    }

    /** 输出按 `###MARKER` 分段；找不到分段时回退为空串，由各解析器兜底。 */
    private fun splitSections(output: String): Map<String, String> {
        val sections = LinkedHashMap<String, String>()
        var current = ""
        var currentParts = StringBuilder()
        output.lineSequence().forEach { line ->
            val marker = line.trim().takeIf { it.startsWith(SectionPrefix) }
            if (marker != null) {
                if (current.isNotEmpty()) sections[current] = currentParts.toString()
                current = marker
                currentParts = StringBuilder()
            } else {
                currentParts.append(line).append('\n')
            }
        }
        if (current.isNotEmpty()) sections[current] = currentParts.toString()
        return sections
    }

    private fun parseNetwork(output: String): String {
        val parts = StringBuilder()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("Active default network")) {
                val netId = line.substringAfter(':').trim()
                if (netId.isNotBlank() && netId != "0" && netId != "none") {
                    parts.append("netId ").append(netId).append('\n')
                }
            } else if (line.contains("NetworkAgentInfo")) {
                val type = line.substringAfter("{", "").substringBefore("}")
                if (type.isNotBlank()) parts.append(type.trim()).append('\n')
            }
        }
        return parts.toString().trim().ifBlank { appString(R.string.status_no_active_network) }
    }

    private fun parseBattery(output: String): String {
        var level: Int? = null
        var status: Int? = null
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("level:") -> level = line.substringAfter(':').trim().toIntOrNull()
                line.startsWith("status:") -> status = line.substringAfter(':').trim().toIntOrNull()
            }
        }
        val stateText = when (status) {
            2 -> appString(R.string.status_battery_charging)
            3 -> appString(R.string.status_battery_discharging)
            4 -> appString(R.string.status_battery_not_charging)
            5 -> appString(R.string.status_battery_full)
            else -> null
        }
        return when {
            level != null && stateText != null ->
                appString(R.string.status_battery_format, level.toString(), stateText)
            level != null -> appString(R.string.status_battery_format, level.toString(), appString(R.string.unknown))
            stateText != null -> stateText
            else -> appString(R.string.unknown)
        }
    }

    private fun parseStorage(output: String): String {
        val dataLine = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { line -> line.isNotBlank() && !line.startsWith("Filesystem") && line.endsWith("/data") }
            ?: output.lineSequence().map { it.trim() }.lastOrNull { it.isNotBlank() }
            ?: return appString(R.string.unknown)
        val fields = dataLine.split(Regex("\\s+"))
        if (fields.size < 5) return dataLine
        val size = fields[1]
        val used = fields[2]
        val usePercent = fields[4].trimEnd('%')
        return appString(R.string.status_storage_format, used, size, usePercent)
    }

    private fun parseCpu(output: String): String {
        val cpuLine = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("CPU:") }
            ?: return appString(R.string.unknown)
        val values = Regex("([\\d.]+)%\\s*([a-z]+)").findAll(cpuLine)
            .associate { it.groupValues[2] to it.groupValues[1].toDoubleOrNull() }
        val idle = values["idle"]
        val usage = if (idle != null) 100.0 - idle else values["user"]?.let { user ->
            user + (values["system"] ?: 0.0) + (values["nice"] ?: 0.0) +
                (values["iowait"] ?: 0.0) + (values["irq"] ?: 0.0)
        }
        return usage?.let { String.format(Locale.US, "%.1f%%", it.coerceIn(0.0, 100.0)) }
            ?: cpuLine
    }

    private fun parseMemory(output: String): String {
        var totalKb = 0L
        var availableKb = 0L
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("MemTotal:") -> totalKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
                line.startsWith("MemAvailable:") -> availableKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
            }
        }
        if (totalKb <= 0L || availableKb < 0L) return appString(R.string.unknown)
        val usedKb = totalKb - availableKb
        val percent = (usedKb * 100.0 / totalKb).coerceIn(0.0, 100.0)
        return appString(
            R.string.status_memory_format,
            formatBytes(usedKb),
            formatBytes(totalKb),
            String.format(Locale.US, "%.1f%%", percent),
        )
    }

    private fun parseBrightness(output: String): String {
        val level = output.trim().toIntOrNull() ?: return appString(R.string.unknown)
        val percent = (level * 100 / 255.0).toInt().coerceIn(0, 100)
        return appString(R.string.status_brightness_format, level, percent)
    }

    private fun parseSystemTime(timeZoneId: String, epochSeconds: String): String {
        val epoch = epochSeconds.trim().toLongOrNull() ?: return appString(R.string.unknown)
        val zone = runCatching { TimeZone.getTimeZone(timeZoneId.trim()) }.getOrNull()
            ?: TimeZone.getTimeZone("UTC")
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { this.timeZone = zone }
            .format(Date(epoch * 1000L))
    }

    private fun parseProcesses(output: String): ProcessLists {
        val app = mutableListOf<String>()
        val system = mutableListOf<String>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line == "USER PID NAME") return@forEach
            val fields = line.split(Regex("\\s+"), limit = 3)
            if (fields.size < 3) return@forEach
            val user = fields[0]
            val pid = fields[1]
            val name = fields[2]
            if (pid.toIntOrNull() == null || name.isBlank()) return@forEach
            val entry = "$pid $name"
            if (AppUidRegex.matches(user)) app += entry else system += entry
        }
        return ProcessLists(
            app = app.sortedBy { it.substringBefore(' ').toIntOrNull() ?: Int.MAX_VALUE }.take(MaxProcessEntries),
            system = system.sortedBy { it.substringBefore(' ').toIntOrNull() ?: Int.MAX_VALUE }.take(MaxProcessEntries),
        )
    }

    private fun parseCurrentApp(output: String): String {
        val line = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return appString(R.string.unknown)
        val tokens = line.substringAfter("{", "").split(Regex("\\s+"))
        val component = tokens.getOrNull(2) ?: return appString(R.string.unknown)
        val (pkg, activity) = component.split('/', limit = 2)
        return if (activity.isNotBlank()) "$pkg / $activity" else component
    }

    private fun parseServices(output: String): List<String> {
        val services = LinkedHashSet<String>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.contains("ServiceRecord{")) return@forEach
            val component = ComponentRegex.find(line)?.value ?: return@forEach
            services += component
            if (services.size >= MaxServiceEntries) return@forEach
        }
        return services.toList()
    }

    private fun formatBytes(kb: Long): String {
        val value = kb / 1024.0 / 1024.0
        return String.format(Locale.US, "%.1fG", value)
    }

    private data class ProcessLists(
        val app: List<String>,
        val system: List<String>,
    )

    private companion object {
        const val SectionPrefix = "###"
        const val SectionNetwork = "###NET"
        const val SectionBattery = "###BAT"
        const val SectionStorage = "###DF"
        const val SectionCpu = "###CPU"
        const val SectionMemory = "###MEM"
        const val SectionBrightness = "###BRIGHT"
        const val SectionTimeZone = "###TZ"
        const val SectionTime = "###TIME"
        const val SectionProcesses = "###PS"
        const val SectionActivity = "###ACT"
        const val SectionServices = "###SVC"

        const val MaxProcessEntries = 50
        const val MaxServiceEntries = 40

        val AppUidRegex = Regex("^u\\d+_[ai]\\d+$")
        val ComponentRegex = Regex("[\\w.]{1,255}/[\\.\\w]{1,255}")

        /** 单次会话采集全部指标，避免多次 ADB 往返；输出按 `###MARKER` 分段。 */
        val CombinedCommand = buildString {
            append("echo '$SectionNetwork'; dumpsys connectivity | grep -m 4 -E \"Active default network|NetworkAgentInfo\"; ")
            append("echo '$SectionBattery'; dumpsys battery; ")
            append("echo '$SectionStorage'; df -h /data; ")
            append("echo '$SectionCpu'; top -b -n 1 | head -n 5; ")
            append("echo '$SectionMemory'; cat /proc/meminfo; ")
            append("echo '$SectionBrightness'; settings get system screen_brightness; ")
            append("echo '$SectionTimeZone'; getprop persist.sys.timezone; ")
            append("echo '$SectionTime'; date +%s; ")
            append("echo '$SectionProcesses'; ps -A -o USER,PID,NAME; ")
            append("echo '$SectionActivity'; dumpsys activity activities | grep -m 1 -E \"topResumedActivity|mResumedActivity\"; ")
            append("echo '$SectionServices'; dumpsys activity services | grep \"ServiceRecord{\" | head -n 60")
        }
    }
}
