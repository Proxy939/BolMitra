package org.bolmitra.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one check the tier logic needs.
 *
 * [resolveTier]'s downward-only rule is safety-relevant: per V55, forcing a tier the
 * hardware cannot hold risks an unrecoverable launch loop, because native OOM inside
 * ONNX Runtime aborts the process and API 28-29 cannot report why. If that rule ever
 * regresses, this test fails.
 */
class DeviceProbeTest {

    private fun spec(ramGiB: Double, lowRam: Boolean = false) = DeviceSpec(
        manufacturer = "test",
        model = "test",
        socModel = "test",
        androidRelease = "13",
        apiLevel = 33,
        totalRamBytes = (ramGiB * DeviceSpec.GIB).toLong(),
        isLowRamDevice = lowRam,
        abis = listOf("arm64-v8a"),
        cpuCores = 8,
        hasDotProduct = true,
        availableStorageBytes = 64L * 1024 * 1024 * 1024,
    )

    @Test
    fun `2 GB device is FLOOR`() {
        assertEquals(DeviceTier.FLOOR, assignTier(spec(2.0)))
    }

    @Test
    fun `6 GB device is ROOMY`() {
        assertEquals(DeviceTier.ROOMY, assignTier(spec(6.0)))
    }

    @Test
    fun `isLowRamDevice forces FLOOR regardless of reported RAM`() {
        assertEquals(DeviceTier.FLOOR, assignTier(spec(8.0, lowRam = true)))
    }

    @Test
    fun `3 GiB is the ROOMY boundary`() {
        assertEquals(DeviceTier.FLOOR, assignTier(spec(2.99)))
        assertEquals(DeviceTier.ROOMY, assignTier(spec(3.0)))
    }

    @Test
    fun `forcing down is always honoured`() {
        assertEquals(DeviceTier.FLOOR, resolveTier(DeviceTier.ROOMY, TierMode.FORCE_FLOOR))
        assertEquals(DeviceTier.FLOOR, resolveTier(DeviceTier.FLOOR, TierMode.FORCE_FLOOR))
    }

    @Test
    fun `forcing up past the hardware is refused - V55 launch-loop guard`() {
        assertEquals(DeviceTier.FLOOR, resolveTier(DeviceTier.FLOOR, TierMode.FORCE_ROOMY))
    }

    @Test
    fun `forcing up is honoured when hardware already supports it`() {
        assertEquals(DeviceTier.ROOMY, resolveTier(DeviceTier.ROOMY, TierMode.FORCE_ROOMY))
    }

    @Test
    fun `AUTO passes the detected tier through`() {
        assertEquals(DeviceTier.ROOMY, resolveTier(DeviceTier.ROOMY, TierMode.AUTO))
        assertEquals(DeviceTier.FLOOR, resolveTier(DeviceTier.FLOOR, TierMode.AUTO))
    }
}
