package me.yuki.foly.ui.screen

import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yuki.foly.R
import me.yuki.foly.util.RootShell
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 环境检测页面 - 参考真实 NativeTest 和 Ruru 检测工具
 * 提供完整的 Root/Hook/系统环境检测
 */

// 检测结果枚举
enum class DetectStatus {
    SAFE,       // 安全/未检测到
    RISK,       // 风险/已检测到
    WARNING,    // 警告
    UNKNOWN     // 未知/检测失败
}

// 检测分类
data class DetectCategory(
    val name: String,
    val icon: @Composable () -> Unit,
    val items: List<DetectItem>
)

data class DetectItem(
    val id: String,
    val name: String,
    val description: String = "",
    var status: DetectStatus = DetectStatus.UNKNOWN,
    var detail: String = "",
    var isDetecting: Boolean = false
)

@Destination<RootGraph>
@Composable
fun EnvDetectScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var isDetecting by remember { mutableStateOf(false) }
    var detectProgress by remember { mutableStateOf(0f) }
    var overallStatus by remember { mutableStateOf<DetectStatus?>(null) }

    // 定义所有检测项（参考真实 NativeTest 和 Ruru）
    val categories = remember {
        listOf(
            // === Root 权限检测 ===
            DetectCategory(
                name = "Root 权限",
                icon = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) }
            ) {
                listOf(
                    DetectItem("root_access", "Root 权限", "检测设备是否获取 Root 权限"),
                    DetectItem("su_binary", "su 二进制文件", "检测 su 命令是否存在"),
                    DetectItem("magisk", "Magisk", "检测 Magisk 框架"),
                    DetectItem("kernelsu", "KernelSU", "检测 KernelSU 框架"),
                    DetectItem("apatch", "APatch", "检测 APatch 框架"),
                    DetectItem("superuser", "SuperSU", "检测 SuperSU"),
                    DetectItem("kingroot", "KingRoot", "检测 KingRoot"),
                )
            },
            // === Hook 框架检测 ===
            DetectCategory(
                name = "Hook 框架",
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800)) }
            ) {
                listOf(
                    DetectItem("xposed", "Xposed", "检测 Xposed 框架"),
                    DetectItem("lsposed", "LSPosed", "检测 LSPosed 模块"),
                    DetectItem("edxposed", "EdXposed", "检测 EdXposed 框架"),
                    DetectItem("frida", "Frida", "检测 Frida 动态插桩工具"),
                    DetectItem("substrate", "Substrate", "检测 Cydia Substrate"),
                )
            },
            // === 可疑应用检测 ===
            DetectCategory(
                name = "可疑应用",
                icon = { Icon(Icons.Default.Error, null, tint = Color(0xFFF44336)) }
            ) {
                listOf(
                    DetectItem("magisk_app", "Magisk Manager", "检测 Magisk 管理器应用"),
                    DetectItem("kernelsu_app", "KernelSU Manager", "检测 KernelSU 管理器"),
                    DetectItem("apatch_app", "APatch", "检测 APatch 应用"),
                    DetectItem("supersu_app", "SuperSU", "检测 SuperSU 应用"),
                    DetectItem("lsposed_app", "LSPosed Manager", "检测 LSPosed 管理器"),
                    DetectItem("xposed_app", "Xposed Installer", "检测 Xposed 安装器"),
                    DetectItem("hide_app", "隐藏应用列表", "检测 HideMyApplist"),
                    DetectItem("shamiko", "Shamiko", "检测 Shamiko 模块"),
                )
            },
            // === 系统环境检测 ===
            DetectCategory(
                name = "系统环境",
                icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)) }
            ) {
                listOf(
                    DetectItem("bootloader", "Bootloader 状态", "检测 Bootloader 是否解锁"),
                    DetectItem("selinux", "SELinux", "检测 SELinux 安全策略状态"),
                    DetectItem("system_rw", "/system 可写", "检测系统分区是否可写"),
                    DetectItem("debuggable", "Debug 模式", "检测系统是否处于 Debug 模式"),
                    DetectItem("test_keys", "Test Keys", "检测是否使用测试签名"),
                    DetectItem("dangerous_props", "危险属性", "检测 ro.debuggable 等危险属性"),
                )
            },
            // === 开发者选项检测 ===
            DetectCategory(
                name = "开发者选项",
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800)) }
            ) {
                listOf(
                    DetectItem("dev_options", "开发者选项", "检测开发者选项是否开启"),
                    DetectItem("usb_debug", "USB 调试", "检测 USB 调试是否开启"),
                    DetectItem("adb_enabled", "ADB 调试", "检测 ADB 是否启用"),
                    DetectItem("mock_location", "模拟位置", "检测是否允许模拟位置"),
                )
            },
            // === 内存/进程检测 ===
            DetectCategory(
                name = "内存进程",
                icon = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.secondary) }
            ) {
                listOf(
                    DetectItem("maps_check", "Maps 扫描", "扫描 /proc/self/maps 中的异常"),
                    DetectItem("zygote", "Zygote 注入", "检测 Zygote 进程是否被注入"),
                    DetectItem("ptrace", "Ptrace 状态", "检测 Ptrace 反调试状态"),
                )
            }
        )
    }

    // 扁平化的检测项列表（用于状态管理）
    var allItems by remember { mutableStateOf(categories.flatMap { it.items }) }

    // 计算总体状态
    LaunchedEffect(allItems) {
        val checked = allItems.filter { it.status != DetectStatus.UNKNOWN }
        if (checked.isNotEmpty()) {
            overallStatus = when {
                checked.any { it.status == DetectStatus.RISK } -> DetectStatus.RISK
                checked.any { it.status == DetectStatus.WARNING } -> DetectStatus.WARNING
                checked.all { it.status == DetectStatus.SAFE } -> DetectStatus.SAFE
                else -> DetectStatus.UNKNOWN
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境检测") },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isDetecting) {
                                isDetecting = true
                                allItems = allItems.map { 
                                    it.copy(status = DetectStatus.UNKNOWN, detail = "", isDetecting = false) 
                                }
                            }
                        },
                        enabled = !isDetecting
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新检测")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 总体状态卡片
            OverallStatusCard(
                status = overallStatus,
                isDetecting = isDetecting,
                progress = detectProgress,
                totalCount = allItems.size,
                checkedCount = allItems.count { it.status != DetectStatus.UNKNOWN },
                riskCount = allItems.count { it.status == DetectStatus.RISK },
                warningCount = allItems.count { it.status == DetectStatus.WARNING }
            )

            // 检测进度条
            if (isDetecting) {
                LinearProgressIndicator(
                    progress = { detectProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // 检测结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                categories.forEach { category ->
                    item {
                        CategoryHeader(category)
                    }
                    items(category.items) { item ->
                        val currentItem = allItems.find { it.id == item.id } ?: item
                        DetectItemCard(
                            item = currentItem,
                            onClick = {
                                // 可以展开详情
                            }
                        )
                    }
                }
            }
        }
    }

    // 执行检测
    LaunchedEffect(isDetecting) {
        if (isDetecting) {
            val total = allItems.size
            var completed = 0

            allItems = allItems.map { item ->
                item.copy(isDetecting = true)
            }

            val results = withContext(Dispatchers.IO) {
                allItems.map { item ->
                    val (status, detail) = runRealDetection(context, item.id)
                    completed++
                    detectProgress = completed.toFloat() / total
                    item.copy(
                        status = status,
                        detail = detail,
                        isDetecting = false
                    )
                }
            }

            allItems = results
            isDetecting = false
            detectProgress = 1f
        }
    }
}

@Composable
private fun OverallStatusCard(
    status: DetectStatus?,
    isDetecting: Boolean,
    progress: Float,
    totalCount: Int,
    checkedCount: Int,
    riskCount: Int,
    warningCount: Int
) {
    val cardColor = when {
        isDetecting -> MaterialTheme.colorScheme.surfaceVariant
        status == DetectStatus.RISK -> Color(0xFFFFEBEE)
        status == DetectStatus.WARNING -> Color(0xFFFFF8E1)
        status == DetectStatus.SAFE -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconColor = when {
        isDetecting -> MaterialTheme.colorScheme.primary
        status == DetectStatus.RISK -> Color(0xFFF44336)
        status == DetectStatus.WARNING -> Color(0xFFFF9800)
        status == DetectStatus.SAFE -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        isDetecting -> Icons.Default.Refresh
                        status == DetectStatus.RISK -> Icons.Default.Error
                        status == DetectStatus.WARNING -> Icons.Default.Warning
                        status == DetectStatus.SAFE -> Icons.Default.CheckCircle
                        else -> Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = when {
                            isDetecting -> "正在检测... ${(progress * 100).toInt()}%"
                            status == DetectStatus.RISK -> "⚠️ 检测到风险环境"
                            status == DetectStatus.WARNING -> "⚡ 存在警告项"
                            status == DetectStatus.SAFE -> "✓ 环境安全"
                            else -> "点击刷新开始检测"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                    if (!isDetecting && checkedCount > 0) {
                        Text(
                            text = "已检测 $checkedCount/$totalCount 项 | 风险 $riskCount | 警告 $warningCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isDetecting && status != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (status) {
                        DetectStatus.RISK -> "检测到 Root/Hook 框架，建议在使用敏感应用前隐藏环境"
                        DetectStatus.WARNING -> "发现部分可疑配置，建议检查开发者选项设置"
                        DetectStatus.SAFE -> "未检测到 Root 或 Hook 框架，环境相对安全"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: DetectCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        category.icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DetectItemCard(
    item: DetectItem,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        when (item.status) {
            DetectStatus.SAFE -> Color(0xFFE8F5E9)
            DetectStatus.RISK -> Color(0xFFFFEBEE)
            DetectStatus.WARNING -> Color(0xFFFFF8E1)
            DetectStatus.UNKNOWN -> MaterialTheme.colorScheme.surface
        },
        label = "bgColor"
    )

    val iconColor = when (item.status) {
        DetectStatus.SAFE -> Color(0xFF4CAF50)
        DetectStatus.RISK -> Color(0xFFF44336)
        DetectStatus.WARNING -> Color(0xFFFF9800)
        DetectStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (item.status) {
        DetectStatus.SAFE -> "安全"
        DetectStatus.RISK -> "风险"
        DetectStatus.WARNING -> "警告"
        DetectStatus.UNKNOWN -> if (item.isDetecting) "检测中..." else "未检测"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isDetecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    when (item.status) {
                        DetectStatus.SAFE -> Icons.Default.CheckCircle
                        DetectStatus.RISK -> Icons.Default.Close
                        DetectStatus.WARNING -> Icons.Default.Warning
                        DetectStatus.UNKNOWN -> Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (item.detail.isNotEmpty()) {
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = iconColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== 真实检测实现 ====================

private fun runRealDetection(context: android.content.Context, id: String): Pair<DetectStatus, String> {
    return try {
        when (id) {
            // === Root 权限检测 ===
            "root_access" -> detectRootAccess()
            "su_binary" -> detectSuBinary()
            "magisk" -> detectMagisk()
            "kernelsu" -> detectKernelSU()
            "apatch" -> detectAPatch()
            "superuser" -> detectSuperSU()
            "kingroot" -> detectKingRoot()

            // === Hook 框架检测 ===
            "xposed" -> detectXposed()
            "lsposed" -> detectLSPosed()
            "edxposed" -> detectEdXposed()
            "frida" -> detectFrida()
            "substrate" -> detectSubstrate()

            // === 可疑应用检测 ===
            "magisk_app" -> detectAppInstalled(context, "com.topjohnwu.magisk", "Magisk")
            "kernelsu_app" -> detectAppInstalled(context, "me.weishu.kernelsu", "KernelSU")
            "apatch_app" -> detectAppInstalled(context, "me.bmax.apatch", "APatch")
            "supersu_app" -> detectAppInstalled(context, "eu.chainfire.supersu", "SuperSU")
            "lsposed_app" -> detectAppInstalled(context, "org.lsposed.manager", "LSPosed")
            "xposed_app" -> detectAppInstalled(context, "de.robv.android.xposed.installer", "Xposed")
            "hide_app" -> detectAppInstalled(context, "com.tsng.hidemyapplist", "隐藏应用列表")
            "shamiko" -> detectShamiko()

            // === 系统环境检测 ===
            "bootloader" -> detectBootloader()
            "selinux" -> detectSELinux()
            "system_rw" -> detectSystemWritable()
            "debuggable" -> detectDebuggable()
            "test_keys" -> detectTestKeys()
            "dangerous_props" -> detectDangerousProps()

            // === 开发者选项检测 ===
            "dev_options" -> detectDevOptions(context)
            "usb_debug" -> detectUsbDebug(context)
            "adb_enabled" -> detectAdbEnabled(context)
            "mock_location" -> detectMockLocation(context)

            // === 内存进程检测 ===
            "maps_check" -> detectMapsAnomaly()
            "zygote" -> detectZygoteInjection()
            "ptrace" -> detectPtrace()

            else -> Pair(DetectStatus.UNKNOWN, "未知检测项")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

// === Root 权限检测 ===

private fun detectRootAccess(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "su -c 'id' 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode == 0 && output.contains("uid=0")) {
            Pair(DetectStatus.RISK, "已获取 Root 权限: $output")
        } else {
            Pair(DetectStatus.SAFE, "未获取 Root 权限")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.SAFE, "无法执行 su 命令")
    }
}

private fun detectSuBinary(): Pair<DetectStatus, String> {
    val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/system/sd/xbin/su"
    )

    val found = suPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未发现 su 二进制文件")
    } else {
        Pair(DetectStatus.RISK, "发现 su: ${found.first()}")
    }
}

private fun detectMagisk(): Pair<DetectStatus, String> {
    val magiskPaths = listOf(
        "/data/adb/magisk", "/sbin/.magisk", "/dev/.magisk",
        "/data/adb/modules", "/data/adb/post-fs-data.d"
    )
    val found = magiskPaths.filter { File(it).exists() }

    // 检查 Magisk 进程
    val hasMagiskProcess = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps -A | grep -i magisk 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.isNotEmpty()
    } catch (_: Exception) { false }

    return if (found.isEmpty() && !hasMagiskProcess) {
        Pair(DetectStatus.SAFE, "未检测到 Magisk")
    } else {
        val details = mutableListOf<String>()
        if (found.isNotEmpty()) details.add("路径: ${found.first()}")
        if (hasMagiskProcess) details.add("进程运行中")
        Pair(DetectStatus.RISK, "检测到 Magisk (${details.joinToString(", ")})")
    }
}

private fun detectKernelSU(): Pair<DetectStatus, String> {
    val ksuPaths = listOf("/data/adb/ksu", "/data/adb/ksu/bin/ksu")
    val found = ksuPaths.filter { File(it).exists() }

    // 检查 KernelSU 模块
    val hasModules = File("/data/adb/modules").exists()

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 KernelSU")
    } else {
        Pair(DetectStatus.RISK, "检测到 KernelSU${if (hasModules) " (有模块)" else ""}")
    }
}

private fun detectAPatch(): Pair<DetectStatus, String> {
    val apPaths = listOf("/data/adb/ap", "/data/adb/ap/bin/apd")
    val found = apPaths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 APatch")
    } else {
        Pair(DetectStatus.RISK, "检测到 APatch")
    }
}

private fun detectSuperSU(): Pair<DetectStatus, String> {
    val paths = listOf("/system/app/Superuser", "/system/xbin/daemonsu")
    val found = paths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 SuperSU")
    } else {
        Pair(DetectStatus.RISK, "检测到 SuperSU")
    }
}

private fun detectKingRoot(): Pair<DetectStatus, String> {
    val paths = listOf(
        "/system/app/Kinguser",
        "/data/data/com.kingroot.kinguser"
    )
    val found = paths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 KingRoot")
    } else {
        Pair(DetectStatus.RISK, "检测到 KingRoot")
    }
}

// === Hook 框架检测 ===

private fun detectXposed(): Pair<DetectStatus, String> {
    val xposedPaths = listOf(
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/data/data/de.robv.android.xposed.installer"
    )
    val found = xposedPaths.filter { File(it).exists() }

    // 检查 Xposed 属性
    val hasXposedProp = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.build.xposed 2>/dev/null"))
        process.inputStream.bufferedReader().readText().trim().isNotEmpty()
    } catch (_: Exception) { false }

    return if (found.isEmpty() && !hasXposedProp) {
        Pair(DetectStatus.SAFE, "未检测到 Xposed")
    } else {
        Pair(DetectStatus.RISK, "检测到 Xposed 框架")
    }
}

private fun detectLSPosed(): Pair<DetectStatus, String> {
    val paths = listOf(
        "/data/adb/lspd",
        "/data/adb/modules/zygisk_lsposed",
        "/data/adb/modules/riru_lsposed"
    )
    val found = paths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 LSPosed")
    } else {
        Pair(DetectStatus.RISK, "检测到 LSPosed")
    }
}

private fun detectEdXposed(): Pair<DetectStatus, String> {
    val paths = listOf(
        "/data/adb/modules/riru_edxposed",
        "/data/adb/modules/edxposed"
    )
    val found = paths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 EdXposed")
    } else {
        Pair(DetectStatus.RISK, "检测到 EdXposed")
    }
}

private fun detectFrida(): Pair<DetectStatus, String> {
    // 检查 Frida 特征
    val fridaIndicators = listOf(
        "frida-server", "frida-gadget", "frida-agent"
    )

    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "cat /proc/self/maps 2>/dev/null | grep -i frida | head -1"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output.isNotEmpty()) {
            Pair(DetectStatus.RISK, "内存中发现 Frida 特征")
        } else {
            // 检查进程
            val psProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "ps -A | grep -i frida 2>/dev/null | head -1"))
            val psOutput = psProcess.inputStream.bufferedReader().readText().trim()
            psProcess.waitFor()

            if (psOutput.isNotEmpty()) {
                Pair(DetectStatus.RISK, "发现 Frida 进程")
            } else {
                Pair(DetectStatus.SAFE, "未检测到 Frida")
            }
        }
    } catch (e: Exception) {
        Pair(DetectStatus.SAFE, "无法检测 Frida")
    }
}

private fun detectSubstrate(): Pair<DetectStatus, String> {
    val paths = listOf(
        "/system/lib/libsubstrate.so",
        "/system/lib/libsubstrate-dvm.so"
    )
    val found = paths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 Substrate")
    } else {
        Pair(DetectStatus.RISK, "检测到 Substrate")
    }
}

// === 应用检测 ===

private fun detectAppInstalled(context: android.content.Context, packageName: String, appName: String): Pair<DetectStatus, String> {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        Pair(DetectStatus.RISK, "$appName 已安装 ($packageName)")
    } catch (_: PackageManager.NameNotFoundException) {
        Pair(DetectStatus.SAFE, "$appName 未安装")
    }
}

private fun detectShamiko(): Pair<DetectStatus, String> {
    val shamikoPaths = listOf(
        "/data/adb/modules/zygisk_shamiko",
        "/data/adb/modules/shamiko"
    )
    val found = shamikoPaths.filter { File(it).exists() }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未检测到 Shamiko")
    } else {
        Pair(DetectStatus.RISK, "检测到 Shamiko (Magisk 隐藏模块)")
    }
}

// === 系统环境检测 ===

private fun detectBootloader(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "getprop ro.boot.verifiedbootstate 2>/dev/null; getprop ro.boot.flash.locked 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        when {
            output.contains("orange") -> Pair(DetectStatus.RISK, "Bootloader 已解锁 (orange)")
            output.contains("1") -> Pair(DetectStatus.SAFE, "Bootloader 已锁定")
            output.contains("0") -> Pair(DetectStatus.RISK, "Bootloader 未锁定")
            else -> Pair(DetectStatus.UNKNOWN, "无法确定状态")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectSELinux(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getenforce 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        when (output) {
            "Enforcing" -> Pair(DetectStatus.SAFE, "SELinux: Enforcing (强制模式)")
            "Permissive" -> Pair(DetectStatus.RISK, "SELinux: Permissive (宽容模式)")
            "Disabled" -> Pair(DetectStatus.RISK, "SELinux: Disabled (已禁用)")
            else -> Pair(DetectStatus.UNKNOWN, "SELinux 状态: $output")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectSystemWritable(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "mount | grep ' /system ' 2>/dev/null | head -1"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output.contains("rw,")) {
            Pair(DetectStatus.RISK, "/system 以读写模式挂载")
        } else if (output.contains("ro,")) {
            Pair(DetectStatus.SAFE, "/system 以只读模式挂载")
        } else {
            // 尝试写入测试
            val testFile = File("/system/.test_write_${System.currentTimeMillis()}")
            val canWrite = try {
                testFile.createNewFile()
            } catch (_: Exception) { false }
            if (canWrite) {
                testFile.delete()
                Pair(DetectStatus.RISK, "/system 可写")
            } else {
                Pair(DetectStatus.SAFE, "/system 只读")
            }
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectDebuggable(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.debuggable 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output == "1") {
            Pair(DetectStatus.RISK, "ro.debuggable=1 (Debug 模式)")
        } else {
            Pair(DetectStatus.SAFE, "ro.debuggable=0")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectTestKeys(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.build.tags 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output.contains("test-keys")) {
            Pair(DetectStatus.WARNING, "使用测试签名 (test-keys)")
        } else if (output.contains("release-keys")) {
            Pair(DetectStatus.SAFE, "使用发布签名 (release-keys)")
        } else {
            Pair(DetectStatus.UNKNOWN, "签名类型: $output")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectDangerousProps(): Pair<DetectStatus, String> {
    val dangerous = listOf(
        "ro.debuggable" to "1",
        "ro.secure" to "0",
        "ro.allow.mock.location" to "1"
    )

    val found = mutableListOf<String>()
    for ((prop, badValue) in dangerous) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop $prop 2>/dev/null"))
            val value = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (value == badValue) {
                found.add("$prop=$value")
            }
        } catch (_: Exception) {}
    }

    return if (found.isEmpty()) {
        Pair(DetectStatus.SAFE, "未发现危险属性")
    } else {
        Pair(DetectStatus.WARNING, "发现: ${found.joinToString(", ")}")
    }
}

// === 开发者选项检测 ===

private fun detectDevOptions(context: android.content.Context): Pair<DetectStatus, String> {
    return try {
        val enabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        if (enabled) {
            Pair(DetectStatus.WARNING, "开发者选项已开启")
        } else {
            Pair(DetectStatus.SAFE, "开发者选项未开启")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectUsbDebug(context: android.content.Context): Pair<DetectStatus, String> {
    return try {
        val enabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        if (enabled) {
            Pair(DetectStatus.WARNING, "USB 调试已开启")
        } else {
            Pair(DetectStatus.SAFE, "USB 调试未开启")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectAdbEnabled(context: android.content.Context): Pair<DetectStatus, String> {
    return detectUsbDebug(context)
}

private fun detectMockLocation(context: android.content.Context): Pair<DetectStatus, String> {
    return try {
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 1
        if (enabled) {
            Pair(DetectStatus.WARNING, "允许模拟位置")
        } else {
            Pair(DetectStatus.SAFE, "未允许模拟位置")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

// === 内存/进程检测 ===

private fun detectMapsAnomaly(): Pair<DetectStatus, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "cat /proc/self/maps 2>/dev/null | grep -iE 'xposed|frida|substrate|lsposed|riru|zygisk|magisk' | head -3"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output.isNotEmpty()) {
            val firstLine = output.lines().firstOrNull() ?: ""
            Pair(DetectStatus.RISK, "内存中发现异常: ${firstLine.take(50)}")
        } else {
            Pair(DetectStatus.SAFE, "内存映射正常")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectZygoteInjection(): Pair<DetectStatus, String> {
    // 检查 Zygote 进程是否异常
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "ps -A | grep -E 'zygote|zygisk' 2>/dev/null | head -3"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (output.contains("zygisk") || output.lines().size > 2) {
            Pair(DetectStatus.WARNING, "发现 Zygote 相关异常进程")
        } else {
            Pair(DetectStatus.SAFE, "Zygote 进程正常")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}

private fun detectPtrace(): Pair<DetectStatus, String> {
    // 检查是否被 ptrace 附加
    return try {
        val statusFile = File("/proc/self/status")
        if (statusFile.exists()) {
            val content = statusFile.readText()
            val tracerPid = content.lines()
                .find { it.startsWith("TracerPid:") }
                ?.substringAfter(":")
                ?.trim()
                ?: "0"

            if (tracerPid != "0") {
                Pair(DetectStatus.RISK, "被进程 $tracerPid 调试附加")
            } else {
                Pair(DetectStatus.SAFE, "未被调试")
            }
        } else {
            Pair(DetectStatus.UNKNOWN, "无法读取状态")
        }
    } catch (e: Exception) {
        Pair(DetectStatus.UNKNOWN, "检测失败: ${e.message}")
    }
}
