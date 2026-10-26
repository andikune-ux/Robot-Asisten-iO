package dev.andikune.robotasisten.io.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kebijakan Privasi & Etika") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "1. Privasi Data Pengguna",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RELX.iO menyimpan API key terenkripsi melalui Android Keystore. Pembacaan layar hanya dilakukan setelah pengguna memilih aksi OCR/analisa. Jika mode Cloud dipilih, teks soal yang terbaca dikirim ke provider AI yang dikonfigurasi untuk diproses sesuai kebijakan provider tersebut; pilih mode Library atau Offline untuk pemrosesan lokal. Aplikasi tidak melakukan perekaman layar terus-menerus.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "2. Penggunaan Layanan Aksesibilitas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Layanan Aksesibilitas dipakai setelah pengguna memicu OCR/analisa, untuk membaca teks layar dan mencoba mengisi kolom teks yang sedang fokus setelah pengguna memilih tindakan isi. Layanan tidak berjalan sebagai perekam layar terus-menerus; hindari menjalankan analisa pada halaman yang berisi data sensitif.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "3. Pedoman Etika Belajar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Aplikasi ini dirancang sebagai sarana bimbingan belajar dan pemahaman materi secara mandiri. Dilarang keras menyalahgunakan aplikasi selama ujian tertutup, UTS, UAS, atau tes resmi lainnya.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
