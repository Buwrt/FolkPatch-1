package me.yuki.foly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import icu.nullptr.applistdetector.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yuki.foly.R

/**
 * 应用检测页面
 * 集成ApplistDetector检测功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetectScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 检测结果状态
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var currentDetector by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DetectorResult>>(emptyList()) }
    
    // 要检测的应用包名列表
    val targetPackages = remember {
        listOf(
            "com.topjohnwu.magisk",           // 官方Magisk
            "io.github.vvb2060.magisk",       // Magisk Delta
            "de.robv.android.xposed.installer", // Xposed Installer
            "org.meowcat.edxposed.manager",   // EdXposed Manager
            "org.lsposed.manager",            // LSPosed Manager
            "top.canyie.dreamland.manager",   // Dreamland Manager
            "me.weishu.exp",                  // 太极
            "moe.shizuku.redirectstorage",    // Shizuku
            "com.android.vending"             // 伪装包名
        )
    }
    
    // 检测器列表
    val detectors = remember {
        listOf(
            "PM命令检测" to { detail: Detail? -> PMCommand(context).run(targetPackages, detail) },
            "PM常规API" to { detail: Detail? -> PMConventionalAPIs(context).run(targetPackages, detail) },
            "PM杂项API" to { detail: Detail? -> PMSundryAPIs(context).run(targetPackages, detail) },
            "PM Intent查询" to { detail: Detail? -> PMQueryIntentActivities(context).run(targetPackages, detail) },
            "Xposed模块" to { detail: Detail? -> XposedModules(context).run(null, detail) },
            "Magisk应用" to { detail: Detail? -> MagiskApp(context).run(null, detail) },
            "文件检测" to { detail: Detail? -> FileDetection(context, false).run(targetPackages, detail) },
            "异常环境" to { detail: Detail? -> AbnormalEnvironment(context).run(null, detail) }
        )
    }
    
    LaunchedEffect(Unit) {
        // 页面加载时自动开始扫描
        if (results.isEmpty() && !isScanning) {
            scope.launch {
                isScanning = true
                results = runDetectors(detectors) { progress, name ->
                    scanProgress = progress
                    currentDetector = name
                }
                isScanning = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "应用检测",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            if (!isScanning) {
                                scope.launch {
                                    isScanning = true
                                    results = runDetectors(detectors) { progress, name ->
                                        scanProgress = progress
                                        currentDetector = name
                                    }
                                    isScanning = false
                                }
                            }
                        },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 扫描进度
            if (isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "正在扫描...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentDetector,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // 检测结果
            results.forEach { result ->
                DetectorResultCard(result)
            }
            
            // 说明信息
            if (!isScanning && results.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "检测结果说明",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = """
• 未发现: 未检测到目标应用
• 方法不可用: 当前系统不支持此检测方法
• 可疑: 检测到可疑痕迹
• 已发现: 确认检测到目标应用

注意: 检测结果仅供参考，可能存在误报或漏报。
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 检测结果卡片
 */
@Composable
fun DetectorResultCard(result: DetectorResult) {
    val (backgroundColor, iconColor, icon) = when (result.result) {
        IDetector.Result.NOT_FOUND -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF4CAF50),
            Icons.Default.CheckCircle
        )
        IDetector.Result.METHOD_UNAVAILABLE -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFFF9800),
            Icons.Default.Warning
        )
        IDetector.Result.SUSPICIOUS -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFFF5722),
            Icons.Default.Warning
        )
        IDetector.Result.FOUND -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFF44336),
            Icons.Default.Error
        )
    }
    
    val resultText = when (result.result) {
        IDetector.Result.NOT_FOUND -> "未发现"
        IDetector.Result.METHOD_UNAVAILABLE -> "方法不可用"
        IDetector.Result.SUSPICIOUS -> "可疑"
        IDetector.Result.FOUND -> "已发现"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.labelMedium,
                            color = iconColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 详细结果
            if (result.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                
                result.details.forEach { (name, res) ->
                    val detailColor = when (res) {
                        IDetector.Result.NOT_FOUND -> Color(0xFF4CAF50)
                        IDetector.Result.METHOD_UNAVAILABLE -> Color(0xFFFF9800)
                        IDetector.Result.SUSPICIOUS -> Color(0xFFFF5722)
                        IDetector.Result.FOUND -> Color(0xFFF44336)
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (res) {
                                IDetector.Result.NOT_FOUND -> "未发现"
                                IDetector.Result.METHOD_UNAVAILABLE -> "不可用"
                                IDetector.Result.SUSPICIOUS -> "可疑"
                                IDetector.Result.FOUND -> "已发现"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = detailColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 检测结果数据类
 */
data class DetectorResult(
    val name: String,
    val result: IDetector.Result,
    val details: List<Pair<String, IDetector.Result>> = emptyList()
)

/**
 * 执行所有检测器
 */
suspend fun runDetectors(
    detectors: List<Pair<String, (Detail?) -> IDetector.Result>>,
    onProgress: (Float, String) -> Unit
): List<DetectorResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DetectorResult>()
    
    detectors.forEachIndexed { index, (name, detector) ->
        onProgress((index + 1).toFloat() / detectors.size, name)
        
        try {
            val detail = mutableListOf<Pair<String, IDetector.Result>>()
            val result = detector(detail)
            results.add(DetectorResult(name, result, detail))
        } catch (e: Exception) {
            results.add(DetectorResult(name, IDetector.Result.METHOD_UNAVAILABLE, listOf("错误" to IDetector.Result.METHOD_UNAVAILABLE)))
        }
    }
    
    results
}
