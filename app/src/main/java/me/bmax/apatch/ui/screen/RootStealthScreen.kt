package me.bmax.apatch.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.ui.theme.BackgroundConfig

private const val TAG = "RootStealth"

// ============================================================
// 数据模型
// ============================================================

data class StealthSubFeature(
    val name: String,
    val description: String,
    val commands: List<String>,
    var enabled: Boolean = true
)

data class StealthModule(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val subFeatures: List<StealthSubFeature>
)

// ============================================================
// 7大核心模块定义
// ============================================================

private val module1MountIsolation = StealthModule(
    title = "内核级挂载隔离",
    description = "彻底抹除Magisk挂载特征，应用无法通过任何文件路径检测Root",
    icon = Icons.Outlined.VisibilityOff,
    subFeatures = listOf(
        StealthSubFeature(
            "隐藏挂载信息",
            "将/proc/mounts重定向到/dev/null",
            listOf(
                "mount --bind /dev/null /proc/self/mounts 2>/dev/null",
                "mount --bind /dev/null /proc/mounts 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "隐藏Magisk目录",
            "设置/data/adb/magisk权限为000",
            listOf("chmod 000 /data/adb/magisk 2>/dev/null")
        ),
        StealthSubFeature(
            "隐藏KSU目录",
            "设置/data/adb/ksu权限为000",
            listOf("chmod 000 /data/adb/ksu 2>/dev/null")
        ),
        StealthSubFeature(
            "隐藏APatch目录",
            "设置/data/adb/ap权限为000",
            listOf("chmod 000 /data/adb/ap 2>/dev/null")
        ),
        StealthSubFeature(
            "强制SELinux Enforcing",
            "确保SELinux处于强制模式",
            listOf("setenforce 1 2>/dev/null")
        )
    )
)

private val module2ZygiskStealth = StealthModule(
    title = "ZygiskNext 注入隐身",
    description = "Hook全量Root检测系统调用，伪造未Root系统环境",
    icon = Icons.Outlined.Memory,
    subFeatures = listOf(
        StealthSubFeature(
            "创建Shamiko兼容层",
            "创建Shamiko模块目录并启用",
            listOf(
                "mkdir -p /data/adb/modules/shamiko 2>/dev/null",
                "touch /data/adb/modules/shamiko/enable 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "配置白名单模式",
            "设置Shamiko为白名单模式（仅允许列表内应用检测Root）",
            listOf(
                "echo 'BLACKLIST_MODE=2' > /data/adb/modules/shamiko/config 2>/dev/null",
                "echo 'whitelist' > /data/adb/modules/shamiko/mode 2>/dev/null",
                "chmod 644 /data/adb/modules/shamiko/config 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "配置黑名单模式",
            "设置Shamiko为黑名单模式（隐藏列表内应用的Root）",
            listOf(
                "echo 'BLACKLIST_MODE=1' > /data/adb/modules/shamiko/config 2>/dev/null",
                "echo 'blacklist' > /data/adb/modules/shamiko/mode 2>/dev/null",
                "chmod 644 /data/adb/modules/shamiko/config 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "启用Zygisk",
            "确保Zygisk已启用",
            listOf(
                "mkdir -p /data/adb/magisk 2>/dev/null",
                "echo 'ZYGISK_ENABLED=true' >> /data/adb/magisk/config 2>/dev/null"
            )
        )
    )
)

private val module3DynamicRename = StealthModule(
    title = "动态重命名核心文件",
    description = "自动重命名su/magisk核心文件，清理时间戳和权限痕迹",
    icon = Icons.Outlined.Key,
    subFeatures = listOf(
        StealthSubFeature(
            "隐藏su二进制",
            "重命名su为隐藏文件名",
            listOf(
                "cp /system/bin/su /system/bin/.su_hidden 2>/dev/null",
                "chmod 000 /system/bin/su 2>/dev/null",
                "cp /sbin/su /sbin/.su_hidden 2>/dev/null",
                "chmod 000 /sbin/su 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "隐藏Magisk二进制",
            "重命名magisk为隐藏文件名",
            listOf(
                "cp /data/adb/magisk/magisk /data/adb/magisk/.magisk_hidden 2>/dev/null",
                "chmod 000 /data/adb/magisk/.magisk_hidden 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "清理时间戳",
            "将隐藏文件的时间戳伪装为系统文件",
            listOf(
                "touch -r /system/bin/sh /system/bin/.su_hidden 2>/dev/null",
                "touch -r /system/build.prop /data/adb/magisk/.magisk_hidden 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "设置隐藏属性",
            "设置Magisk隐藏属性",
            listOf("setprop persist.magisk.hide 1 2>/dev/null")
        )
    )
)

private val module4PropertyFix = StealthModule(
    title = "修正敏感系统属性",
    description = "自动修正ro.debuggable、TestKey、Bootloader解锁等所有敏感属性",
    icon = Icons.Outlined.DeviceUnknown,
    subFeatures = listOf(
        StealthSubFeature(
            "关闭debuggable",
            "将ro.debuggable设为0",
            listOf("resetprop ro.debuggable 0 2>/dev/null")
        ),
        StealthSubFeature(
            "启用secure",
            "将ro.secure设为1",
            listOf("resetprop ro.secure 1 2>/dev/null")
        ),
        StealthSubFeature(
            "伪装构建类型",
            "将ro.build.type改为user",
            listOf("resetprop ro.build.type user 2>/dev/null")
        ),
        StealthSubFeature(
            "伪装构建标签",
            "将ro.build.tags改为release-keys",
            listOf("resetprop ro.build.tags release-keys 2>/dev/null")
        ),
        StealthSubFeature(
            "锁定Bootloader",
            "伪装Bootloader已锁定",
            listOf(
                "resetprop ro.boot.verifiedbootstate green 2>/dev/null",
                "resetprop ro.boot.flash.locked 1 2>/dev/null",
                "resetprop ro.boot.vbmeta.device_state locked 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "随机化设备指纹",
            "随机修改部分设备标识属性",
            listOf(
                "resetprop ro.build.fingerprint \$(getprop ro.build.fingerprint | md5sum | cut -c1-16) 2>/dev/null"
            )
        )
    )
)

private val module5ProcessLogShield = StealthModule(
    title = "屏蔽进程和日志",
    description = "屏蔽Root相关进程、系统日志、dmesg日志，实现内存级无痕",
    icon = Icons.Outlined.Block,
    subFeatures = listOf(
        StealthSubFeature(
            "屏蔽kmsg",
            "禁止读取内核日志",
            listOf(
                "chmod 000 /proc/kmsg 2>/dev/null",
                "chmod 000 /dev/kmsg 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "清除logcat",
            "清空系统日志缓冲区",
            listOf("logcat -c 2>/dev/null")
        ),
        StealthSubFeature(
            "隐藏Magisk进程",
            "终止可被检测的Magisk进程",
            listOf("pkill -f 'magisk' 2>/dev/null")
        ),
        StealthSubFeature(
            "隐藏KSU进程",
            "终止可被检测的KernelSU进程",
            listOf("pkill -f 'ksud' 2>/dev/null")
        ),
        StealthSubFeature(
            "清理日志文件",
            "删除所有Root相关日志文件",
            listOf(
                "chmod 600 /data/adb/magisk/log 2>/dev/null",
                "chmod 600 /data/adb/ksu/log 2>/dev/null",
                "rm -rf /cache/magisk_log* 2>/dev/null",
                "rm -rf /data/local/tmp/*.log 2>/dev/null"
            )
        )
    )
)

private val module6ConfigSanitize = StealthModule(
    title = "配置规则脱敏",
    description = "使用伪装词汇替换敏感词，无任何Magisk/Root特征",
    icon = Icons.Outlined.DeleteSweep,
    subFeatures = listOf(
        StealthSubFeature(
            "替换magisk关键词",
            "将配置中的magisk替换为system_service",
            listOf(
                "sed -i 's/magisk/system_service/g' /data/adb/magisk/config 2>/dev/null",
                "sed -i 's/Magisk/SystemFramework/g' /data/adb/modules/*/module.prop 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "替换root关键词",
            "将配置中的root替换为core_service",
            listOf(
                "sed -i 's/root/core_service/g' /data/adb/magisk/config 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "替换su关键词",
            "将配置中的su替换为daemon",
            listOf(
                "sed -i 's/su/daemon/g' /data/adb/magisk/config 2>/dev/null"
            )
        ),
        StealthSubFeature(
            "清理模块名称",
            "将模块描述中的敏感词替换",
            listOf(
                "for f in /data/adb/modules/*/module.prop; do " +
                "sed -i 's/root/manager/g; s/magisk/framework/g; s/su/daemon/g' \"\$f\" 2>/dev/null; done"
            )
        )
    )
)

private val allModules = listOf(
    module1MountIsolation,
    module2ZygiskStealth,
    module3DynamicRename,
    module4PropertyFix,
    module5ProcessLogShield,
    module6ConfigSanitize
)

// ============================================================
// 一键应急隐身命令
// ============================================================

private val emergencyCommands = listOf(
    // 模块1：挂载隔离
    "mount --bind /dev/null /proc/self/mounts 2>/dev/null",
    "mount --bind /dev/null /proc/mounts 2>/dev/null",
    "chmod 000 /data/adb/magisk 2>/dev/null",
    "chmod 000 /data/adb/ksu 2>/dev/null",
    "chmod 000 /data/adb/ap 2>/dev/null",
    "setenforce 1 2>/dev/null",
    // 模块2：Zygisk
    "mkdir -p /data/adb/modules/shamiko 2>/dev/null",
    "touch /data/adb/modules/shamiko/enable 2>/dev/null",
    "echo 'BLACKLIST_MODE=2' > /data/adb/modules/shamiko/config 2>/dev/null",
    "chmod 644 /data/adb/modules/shamiko/config 2>/dev/null",
    // 模块3：重命名
    "cp /system/bin/su /system/bin/.su_hidden 2>/dev/null",
    "chmod 000 /system/bin/su 2>/dev/null",
    "touch -r /system/bin/sh /system/bin/.su_hidden 2>/dev/null",
    // 模块4：属性修正
    "resetprop ro.debuggable 0 2>/dev/null",
    "resetprop ro.secure 1 2>/dev/null",
    "resetprop ro.build.type user 2>/dev/null",
    "resetprop ro.build.tags release-keys 2>/dev/null",
    "resetprop ro.boot.verifiedbootstate green 2>/dev/null",
    "resetprop ro.boot.flash.locked 1 2>/dev/null",
    "resetprop ro.boot.vbmeta.device_state locked 2>/dev/null",
    // 模块5：日志屏蔽
    "chmod 000 /proc/kmsg 2>/dev/null",
    "chmod 000 /dev/kmsg 2>/dev/null",
    "logcat -c 2>/dev/null",
    "rm -rf /cache/magisk_log* 2>/dev/null",
    // 模块6：脱敏
    "sed -i 's/magisk/system_service/g' /data/adb/magisk/config 2>/dev/null",
    "sed -i 's/root/core_service/g' /data/adb/magisk/config 2>/dev/null",
    // 应急额外
    "setprop ro.build.display.id \$(getprop ro.build.display.id | sed 's/test/release/g') 2>/dev/null",
    "setprop ro.build.description \$(getprop ro.build.description | sed 's/testkeys/release-keys/g') 2>/dev/null",
    "resetprop ro.product.first_api_level 31 2>/dev/null",
    "resetprop ro.vendor.boot.vbmeta.device_state locked 2>/dev/null",
    "rm -rf /sbin/.magisk 2>/dev/null",
    "rm -rf /data/adb/.magisk 2>/dev/null"
)

// ============================================================
// 主界面
// ============================================================

@Destination<RootGraph>
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootStealthScreen() {
    val coroutineScope = rememberCoroutineScope()
    var isExecuting by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf("") }
    var securityScore by rememberSaveable { mutableIntStateOf(0) }
    var rootType by rememberSaveable { mutableStateOf("检测中...") }
    var suPath by rememberSaveable { mutableStateOf("检测中...") }
    var selinuxStatus by rememberSaveable { mutableStateOf("检测中...") }

    // 检测设备信息
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val result = Shell.cmd(
                    "id",
                    "which su 2>/dev/null || echo NOT_FOUND",
                    "getenforce 2>/dev/null || echo UNKNOWN",
                    "[ -d /data/adb/magisk ] && echo MAGISK || true",
                    "[ -d /data/adb/ksu ] && echo KSU || true",
                    "[ -d /data/adb/ap ] && echo APATCH || true",
                    "[ -f /system/bin/kp ] && echo KPATCH || true"
                ).exec()

                val output = result.out.joinToString("\n")
                if (result.isSuccess) {
                    when {
                        output.contains("MAGISK") -> rootType = "Magisk"
                        output.contains("KSU") -> rootType = "KernelSU"
                        output.contains("APATCH") -> rootType = "APatch"
                        output.contains("KPATCH") -> rootType = "KernelPatch"
                        output.contains("uid=0") -> rootType = "通用Root"
                        else -> rootType = "未检测到Root"
                    }

                    val suLine = output.lines().find { it.contains("/") && !it.contains("uid") }
                    suPath = suLine?.trim()?.takeIf { it != "NOT_FOUND" } ?: "未找到su"

                    val enforceLine = output.lines().find { it.contains("Enforcing") || it.contains("Permissive") || it.contains("Unknown") }
                    selinuxStatus = when {
                        enforceLine?.contains("Enforcing") == true -> "Enforcing（强制）"
                        enforceLine?.contains("Permissive") == true -> "Permissive（宽容）⚠️"
                        else -> "Unknown"
                    }

                    // 计算安全评分
                    var score = 50
                    if (rootType == "未检测到Root") score = 100
                    else {
                        if (selinuxStatus.contains("Enforcing")) score += 10
                        if (suPath == "未找到su") score += 15
                        if (!output.contains("MAGISK") && !output.contains("KSU")) score += 10
                        if (Shell.cmd("getprop ro.debuggable").exec().out.any { it.trim() == "0" }) score += 10
                        if (Shell.cmd("getprop ro.secure").exec().out.any { it.trim() == "1" }) score += 5
                    }
                    securityScore = score.coerceIn(0, 100)
                } else {
                    rootType = "无Root权限"
                    suPath = "不可用"
                    selinuxStatus = "未知"
                    securityScore = 0
                }
            }.onFailure {
                rootType = "检测失败"
                suPath = "不可用"
                selinuxStatus = "未知"
                Log.e(TAG, "检测失败", it)
            }
        }
    }

    val containerColor = if (BackgroundConfig.isCustomBackgroundEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = BackgroundConfig.customBackgroundOpacity)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root 隐身管理") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (BackgroundConfig.isCustomBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    }
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 一键应急隐身
            item {
                EmergencyStealthCard(
                    isExecuting = isExecuting,
                    onExecute = {
                        coroutineScope.launch {
                            isExecuting = true
                            val (exitCode, output) = executeEmergencyStealth()
                            resultMessage = if (exitCode == 0) "✅ 应急隐身已启用，共执行${emergencyCommands.size}项操作" else "❌ 执行失败: $output"
                            isExecuting = false
                            Log.d(TAG, "应急隐身结果: $output")
                        }
                    }
                )
            }

            // 执行结果
            if (resultMessage.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (resultMessage.startsWith("✅"))
                                Color(0xFF1B5E20).copy(alpha = 0.15f)
                            else
                                Color(0xFFB71C1C).copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = resultMessage,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 6大模块
            allModules.forEachIndexed { index, module ->
                item(key = "module_$index") {
                    StealthModuleCard(
                        module = module,
                        isExecuting = isExecuting,
                        onExecute = { commands ->
                            coroutineScope.launch {
                                isExecuting = true
                                val (exitCode, output) = executeModuleCommands(commands)
                                resultMessage = if (exitCode == 0) "✅ ${module.title}执行成功" else "❌ 执行失败: $output"
                                isExecuting = false
                            }
                        }
                    )
                }
            }

            // 设备指纹信息
            item(key = "fingerprint") {
                DeviceFingerprintCard(
                    rootType = rootType,
                    suPath = suPath,
                    selinuxStatus = selinuxStatus,
                    securityScore = securityScore
                )
            }

            // 底部间距
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ============================================================
// 一键应急隐身卡片
// ============================================================

@Composable
private fun EmergencyStealthCard(
    isExecuting: Boolean,
    onExecute: () -> Unit
) {
    var enabled by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFFFF6B35).copy(alpha = 0.12f)
            else Color(0xFFD32F2F).copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFFFF6B35) else Color(0xFFD32F2F),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "一键应急隐身模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) Color(0xFFFF6B35) else Color(0xFFD32F2F)
                    )
                    Text(
                        text = "同时启用全部6大隐身模块 + 应急清理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF6B35),
                        checkedTrackColor = Color(0xFFFF6B35).copy(alpha = 0.5f)
                    )
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "⚠️ 启用后将执行 ${emergencyCommands.size} 项隐身操作，包括挂载隔离、Zygisk隐身、文件重命名、属性修正、日志屏蔽和配置脱敏。建议重启设备使所有更改生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onExecute,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35)
                    )
                ) {
                    if (isExecuting) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .height(20.dp)
                                .fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.Transparent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即执行全部隐身操作")
                    }
                }
            }
        }
    }
}

// ============================================================
// 模块卡片
// ============================================================

@Composable
private fun StealthModuleCard(
    module: StealthModule,
    isExecuting: Boolean,
    onExecute: (List<String>) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val subFeatureStates = rememberSaveable(saver = listSaver<MutableList<Pair<Int, Boolean>>, Pair<Int, Boolean>>(
        save = { stateList -> stateList.toList() },
        restore = { it.toMutableList() }
    )) { mutableListOf<Pair<Int, Boolean>>() }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (BackgroundConfig.isCustomBackgroundEnabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = BackgroundConfig.customBackgroundOpacity)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            }
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = module.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 2
                    )
                }
            }

            // 展开的子功能
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    module.subFeatures.forEachIndexed { subIndex, sub ->
                        val subEnabled = subFeatureStates.find { it.first == subIndex }?.second ?: sub.enabled
                        ListItem(
                            headlineContent = { Text(sub.name, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = {
                                Text(sub.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            },
                            trailingContent = {
                                Switch(
                                    checked = subEnabled,
                                    onCheckedChange = { checked ->
                                        val existing = subFeatureStates.find { it.first == subIndex }
                                        if (existing != null) {
                                            subFeatureStates[subFeatureStates.indexOf(existing)] = Pair(subIndex, checked)
                                        } else {
                                            subFeatureStates.add(Pair(subIndex, checked))
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 执行按钮
                    val enabledCommands = module.subFeatures.filterIndexed { idx, _ ->
                        subFeatureStates.find { it.first == idx }?.second ?: true
                    }.flatMap { it.commands }

                    Button(
                        onClick = { onExecute(enabledCommands) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExecuting && enabledCommands.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isExecuting) {
                            LinearProgressIndicator(
                                modifier = Modifier.height(20.dp).fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                trackColor = Color.Transparent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("执行 (${enabledCommands.size} 项操作)")
                        }
                    }

                    if (enabledCommands.isEmpty()) {
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        ) {
                            Text("请至少启用一项子功能")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 设备指纹信息卡片
// ============================================================

@Composable
private fun DeviceFingerprintCard(
    rootType: String,
    suPath: String,
    selinuxStatus: String,
    securityScore: Int
) {
    val scoreColor = when {
        securityScore >= 80 -> Color(0xFF4CAF50)
        securityScore >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    val scoreLabel = when {
        securityScore >= 80 -> "安全"
        securityScore >= 50 -> "一般"
        else -> "危险"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (BackgroundConfig.isCustomBackgroundEnabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = BackgroundConfig.customBackgroundOpacity)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            }
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "设备安全信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Root 类型
            InfoRow("Root 类型", rootType)
            Spacer(modifier = Modifier.height(8.dp))
            // su 路径
            InfoRow("su 路径", suPath)
            Spacer(modifier = Modifier.height(8.dp))
            // SELinux
            InfoRow("SELinux", selinuxStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // 安全评分
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "安全评分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = securityScore.toFloat(),
                    onValueChange = { },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = scoreColor,
                        activeTrackColor = scoreColor
                    ),
                    enabled = false
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$securityScore ($scoreLabel)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================
// Root 命令执行
// ============================================================

private suspend fun executeEmergencyStealth(): Pair<Int, String> = withContext(Dispatchers.IO) {
    runCatching {
        val fullCmd = emergencyCommands.joinToString(" && ")
        val result = Shell.cmd(fullCmd).exec()
        val output = if (result.out.isNotEmpty()) result.out.joinToString("\n")
        else if (result.err.isNotEmpty()) result.err.joinToString("\n")
        else "执行完成"
        Pair(if (result.isSuccess) 0 else -1, output)
    }.getOrElse { e ->
        Log.e(TAG, "应急隐身执行失败", e)
        Pair(-1, e.message ?: "未知错误")
    }
}

private suspend fun executeModuleCommands(commands: List<String>): Pair<Int, String> = withContext(Dispatchers.IO) {
    runCatching {
        val fullCmd = commands.joinToString(" && ")
        val result = Shell.cmd(fullCmd).exec()
        val output = if (result.out.isNotEmpty()) result.out.joinToString("\n")
        else if (result.err.isNotEmpty()) result.err.joinToString("\n")
        else "执行完成"
        Pair(if (result.isSuccess) 0 else -1, output)
    }.getOrElse { e ->
        Log.e(TAG, "模块命令执行失败", e)
        Pair(-1, e.message ?: "未知错误")
    }
}
