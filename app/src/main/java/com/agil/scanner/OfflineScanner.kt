package com.agil.scanner

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineScannerScreen(onBack: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler { onBack() }

    var fileLabel by remember { mutableStateOf("") }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(options) }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdf = scanResult?.pdf
            if (pdf?.uri != null) {
                try {
                    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                        ?: context.filesDir
                    if (!outputDir.exists()) outputDir.mkdirs()

                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val label = fileLabel.trim()
                        .uppercase()
                        .replace(Regex("[^A-Z0-9_-]"), "_")
                        .trim('_')
                    val name = if (label.isBlank()) {
                        "OFFLINE_$stamp.pdf"
                    } else {
                        "OFFLINE_${label}_$stamp.pdf"
                    }
                    val output = File(outputDir, name)

                    context.contentResolver.openInputStream(pdf.uri).use { input ->
                        FileOutputStream(output).use { out ->
                            requireNotNull(input) { "Não foi possível abrir o PDF." }
                            input.copyTo(out)
                        }
                    }

                    savedFile = output
                    pageCount = pdf.pageCount
                    Toast.makeText(
                        context,
                        "Arquivo salvo no aparelho.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Erro ao salvar: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun startOfflineScan() {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    context,
                    "Não foi possível abrir o scanner: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack) { Text("← Voltar") }
        }

        BrandHeader("Digitalização offline")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "DIGITALIZAÇÃO OFFLINE",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Digitalize folhas ou escolha fotos da galeria. O PDF fica salvo somente no aparelho e não é enviado para a nuvem.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = fileLabel,
                    onValueChange = { fileLabel = it },
                    label = { Text("Nome do arquivo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { startOfflineScan() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF101722),
                        contentColor = Color.White
                    )
                ) {
                    Text("DIGITALIZAR / ESCOLHER FOTO")
                }
            }
        }

        savedFile?.let { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("✓ Arquivo salvo", fontWeight = FontWeight.Bold)
                    Text("${file.name}\n$pageCount página(s)")

                    Button(
                        onClick = {
                            shareSinglePdf(
                                context = context,
                                file = file,
                                message = file.nameWithoutExtension
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("COMPARTILHAR ARQUIVO")
                    }
                }
            }
        }

        Text(
            "Os arquivos offline também aparecem em Arquivos Salvos e podem ser editados posteriormente.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun OfflineHomeScreen(
    onScan: () -> Unit,
    onFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandHeader("Modo offline")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6))
        ) {
            Text(
                "Sem conexão com o servidor. Somente a digitalização e os arquivos salvos estão disponíveis.",
                modifier = Modifier.padding(16.dp)
            )
        }

        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF101722),
                contentColor = Color.White
            )
        ) {
            Text("DIGITALIZAR ARQUIVO OFFLINE")
        }

        OutlinedButton(
            onClick = onFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ARQUIVOS SALVOS")
        }
    }
}
