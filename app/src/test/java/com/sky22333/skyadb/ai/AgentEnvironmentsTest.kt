package com.sky22333.skyadb.ai

import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceType
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEnvironmentsTest {
    @Test fun recentDevicesShowTheirActualConnectionState() {
        val recent = listOf(AdbDevice("tv", "Living room", "192.168.1.10", 5555, DeviceType.Tv, ConnectionState.Disconnected, ""))
        val offline = AgentTools.formatEnvironments(AiConfig(), listOf(AgentSessions.Device("tv", "192.168.1.10:5555", false)), recent)
        assertTrue(offline.contains("disconnected; use agent_connect to reconnect"))
        assertTrue(offline.contains("name=\"Living room\", host=\"192.168.1.10\", port=5555, not connected"))
        val online = AgentTools.formatEnvironments(AiConfig(), listOf(AgentSessions.Device("active", "192.168.1.10:5555")), recent)
        assertTrue(online.contains("connected ID=active"))
    }
}
