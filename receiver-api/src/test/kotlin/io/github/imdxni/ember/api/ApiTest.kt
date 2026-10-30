package io.github.imdxni.ember.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTest {
    @Test
    fun deviceIdRoundTrip() {
        val id = DeviceId.parse("02:1a:2B:3c:4D:5e")
        assertEquals("02:1A:2B:3C:4D:5E", id.toString())
        assertEquals(id, DeviceId.parse("021A2B3C4D5E"))
        assertFailsWith<IllegalArgumentException> { DeviceId.parse("02:1A") }
    }

    @Test
    fun randomDeviceIdIsLocalUnicast() {
        repeat(100) {
            val first = DeviceId.random().toByteArray()[0].toInt()
            assertTrue(first and 0x02 != 0, "locally administered bit")
            assertTrue(first and 0x01 == 0, "unicast bit")
        }
    }

    @Test
    fun maxSizePrefersHardwareDecoder() {
        val caps = DeviceCapabilities(
            video = listOf(
                decoder("sw", VideoCodec.H264, 4096, 2304, hardware = false),
                decoder("hw", VideoCodec.H264, 1920, 1080, hardware = true),
            ),
            audio = setOf(AudioCodec.AAC_ELD),
            display = DisplayInfo(1920, 1080, 60f),
            abis = listOf("armeabi-v7a"),
            sdkInt = 28,
        )
        assertEquals(VideoSize(1920, 1080), caps.maxSize(VideoCodec.H264))
        assertNull(caps.maxSize(VideoCodec.H265))
        assertTrue(caps.supports(AudioCodec.AAC_ELD))
    }

    private fun decoder(name: String, codec: VideoCodec, w: Int, h: Int, hardware: Boolean) =
        VideoDecoderInfo(name, codec, w, h, 60, hardware, adaptivePlayback = true, lowLatency = false)
}
