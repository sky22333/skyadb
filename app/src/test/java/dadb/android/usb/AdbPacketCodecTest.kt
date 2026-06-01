package dadb.android.usb

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AdbPacketCodecTest {
    @Test
    fun completePacketLengthWaitsForFullPayload() {
        val packet = buildPacket(command = CMD_WRTE, payload = "hello".toByteArray())
        val pending = Buffer()

        pending.write(packet, 0, AdbPacketCodec.HEADER_LENGTH)
        assertNull(AdbPacketCodec.completePacketLength(pending))

        pending.write(packet, AdbPacketCodec.HEADER_LENGTH, packet.size - AdbPacketCodec.HEADER_LENGTH)
        assertEquals(packet.size.toLong(), AdbPacketCodec.completePacketLength(pending))
    }

    @Test
    fun completePacketLengthReadsOnlyFirstFullPacket() {
        val first = buildPacket(command = CMD_OKAY, payload = ByteArray(0))
        val second = buildPacket(command = CMD_CLSE, payload = ByteArray(0))
        val pending = Buffer().write(first).write(second)

        assertEquals(first.size.toLong(), AdbPacketCodec.completePacketLength(pending))
    }

    @Test
    fun parseHeaderRejectsInvalidMagic() {
        val packet = buildPacket(command = CMD_OKAY, payload = ByteArray(0))
        val header = packet.copyOf(AdbPacketCodec.HEADER_LENGTH)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 0)

        assertThrows(IOException::class.java) {
            AdbPacketCodec.parseHeader(header)
        }
    }

    private fun buildPacket(command: Int, payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(AdbPacketCodec.HEADER_LENGTH + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(1)
            .putInt(2)
            .putInt(payload.size)
            .putInt(payload.sumOf { it.toUByte().toInt() })
            .putInt(command xor -0x1)
            .put(payload)
            .array()
    }

    private companion object {
        const val CMD_OKAY = 0x59414b4f
        const val CMD_CLSE = 0x45534c43
        const val CMD_WRTE = 0x45545257
    }
}
