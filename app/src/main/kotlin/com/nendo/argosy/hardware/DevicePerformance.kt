package com.nendo.argosy.hardware

import android.os.Build
import com.nendo.argosy.util.PServerExecutor
import com.nendo.argosy.util.XsuExecutor
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PerformanceMode {
    ECO,
    BALANCED,
    STREAMING,
    GAMING,
    STANDARD,
    HIGH,
    MAX
}

/**
 * The performance controls one device family exposes. Every call blocks on file or root-shell
 * I/O, so callers run them off the main thread.
 */
interface DevicePerformance {
    val modes: List<PerformanceMode>
    val controlsFan: Boolean
    val controlsRefreshRate: Boolean
    val canWrite: Boolean

    /**
     * The mode the hardware is running now, or null when its state matches none of [modes].
     */
    fun currentMode(): PerformanceMode?

    fun applyMode(mode: PerformanceMode): Boolean
}

@Singleton
class DevicePerformanceResolver @Inject constructor(
    private val fanController: FanController
) {
    fun resolve(): DevicePerformance? {
        if (fanController.isAvailable()) return AynPerformance
        return AyaneoPerformance.socFor(Build.PRODUCT)?.let(::AyaneoPerformance)
    }
}

private object AynPerformance : DevicePerformance {
    private const val SETTING_PERFORMANCE_MODE = "performance_mode"
    private val values = mapOf(
        PerformanceMode.STANDARD to 0,
        PerformanceMode.HIGH to 1,
        PerformanceMode.MAX to 2
    )

    override val modes = values.keys.toList()
    override val controlsFan = true
    override val controlsRefreshRate = false
    override val canWrite: Boolean get() = PServerExecutor.isAvailable

    override fun currentMode(): PerformanceMode {
        val value = PServerExecutor.getSystemSetting(SETTING_PERFORMANCE_MODE, 0)
        return values.entries.firstOrNull { it.value == value }?.key ?: PerformanceMode.STANDARD
    }

    override fun applyMode(mode: PerformanceMode): Boolean {
        val value = values[mode] ?: return false
        return PServerExecutor.setSystemSetting(SETTING_PERFORMANCE_MODE, value)
    }
}

internal data class AyaneoModeProfile(
    val governor: String,
    val cpuMaxKhz: List<Int>,
    val gpuMinHz: Int,
    val gpuMaxHz: Int
)

internal data class AyaneoSoc(
    val policies: List<Int>,
    val cpuMinKhz: List<Int>,
    val profiles: Map<PerformanceMode, AyaneoModeProfile>
) {
    fun modeMatching(governor: String, cpuMaxKhz: List<Int>, gpuMaxHz: Int): PerformanceMode? =
        profiles.entries.firstOrNull { (_, profile) ->
            profile.governor == governor && profile.cpuMaxKhz == cpuMaxKhz && profile.gpuMaxHz == gpuMaxHz
        }?.key
}

internal class AyaneoPerformance(private val soc: AyaneoSoc) : DevicePerformance {
    override val modes = soc.profiles.keys.toList()
    override val controlsFan = false
    override val controlsRefreshRate = true
    override val canWrite: Boolean get() = XsuExecutor.isAvailable

    override fun currentMode(): PerformanceMode? {
        val governor = read(policyFile(soc.policies.first(), "scaling_governor")) ?: return null
        val cpuMax = soc.policies.map { read(policyFile(it, "scaling_max_freq"))?.toIntOrNull() ?: return null }
        val gpuMax = read("$GPU/max_gpuclk")?.toIntOrNull() ?: return null
        return soc.modeMatching(governor, cpuMax, gpuMax)
    }

    override fun applyMode(mode: PerformanceMode): Boolean {
        val profile = soc.profiles[mode] ?: return false
        if (XsuExecutor.execute(writeCommands(soc, profile).joinToString("; ")).isFailure) return false
        return currentMode() == mode
    }

    private fun read(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
        private const val GPU = "/sys/class/kgsl/kgsl-3d0"
        private const val GPU_IDLE_TIMER_MS = 80

        private val socByProduct = mapOf("AYANEO_Pocket_FIT" to AyaneoSocs.AR14)

        fun socFor(product: String?): AyaneoSoc? = socByProduct[product]

        private fun policyFile(policy: Int, name: String) = "$CPUFREQ/policy$policy/$name"

        internal fun writeCommands(soc: AyaneoSoc, profile: AyaneoModeProfile): List<String> =
            soc.policies.indices.flatMap { i ->
                val policy = soc.policies[i]
                listOf(
                    "echo ${profile.governor} > ${policyFile(policy, "scaling_governor")}",
                    "echo ${soc.cpuMinKhz[i]} > ${policyFile(policy, "scaling_min_freq")}",
                    "echo ${profile.cpuMaxKhz[i]} > ${policyFile(policy, "scaling_max_freq")}"
                )
            } + listOf(
                "echo $GPU_IDLE_TIMER_MS > $GPU/idle_timer",
                "echo ${profile.gpuMaxHz} > $GPU/max_gpuclk",
                "echo ${profile.gpuMaxHz} > $GPU/devfreq/max_freq",
                "echo ${profile.gpuMinHz} > $GPU/devfreq/min_freq"
            )
    }
}

internal object AyaneoSocs {
    private const val POWERSAVE = "powersave"
    private const val SCHEDUTIL = "schedutil"
    private const val PERFORMANCE = "performance"
    private const val GPU_MIN = 231_000_000
    private val fullClocks = listOf(2_265_600, 3_148_800, 2_956_800, 3_302_400)

    val AR14 = AyaneoSoc(
        policies = listOf(0, 2, 5, 7),
        cpuMinKhz = listOf(364_800, 499_200, 499_200, 480_000),
        profiles = linkedMapOf(
            PerformanceMode.ECO to AyaneoModeProfile(
                POWERSAVE, listOf(787_200, 729_600, 729_600, 480_000), GPU_MIN, 310_000_000
            ),
            PerformanceMode.BALANCED to AyaneoModeProfile(SCHEDUTIL, fullClocks, GPU_MIN, 903_000_000),
            PerformanceMode.STREAMING to AyaneoModeProfile(
                PERFORMANCE, listOf(2_265_600, 2_131_200, 2_035_200, 2_112_000), GPU_MIN, 680_000_000
            ),
            PerformanceMode.GAMING to AyaneoModeProfile(PERFORMANCE, fullClocks, GPU_MIN, 903_000_000),
            PerformanceMode.MAX to AyaneoModeProfile(PERFORMANCE, fullClocks, GPU_MIN, 1_050_000_000)
        )
    )
}
