package dev.andikune.robotasisten.io.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.andikune.robotasisten.io.service.MyAccessibilityService
import dev.andikune.robotasisten.io.ui.components.PermissionItem
import dev.andikune.robotasisten.io.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionChecklistScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlay by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context, MyAccessibilityService::class.java)) }
    var hasBattery by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }
    var hasNotif by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotif = granted || PermissionHelper.hasNotificationPermission(context) }

    fun refreshPermissions() {
        hasOverlay = PermissionHelper.hasOverlayPermission(context)
        hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context, MyAccessibilityService::class.java)
        hasBattery = PermissionHelper.isBatteryOptimizationIgnored(context)
        hasNotif = PermissionHelper.hasNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ Checklist Izin Layanan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "RELX.iO membutuhkan izin berikut untuk menampilkan bubble, membaca layar atas permintaan Anda, dan mencoba mengisi kolom teks yang sedang fokus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionItem(
                title = "Izin Tampil di Atas Aplikasi Lain",
                description = "Wajib untuk menampilkan bubble robot mengambang di atas semua aplikasi belajar.",
                icon = Icons.Default.Layers,
                isGranted = hasOverlay,
                onRequestPermission = { PermissionHelper.openOverlaySettings(context) }
            )
            PermissionItem(
                title = "Layanan Aksesibilitas (Accessibility)",
                description = "Wajib untuk OCR layar saat diminta dan mencoba mengisi kolom teks aktif. Aplikasi tidak menekan pilihan jawaban otomatis.",
                icon = Icons.Default.Accessibility,
                isGranted = hasAccessibility,
                onRequestPermission = { PermissionHelper.openAccessibilitySettings(context) }
            )
            PermissionItem(
                title = "Abaikan Optimasi Baterai",
                description = "Mencegah sistem mematikan background service saat aplikasi belajar sedang aktif.",
                icon = Icons.Default.BatteryAlert,
                isGranted = hasBattery,
                onRequestPermission = { PermissionHelper.openBatteryOptimizationSettings(context) }
            )
            PermissionItem(
                title = "Izin Notifikasi",
                description = "Menampilkan kontrol cepat dan status robot di status bar.",
                icon = Icons.Default.Notifications,
                isGranted = hasNotif,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotif) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            Text(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Backup Aman menyimpan .TXT ke Penyimpanan internal/Relxio/Backup aman menggunakan akses scoped Android. Tidak perlu izin akses seluruh penyimpanan."
                } else {
                    "Backup Aman meminta Anda memilih folder. Android memberi akses baca/tulis hanya ke folder yang dipilih; aplikasi tidak meminta akses seluruh penyimpanan."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
