package com.sky22333.skyadb.adb

import com.rv882.fastbootjava.FastbootResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootOtgManagerTest {
    @Test
    fun aliveResponse_acceptsProtocolStatusesIncludingFail() {
        assertTrue(FastbootOtgManager.isAliveResponse(FastbootResponse.ResponseStatus.OKAY))
        assertTrue(FastbootOtgManager.isAliveResponse(FastbootResponse.ResponseStatus.FAIL))
        assertTrue(FastbootOtgManager.isAliveResponse(FastbootResponse.ResponseStatus.INFO))
        assertTrue(FastbootOtgManager.isAliveResponse(FastbootResponse.ResponseStatus.DATA))
        assertFalse(FastbootOtgManager.isAliveResponse(FastbootResponse.ResponseStatus.UNKNOWN))
    }
}
