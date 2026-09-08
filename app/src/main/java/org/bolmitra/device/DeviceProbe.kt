package org.bolmitra.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Device capability probe and tier assignment.
 *
 * ARCHITECTURE.md §6.3 (model manager): "On first run, probe RAM / ABI / core count /
 * free storage -> assign a device tier -> fetch the matching model variant set."
 *
 * The tier decides which model *variants* load. It never adds or removes a subsystem:
 * §5.3 states there is exactly one resident configuration, and §4.5 cut the mid-size
 * on-device MT tier permanently.
 */

enum class DeviceTier {
    /** 2 GB-class. §5.3 budgets ~600-800 MB usable. Batch ASR, int8 everywhere. */
    FLOOR,

    /** 3 GB+ and not low-RAM. Streaming ASR becomes affordable (§5.3 option A). */
    ROOMY,
}

/** §6.3 tier override. Default AUTO; forcing *down* is always safe, forcing up is not. */
enum class TierMode { AUTO, FORCE_FLOOR, FORCE_ROOMY }

data class DeviceSpec(
    val manufacturer: String,
    val model: String,
    val socModel: String,
    val androidRelease: String,
    val apiLevel: Int,
    val totalRamBytes: Long,
    val isLowRamDevice: Boolean,
    val abis: List<String>,
    val cpuCores: Int,
    /**
     * Arm dot-product (SDOT/UDOT), reported as `asimddp` in /proc/cpuinfo Features.
     *
     * V56: these are an Armv8.2 extension that Cortex-A53 predates. Where absent,
     * ONNX Runtime's own guidance is that int8 can be *slower* than fp32 — so int8
     * buys size, not speed. Phase 0 must compare both where this is false.
     */
    val hasDotProduct: Boolean,
    val availableStorageBytes: Long,
) {
    val totalRamGiB: Double get() = totalRamBytes / GIB
    val availableStorageGiB: Double get() = availableStorageBytes / GIB
    val is64BitOnly: Boolean get() = abis.none { it.startsWith("armeabi") }

    companion object {
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}

/**
 * Pure tier decision, kept separate from [probe] so it is unit-testable.
 *
 * Threshold rationale: §5.3 budgets against a 2 GB device, while §1.3 found that real
 * government procurement lands at 3-4 GB. 3 GiB is therefore the natural boundary —
 * below it we are in the budget §5.3 actually models.
 */
fun assignTier(spec: DeviceSpec): DeviceTier = when {
    spec.isLowRamDevice -> DeviceTier.FLOOR
    spec.totalRamBytes < ROOMY_RAM_FLOOR_BYTES -> DeviceTier.FLOOR
    else -> DeviceTier.ROOMY
}

/**
 * Applies a [TierMode] override to a detected tier.
 *
 * **Downward only.** V55 is the reason: native OOM inside ONNX Runtime aborts the
 * process rather than raising a catchable error, and on API 28-29 there is no way to
 * learn why the process died. A setting that let someone force a tier the hardware
 * cannot hold could produce an unrecoverable launch loop. So FORCE_ROOMY is honoured
 * only when the hardware already detected as ROOMY.
 */
fun resolveTier(detected: DeviceTier, mode: TierMode): DeviceTier = when (mode) {
    TierMode.AUTO -> detected
    TierMode.FORCE_FLOOR -> DeviceTier.FLOOR
    TierMode.FORCE_ROOMY -> detected
}

private const val ROOMY_RAM_FLOOR_BYTES = 3L * 1024L * 1024L * 1024L

fun probe(context: Context): DeviceSpec {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

    val storage = try {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: Exception) {
        -1L
    }

    return DeviceSpec(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unknown (API < 31)",
        androidRelease = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        totalRamBytes = mem.totalMem,
        isLowRamDevice = am.isLowRamDevice,
        abis = Build.SUPPORTED_ABIS.toList(),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        hasDotProduct = readCpuFeatures().contains("asimddp"),
        availableStorageBytes = storage,
    )
}

/**
 * Reads the Features line from /proc/cpuinfo. Returns an empty set if unreadable —
 * absence of evidence is treated as absence of the feature, which is the conservative
 * direction (we would then measure fp32 as well as int8 rather than assume int8 wins).
 */
private fun readCpuFeatures(): Set<String> = try {
    File("/proc/cpuinfo").readLines()
        .firstOrNull { it.startsWith("Features") }
        ?.substringAfter(':')
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.toSet()
        ?: emptySet()
} catch (e: Exception) {
    emptySet()
}
