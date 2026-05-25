package me.yuki.foly.util

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Root权限执行工具类
 * 支持多种Root方案：Magisk、KernelSU、APatch
 */
object RootShell {
    private const val TAG = "RootShell"

    // 可能的SU路径
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/system/bin/magisk",
        "/data/adb/magisk/magisk",
        "/data/adb/ksu/bin/ksu",
        "/data/adb/ap/bin/apd"
    )

    /**
     * 获取可用的SU路径
     */
    fun getSuPath(): String? {
        // 首先检查环境变量中的su
        val pathEnv = System.getenv("PATH") ?: ""
        val paths = pathEnv.split(":")
        for (path in paths) {
            val suFile = File("$path/su")
            if (suFile.exists() && suFile.canExecute()) {
                Log.d(TAG, "Found su in PATH: ${suFile.absolutePath}")
                return suFile.absolutePath
            }
        }

        // 检查常见路径
        for (path in SU_PATHS) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                Log.d(TAG, "Found su at: $path")
                return path
            }
        }

        // 尝试使用which命令查找
        try {
            val process = Runtime.getRuntime().exec("sh")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            os.writeBytes("which su 2>/dev/null || echo 'NOT_FOUND'\n")
            os.writeBytes("exit\n")
            os.flush()

            val result = reader.readLine()?.trim()
            if (result != null && result != "NOT_FOUND" && File(result).exists()) {
                Log.d(TAG, "Found su via which: $result")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "which su failed: ${e.message}")
        }

        // 尝试使用magisk的su
        try {
            val magiskSu = File("/data/adb/magisk/magisk")
            if (magiskSu.exists()) {
                Log.d(TAG, "Using Magisk su")
                return magiskSu.absolutePath + " su"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Magisk check failed: ${e.message}")
        }

        Log.w(TAG, "No su found, using default 'su'")
        return "su"
    }

    /**
     * 执行Root命令
     */
    fun exec(vararg commands: String): Process? {
        val suPath = getSuPath() ?: return null
        return try {
            val pb = ProcessBuilder(suPath.split(" "))
            pb.redirectErrorStream(true)

            // 设置环境变量
            val env = pb.environment()
            val currentPath = System.getenv("PATH") ?: ""
            env["PATH"] = "$currentPath:/system/bin:/system/xbin:/sbin:/su/bin:/magisk/.core/bin:/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin"

            val process = pb.start()

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
            null
        }
    }

    /**
     * 执行命令并返回结果
     */
    fun execWithOutput(vararg commands: String): Pair<Int, String> {
        val process = exec(*commands) ?: return Pair(-1, "Failed to get root")

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
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * 检查是否有Root权限
     */
    fun hasRoot(): Boolean {
        val (exitCode, output) = execWithOutput("id")
        return exitCode == 0 && output.contains("uid=0")
    }
}
