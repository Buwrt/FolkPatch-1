package me.yuki.foly.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.yuki.foly.R
import me.yuki.foly.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 环境检测页面 - 集成牛头32检测和Ruru检测
 * 检测Root状态、Magisk/Xposed框架、Bootloader解锁、系统完整性等
 */
data class DetectItem(
    val name: String,
    val category: String,
    var result: DetectResult = DetectResult.PENDING,
    var detail: String = ""
)

enum class DetectResult {
    PASS,       // 通过/安全
    FAIL,       // 未通过/检测到
    WARNING,    // 警告
    PENDING,    // 待检测
    ERROR       // 检测出错
}

@Destination<RootGraph>
@Composable
fun EnvDetectScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var isDetecting by remember { mutableStateOf(false) }
    var detectItems by remember { mutableStateOf(listOf<DetectItem>()) }

    // 初始化检测项
    val allItems = remember {
        listOf(
            // === 牛头32检测项 ===
            DetectItem("Root 权限检测", "牛头32"),
            DetectItem("su 路径检测", "牛头32"),
            DetectItem("Magisk 检测", "牛头32"),
            DetectItem("KernelSU 检测", "牛头32"),
            DetectItem("APatch 检测", "牛头32"),
            DetectItem("Xposed 框架检测", "牛头32"),
            DetectItem("LSPosed 检测", "牛头32"),
            DetectItem("Bootloader 解锁检测", "牛头32"),
            DetectItem("系统完整性检测", "牛头32"),
            DetectItem("Debug 模式检测", "牛头32"),
            DetectItem("开发者选项检测", "牛头32"),
            DetectItem("USB 调试检测", "牛头32"),
            DetectItem("允许安装未知来源检测", "牛头32"),
            // === Ruru检测项 ===
            DetectItem("Root 管理应用检测", "Ruru"),
            DetectItem("Magisk Manager 检测", "Ruru"),
            DetectItem("KernelSU Manager 检测", "Ruru"),
            DetectItem("APatch Manager 检测", "Ruru"),
            DetectItem("SuperSU 检测", "Ruru"),
            DetectItem("KingRoot 检测", "Ruru"),
            DetectItem("Dangerous Props 检测", "Ruru"),
            DetectItem("SELinux 状态检测", "Ruru"),
            DetectItem("/system 分区可写检测", "Ruru"),
            DetectItem("风险属性检测", "Ruru"),
            DetectItem("Hook 框架特征检测", "Ruru"),
        )
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
                                detectItems = allItems.map { it.copy(result = DetectResult.PENDING, detail = "") }
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
            // 检测状态概览
            if (detectItems.isNotEmpty()) {
                val passCount = detectItems.count { it.result == DetectResult.PASS }
                val failCount = detectItems.count { it.result == DetectResult.FAIL }
                val warnCount = detectItems.count { it.result == DetectResult.WARNING }
                val totalCount = detectItems.size
                val checkedCount = detectItems.count { it.result != DetectResult.PENDING }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            failCount > 0 -> MaterialTheme.colorScheme.errorContainer
                            warnCount > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                            checkedCount == totalCount -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                failCount > 0 -> Icons.Default.Error
                                warnCount > 0 -> Icons.Default.Warning
                                checkedCount == totalCount -> Icons.Default.CheckCircle
                                else -> Icons.Default.Security
                            },
                            contentDescription = null,
                            tint = when {
                                failCount > 0 -> MaterialTheme.colorScheme.error
                                warnCount > 0 -> MaterialTheme.colorScheme.tertiary
                                checkedCount == totalCount -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    isDetecting -> "正在检测中... ($checkedCount/$totalCount)"
                                    checkedCount == 0 -> "点击右上角刷新按钮开始检测"
                                    failCount > 0 -> "检测完成：发现 $failCount 项风险"
                                    warnCount > 0 -> "检测完成：$warnCount 项警告"
                                    else -> "检测完成：全部通过 ✓"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (checkedCount > 0) {
                                Text(
                                    text = "通过 $passCount / 警告 $warnCount / 风险 $failCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 检测结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 按分类分组
                val grouped = detectItems.groupBy { it.category }
                grouped.forEach { (category, items) ->
                    item {
                        Text(
                            text = if (category == "牛头32") "🐂 牛头32 NativeTest" else "🔍 Ruru 检测",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(items) { item ->
                        DetectItemCard(item)
                    }
                }
            }
        }
    }

    // 执行检测
    LaunchedEffect(isDetecting) {
        if (isDetecting) {
            val results = withContext(Dispatchers.IO) {
                allItems.map { item ->
                    val (result, detail) = runDetection(context, item.name)
                    item.copy(result = result, detail = detail)
                }
            }
            detectItems = results
            isDetecting = false
        }
    }
}

@Composable
private fun DetectItemCard(item: DetectItem) {
    val bgColor by animateColorAsState(
        when (item.result) {
            DetectResult.PASS -> Color(0xFFE8F5E9)
            DetectResult.FAIL -> Color(0xFFFFEBEE)
            DetectResult.WARNING -> Color(0xFFFFF8E1)
            DetectResult.ERROR -> Color(0xFFFBE9E7)
            DetectResult.PENDING -> MaterialTheme.colorScheme.surface
        },
        label = "bgColor"
    )
    val iconColor = when (item.result) {
        DetectResult.PASS -> Color(0xFF4CAF50)
        DetectResult.FAIL -> Color(0xFFF44336)
        DetectResult.WARNING -> Color(0xFFFF9800)
        DetectResult.ERROR -> Color(0xFF795548)
        DetectResult.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (item.result) {
        DetectResult.PASS -> "安全"
        DetectResult.FAIL -> "检测到"
        DetectResult.WARNING -> "警告"
        DetectResult.ERROR -> "出错"
        DetectResult.PENDING -> "待检测"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (item.result) {
                    DetectResult.PASS -> Icons.Default.CheckCircle
                    DetectResult.FAIL -> Icons.Default.Close
                    DetectResult.WARNING -> Icons.Default.Warning
                    DetectResult.ERROR -> Icons.Default.Error
                    DetectResult.PENDING -> Icons.Default.Security
                },
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 执行单项检测
 */
private fun runDetection(context: android.content.Context, name: String): Pair<DetectResult, String> {
    return try {
        when (name) {
            "Root 权限检测" -> detectRootAccess()
            "su 路径检测" -> detectSuPath()
            "Magisk 检测" -> detectMagisk()
            "KernelSU 检测" -> detectKernelSU()
            "APatch 检测" -> detectAPatch()
            "Xposed 框架检测" -> detectXposed()
            "LSPosed 检测" -> detectLSPosed()
            "Bootloader 解锁检测" -> detectBootloader()
            "系统完整性检测" -> detectSystemIntegrity()
            "Debug 模式检测" -> detectDebugMode()
            "开发者选项检测" -> detectDeveloperOptions(context)
            "USB 调试检测" -> detectUsbDebugging(context)
            "允许安装未知来源检测" -> detectUnknownSources(context)
            "Root 管理应用检测" -> detectRootApps(context)
            "Magisk Manager 检测" -> detectAppInstalled(context, "com.topjohnwu.magisk", "Magisk Manager")
            "KernelSU Manager 检测" -> detectAppInstalled(context, "me.weishu.kernelsu", "KernelSU Manager")
            "APatch Manager 检测" -> detectAppInstalled(context, "me.bmax.apatch", "APatch")
            "SuperSU 检测" -> detectAppInstalled(context, "eu.chainfire.supersu", "SuperSU")
            "KingRoot 检测" -> detectAppInstalled(context, "com.kingroot.kinguser", "KingRoot")
            "Dangerous Props 检测" -> detectDangerousProps()
            "SELinux 状态检测" -> detectSELinux()
            "/system 分区可写检测" -> detectSystemWritable()
            "风险属性检测" -> detectRiskyProps()
            "Hook 框架特征检测" -> detectHookFramework(context)
            else -> Pair(DetectResult.ERROR, "未知检测项")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测出错: ${e.message}")
    }
}

// ==================== 牛头32检测实现 ====================

private fun detectRootAccess(): Pair<DetectResult, String> {
    return try {
        val hasRoot = RootShell.hasRoot()
        if (hasRoot) {
            val rootType = RootShell.detectRootType()
            Pair(DetectResult.FAIL, "设备已Root ($rootType)")
        } else {
            Pair(DetectResult.PASS, "未检测到Root权限")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectSuPath(): Pair<DetectResult, String> {
    val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/magisk/.core/bin/su", "/data/adb/magisk/magisk",
        "/data/adb/ksu/bin/ksu", "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/apd", "/data/adb/ap/bin/su",
        "/system/bin/kp"
    )
    val found = suPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未发现su路径")
    } else {
        Pair(DetectResult.FAIL, "发现 ${found.size} 个su路径: ${found.joinToString(", ")}")
    }
}

private fun detectMagisk(): Pair<DetectResult, String> {
    val magiskPaths = listOf(
        "/data/adb/magisk", "/sbin/.magisk", "/magisk"
    )
    val found = magiskPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未检测到Magisk")
    } else {
        Pair(DetectResult.FAIL, "检测到Magisk: ${found.joinToString(", ")}")
    }
}

private fun detectKernelSU(): Pair<DetectResult, String> {
    val ksuPaths = listOf(
        "/data/adb/ksu", "/data/adb/ksu/bin/ksu"
    )
    val found = ksuPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未检测到KernelSU")
    } else {
        Pair(DetectResult.FAIL, "检测到KernelSU: ${found.joinToString(", ")}")
    }
}

private fun detectAPatch(): Pair<DetectResult, String> {
    val apPaths = listOf(
        "/data/adb/ap", "/data/adb/ap/bin/apd"
    )
    val found = apPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未检测到APatch")
    } else {
        Pair(DetectResult.FAIL, "检测到APatch: ${found.joinToString(", ")}")
    }
}

private fun detectXposed(): Pair<DetectResult, String> {
    // 检查 Xposed 框架特征
    val xposedPaths = listOf(
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/framework/XposedInstaller.apk"
    )
    val found = xposedPaths.filter { File(it).exists() }
    // 检查 Xposed 相关属性
    val propCheck = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.build.xposed 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        output.isNotEmpty()
    } catch (_: Exception) { false }
    
    return if (found.isEmpty() && !propCheck) {
        Pair(DetectResult.PASS, "未检测到Xposed框架")
    } else {
        val details = mutableListOf<String>()
        if (found.isNotEmpty()) details.add("路径: ${found.joinToString(", ")}")
        if (propCheck) details.add("属性: ro.build.xposed")
        Pair(DetectResult.FAIL, "检测到Xposed: ${details.joinToString("; ")}")
    }
}

private fun detectLSPosed(): Pair<DetectResult, String> {
    val lsposedPaths = listOf(
        "/data/adb/lspd", "/data/adb/modules/lsposed"
    )
    val found = lsposedPaths.filter { File(it).exists() }
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未检测到LSPosed")
    } else {
        Pair(DetectResult.FAIL, "检测到LSPosed: ${found.joinToString(", ")}")
    }
}

private fun detectBootloader(): Pair<DetectResult, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.boot.flash.locked 2>/dev/null; getprop ro.boot.verifiedbootstate 2>/dev/null; getprop ro.boot.veritymode 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        
        val isLocked = output.contains("1") || output.contains("verified") || output.contains("enforcing")
        if (isLocked) {
            Pair(DetectResult.PASS, "Bootloader 已锁定")
        } else {
            // 检查其他方式
            val process2 = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.boot.verifiedbootstate 2>/dev/null"))
            val vbs = process2.inputStream.bufferedReader().readText().trim()
            process2.waitFor()
            
            if (vbs.contains("orange") || vbs.contains("disabled")) {
                Pair(DetectResult.FAIL, "Bootloader 已解锁 (verifiedbootstate=$vbs)")
            } else if (vbs.isNotEmpty()) {
                Pair(DetectResult.WARNING, "状态: $vbs")
            } else {
                Pair(DetectResult.WARNING, "无法确定Bootloader状态")
            }
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectSystemIntegrity(): Pair<DetectResult, String> {
    return try {
        // 检查系统关键文件是否被修改
        val systemFiles = listOf(
            "/system/bin/app_process",
            "/system/bin/servicemanager",
            "/system/framework/boot.jar",
            "/system/framework/services.jar"
        )
        val modified = mutableListOf<String>()
        for (file in systemFiles) {
            val f = File(file)
            if (f.exists()) {
                // 检查文件大小是否异常（简单检测）
                if (f.length() == 0L) {
                    modified.add("$file (空文件)")
                }
            }
        }
        if (modified.isEmpty()) {
            Pair(DetectResult.PASS, "系统文件完整性正常")
        } else {
            Pair(DetectResult.FAIL, "发现异常: ${modified.joinToString("; ")}")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectDebugMode(): Pair<DetectResult, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.debuggable 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (output == "1") {
            Pair(DetectResult.FAIL, "系统处于Debug模式")
        } else {
            Pair(DetectResult.PASS, "系统非Debug模式")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectDeveloperOptions(context: android.content.Context): Pair<DetectResult, String> {
    return try {
        val enabled = android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
        if (enabled) {
            Pair(DetectResult.WARNING, "开发者选项已开启")
        } else {
            Pair(DetectResult.PASS, "开发者选项未开启")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectUsbDebugging(context: android.content.Context): Pair<DetectResult, String> {
    return try {
        val enabled = android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.ADB_ENABLED, 0
        ) == 1
        if (enabled) {
            Pair(DetectResult.WARNING, "USB调试已开启")
        } else {
            Pair(DetectResult.PASS, "USB调试未开启")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectUnknownSources(context: android.content.Context): Pair<DetectResult, String> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 每个应用独立控制，检查是否安装了未知来源的应用
            Pair(DetectResult.PASS, "Android 8.0+ 每应用独立控制")
        } else {
            val enabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.INSTALL_NON_MARKET_APPS, 0
            ) == 1
            if (enabled) {
                Pair(DetectResult.WARNING, "允许安装未知来源应用")
            } else {
                Pair(DetectResult.PASS, "未允许安装未知来源")
            }
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

// ==================== Ruru检测实现 ====================

private fun detectRootApps(context: android.content.Context): Pair<DetectResult, String> {
    val rootAppPackages = listOf(
        "com.topjohnwu.magisk",   // Magisk
        "me.weishu.kernelsu",      // KernelSU
        "me.bmax.apatch",          // APatch
        "eu.chainfire.supersu",    // SuperSU
        "com.kingroot.kinguser",   // KingRoot
        "com.noshufou.android.su", // SuperUser
        "com.koushikdutta.superuser", // SuperUser
        "com.thirdparty.superuser", // SuperUser
        "com.yellowes.su",         // YellowES SuperUser
        "com.chainfire.supersu",   // SuperSU (alt)
        "de.robv.android.xposed.installer", // Xposed Installer
        "org.lsposed.manager",     // LSPosed
        "io.github.vvb2060.magisk", // Alpha Magisk
    )
    
    val pm = context.packageManager
    val found = mutableListOf<String>()
    for (pkg in rootAppPackages) {
        try {
            pm.getPackageInfo(pkg, 0)
            found.add(pkg)
        } catch (_: PackageManager.NameNotFoundException) {}
    }
    
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未发现Root管理应用")
    } else {
        Pair(DetectResult.FAIL, "发现 ${found.size} 个Root应用: ${found.joinToString(", ")}")
    }
}

private fun detectAppInstalled(context: android.content.Context, packageName: String, appName: String): Pair<DetectResult, String> {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        Pair(DetectResult.FAIL, "$appName 已安装 ($packageName)")
    } catch (_: PackageManager.NameNotFoundException) {
        Pair(DetectResult.PASS, "$appName 未安装")
    }
}

private fun detectDangerousProps(): Pair<DetectResult, String> {
    val dangerousProps = listOf(
        "ro.debuggable" to "1",
        "ro.secure" to "0",
        "service.bootanim.exit" to "1",
        "ro.build.tags" to "test-keys",
        "ro.build.type" to "eng",
    )
    
    val found = mutableListOf<String>()
    for ((prop, value) in dangerousProps) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop $prop 2>/dev/null"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output == value) {
                found.add("$prop=$output")
            }
        } catch (_: Exception) {}
    }
    
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未发现危险属性")
    } else {
        Pair(DetectResult.FAIL, "发现 ${found.size} 个危险属性: ${found.joinToString("; ")}")
    }
}

private fun detectSELinux(): Pair<DetectResult, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getenforce 2>/dev/null"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        when (output) {
            "Enforcing" -> Pair(DetectResult.PASS, "SELinux 状态: Enforcing (强制)")
            "Permissive" -> Pair(DetectResult.FAIL, "SELinux 状态: Permissive (宽容)")
            "Disabled" -> Pair(DetectResult.FAIL, "SELinux 状态: Disabled (禁用)")
            else -> Pair(DetectResult.WARNING, "SELinux 状态: $output")
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectSystemWritable(): Pair<DetectResult, String> {
    return try {
        val testFile = File("/system/test_folkpatch_rw")
        val canWrite = try {
            testFile.createNewFile()
        } catch (_: Exception) { false }
        
        if (canWrite) {
            testFile.delete()
            Pair(DetectResult.FAIL, "/system 分区可写")
        } else {
            // 检查 mount 状态
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "mount | grep ' /system ' 2>/dev/null"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.contains("rw,")) {
                Pair(DetectResult.FAIL, "/system 以读写模式挂载")
            } else {
                Pair(DetectResult.PASS, "/system 分区只读")
            }
        }
    } catch (e: Exception) {
        Pair(DetectResult.ERROR, "检测失败: ${e.message}")
    }
}

private fun detectRiskyProps(): Pair<DetectResult, String> {
    val riskyProps = listOf(
        "ro.build.selinux",
        "persist.sys.safemode",
        "ro.allow.mock.location",
        "ro.hardware",
    )
    
    val found = mutableListOf<String>()
    for (prop in riskyProps) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop $prop 2>/dev/null"))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.isNotEmpty() && output != "0" && output != "false") {
                found.add("$prop=$output")
            }
        } catch (_: Exception) {}
    }
    
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未发现风险属性")
    } else {
        Pair(DetectResult.WARNING, "发现 ${found.size} 个属性: ${found.joinToString("; ")}")
    }
}

private fun detectHookFramework(context: android.content.Context): Pair<DetectResult, String> {
    val found = mutableListOf<String>()
    
    // 检查内存中的 Hook 特征
    try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
            "cat /proc/self/maps 2>/dev/null | grep -iE 'xposed|substrate|frida|lsposed|riru|zygisk' | head -5"))
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (output.isNotEmpty()) {
            found.add("内存映射: ${output.lines().firstOrNull() ?: ""}")
        }
    } catch (_: Exception) {}
    
    // 检查已安装的 Hook 框架应用
    val hookPackages = listOf(
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "org.meowcat.edxposed.manager",
        "com.tsng.hidemyapplist",
        "btools.hideapp",
    )
    val pm = context.packageManager
    for (pkg in hookPackages) {
        try {
            pm.getPackageInfo(pkg, 0)
            found.add(pkg)
        } catch (_: PackageManager.NameNotFoundException) {}
    }
    
    return if (found.isEmpty()) {
        Pair(DetectResult.PASS, "未检测到Hook框架")
    } else {
        Pair(DetectResult.FAIL, "发现 ${found.size} 项Hook特征: ${found.joinToString("; ")}")
    }
}
