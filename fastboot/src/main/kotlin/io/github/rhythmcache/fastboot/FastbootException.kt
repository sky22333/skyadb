package io.github.rhythmcache.fastboot

sealed class FastbootException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Io(message: String, cause: Throwable? = null) : FastbootException(message, cause)

    class Protocol(message: String) : FastbootException(message)

    class DeviceFail(message: String) : FastbootException(message)

    class Timeout(message: String = "fastboot operation timed out") : FastbootException(message)

    class BadArgument(message: String) : FastbootException(message)
}
