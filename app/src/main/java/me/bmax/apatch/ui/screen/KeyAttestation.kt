package me.bmax.apatch.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.vvb2060.keyattestation.attestation.Attestation
import io.github.vvb2060.keyattestation.attestation.AuthorizationList
import io.github.vvb2060.keyattestation.attestation.CertificateInfo
import io.github.vvb2060.keyattestation.attestation.KnoxAttestation
import io.github.vvb2060.keyattestation.attestation.RootOfTrust
import io.github.vvb2060.keyattestation.attestation.RootPublicKey
import io.github.vvb2060.keyattestation.repository.AttestationData
import io.github.vvb2060.keyattestation.repository.FolkAttestationHelper
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ======================== Constants ========================

private const val KEYBOX_PATH = "/data/adb/tricky_store/keybox.xml"

// ======================== Main Screen ========================

@Destination<RootGraph>
@Composable
fun KeyAttestationScreen(navigator: DestinationsNavigator) {
    val viewModel: KeyAttestationViewModel = viewModel()
    val context = LocalContext.current

    // File picker for loading certificate
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadCertificateFromUri(context.contentResolver.openInputStream(it))
        }
    }

    // File saver for saving certificate
    val fileSaverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.saveCertificateToUri(context.contentResolver.openOutputStream(it))
        }
    }

    // File picker for keybox.xml
    val keyboxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.replaceKeybox(context.contentResolver.openInputStream(it))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("密钥认证") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "加载证书")
                    }
                    IconButton(onClick = { viewModel.generateAttestation() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Status Card
            AttestationStatusCard(viewModel)

            // Action Buttons
            ActionButtonsRow(
                viewModel = viewModel,
                onSave = {
                    viewModel.certificateChain?.let {
                        fileSaverLauncher.launch("attestation_${System.currentTimeMillis()}.bin")
                    } ?: run {
                        Toast.makeText(context, "暂无数据，请先生成认证", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // KeyBox Section
            KeyBoxSection(viewModel, keyboxPickerLauncher)

            // Attestation Results
            viewModel.attestationData?.let { data ->
                Spacer(Modifier.height(4.dp))

                // Certificate Root Trust Status Header
                CertificateRootTrustHeader(data)

                // Bootloader Status
                BootloaderStatusCard(data)

                // Certificate Chain Details
                CertificateChainDetailCard(data)

                // Attestation Basic Info
                AttestationBasicInfoCard(data)

                // Full 44 Authorization Items (SW + HW side by side)
                FullAuthorizationCard(data)

                // Knox Attestation Info (if available)
                KnoxAttestationCard(data)
            }

            // Error Display
            viewModel.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "错误",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Loading Indicator
            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ======================== Status Card ========================

@Composable
private fun AttestationStatusCard(viewModel: KeyAttestationViewModel) {
    val statusColor = when {
        viewModel.attestationData != null && viewModel.error == null ->
            MaterialTheme.colorScheme.primaryContainer
        viewModel.error != null ->
            MaterialTheme.colorScheme.errorContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant
    }

    val statusIcon = when {
        viewModel.attestationData != null && viewModel.error == null -> Icons.Filled.CheckCircle
        viewModel.error != null -> Icons.Filled.Error
        else -> Icons.Outlined.Info
    }

    val statusText = when {
        viewModel.attestationData != null && viewModel.error == null -> "认证完成"
        viewModel.error != null -> "认证失败"
        else -> "等待检测"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when {
                    viewModel.attestationData != null && viewModel.error == null ->
                        MaterialTheme.colorScheme.primary
                    viewModel.error != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "密钥认证检测",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ======================== Action Buttons ========================

@Composable
private fun ActionButtonsRow(
    viewModel: KeyAttestationViewModel,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.generateAttestation() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("生成认证")
        }

        OutlinedButton(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存证书")
        }
    }
}

// ======================== KeyBox Section ========================

@Composable
private fun KeyBoxSection(
    viewModel: KeyAttestationViewModel,
    keyboxPickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    var showKeyboxContent by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "KeyBox 管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            // KeyBox Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "当前状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        viewModel.keyboxExists == true -> "已存在 (${viewModel.keyboxSize})"
                        viewModel.keyboxExists == false -> "不存在"
                        else -> "检测中..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        viewModel.keyboxExists == true -> MaterialTheme.colorScheme.primary
                        viewModel.keyboxExists == false -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            AttestationItem("路径", KEYBOX_PATH)

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { keyboxPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("替换 KeyBox", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.checkKeybox()
                        showKeyboxContent = !showKeyboxContent
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看 KeyBox", fontSize = 13.sp)
                }
            }

            // KeyBox Content Display
            if (showKeyboxContent && viewModel.keyboxContent != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "KeyBox 内容:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        viewModel.keyboxContent ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            } else if (showKeyboxContent && viewModel.keyboxContent == null && viewModel.keyboxExists == false) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "KeyBox 文件不存在",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // KeyBox operation message
            viewModel.keyboxMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.startsWith("成功")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ======================== Certificate Root Trust Header ========================

@Composable
private fun CertificateRootTrustHeader(data: AttestationData) {
    val status = data.getStatus()
    val statusName = when (status) {
        RootPublicKey.Status.GOOGLE -> "Google"
        RootPublicKey.Status.GOOGLE_RKP -> "Google (RKP)"
        RootPublicKey.Status.AOSP -> "AOSP"
        RootPublicKey.Status.KNOX -> "Samsung Knox"
        RootPublicKey.Status.OEM -> "OEM"
        RootPublicKey.Status.UNKNOWN -> "未知"
        RootPublicKey.Status.FAILED -> "验证失败"
        RootPublicKey.Status.NULL -> "无数据"
    }

    val statusColor = when (status) {
        RootPublicKey.Status.GOOGLE, RootPublicKey.Status.GOOGLE_RKP,
        RootPublicKey.Status.AOSP, RootPublicKey.Status.KNOX,
        RootPublicKey.Status.OEM -> MaterialTheme.colorScheme.primaryContainer
        RootPublicKey.Status.FAILED -> MaterialTheme.colorScheme.errorContainer
        RootPublicKey.Status.UNKNOWN -> MaterialTheme.colorScheme.tertiaryContainer
        RootPublicKey.Status.NULL -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusIconColor = when (status) {
        RootPublicKey.Status.GOOGLE, RootPublicKey.Status.GOOGLE_RKP,
        RootPublicKey.Status.AOSP, RootPublicKey.Status.KNOX,
        RootPublicKey.Status.OEM -> MaterialTheme.colorScheme.primary
        RootPublicKey.Status.FAILED -> MaterialTheme.colorScheme.error
        RootPublicKey.Status.UNKNOWN -> MaterialTheme.colorScheme.tertiary
        RootPublicKey.Status.NULL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    status == RootPublicKey.Status.FAILED -> Icons.Filled.Warning
                    status == RootPublicKey.Status.NULL -> Icons.Outlined.Info
                    else -> Icons.Filled.Security
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = statusIconColor
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "证书根信任状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = statusIconColor
                )
            }
        }
    }
}

// ======================== Bootloader Status ========================

@Composable
private fun BootloaderStatusCard(data: AttestationData) {
    val rot = data.getRootOfTrust()
    if (rot == null) return

    val bootState = when (rot.getVerifiedBootState()) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> "已验证"
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> "自签名"
        RootOfTrust.KM_VERIFIED_BOOT_UNVERIFIED -> "未验证"
        RootOfTrust.KM_VERIFIED_BOOT_FAILED -> "验证失败"
        else -> "未知"
    }

    val bootStateColor = when (rot.getVerifiedBootState()) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> MaterialTheme.colorScheme.primary
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> MaterialTheme.colorScheme.tertiary
        RootOfTrust.KM_VERIFIED_BOOT_UNVERIFIED,
        RootOfTrust.KM_VERIFIED_BOOT_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val lockedText = if (rot.isDeviceLocked) "已锁定" else "已解锁"
    val lockedColor = if (rot.isDeviceLocked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "引导加载程序状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "引导状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    bootState,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = bootStateColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Bootloader",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    lockedText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = lockedColor
                )
            }

            AttestationItem("验证启动密钥", formatByteArray(rot.getVerifiedBootKey()))

            rot.getVerifiedBootHash()?.let { hash ->
                AttestationItem("验证启动哈希", formatByteArray(hash))
            }
        }
    }
}

// ======================== Certificate Chain Detail ========================

@Composable
private fun CertificateChainDetailCard(data: AttestationData) {
    val certInfos = data.getCertificateInfos()
    if (certInfos.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "证书链详情 (${certInfos.size} 个证书)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            certInfos.forEachIndexed { index, certInfo ->
                val cert = certInfo.getCert()
                val issuerName = issuerStatusToChinese(certInfo.getIssuer())
                val statusText = certStatusToChinese(certInfo.getStatus())
                val statusColor = when (certInfo.getStatus()) {
                    CertificateInfo.CERT_NORMAL -> MaterialTheme.colorScheme.primary
                    CertificateInfo.CERT_EXPIRED -> MaterialTheme.colorScheme.error
                    CertificateInfo.CERT_REVOKED -> MaterialTheme.colorScheme.error
                    CertificateInfo.CERT_SIGN -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "证书 #${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                statusText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    InfoRow("签发者", issuerName)
                    InfoRow("使用者", cert.subjectX500Principal.name)

                    val notBeforeText = remember(cert) {
                        try { formatDate(cert.notBefore) } catch (_: Exception) { "不可用" }
                    }
                    val notAfterText = remember(cert) {
                        try { formatDate(cert.notAfter) } catch (_: Exception) { "不可用" }
                    }
                    InfoRow("生效时间", notBeforeText)
                    InfoRow("过期时间", notAfterText)
                }

                if (index < certInfos.size - 1) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ======================== Attestation Basic Info ========================

@Composable
private fun AttestationBasicInfoCard(data: AttestationData) {
    val attestation = data.getAttestation()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "认证基本信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            AttestationItem("认证版本", Attestation.attestationVersionToString(attestation.getAttestationVersion()))
            AttestationItem("认证安全等级", securityLevelToChinese(attestation.getAttestationSecurityLevel()))
            AttestationItem("Keymaster 版本", Attestation.keymasterVersionToString(attestation.getKeymasterVersion()))
            AttestationItem("Keymaster 安全等级", securityLevelToChinese(attestation.getKeymasterSecurityLevel()))

            attestation.getAttestationChallenge()?.let { challenge ->
                val challengeStr = try {
                    String(challenge)
                } catch (_: Exception) { "" }
                val displayChallenge = if (challengeStr.isNotEmpty() && challengeStr.toByteArray().contentEquals(challenge)) {
                    challengeStr
                } else {
                    android.util.Base64.encodeToString(challenge, android.util.Base64.NO_WRAP)
                }
                AttestationItem("质询 (Challenge)", displayChallenge)
            }

            attestation.getUniqueId()?.let { uid ->
                AttestationItem("唯一 ID", formatByteArray(uid))
            }
        }
    }
}

// ======================== Full 44 Authorization Items ========================

@Composable
private fun FullAuthorizationCard(data: AttestationData) {
    val attestation = data.getAttestation()
    val sw = attestation.getSoftwareEnforced()
    val hw = attestation.getTeeEnforced()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "授权列表 (共 44 项)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "HW = 硬件强制 (TEE/StrongBox)  |  SW = 软件强制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "项目",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "SW",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "HW",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // 1. Purpose (用途)
            AuthDualItem("1. 用途 (Purpose)",
                sw?.getPurposes()?.let { AuthorizationList.purposesToString(it) },
                hw?.getPurposes()?.let { AuthorizationList.purposesToString(it) }
            )

            // 2. Algorithm (算法)
            AuthDualItem("2. 算法 (Algorithm)",
                sw?.getAlgorithm()?.let { AuthorizationList.algorithmToString(it) },
                hw?.getAlgorithm()?.let { AuthorizationList.algorithmToString(it) }
            )

            // 3. Key Size (密钥大小)
            AuthDualItem("3. 密钥大小 (Key Size)",
                sw?.getKeySize()?.let { "$it bits" },
                hw?.getKeySize()?.let { "$it bits" }
            )

            // 4. Digests (摘要)
            AuthDualItem("4. 摘要 (Digests)",
                sw?.getDigests()?.let { AuthorizationList.digestsToString(it) },
                hw?.getDigests()?.let { AuthorizationList.digestsToString(it) }
            )

            // 5. Padding Modes (填充模式)
            AuthDualItem("5. 填充模式 (Padding)",
                sw?.getPaddingModes()?.let { AuthorizationList.paddingModesToString(it) },
                hw?.getPaddingModes()?.let { AuthorizationList.paddingModesToString(it) }
            )

            // 6. EC Curve (椭圆曲线)
            AuthDualItem("6. 椭圆曲线 (EC Curve)",
                sw?.getEcCurve()?.let { AuthorizationList.ecCurveAsString(it) },
                hw?.getEcCurve()?.let { AuthorizationList.ecCurveAsString(it) }
            )

            // 7. RSA Public Exponent (RSA公钥指数)
            AuthDualItem("7. RSA 公钥指数",
                sw?.getRsaPublicExponent()?.toString(),
                hw?.getRsaPublicExponent()?.toString()
            )

            // 8. MGF Digests (MGF摘要)
            AuthDualItem("8. MGF 摘要",
                sw?.getMgfDigests()?.let { AuthorizationList.digestsToString(it) },
                hw?.getMgfDigests()?.let { AuthorizationList.digestsToString(it) }
            )

            // 9. Rollback Resistance (抗回滚 - 旧版)
            AuthDualBoolItem("9. 抗回滚 (Rollback Resistance)",
                sw?.getRollbackResistance(),
                hw?.getRollbackResistance()
            )

            // 10. Early Boot Only (仅早期启动)
            AuthDualBoolItem("10. 仅早期启动 (Early Boot Only)",
                sw?.getEarlyBootOnly(),
                hw?.getEarlyBootOnly()
            )

            // 11. Active DateTime (激活时间)
            AuthDualItem("11. 激活时间",
                sw?.getActiveDateTime()?.let { AuthorizationList.formatDate(it) },
                hw?.getActiveDateTime()?.let { AuthorizationList.formatDate(it) }
            )

            // 12. Origination Expire DateTime (生成失效时间)
            AuthDualItem("12. 生成失效时间",
                sw?.getOriginationExpireDateTime()?.let { AuthorizationList.formatDate(it) },
                hw?.getOriginationExpireDateTime()?.let { AuthorizationList.formatDate(it) }
            )

            // 13. Usage Expire DateTime (使用失效时间)
            AuthDualItem("13. 使用失效时间",
                sw?.getUsageExpireDateTime()?.let { AuthorizationList.formatDate(it) },
                hw?.getUsageExpireDateTime()?.let { AuthorizationList.formatDate(it) }
            )

            // 14. Usage Count Limit (使用次数限制)
            AuthDualItem("14. 使用次数限制",
                sw?.getUsageCountLimit()?.toString(),
                hw?.getUsageCountLimit()?.toString()
            )

            // 15. No Auth Required (不需要身份验证)
            AuthDualBoolItem("15. 不需要身份验证",
                sw?.getNoAuthRequired(),
                hw?.getNoAuthRequired()
            )

            // 16. User Auth Type (身份验证类型)
            AuthDualItem("16. 身份验证类型",
                sw?.getUserAuthType()?.let { AuthorizationList.userAuthTypeToString(it) },
                hw?.getUserAuthType()?.let { AuthorizationList.userAuthTypeToString(it) }
            )

            // 17. Auth Timeout (身份验证超时)
            AuthDualItem("17. 身份验证超时",
                sw?.getAuthTimeout()?.let { "$it 秒" },
                hw?.getAuthTimeout()?.let { "$it 秒" }
            )

            // 18. Allow While On Body (贴身时允许)
            AuthDualBoolItem("18. 贴身时允许",
                sw?.getAllowWhileOnBody(),
                hw?.getAllowWhileOnBody()
            )

            // 19. Trusted User Presence Required (需要用户存在证明)
            AuthDualBoolItem("19. 需要用户存在证明",
                sw?.getTrustedUserPresenceReq(),
                hw?.getTrustedUserPresenceReq()
            )

            // 20. Trusted Confirmation Required (需要确认证明)
            AuthDualBoolItem("20. 需要确认证明",
                sw?.getTrustedConfirmationReq(),
                hw?.getTrustedConfirmationReq()
            )

            // 21. Unlocked Device Required (需要已解锁设备)
            AuthDualBoolItem("21. 需要已解锁设备",
                sw?.getUnlockedDeviceReq(),
                hw?.getUnlockedDeviceReq()
            )

            // 22. All Applications (全部应用)
            AuthDualBoolItem("22. 全部应用",
                sw?.getAllApplications(),
                hw?.getAllApplications()
            )

            // 23. Application ID (应用ID)
            AuthDualItem("23. 应用 ID",
                sw?.getApplicationId(),
                hw?.getApplicationId()
            )

            // 24. Creation DateTime (创建时间)
            AuthDualItem("24. 创建时间",
                sw?.getCreationDateTime()?.let { AuthorizationList.formatDate(it) },
                hw?.getCreationDateTime()?.let { AuthorizationList.formatDate(it) }
            )

            // 25. Origin (来源)
            AuthDualItem("25. 来源 (Origin)",
                sw?.getOrigin()?.let { originToChinese(it) },
                hw?.getOrigin()?.let { originToChinese(it) }
            )

            // 26. Rollback Resistant (抗回滚 - 新版)
            AuthDualBoolItem("26. 抗回滚 (Rollback Resistant)",
                sw?.getRollbackResistant(),
                hw?.getRollbackResistant()
            )

            // 27. Root of Trust (信任根)
            AuthDualItem("27. 信任根 (Root of Trust)",
                sw?.getRootOfTrust()?.let { rot ->
                    "${bootStateToChinese(rot.getVerifiedBootState())} / ${if (rot.isDeviceLocked) "已锁定" else "已解锁"}"
                },
                hw?.getRootOfTrust()?.let { rot ->
                    "${bootStateToChinese(rot.getVerifiedBootState())} / ${if (rot.isDeviceLocked) "已锁定" else "已解锁"}"
                }
            )

            // 28. OS Version (系统版本)
            AuthDualItem("28. 系统版本",
                sw?.getOsVersion()?.let { osVersionToString(it) },
                hw?.getOsVersion()?.let { osVersionToString(it) }
            )

            // 29. OS Patch Level (系统补丁版本)
            AuthDualItem("29. 系统补丁版本",
                sw?.getOsPatchLevel()?.let { patchLevelToString(it) },
                hw?.getOsPatchLevel()?.let { patchLevelToString(it) }
            )

            // 30. Attestation Application ID (认证应用ID)
            AuthDualItem("30. 认证应用 ID",
                sw?.getAttestationApplicationId()?.toString(),
                hw?.getAttestationApplicationId()?.toString()
            )

            // 31. Brand (品牌)
            AuthDualItem("31. 品牌 (Brand)",
                sw?.getBrand(),
                hw?.getBrand()
            )

            // 32. Device (设备)
            AuthDualItem("32. 设备 (Device)",
                sw?.getDevice(),
                hw?.getDevice()
            )

            // 33. Product (产品)
            AuthDualItem("33. 产品 (Product)",
                sw?.getProduct(),
                hw?.getProduct()
            )

            // 34. Serial Number (序列号)
            AuthDualItem("34. 序列号",
                sw?.getSerialNumber(),
                hw?.getSerialNumber()
            )

            // 35. IMEI
            AuthDualItem("35. IMEI",
                sw?.getImei(),
                hw?.getImei()
            )

            // 36. Second IMEI (第二IMEI)
            AuthDualItem("36. 第二 IMEI",
                sw?.getSecondImei(),
                hw?.getSecondImei()
            )

            // 37. MEID
            AuthDualItem("37. MEID",
                sw?.getMeid(),
                hw?.getMeid()
            )

            // 38. Manufacturer (制造商)
            AuthDualItem("38. 制造商 (Manufacturer)",
                sw?.getManufacturer(),
                hw?.getManufacturer()
            )

            // 39. Model (型号)
            AuthDualItem("39. 型号 (Model)",
                sw?.getModel(),
                hw?.getModel()
            )

            // 40. Vendor Patch Level (Vendor补丁版本)
            AuthDualItem("40. Vendor 补丁版本",
                sw?.getVendorPatchLevel()?.let { patchLevelToString(it) },
                hw?.getVendorPatchLevel()?.let { patchLevelToString(it) }
            )

            // 41. Boot Patch Level (Boot补丁版本)
            AuthDualItem("41. Boot 补丁版本",
                sw?.getBootPatchLevel()?.let { patchLevelToString(it) },
                hw?.getBootPatchLevel()?.let { patchLevelToString(it) }
            )

            // 42. Device Unique Attestation (设备唯一认证)
            AuthDualBoolItem("42. 设备唯一认证",
                sw?.getDeviceUniqueAttestation(),
                hw?.getDeviceUniqueAttestation()
            )

            // 43. Identity Credential Key (身份凭据密钥)
            AuthDualBoolItem("43. 身份凭据密钥",
                sw?.getIdentityCredentialKey(),
                hw?.getIdentityCredentialKey()
            )

            // 44. Module Hash (模块摘要)
            AuthDualItem("44. 模块摘要 (Module Hash)",
                sw?.getModuleHash()?.let { formatByteArrayFull(it) },
                hw?.getModuleHash()?.let { formatByteArrayFull(it) }
            )
        }
    }
}

// ======================== Knox Attestation Card ========================

@Composable
private fun KnoxAttestationCard(data: AttestationData) {
    val attestation = data.getAttestation()
    if (attestation !is KnoxAttestation) return

    val knoxIntegrity = attestation.getKnoxIntegrity()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Samsung Knox 认证信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()

            attestation.getKnoxChallenge()?.let {
                AttestationItem("Knox 质询", it)
            }

            attestation.getIdAttest()?.let {
                AttestationItem("ID 认证", it)
            }

            attestation.getRecordHash()?.let {
                AttestationItem("认证记录哈希", formatByteArrayFull(it))
            }

            // Knox Integrity Status
            if (knoxIntegrity != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "完整性状态",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                // TrustBoot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("信任启动 (TrustBoot)", style = MaterialTheme.typography.bodySmall)
                    Text(
                        integrityStatusToChinese(knoxIntegrity.toString().substringAfter("TrustBoot: ").substringBefore("\n").trim()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Parse integrity status from toString
                val integrityStr = knoxIntegrity.toString()
                val trustBoot = extractIntegrityValue(integrityStr, "TrustBoot:")
                val warranty = extractIntegrityValue(integrityStr, "Warranty:")
                val icd = extractIntegrityValue(integrityStr, "ICD:")
                val kernelStatus = extractIntegrityValue(integrityStr, "Kernel Status:")
                val systemStatus = extractIntegrityValue(integrityStr, "System Status:")

                AttestationItem("信任启动", integrityStatusToChinese(trustBoot))
                AttestationItem("保修状态 (Warranty)", integrityStatusToChinese(warranty))
                AttestationItem("ICD", integrityStatusToChinese(icd))
                AttestationItem("内核状态", integrityStatusToChinese(kernelStatus))
                AttestationItem("系统状态", integrityStatusToChinese(systemStatus))
            }
        }
    }
}

// ======================== Dual Authorization Item Composables ========================

@Composable
private fun AuthDualItem(label: String, swValue: String?, hwValue: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            swValue ?: "-",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            hwValue ?: "-",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AuthDualBoolItem(label: String, swValue: Boolean?, hwValue: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            boolToChinese(swValue),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = boolToColor(swValue)
        )
        Text(
            boolToChinese(hwValue),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = boolToColor(hwValue)
        )
    }
}

// ======================== Common Composables ========================

@Composable
private fun AttestationItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ======================== Helper Functions ========================

private fun formatByteArray(bytes: ByteArray?): String {
    if (bytes == null) return "不可用"
    return bytes.take(8).joinToString("") { "%02X".format(it) } +
            if (bytes.size > 8) "..." else ""
}

private fun formatByteArrayFull(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02X".format(it) }
}

private fun bootStateToChinese(state: Int): String {
    return when (state) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> "已验证"
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> "自签名"
        RootOfTrust.KM_VERIFIED_BOOT_UNVERIFIED -> "未验证"
        RootOfTrust.KM_VERIFIED_BOOT_FAILED -> "验证失败"
        else -> "未知 ($state)"
    }
}

private fun securityLevelToChinese(level: Int): String {
    return when (level) {
        Attestation.KM_SECURITY_LEVEL_SOFTWARE -> "软件 (Software)"
        Attestation.KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "可信环境 (TEE)"
        Attestation.KM_SECURITY_LEVEL_STRONG_BOX -> "StrongBox"
        else -> "未知 ($level)"
    }
}

private fun originToChinese(origin: Int): String {
    return when (origin) {
        AuthorizationList.KM_ORIGIN_GENERATED -> "生成 (Generated)"
        AuthorizationList.KM_ORIGIN_DERIVED -> "派生 (Derived)"
        AuthorizationList.KM_ORIGIN_IMPORTED -> "导入 (Imported)"
        AuthorizationList.KM_ORIGIN_UNKNOWN -> "未知 (KM0)"
        AuthorizationList.KM_ORIGIN_SECURELY_IMPORTED -> "安全导入 (Securely Imported)"
        else -> "未知 ($origin)"
    }
}

private fun issuerStatusToChinese(status: RootPublicKey.Status): String {
    return when (status) {
        RootPublicKey.Status.GOOGLE -> "Google"
        RootPublicKey.Status.GOOGLE_RKP -> "Google (RKP)"
        RootPublicKey.Status.AOSP -> "AOSP"
        RootPublicKey.Status.KNOX -> "Samsung Knox"
        RootPublicKey.Status.OEM -> "OEM"
        RootPublicKey.Status.UNKNOWN -> "未知"
        RootPublicKey.Status.FAILED -> "验证失败"
        RootPublicKey.Status.NULL -> "无"
    }
}

private fun certStatusToChinese(status: Int): String {
    return when (status) {
        CertificateInfo.CERT_UNKNOWN -> "未知"
        CertificateInfo.CERT_SIGN -> "签名验证中"
        CertificateInfo.CERT_REVOKED -> "已吊销"
        CertificateInfo.CERT_EXPIRED -> "已过期"
        CertificateInfo.CERT_NORMAL -> "正常"
        else -> "未知 ($status)"
    }
}

private fun integrityStatusToChinese(status: String): String {
    return when {
        status.contains("Normal", ignoreCase = true) -> "正常"
        status.contains("Abnormal", ignoreCase = true) -> "异常"
        status.contains("Not support", ignoreCase = true) -> "不支持"
        status.contains("Not performed", ignoreCase = true) -> "未执行"
        else -> status
    }
}

private fun extractIntegrityValue(fullStr: String, key: String): String {
    val idx = fullStr.indexOf(key)
    if (idx < 0) return "未知"
    val start = idx + key.length
    val end = fullStr.indexOf('\n', start)
    return if (end < 0) fullStr.substring(start).trim()
    else fullStr.substring(start, end).trim()
}

private fun boolToChinese(value: Boolean?): String {
    return when (value) {
        true -> "是"
        false -> "否"
        null -> "-"
    }
}

@Composable
private fun boolToColor(value: Boolean?): Color {
    return when (value) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatDate(date: Date): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}

private fun osVersionToString(version: Int): String {
    val major = (version shr 24) and 0xFF
    val minor = (version shr 16) and 0xFF
    val patch = (version shr 8) and 0xFF
    return "$major.$minor.$patch"
}

private fun patchLevelToString(patchLevel: Int): String {
    val year = patchLevel shr 4
    val month = patchLevel and 0x0F
    return "${year}-${String.format("%02d", month)}"
}

// ======================== ViewModel ========================

class KeyAttestationViewModel : ViewModel() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    var attestationData by mutableStateOf<AttestationData?>(null)
        private set

    var certificateChain by mutableStateOf<ByteArray?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // KeyBox state
    var keyboxExists by mutableStateOf<Boolean?>(null)
        private set

    var keyboxSize by mutableStateOf("")
        private set

    var keyboxContent by mutableStateOf<String?>(null)
        private set

    var keyboxMessage by mutableStateOf<String?>(null)
        private set

    init {
        checkKeybox()
    }

    fun generateAttestation() {
        isLoading = true
        error = null

        executor.execute {
            try {
                val result = FolkAttestationHelper.generateAttestation()
                attestationData = result
                certificateChain = result.getCertificateChainEncoded()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "未知错误"
                attestationData = null
                certificateChain = null
            } finally {
                isLoading = false
            }
        }
    }

    fun loadCertificateFromUri(inputStream: InputStream?) {
        if (inputStream == null) {
            error = "无法打开文件"
            return
        }

        isLoading = true
        error = null

        executor.execute {
            try {
                val bytes = inputStream.readBytes()
                inputStream.close()

                val result = FolkAttestationHelper.loadAttestationData(bytes)
                attestationData = result
                certificateChain = bytes
                error = null
            } catch (e: Exception) {
                error = e.message ?: "加载证书失败"
                attestationData = null
                certificateChain = null
            } finally {
                isLoading = false
            }
        }
    }

    fun saveCertificateToUri(outputStream: java.io.OutputStream?) {
        if (outputStream == null || certificateChain == null) {
            return
        }

        executor.execute {
            try {
                outputStream.write(certificateChain)
                outputStream.close()
            } catch (e: Exception) {
                error = "保存失败: ${e.message}"
            }
        }
    }

    fun checkKeybox() {
        executor.execute {
            try {
                val file = java.io.File(KEYBOX_PATH)
                if (file.exists()) {
                    keyboxExists = true
                    keyboxSize = formatFileSize(file.length())
                    keyboxContent = try { file.readText() } catch (_: Exception) { null }
                } else {
                    keyboxExists = false
                    keyboxSize = ""
                    keyboxContent = null
                }
            } catch (e: Exception) {
                keyboxExists = false
                keyboxSize = ""
                keyboxContent = null
            }
        }
    }

    fun replaceKeybox(inputStream: InputStream?) {
        if (inputStream == null) {
            keyboxMessage = "无法打开文件"
            return
        }

        executor.execute {
            try {
                val content = inputStream.readBytes()
                inputStream.close()

                // Try writing via root shell first, then Shizuku
                var success = false
                var errorMsg: String? = null

                // Method 1: Direct file write (requires root or appropriate permissions)
                try {
                    val targetFile = java.io.File(KEYBOX_PATH)
                    val parentDir = targetFile.parentFile
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs()
                    }
                    targetFile.writeBytes(content)
                    success = true
                } catch (e: Exception) {
                    errorMsg = e.message
                }

                // Method 2: Try via Runtime exec (su)
                if (!success) {
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p /data/adb/tricky_store"))
                        process.waitFor()
                        val process2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $KEYBOX_PATH"))
                        process2.outputStream.write(content)
                        process2.outputStream.close()
                        val exitCode = process2.waitFor()
                        if (exitCode == 0) {
                            success = true
                        } else {
                            errorMsg = "Root 命令执行失败 (exit code: $exitCode)"
                        }
                    } catch (e: Exception) {
                        errorMsg = "Root 执行失败: ${e.message}"
                    }
                }

                if (success) {
                    keyboxMessage = "成功: KeyBox 已替换 (${formatFileSize(content.size.toLong())})"
                    checkKeybox()
                } else {
                    keyboxMessage = "失败: $errorMsg"
                }
            } catch (e: Exception) {
                keyboxMessage = "失败: ${e.message}"
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }

    override fun onCleared() {
        super.onCleared()
        executor.shutdown()
    }
}
