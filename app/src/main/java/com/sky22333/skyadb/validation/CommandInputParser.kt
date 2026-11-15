package com.sky22333.skyadb.validation


/** Normalize pasted client commands once, before dispatching to the current transport. */
object CommandInputParser {
    enum class Error {
        Empty,
        WrongMode,
        Devices,
        HostCommand,
        ClientOptions,
        FastbootSyntax,
        FileTransfer,
    }

    sealed interface Result {
        data class Ready(val command: String) : Result

        data class Rejected(val error: Error) : Result
    }

    fun parse(input: String, fastboot: Boolean): Result {
        var command = input.trim()
        var prefixed = false
        val executable = command.substringBeforeWhitespace()
        val tool = executable.removeSuffix(".exe")
        if (tool == "adb" || tool == "fastboot") {
            if ((tool == "fastboot") != fastboot) return Result.Rejected(Error.WrongMode)
            command = command.drop(executable.length).trimStart()
            prefixed = true
        }
        if (command.isBlank()) return Result.Rejected(Error.Empty)
        val verb = command.substringBeforeWhitespace()
        val arguments = command.drop(verb.length).trim()
        if (prefixed && verb.startsWith('-')) return Result.Rejected(Error.ClientOptions)
        if ((prefixed || fastboot) && verb == "devices") return Result.Rejected(Error.Devices)
        if (!fastboot) {
            if (prefixed && verb == "shell") {
                if (arguments.isBlank()) return Result.Rejected(Error.Empty)
                if (arguments.startsWith('-')) return Result.Rejected(Error.ClientOptions)
                return Result.Ready(arguments)
            }
            if (prefixed && (verb in AdbHostCommands || verb.startsWith("wait-for-"))) {
                return Result.Rejected(Error.HostCommand)
            }
            return Result.Ready(command)
        }

        // Fastboot carries one protocol command, not a script or a desktop file-transfer job.
        if (command.any { it == '\n' || it == '\r' || it == '\u0000' }) {
            return Result.Rejected(Error.FastbootSyntax)
        }
        if (verb in FastbootHostCommands || verb.startsWith('-'))
            return Result.Rejected(Error.HostCommand)
        if (verb in FastbootFileCommands) return Result.Rejected(Error.FileTransfer)
        val normalized =
            when (verb) {
                "getvar",
                "erase",
                "set_active" -> {
                    if (!SingleArgument.matches(arguments))
                        return Result.Rejected(Error.FastbootSyntax)
                    "$verb:$arguments"
                }
                "reboot" ->
                    when (arguments) {
                        "" -> "reboot"
                        "bootloader" -> "reboot-bootloader"
                        "fastboot",
                        "recovery" -> "reboot-$arguments"
                        else -> return Result.Rejected(Error.FastbootSyntax)
                    }
                else -> command // Preserve protocol syntax and OEM extensions verbatim.
            }
        return Result.Ready(normalized)
    }

    private fun String.substringBeforeWhitespace(): String = takeWhile { !it.isWhitespace() }

    private val SingleArgument = Regex("[A-Za-z0-9_.:-]+")
    private val AdbHostCommands =
        setOf(
            "connect",
            "disconnect",
            "pair",
            "mdns",
            "start-server",
            "kill-server",
            "server",
            "version",
            "help",
            "get-state",
            "get-serialno",
            "get-devpath",
            "features",
            "host-features",
            "install",
            "install-multiple",
            "install-multi-package",
            "uninstall",
            "push",
            "pull",
            "sync",
            "forward",
            "reverse",
            "tcpip",
            "usb",
            "root",
            "unroot",
            "remount",
            "disable-verity",
            "enable-verity",
            "sideload",
            "bugreport",
            "keygen",
            "emu",
            "jdwp",
            "track-jdwp",
            "attach",
            "detach",
            "track-devices",
            "reconnect",
        )
    private val FastbootHostCommands = setOf("help", "version", "wait-for-device")
    private val FastbootFileCommands =
        setOf(
            "flash",
            "flash:raw",
            "flashall",
            "update",
            "boot",
            "fetch",
            "download",
            "upload",
            "stage",
            "get_staged",
        )
}
