package icu.nullptr.applistdetector

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/**
 * Magisk应用检测器
 * 检测Magisk应用（包括隐藏版本）
 */
class MagiskApp(context: Context) : IDetector(context) {

    override val name = "Magisk应用检测"

    private val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
            PackageManager.GET_PERMISSIONS

    // 已知的Magisk包名
    private val knownMagiskPackages = setOf(
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "com.android.vending"  // 常见隐藏包名
    )

    @SuppressLint("QueryPermissionsNeeded")
    override fun run(packages: Collection<String>?, detail: Detail?): Result {
        if (packages != null) throw IllegalArgumentException("packages should be null")

        var result = Result.NOT_FOUND
        val pm = context.packageManager
        
        // 方法1: 检查已知Magisk包名
        for (pkgName in knownMagiskPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                if (appInfo != null) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    detail?.add("$label ($pkgName)" to Result.FOUND)
                    result = Result.FOUND
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // 包不存在，继续检查
            }
        }
        
        // 方法2: 检查可疑的Magisk特征
        val intent = Intent(Intent.ACTION_MAIN)
        for (pkg in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
            try {
                val pInfo = pm.getPackageInfo(pkg.activityInfo.packageName, flags)
                val aInfo = pInfo.applicationInfo
                val apkFile = File(aInfo.sourceDir)
                val apkSize = apkFile.length() / 1024
                
                // Magisk stub APK 通常在 20-40KB 或完整版在 9-20MB
                val isSuspiciousSize = apkSize in 20..40 || apkSize in 9 * 1024..20 * 1024
                
                if (isSuspiciousSize && aInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    // 检查是否有Magisk特征权限
                    val permissions = pInfo.requestedPermissions?.toList() ?: emptyList()
                    val hasMagiskPermissions = permissions.any { 
                        it.contains("superuser", ignoreCase = true) ||
                        it.contains("magisk", ignoreCase = true) ||
                        it.contains("root", ignoreCase = true)
                    }
                    
                    // 检查包名是否可疑
                    val isSuspiciousPackage = aInfo.packageName.let { name ->
                        name == "com.android.vending" && apkSize < 100 // 伪装成Play Store但很小
                    }
                    
                    if (hasMagiskPermissions || isSuspiciousPackage) {
                        val label = pm.getApplicationLabel(aInfo).toString()
                        detail?.add("可疑应用: $label (${aInfo.packageName})" to Result.SUSPICIOUS)
                        result = result.coerceAtLeast(Result.SUSPICIOUS)
                    }
                }
            } catch (e: Exception) {
                // 忽略错误
            }
        }
        
        // 方法3: 检查Magisk相关文件
        val magiskPaths = listOf(
            "/data/adb/magisk",
            "/sbin/.magisk",
            "/data/adb/ksu",
            "/data/adb/ap"
        )
        
        for (path in magiskPaths) {
            if (File(path).exists()) {
                detail?.add("发现Magisk目录: $path" to Result.SUSPICIOUS)
                result = result.coerceAtLeast(Result.SUSPICIOUS)
            }
        }
        
        return result
    }
}
