package me.yuki.foly.util

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Root权限执行工具类
 * 支持多种Root方案：Magisk、KernelSU、APatch
 * 不主动申请权限，直接调用系统原生su
 */
object RootShell {
    private const val TAG = "RootShell"

    // 可能的SU路径（按优先级排序）
    private val SU_PATHS = arrayOf(
        // Magisk 路径
        "/data/adb/magisk/magisk",
        "/sbin/.magisk/mirror/system/bin/su",
        "/magisk/.core/bin/su",
        // KernelSU 路径
        "/data/adb/ksu/bin/ksu",
        "/data/adb/ksu/bin/su",
        // APatch 路径
        "/data/adb/ap/bin/apd",
        "/data/adb/ap/bin/su",
        // KernelPatch 路径
        "/system/bin/kp",
        // 系统标准路径
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/system_ext/bin/su"
    )

    // 缓存找到的su路径
    @Volatile
    private var cachedSuPath: String? = null

    /**
     * 获取可用的SU路径
     * 不弹窗申请权限，直接检测系统中存在的su
     */
    fun getSuPath(): String? {
        // 返回缓存结果
        cachedSuPath?.let { return it }

        // 首先检查环境变量中的su
        val pathEnv = System.getenv("PATH") ?: ""
        val paths = pathEnv.split(":")
        for (path in paths) {
            try {
                val suFile = File("$path/su")
                if (suFile.exists() && suFile.canExecute()) {
                    Log.d(TAG, "Found su in PATH: ${suFile.absolutePath}")
                    cachedSuPath = suFile.absolutePath
                    return suFile.absolutePath
                }
            } catch (_: Exception) {}
        }

        // 检查常见路径
        for (path in SU_PATHS) {
            try {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    Log.d(TAG, "Found su at: $path")
                    cachedSuPath = path
                    return path
                }
            } catch (_: Exception) {}
        }

        // 尝试使用which命令查找
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which su 2>/dev/null || echo 'NOT_FOUND'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim()
            process.waitFor()
            reader.close()
            if (result != null && result != "NOT_FOUND" && File(result).exists()) {
                Log.d(TAG, "Found su via which: $result")
                cachedSuPath = result
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "which su failed: ${e.message}")
        }

        // 尝试Magisk特殊路径
        try {
            val magiskSu = File("/data/adb/magisk/magisk")
            if (magiskSu.exists()) {
                Log.d(TAG, "Using Magisk su")
                cachedSuPath = magiskSu.absolutePath
                return magiskSu.absolutePath
            }
        } catch (_: Exception) {}

        // 尝试通过 sh -c "su" 的方式（ProcessBuilder不会搜索PATH，但sh会）
        Log.w(TAG, "No su found in known paths, will try 'sh -c su'")
        return null
    }

    /**
     * 清除缓存的su路径（用于重试）
     */
    fun clearCache() {
        cachedSuPath = null
    }

    /**
     * 执行Root命令
     * 使用 sh -c 方式执行，确保能找到 PATH 中的 su
     */
    fun exec(vararg commands: String): Process? {
        val suPath = getSuPath()
        
        return try {
            val process: Process
            if (suPath != null) {
                // 有明确的su路径，直接使用
                val pb = ProcessBuilder(suPath)
                pb.redirectErrorStream(true)
                val env = pb.environment()
                val currentPath = System.getenv("PATH") ?: ""
                env["PATH"] = "$currentPath:/system/bin:/system/xbin:/sbin:/su/bin:/magisk/.core/bin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin"
                process = pb.start()
            } else {
                // 没有找到明确路径，通过 sh -c 执行 su（sh 会搜索 PATH）
                val pb = ProcessBuilder("sh", "-c", "su")
                pb.redirectErrorStream(true)
                val env = pb.environment()
                val currentPath = System.getenv("PATH") ?: ""
                env["PATH"] = "$currentPath:/system/bin:/system/xbin:/sbin:/su/bin:/magisk/.core/bin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin"
                process = pb.start()
            }

            // 执行命令
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            process
        } catch (e: Exception) {
            Log.e(TAG, "Exec failed: ${e.message}")
            // 最后回退：尝试 sh -c su
            try {
                Log.d(TAG, "Fallback: trying sh -c su")
                val pb = ProcessBuilder("sh", "-c", "su")
                pb.redirectErrorStream(true)
                val env = pb.environment()
                val currentPath = System.getenv("PATH") ?: ""
                env["PATH"] = "$currentPath:/system/bin:/system/xbin:/sbin:/su/bin:/magisk/.core/bin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin"
                val process = pb.start()
                val os = DataOutputStream(process.outputStream)
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                process
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed: ${e2.message}")
                null
            }
        }
    }

    /**
     * 执行命令并返回结果
     */
    fun execWithOutput(vararg commands: String): Pair<Int, String> {
        val process = exec(*commands) ?: return Pair(-1, "无法获取Root权限，请检查设备是否已Root")

        return try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            Pair(exitCode, output.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Read output failed: ${e.message}")
            Pair(-1, e.message ?: "未知错误")
        }
    }

    /**
     * 检查是否有Root权限
     */
    fun hasRoot(): Boolean {
        return try {
            val (exitCode, output) = execWithOutput("id")
            exitCode == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检测Root类型
     */
    fun detectRootType(): String {
        val paths = listOf(
            Pair("/data/adb/magisk/magisk", "Magisk"),
            Pair("/data/adb/ksu/bin/ksu", "KernelSU"),
            Pair("/data/adb/ap/bin/apd", "APatch"),
            Pair("/system/bin/kp", "KernelPatch")
        )
        
        for ((path, name) in paths) {
            if (File(path).exists()) {
                return name
            }
        }
        
        // 尝试通过命令检测
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", 
                "which magisk 2>/dev/null && echo MAGISK; which ksu 2>/dev/null && echo KSU; which apd 2>/dev/null && echo APATCH; which kp 2>/dev/null && echo KPATCH"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            reader.close()
            
            return when {
                output.contains("MAGISK") -> "Magisk"
                output.contains("KSU") -> "KernelSU"
                output.contains("APATCH") -> "APatch"
                output.contains("KPATCH") -> "KernelPatch"
                else -> "未知Root方案"
            }
        } catch (_: Exception) {}
        
        return if (hasRoot()) "通用Root" else "未检测到Root"
    }
}
