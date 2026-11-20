package com.sky22333.skyadb.scrcpy

import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyOptionsTest {
    @Test
    fun cameraOptionsSelectCameraSourceAndFacing() {
        val args = ScrcpyOptions(
            maxFps = 30,
            videoSource = ScrcpyVideoSource.Camera,
            cameraFacing = ScrcpyCameraFacing.Front,
        ).serverArguments()

        assertTrue("video_source=camera" in args)
        assertTrue("camera_facing=front" in args)
        assertTrue("camera_fps=30" in args)
        assertTrue("capture_orientation=270" in args)
    }
}
