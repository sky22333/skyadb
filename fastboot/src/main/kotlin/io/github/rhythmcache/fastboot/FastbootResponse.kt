package io.github.rhythmcache.fastboot

internal sealed class RawResponse {
    data class Okay(val message: String) : RawResponse()

    data class Fail(val message: String) : RawResponse()

    data class Info(val message: String) : RawResponse()

    data class Text(val message: String) : RawResponse()

    data class Data(val size: Long) : RawResponse()
}

data class FastbootResult(
    val response: String,
    val info: List<String>,
)

data class Progress(
    val current: Long,
    val total: Long,
)
