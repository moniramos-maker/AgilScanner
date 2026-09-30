package com.agil.scanner

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgilScannerApp() }
    }
}

private enum class DocumentType(val label: String, val suffix: String) {
    CONFIGURACAO("Relatório de Configuração", "CONFIGURACAO"),
    ATIVO("Relatório de Ativo", "ATIVO"),
    ESTATISTICA("Página de Estatística", "ESTATISTICA")
}

@Composable
private fun AgilScannerApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = androidx.compose.ui.platform.LocalContext.current

    var chamado by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    val savedFiles = remember { mutableStateMapOf<DocumentType, File>() }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember { GmsDocumentScanning.getClient(options) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            val type = selectedType

            if (pdfUri != null && type != null) {
                try {
                    val safeCall = chamado.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                        ?: context.filesDir
                    if (!outputDir.exists()) outputDir.mkdirs()

                    val output = File(outputDir, "${safeCall}_${type.suffix}.pdf")
                    context.contentResolver.openInputStream(pdfUri).use { input ->
                        FileOutputStream(output).use { out ->
                            requireNotNull(input) { "Não foi possível abrir o PDF gerado." }
                            input.copyTo(out)
                        }
                    }
                    savedFiles[type] = output
                    Toast.makeText(context, "PDF salvo: ${output.name}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao salvar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun startScan(type: DocumentType) {
        if (chamado.isBlank()) {
            Toast.makeText(context, "Informe o número do chamado.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedType = type
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                Toast.makeText(context, "Não foi possível abrir o scanner: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    fun shareOnWhatsApp(files: List<File>) {
        if (files.isEmpty()) {
            Toast.makeText(context, "Digitalize pelo menos um documento.", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = ArrayList<Uri>()
        files.forEach { file ->
            uris += FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, "Chamado ${chamado.trim()} - documentação digitalizada")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Documentos", uris.first())
            uris.drop(1).forEach { clipData?.addItem(ClipData.Item(it)) }
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(sendIntent)
        } catch (_: Exception) {
            sendIntent.setPackage(null)
            context.startActivity(Intent.createChooser(sendIntent, "Enviar PDFs"))
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F0E6)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ÁGIL Scanner",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Digitalização de documentos em PDF")

                OutlinedTextField(
                    value = chamado,
                    onValueChange = { chamado = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Número do chamado") },
                    singleLine = true
                )

                Spacer(Modifier.height(4.dp))

                DocumentCard(
                    type = DocumentType.CONFIGURACAO,
                    completed = savedFiles.containsKey(DocumentType.CONFIGURACAO),
                    onScan = { startScan(DocumentType.CONFIGURACAO) },
                    onShare = { savedFiles[DocumentType.CONFIGURACAO]?.let { shareOnWhatsApp(listOf(it)) } }
                )
                DocumentCard(
                    type = DocumentType.ATIVO,
                    completed = savedFiles.containsKey(DocumentType.ATIVO),
                    onScan = { startScan(DocumentType.ATIVO) },
                    onShare = { savedFiles[DocumentType.ATIVO]?.let { shareOnWhatsApp(listOf(it)) } }
                )
                DocumentCard(
                    type = DocumentType.ESTATISTICA,
                    completed = savedFiles.containsKey(DocumentType.ESTATISTICA),
                    onScan = { startScan(DocumentType.ESTATISTICA) },
                    onShare = { savedFiles[DocumentType.ESTATISTICA]?.let { shareOnWhatsApp(listOf(it)) } }
                )

                val count = savedFiles.size
                Text(
                    text = "Documentação: $count de 3 concluída",
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = {
                        shareOnWhatsApp(DocumentType.entries.mapNotNull { savedFiles[it] })
                    },
                    enabled = savedFiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF246B46))
                ) {
                    Text(if (count == 3) "ENVIAR OS 3 PDFs PELO WHATSAPP" else "ENVIAR PDFs DIGITALIZADOS")
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(
    type: DocumentType,
    completed: Boolean,
    onScan: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (completed) "☑ ${type.label}" else "☐ ${type.label}",
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan) {
                    Text(if (completed) "Refazer" else "Digitalizar")
                }
                if (completed) {
                    OutlinedButton(onClick = onShare) {
                        Text("WhatsApp")
                    }
                }
            }
        }
    }
}
