package me.bmax.apatch.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.yuki.foly.R
import me.bmax.apatch.apApp
import me.bmax.apatch.util.AppData
import me.bmax.apatch.util.HardwareMonitor
import me.bmax.apatch.util.Version

data class SystemMonitorState(
    val cpuUsage: Int = 0,
    val gpuUsage: Int = 0,
    val cpuTemp: Float = 0f,
    val cpuFrequencies: List<HardwareMonitor.CpuFreqInfo> = emptyList(),
    val memoryInfo: HardwareMonitor.MemoryInfo? = null,
    val storageUsedPercent: Float = 0f,
    val batteryLevel: Int = 0,
    val batteryTemp: Float = 0f,
    val batteryCharging: Boolean = false,
    val networkRxBytes: Long = 0L,
    val networkTxBytes: Long = 0L,
    val storagePartitions: List<HardwareMonitor.StoragePartitionInfo> = emptyList()
)

data class TimeSeriesData(
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
    val cpuTempHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList()
)

data class ModuleStatsState(
    val kernelModuleCount: Int = 0,
    val apmModuleCount: Int = 0,
    val superuserCount: Int = 0
)

data class PatchStatusState(
    val kpState: APApplication.State = APApplication.State.UNKNOWN_STATE,
    val apState: APApplication.State = APApplication.State.UNKNOWN_STATE,
    val kpVersion: String = "",
    val managerVersion: String = "",
    val selinuxStatus: String = ""
)

data class DashboardUiState(
    val patchStatus: PatchStatusState = PatchStatusState(),
    val systemMonitor: SystemMonitorState = SystemMonitorState(),
    val moduleStats: ModuleStatsState = ModuleStatsState(),
    val isLoading: Boolean = true
)

class DashboardViewModel : ViewModel() {

    companion object {
        private const val TAG = "DashboardViewModel"
        private const val CPU_POLL_INTERVAL_MS = 3_000L
        private const val GPU_POLL_INTERVAL_MS = 1_000L
        private const val MEMORY_POLL_INTERVAL_MS = 5_000L
        private const val STORAGE_BATTERY_POLL_INTERVAL_MS = 10_000L
        private const val TIME_SERIES_MAX_SIZE = 30
    }

    private val _dashboardUiState = MutableStateFlow(DashboardUiState())
    val dashboardUiState: StateFlow<DashboardUiState> = _dashboardUiState.asStateFlow()

    private val _timeSeriesData = MutableStateFlow(TimeSeriesData())
    val timeSeriesData: StateFlow<TimeSeriesData> = _timeSeriesData.asStateFlow()

    private var cpuGpuJob: Job? = null
    private var gpuOnlyJob: Job? = null
    private var memoryJob: Job? = null
    private var storageBatteryJob: Job? = null
    private var cpuTempJob: Job? = null
    private var storagePartitionsJob: Job? = null

    private val liveDataObservers = mutableListOf<Observer<*>>()

    init {
        loadOneTimeData()
        observeLiveDataSources()
        collectModuleCounts()
    }

    private fun loadOneTimeData() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _dashboardUiState.value

            runCatching {
                AppData.DataRefreshManager.ensureCountsLoaded(force = false)
            }.onFailure { e ->
                Log.e(TAG, "Error loading module counts", e)
            }

            val newState = current.copy(
                patchStatus = current.patchStatus.copy(
                    kpVersion = runCatching { Version.installedKPVString() }.getOrDefault(""),
                    managerVersion = runCatching { Version.getManagerVersion().first }.getOrDefault(""),
                    selinuxStatus = fetchSELinuxStatus(apApp)
                ),
                systemMonitor = current.systemMonitor.copy(
                    storageUsedPercent = fetchStorageUsedPercent(),
                    batteryLevel = fetchBatteryLevel(apApp),
                    batteryTemp = fetchBatteryTemp(apApp)
                ),
                isLoading = false
            )

            _dashboardUiState.value = newState
        }
    }

    private fun observeLiveDataSources() {
        observeLiveData(APApplication.kpStateLiveData) { state ->
            val current = _dashboardUiState.value
            _dashboardUiState.value = current.copy(
                patchStatus = current.patchStatus.copy(kpState = state)
            )
        }

        observeLiveData(APApplication.apStateLiveData) { state ->
            val current = _dashboardUiState.value
            _dashboardUiState.value = current.copy(
                patchStatus = current.patchStatus.copy(apState = state)
            )
        }
    }

    private fun <T> observeLiveData(liveData: LiveData<T>, onChanged: (T) -> Unit) {
        val observer = Observer<T> { value ->
            onChanged(value)
        }
        @Suppress("UNCHECKED_CAST")
        liveDataObservers.add(observer as Observer<*>)
        liveData.observeForever(observer)
    }

    private fun collectModuleCounts() {
        AppData.DataRefreshManager.superuserCount
            .onEach { count ->
                val current = _dashboardUiState.value
                _dashboardUiState.value = current.copy(
                    moduleStats = current.moduleStats.copy(superuserCount = count)
                )
            }
            .catch { e -> Log.e(TAG, "Error collecting superuserCount", e) }
            .launchIn(viewModelScope)

        AppData.DataRefreshManager.apmModuleCount
            .onEach { count ->
                val current = _dashboardUiState.value
                _dashboardUiState.value = current.copy(
                    moduleStats = current.moduleStats.copy(apmModuleCount = count)
                )
            }
            .catch { e -> Log.e(TAG, "Error collecting apmModuleCount", e) }
            .launchIn(viewModelScope)

        AppData.DataRefreshManager.kernelModuleCount
            .onEach { count ->
                val current = _dashboardUiState.value
                _dashboardUiState.value = current.copy(
                    moduleStats = current.moduleStats.copy(kernelModuleCount = count)
                )
            }
            .catch { e -> Log.e(TAG, "Error collecting kernelModuleCount", e) }
            .launchIn(viewModelScope)
    }

    fun startPeriodicPolling() {
        cpuGpuJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlin.runCatching {
                    val cpu = HardwareMonitor.getCpuUsage()
                    val cpuFreqs = HardwareMonitor.getCpuFrequencies()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(
                                cpuUsage = cpu,
                                cpuFrequencies = cpuFreqs
                            )
                        )

                        val ts = _timeSeriesData.value
                        _timeSeriesData.value = ts.copy(
                            cpuHistory = (ts.cpuHistory + cpu.toFloat()).takeLast(TIME_SERIES_MAX_SIZE)
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling CPU", e)
                }

                kotlinx.coroutines.delay(CPU_POLL_INTERVAL_MS)
            }
        }

        gpuOnlyJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlin.runCatching {
                    val gpu = HardwareMonitor.getGpuUsage()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(
                                gpuUsage = gpu
                            )
                        )

                        val ts = _timeSeriesData.value
                        _timeSeriesData.value = ts.copy(
                            gpuHistory = (ts.gpuHistory + gpu.toFloat()).takeLast(TIME_SERIES_MAX_SIZE)
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling GPU", e)
                }

                kotlinx.coroutines.delay(GPU_POLL_INTERVAL_MS)
            }
        }

        cpuTempJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1_000L)

            while (true) {
                kotlin.runCatching {
                    val temp = HardwareMonitor.getCpuTemperature()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(cpuTemp = temp)
                        )

                        val ts = _timeSeriesData.value
                        _timeSeriesData.value = ts.copy(
                            cpuTempHistory = (ts.cpuTempHistory + temp).takeLast(TIME_SERIES_MAX_SIZE)
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling CPU temp", e)
                }

                kotlinx.coroutines.delay(MEMORY_POLL_INTERVAL_MS)
            }
        }

        memoryJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2_000L)

            while (true) {
                kotlin.runCatching {
                    val memInfo = HardwareMonitor.getMemoryInfo()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(
                                memoryInfo = memInfo
                            )
                        )

                        val ramPercent = if (memInfo.ramTotal > 0) {
                            (memInfo.ramUsed.toFloat() / memInfo.ramTotal.toFloat() * 100f)
                        } else 0f
                        val ts = _timeSeriesData.value
                        _timeSeriesData.value = ts.copy(
                            ramHistory = (ts.ramHistory + ramPercent).takeLast(TIME_SERIES_MAX_SIZE)
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling Memory", e)
                }

                kotlinx.coroutines.delay(MEMORY_POLL_INTERVAL_MS)
            }
        }

        storageBatteryJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5_000L)

            while (true) {
                kotlin.runCatching {
                    val storagePercent = fetchStorageUsedPercent()
                    val batteryLevel = fetchBatteryLevel(apApp)
                    val batteryTemp = fetchBatteryTemp(apApp)
                    val batteryCharging = fetchBatteryCharging(apApp)
                    val networkRx = TrafficStats.getTotalRxBytes()
                    val networkTx = TrafficStats.getTotalTxBytes()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(
                                storageUsedPercent = storagePercent,
                                batteryLevel = batteryLevel,
                                batteryTemp = batteryTemp,
                                batteryCharging = batteryCharging,
                                networkRxBytes = networkRx,
                                networkTxBytes = networkTx
                            )
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling Storage/Battery/Network", e)
                }

                kotlinx.coroutines.delay(STORAGE_BATTERY_POLL_INTERVAL_MS)
            }
        }

        storagePartitionsJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(8_000L)

            while (true) {
                kotlin.runCatching {
                    val partitions = HardwareMonitor.getStoragePartitions()

                    withContext(Dispatchers.Main) {
                        val current = _dashboardUiState.value
                        _dashboardUiState.value = current.copy(
                            systemMonitor = current.systemMonitor.copy(
                                storagePartitions = partitions
                            )
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error polling Storage Partitions", e)
                }

                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _dashboardUiState.value

            _dashboardUiState.value = current.copy(isLoading = true)

            val newState = current.copy(
                patchStatus = current.patchStatus.copy(
                    kpVersion = runCatching { Version.installedKPVString() }.getOrDefault(current.patchStatus.kpVersion),
                    managerVersion = runCatching { Version.getManagerVersion().first }.getOrDefault(current.patchStatus.managerVersion),
                    selinuxStatus = fetchSELinuxStatus(apApp)
                ),
                systemMonitor = current.systemMonitor.copy(
                    storageUsedPercent = fetchStorageUsedPercent(),
                    batteryLevel = fetchBatteryLevel(apApp),
                    batteryTemp = fetchBatteryTemp(apApp)
                ),
                isLoading = false
            )

            _dashboardUiState.value = newState

            runCatching {
                AppData.DataRefreshManager.ensureCountsLoaded(force = true)
            }.onFailure { e ->
                Log.e(TAG, "Error refreshing module counts", e)
            }
        }
    }

    fun stopPeriodicPolling() {
        cpuGpuJob?.cancel()
        gpuOnlyJob?.cancel()
        memoryJob?.cancel()
        storageBatteryJob?.cancel()
        cpuTempJob?.cancel()
        storagePartitionsJob?.cancel()
        cpuGpuJob = null
        gpuOnlyJob = null
        memoryJob = null
        storageBatteryJob = null
        cpuTempJob = null
        storagePartitionsJob = null
        Log.d(TAG, "Periodic polling stopped")
    }

    override fun onCleared() {
        super.onCleared()
        liveDataObservers.forEach { observer ->
            @Suppress("UNCHECKED_CAST")
            APApplication.kpStateLiveData.removeObserver(observer as Observer<APApplication.State>)
            @Suppress("UNCHECKED_CAST")
            APApplication.apStateLiveData.removeObserver(observer as Observer<APApplication.State>)
        }
        liveDataObservers.clear()

        stopPeriodicPolling()
        Log.d(TAG, "DashboardViewModel cleared, all polling jobs cancelled")
    }

    private suspend fun fetchSELinuxStatus(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val enforcing = context.getString(R.string.home_selinux_status_enforcing)
            val permissive = context.getString(R.string.home_selinux_status_permissive)
            val disabled = context.getString(R.string.home_selinux_status_disabled)
            val unknown = context.getString(R.string.home_selinux_status_unknown)

            val shell = Shell.Builder.create().build("sh")
            val list = ArrayList<String>()
            val result = shell.newJob().add("getenforce").to(list, list).exec()
            val output = result.out.joinToString("\n").trim()
            shell.close()

            if (result.isSuccess) {
                when (output) {
                    "Enforcing" -> enforcing
                    "Permissive" -> permissive
                    "Disabled" -> disabled
                    else -> unknown
                }
            } else if (output.endsWith("Permission denied")) {
                enforcing
            } else {
                unknown
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SELinux status", e)
            context.getString(R.string.home_selinux_status_unknown)
        }
    }

    private fun fetchStorageUsedPercent(): Float {
        return try {
            val statFs = StatFs("/data")
            val totalBytes = statFs.totalBytes.toLong()
            val availableBytes = statFs.availableBytes.toLong()

            if (totalBytes > 0) {
                ((totalBytes - availableBytes).toFloat() / totalBytes.toFloat() * 100f)
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching storage info", e)
            0f
        }
    }

    private fun fetchBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching battery level", e)
            0
        }
    }

    private fun fetchBatteryTemp(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tempTenths / 10f
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching battery temperature", e)
            0f
        }
    }

    private fun fetchBatteryCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching battery charging status", e)
            false
        }
    }
}
