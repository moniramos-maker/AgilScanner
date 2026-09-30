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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream

private val AgilBackground = Color(0xFFF4F6F9)
private val AgilSurface = Color(0xFFFFFFFF)
private val AgilDark = Color(0xFF101722)
private val AgilBlue = Color(0xFF8EB8FF)
private val AgilBlueSoft = Color(0xFFEAF2FF)
private val AgilMuted = Color(0xFF687384)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgilScannerApp() }
    }
}

private enum class MachineType(val label: String, val suffix: String, val subtitle: String) {
    NOVA("MÁQUINA NOVA", "NOVA", "Equipamento que será instalado"),
    ANTIGA("MÁQUINA ANTIGA", "ANTIGA", "Equipamento que será desinstalado")
}

private enum class DocumentType(val label: String, val suffix: String) {
    CONFIGURACAO("Relatório de Configuração", "CONFIGURACAO"),
    ATIVO("Relatório de Ativo", "ATIVO"),
    ESTATISTICA("Página de Estatística", "ESTATISTICA")
}

private data class ScannedDocument(
    val file: File,
    val pageCount: Int
)

@Composable
private fun AgilScannerApp() {
    var machineType by remember { mutableStateOf<MachineType?>(null) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AgilBackground
        ) {
            if (machineType == null) {
                MachineSelectionScreen(
                    onSelect = { machineType = it }
                )
            } else {
                ScannerScreen(
                    machineType = machineType!!,
                    onNewSession = { machineType = null }
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.agil_logo),
            contentDescription = "Grupo Ágil",
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(78.dp)
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = AgilMuted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MachineSelectionScreen(
    onSelect: (MachineType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader("Digitalização de documentos em PDF")
        Spacer(Modifier.height(34.dp))

        Text(
            text = "Selecione o equipamento",
            color = AgilDark,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Escolha antes de iniciar a digitalização.",
            color = AgilMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        MachineCard(MachineType.NOVA, onSelect)
        Spacer(Modifier.height(14.dp))
        MachineCard(MachineType.ANTIGA, onSelect)
    }
}

@Composable
private fun MachineCard(
    type: MachineType,
    onSelect: (MachineType) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(type) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (type == MachineType.NOVA) AgilBlueSoft else AgilDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (type == MachineType.NOVA) AgilBlue else Color.White,
                        RoundedCornerShape(10.dp)
                    )
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = type.label,
                    color = if (type == MachineType.NOVA) AgilDark else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = type.subtitle,
                    color = if (type == MachineType.NOVA) AgilMuted else Color(0xFFD5DBE5),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerScreen(
    machineType: MachineType,
    onNewSession: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = androidx.compose.ui.platform.LocalContext.current

    var chamado by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    val savedFiles = remember { mutableStateMapOf<DocumentType, ScannedDocument>() }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
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
            val pdf = scanResult?.pdf
            val type = selectedType

            if (pdf?.uri != null && type != null) {
                try {
                    val safeCall = chamado.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                        ?: context.filesDir
                    if (!outputDir.exists()) outputDir.mkdirs()

                    val output = File(
                        outputDir,
                        "${safeCall}_${machineType.suffix}_${type.suffix}.pdf"
                    )

                    context.contentResolver.openInputStream(pdf.uri).use { input ->
                        FileOutputStream(output).use { out ->
                            requireNotNull(input) { "Não foi possível abrir o PDF gerado." }
                            input.copyTo(out)
                        }
                    }

                    savedFiles[type] = ScannedDocument(
                        file = output,
                        pageCount = pdf.pageCount
                    )

                    Toast.makeText(
                        context,
                        "PDF salvo com ${pdf.pageCount} página(s).",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Erro ao salvar PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
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
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    context,
                    "Não foi possível abrir o scanner: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
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

        fun buildIntent(packageName: String?): Intent {
            return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Chamado ${chamado.trim()} - ${machineType.label.lowercase()} - documentação digitalizada"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Documentos", uris.first())
                uris.drop(1).forEach { clipData?.addItem(ClipData.Item(it)) }
                if (packageName != null) setPackage(packageName)
            }
        }

        try {
            context.startActivity(buildIntent("com.whatsapp"))
        } catch (_: Exception) {
            try {
                context.startActivity(buildIntent("com.whatsapp.w4b"))
            } catch (_: Exception) {
                context.startActivity(
                    Intent.createChooser(buildIntent(null), "Enviar PDFs")
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (machineType == MachineType.NOVA) AgilBlueSoft else AgilDark
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = machineType.label,
                        fontWeight = FontWeight.Bold,
                        color = if (machineType == MachineType.NOVA) AgilDark else Color.White
                    )
                    Text(
                        text = machineType.subtitle,
                        color = if (machineType == MachineType.NOVA) AgilMuted else Color(0xFFD5DBE5),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onNewSession) {
                    Text("Trocar")
                }
            }
        }

        OutlinedTextField(
            value = chamado,
            onValueChange = { chamado = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número do chamado") },
            singleLine = true
        )

        Text(
            text = "Cada relatório pode ter várias fotos/páginas. Fotografe todas antes de concluir o documento.",
            color = AgilMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        DocumentCard(
            type = DocumentType.CONFIGURACAO,
            scanned = savedFiles[DocumentType.CONFIGURACAO],
            onScan = { startScan(DocumentType.CONFIGURACAO) },
            onShare = {
                savedFiles[DocumentType.CONFIGURACAO]?.file?.let {
                    shareOnWhatsApp(listOf(it))
                }
            }
        )

        DocumentCard(
            type = DocumentType.ATIVO,
            scanned = savedFiles[DocumentType.ATIVO],
            onScan = { startScan(DocumentType.ATIVO) },
            onShare = {
                savedFiles[DocumentType.ATIVO]?.file?.let {
                    shareOnWhatsApp(listOf(it))
                }
            }
        )

        DocumentCard(
            type = DocumentType.ESTATISTICA,
            scanned = savedFiles[DocumentType.ESTATISTICA],
            onScan = { startScan(DocumentType.ESTATISTICA) },
            onShare = {
                savedFiles[DocumentType.ESTATISTICA]?.file?.let {
                    shareOnWhatsApp(listOf(it))
                }
            }
        )

        val count = savedFiles.size
        Text(
            text = "Documentação: $count de 3 concluída",
            color = AgilDark,
            fontWeight = FontWeight.SemiBold
        )

        Button(
            onClick = {
                shareOnWhatsApp(
                    DocumentType.entries.mapNotNull { savedFiles[it]?.file }
                )
            },
            enabled = savedFiles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgilDark,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD7DBE1)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (count == 3) "ENVIAR OS 3 PDFs PELO WHATSAPP"
                else "ENVIAR PDFs DIGITALIZADOS"
            )
        }
    }
}

@Composable
private fun DocumentCard(
    type: DocumentType,
    scanned: ScannedDocument?,
    onScan: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AgilSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = if (scanned != null) "✓ ${type.label}" else type.label,
                color = AgilDark,
                fontWeight = FontWeight.Bold
            )

            if (scanned != null) {
                Text(
                    text = "${scanned.pageCount} página(s) no PDF",
                    color = AgilMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "Pode conter uma ou várias fotos",
                    color = AgilMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgilBlue,
                        contentColor = AgilDark
                    )
                ) {
                    Text(if (scanned != null) "Refazer" else "Digitalizar")
                }

                if (scanned != null) {
                    OutlinedButton(onClick = onShare) {
                        Text("WhatsApp")
                    }
                }
            }
        }
    }
}
