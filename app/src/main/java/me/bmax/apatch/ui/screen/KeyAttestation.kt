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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.vvb2060.keyattestation.attestation.Attestation
import io.github.vvb2060.keyattestation.attestation.AuthorizationList
import io.github.vvb2060.keyattestation.attestation.RootOfTrust
import io.github.vvb2060.keyattestation.repository.AttestationData
import io.github.vvb2060.keyattestation.repository.FolkAttestationHelper
import me.bmax.apatch.R
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.saveCertificateToUri(context.contentResolver.openOutputStream(it))
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.key_attestation)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Load")
                    }
                    IconButton(onClick = { viewModel.generateAttestation() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
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
                    Text(stringResource(R.string.ka_generate))
                }
                
                OutlinedButton(
                    onClick = { 
                        viewModel.certificateChain?.let {
                            fileSaverLauncher.launch("attestation_${System.currentTimeMillis()}.bin")
                        } ?: run {
                            Toast.makeText(context, context.getString(R.string.ka_no_data), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ka_save))
                }
            }
            
            // Attestation Results
            viewModel.attestationData?.let { data ->
                AttestationResultCard(data)
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
                                stringResource(R.string.ka_error),
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

@Composable
fun AttestationStatusCard(viewModel: KeyAttestationViewModel) {
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
        viewModel.attestationData != null && viewModel.error == null -> 
            stringResource(R.string.ka_status_verified)
        viewModel.error != null -> stringResource(R.string.ka_status_error)
        else -> stringResource(R.string.ka_status_ready)
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
                    stringResource(R.string.key_attestation),
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

@Composable
fun AttestationResultCard(data: AttestationData) {
    val attestation = data.getAttestation()
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Basic Info Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ka_basic_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                AttestationItem(stringResource(R.string.ka_attest_version), 
                    Attestation.attestationVersionToString(attestation.getAttestationVersion()))
                AttestationItem(stringResource(R.string.ka_attest_security), 
                    Attestation.securityLevelToString(attestation.getAttestationSecurityLevel()))
                AttestationItem(stringResource(R.string.ka_km_version), 
                    Attestation.keymasterVersionToString(attestation.getKeymasterVersion()))
                AttestationItem(stringResource(R.string.ka_km_security), 
                    Attestation.securityLevelToString(attestation.getKeymasterSecurityLevel()))
            }
        }
        
        // Root of Trust Card
        attestation.getRootOfTrust()?.let { rot ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.ka_root_of_trust),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    
                    AttestationItem(stringResource(R.string.ka_verified_boot_key), 
                        formatByteArray(rot.getVerifiedBootKey()))
                    AttestationItem(stringResource(R.string.ka_device_locked), 
                        if (rot.isDeviceLocked()) stringResource(R.string.ka_yes) else stringResource(R.string.ka_no))
                    AttestationItem(stringResource(R.string.ka_boot_state), 
                        bootStateToString(rot.getVerifiedBootState()))
                }
            }
        }
        
        // Software Enforced Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ka_sw_enforced),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                AuthorizationListItems(attestation.getSoftwareEnforced())
            }
        }
        
        // TEE Enforced Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ka_tee_enforced),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                AuthorizationListItems(attestation.getTeeEnforced())
            }
        }
        
        // Certificate Chain Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ka_cert_chain),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                data.getCertificateInfos().forEachIndexed { index, certInfo ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            "${stringResource(R.string.ka_certificate)} #${index + 1}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "${stringResource(R.string.ka_issuer)}: ${certInfo.getIssuer().name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${stringResource(R.string.ka_subject)}: ${certInfo.getCert().subjectX500Principal.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttestationItem(label: String, value: String) {
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
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AuthorizationListItems(list: AuthorizationList?) {
    list?.let { authList ->
        if (authList.getPurposes() != null) {
            AttestationItem(stringResource(R.string.ka_purposes), authList.getPurposes().toString())
        }
        if (authList.getAlgorithm() != null) {
            AttestationItem(stringResource(R.string.ka_algorithm), 
                AuthorizationList.algorithmToString(authList.getAlgorithm()))
        }
        if (authList.getKeySize() != null) {
            AttestationItem(stringResource(R.string.ka_key_size), "${authList.getKeySize()} bits")
        }
        if (authList.getDigests() != null) {
            AttestationItem(stringResource(R.string.ka_digests), AuthorizationList.digestsToString(authList.getDigests()))
        }
        if (authList.getPaddingModes() != null) {
            AttestationItem(stringResource(R.string.ka_padding), AuthorizationList.paddingModesToString(authList.getPaddingModes()))
        }
        if (authList.getOrigin() != null) {
            AttestationItem(stringResource(R.string.ka_origin), AuthorizationList.originToString(authList.getOrigin()))
        }
        if (authList.getCreationDateTime() != null) {
            AttestationItem(stringResource(R.string.ka_creation_time), 
                authList.getCreationDateTime().toString())
        }
    } ?: run {
        Text(
            stringResource(R.string.ka_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helper functions
private fun formatByteArray(bytes: ByteArray?): String {
    if (bytes == null) return "不可用"
    return bytes.take(8).joinToString("") { "%02X".format(it) } + 
        if (bytes.size > 8) "..." else ""
}

private fun bootStateToString(state: Int): String {
    return when (state) {
        RootOfTrust.KM_VERIFIED_BOOT_VERIFIED -> "已验证"
        RootOfTrust.KM_VERIFIED_BOOT_SELF_SIGNED -> "自签名"
        RootOfTrust.KM_VERIFIED_BOOT_UNVERIFIED -> "未验证"
        RootOfTrust.KM_VERIFIED_BOOT_FAILED -> "验证失败"
        else -> "未知 ($state)"
    }
}

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
    
    override fun onCleared() {
        super.onCleared()
        executor.shutdown()
    }
}
