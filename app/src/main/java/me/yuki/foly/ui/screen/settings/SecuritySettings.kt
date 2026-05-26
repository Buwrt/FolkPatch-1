package me.yuki.foly.ui.screen.settings

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import me.yuki.foly.APApplication
import me.yuki.foly.R
import me.yuki.foly.ui.component.SplicedColumnGroup
import me.yuki.foly.ui.component.ToggleSettingCard

@Composable
fun SecuritySettingsContent(
    snackBarHost: SnackbarHostState,
    kPatchReady: Boolean,
    flat: Boolean = false,
    highlightKey: String? = null,
) {
    val prefs = APApplication.sharedPreferences
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var biometricLogin by remember { mutableStateOf(prefs.getBoolean("biometric_login", false)) }

    val biometricManager = androidx.biometric.BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    if (!canAuthenticate) {
        // 设备不支持生物识别，显示提示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "设备不支持生物识别",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "当前设备未检测到指纹传感器或未注册生物识别信息，生物识别登录功能不可用。\n\n如需使用此功能，请在系统设置中添加指纹或面容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    SplicedColumnGroup(flat = flat, highlightKey = highlightKey) {
        item(key = "security_biometric_login", visible = canAuthenticate) {
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.Fingerprint,
                title = stringResource(id = R.string.settings_biometric_login),
                description = stringResource(id = R.string.settings_biometric_login_summary),
                checked = biometricLogin,
                onCheckedChange = { checked ->
                    if (!checked) {
                        if (activity != null) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val biometricPrompt = BiometricPrompt(activity, executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        biometricLogin = false
                                        prefs.edit().putBoolean("biometric_login", false).apply()
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                    }
                                })

                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle(context.getString(R.string.action_biometric))
                                .setSubtitle(context.getString(R.string.msg_biometric))
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()

                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            biometricLogin = false
                            prefs.edit().putBoolean("biometric_login", false).apply()
                        }
                    } else {
                        biometricLogin = true
                        prefs.edit().putBoolean("biometric_login", true).apply()
                    }
                }
            )
        }

        item(key = "security_strong_biometric", visible = biometricLogin && canAuthenticate) {
            var strongBiometric by remember { mutableStateOf(prefs.getBoolean("strong_biometric", false)) }
            ToggleSettingCard(
                flat = flat,
                icon = Icons.Filled.Shield,
                title = stringResource(id = R.string.settings_strong_biometric),
                description = stringResource(id = R.string.settings_strong_biometric_summary),
                checked = strongBiometric,
                onCheckedChange = {
                    strongBiometric = it
                    prefs.edit().putBoolean("strong_biometric", it).apply()
                }
            )
        }
    }
}
