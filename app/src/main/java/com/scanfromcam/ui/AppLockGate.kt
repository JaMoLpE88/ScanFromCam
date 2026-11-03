package com.scanfromcam.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scanfromcam.core.AppLock
import com.scanfromcam.core.Strings
import com.scanfromcam.ui.theme.Tokens

/**
 * Shows [content] only once the device credential has been confirmed. There is
 * no app PIN: the system prompt asks for biometrics or the phone's PIN/pattern.
 */
@Composable
fun AppLockGate(lock: AppLock, content: @Composable () -> Unit) {
    if (!lock.locked) {
        content()
        return
    }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val t = Strings.t
    val prompting = remember { mutableStateOf(false) }
    val autoAsked = remember { mutableStateOf(false) }
    val promptRef = remember { mutableStateOf<BiometricPrompt?>(null) }

    fun finish(ok: Boolean) {
        prompting.value = false
        promptRef.value = null
        if (ok) lock.onUnlocked()
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> finish(result.resultCode == Activity.RESULT_OK) }

    fun authenticate() {
        // A prompt created while the activity is stopped is never delivered, which
        // is what left the button dead after coming back from the background.
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (prompting.value) return
        prompting.value = true
        if (activity == null || Build.VERSION.SDK_INT < 28) {
            // Android 7-8: no BiometricPrompt credential fallback, ask the keyguard directly.
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val intent = keyguard?.createConfirmDeviceCredentialIntent(t.unlockAppTitle, null)
            // No device credential at all: nothing left to ask, the lock is meaningless.
            if (intent == null) finish(true) else credentialLauncher.launch(intent)
            return
        }
        promptRef.value = promptDeviceCredential(activity, t.unlockAppTitle) { ok -> finish(ok) }
    }

    /** Automatic prompt: once per time the app comes to the foreground. */
    fun promptIfIdle() {
        if (prompting.value || autoAsked.value) return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        autoAsked.value = true
        authenticate()
    }

    LaunchedEffect(Unit) { promptIfIdle() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> promptIfIdle()
                Lifecycle.Event.ON_STOP -> {
                    // The system drops the prompt when we leave; forget it so it can be asked again.
                    promptRef.value?.cancelAuthentication()
                    promptRef.value = null
                    prompting.value = false
                    autoAsked.value = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LockedScreen(
        onUnlock = {
            // Pressing the button always forces a new prompt.
            promptRef.value?.cancelAuthentication()
            promptRef.value = null
            prompting.value = false
            authenticate()
        },
    )
}

private fun promptDeviceCredential(
    activity: FragmentActivity,
    title: String,
    onResult: (Boolean) -> Unit,
): BiometricPrompt {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(true)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onResult(false)
        }
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply {
            if (Build.VERSION.SDK_INT >= 30) {
                setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                setDeviceCredentialAllowed(true)
            }
        }
        .build()
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
    prompt.authenticate(info)
    return prompt
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    val t = Strings.t
    Box(modifier = Modifier.fillMaxSize().background(Tokens.Surface0)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Tokens.AccentMid, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = t.lockedApp,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = t.lockedAppHint,
                color = Tokens.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(listOf(Tokens.AccentStart, Tokens.AccentMid, Tokens.AccentEnd))
                    )
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Tokens.OnAccent)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = t.unlockApp,
                    color = Tokens.OnAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Device has a PIN, pattern, password or biometrics? Without one the lock is pointless. */
fun deviceHasCredential(context: Context): Boolean {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (keyguard?.isDeviceSecure == true) return true
    return BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
}
