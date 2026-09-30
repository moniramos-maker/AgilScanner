package com.agil.scanner

import android.app.Activity
import android.content.ClipData
import android.content.Context
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
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
        PDFBoxResourceLoader.init(applicationContext)
        setContent { AgilScannerApp() }
    }
}

private enum class MachineType(
    val label: String,
    val suffix: String,
    val subtitle: String,
    val finalStatus: String
) {
    NOVA(
        "IMPRESSORA NOVA",
        "NOVA",
        "Equipamento que será instalado",
        "INSTALADA"
    ),
    ANTIGA(
        "IMPRESSORA ANTIGA",
        "ANTIGA",
        "Equipamento que será desinstalado",
        "RETIRADA"
    )
}

private enum class DocumentType(val label: String, val suffix: String) {
    CONFIGURACAO("Relatório de Configuração", "CONFIGURACAO"),
    ATIVO("Relatório de Ativo", "ATIVO"),
    REDE("Relatório de Rede", "REDE"),
    ESTATISTICA("Página de Estatística", "ESTATISTICA")
}

private fun DocumentType.labelFor(machineType: MachineType): String = label

private fun documentsFor(machineType: MachineType): List<DocumentType> =
    if (machineType == MachineType.NOVA) {
        listOf(
            DocumentType.ATIVO,
            DocumentType.REDE,
            DocumentType.ESTATISTICA
        )
    } else {
        listOf(
            DocumentType.CONFIGURACAO,
            DocumentType.ATIVO,
            DocumentType.ESTATISTICA
        )
    }

private data class ScannedDocument(
    val file: File,
    val pageCount: Int
)

private enum class HomeMode {
    EQUIPMENT,
    RAT
}

@Composable
private fun AgilScannerApp() {
    var machineType by remember { mutableStateOf<MachineType?>(null) }
    var homeMode by remember { mutableStateOf<HomeMode?>(null) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AgilBackground
        ) {
            when {
                homeMode == HomeMode.RAT -> {
                    RatScreen(
                        onBack = {
                            homeMode = null
                            machineType = null
                        }
                    )
                }

                machineType != null -> {
                    ScannerScreen(
                        machineType = machineType!!,
                        onNewSession = {
                            machineType = null
                            homeMode = null
                        }
                    )
                }

                else -> {
                    MachineSelectionScreen(
                        onSelect = {
                            homeMode = HomeMode.EQUIPMENT
                            machineType = it
                        },
                        onRat = {
                            homeMode = HomeMode.RAT
                            machineType = null
                        }
                    )
                }
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
    onSelect: (MachineType) -> Unit,
    onRat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader("Digitalização padronizada de OS em PDF")
        Spacer(Modifier.height(34.dp))

        Text(
            text = "Selecione a impressora",
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

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRat() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        .background(AgilBlue, RoundedCornerShape(10.dp))
                )

                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "RAT DE ATENDIMENTO",
                        color = AgilDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Digitalizar e enviar RAT pelo número do chamado",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RatScreen(
    onBack: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = androidx.compose.ui.platform.LocalContext.current

    var chamado by remember { mutableStateOf("") }
    var ratFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }

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

            if (pdf?.uri != null) {
                try {
                    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                        ?: context.filesDir

                    if (!outputDir.exists()) outputDir.mkdirs()

                    val output = File(
                        outputDir,
                        "${safeFilePart(chamado)}_RAT.pdf"
                    )

                    context.contentResolver.openInputStream(pdf.uri).use { input ->
                        FileOutputStream(output).use { out ->
                            requireNotNull(input) { "Não foi possível abrir o PDF gerado." }
                            input.copyTo(out)
                        }
                    }

                    ratFile = output
                    pageCount = pdf.pageCount

                    Toast.makeText(
                        context,
                        "RAT salva com ${pdf.pageCount} página(s).",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Erro ao salvar RAT: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun startRatScan() {
        if (chamado.isBlank()) {
            Toast.makeText(
                context,
                "Informe o número do chamado.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader("RAT de Atendimento")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgilBlueSoft)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RAT DE ATENDIMENTO",
                        fontWeight = FontWeight.Bold,
                        color = AgilDark
                    )
                    Text(
                        text = "Somente o número do chamado é obrigatório",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(onClick = onBack) {
                    Text("Voltar")
                }
            }
        }

        OutlinedTextField(
            value = chamado,
            onValueChange = {
                chamado = it
                ratFile = null
                pageCount = 0
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número do chamado") },
            singleLine = true
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgilSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (ratFile != null) "✓ RAT digitalizada" else "RAT de Atendimento",
                    color = AgilDark,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (ratFile != null) {
                        "$pageCount página(s) no PDF"
                    } else {
                        "Pode conter uma ou várias páginas"
                    },
                    color = AgilMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = { startRatScan() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgilBlue,
                        contentColor = AgilDark
                    )
                ) {
                    Text(if (ratFile != null) "Refazer RAT" else "Digitalizar RAT")
                }
            }
        }

        if (ratFile != null) {
            Text(
                text = "Arquivo: ${safeFilePart(chamado)}_RAT.pdf",
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                ratFile?.let {
                    shareSinglePdf(
                        context = context,
                        file = it,
                        message = "Chamado ${chamado.trim()} - RAT de atendimento"
                    )
                }
            },
            enabled = chamado.isNotBlank() && ratFile != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgilDark,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD7DBE1)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("ENVIAR RAT PELO WHATSAPP")
        }
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
    var serial by remember { mutableStateOf("") }
    var serialInfo by remember { mutableStateOf("A série será tentada automaticamente ao digitalizar.") }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }

    val savedFiles = remember { mutableStateMapOf<DocumentType, ScannedDocument>() }
    val requiredDocuments = documentsFor(machineType)

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
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
                    val safeCall = safeFilePart(chamado)
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

                    val pageUris = scanResult.pages?.map { it.imageUri }.orEmpty()
                    if (serial.isBlank() && pageUris.isNotEmpty()) {
                        serialInfo = "Tentando identificar a série..."
                        detectSerialFromPages(
                            context = context,
                            uris = pageUris,
                            onResult = { detected ->
                                if (!detected.isNullOrBlank() && serial.isBlank()) {
                                    serial = detected
                                    serialInfo = "Série identificada automaticamente. Confirme antes de enviar."
                                } else if (serial.isBlank()) {
                                    serialInfo = "Não consegui identificar a série. Digite manualmente."
                                }
                            }
                        )
                    }

                    Toast.makeText(
                        context,
                        "Documento salvo com ${pdf.pageCount} página(s).",
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

    fun buildAndShareFinalPdf() {
        if (serial.isBlank()) {
            Toast.makeText(context, "Confirme ou informe a série.", Toast.LENGTH_SHORT).show()
            return
        }

        if (requiredDocuments.any { !savedFiles.containsKey(it) }) {
            Toast.makeText(context, "Digitalize todos os documentos obrigatórios.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir

            val safeCall = safeFilePart(chamado)
            val safeSerial = safeFilePart(serial.uppercase())
            val finalPdf = File(
                outputDir,
                "${safeCall}_${safeSerial}_${machineType.finalStatus}.pdf"
            )

            val merger = PDFMergerUtility()
            requiredDocuments.forEach { document ->
                merger.addSource(requireNotNull(savedFiles[document]).file)
            }
            merger.destinationFileName = finalPdf.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())

            shareSinglePdf(
                context = context,
                file = finalPdf,
                message = "Chamado ${chamado.trim()} - Série ${serial.trim().uppercase()} - ${machineType.finalStatus.lowercase()}"
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Erro ao gerar PDF único: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val count = requiredDocuments.count { savedFiles.containsKey(it) }
    val total = requiredDocuments.size
    val ready = count == total && serial.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        OutlinedTextField(
            value = serial,
            onValueChange = {
                serial = it.uppercase().replace(Regex("[^A-Z0-9]"), "")
                serialInfo = "Série confirmada/editada manualmente."
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Série da impressora") },
            supportingText = { Text(serialInfo) },
            singleLine = true
        )

        Text(
            text = "Cada item pode conter várias fotos. O aplicativo juntará tudo em um único PDF somente na hora do envio.",
            color = AgilMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        requiredDocuments.forEach { document ->
            DocumentCard(
                label = document.labelFor(machineType),
                scanned = savedFiles[document],
                onScan = { startScan(document) }
            )
        }

        Text(
            text = "Documentação: $count de $total concluída",
            color = AgilDark,
            fontWeight = FontWeight.SemiBold
        )

        if (serial.isNotBlank()) {
            Text(
                text = "Arquivo final: ${safeFilePart(chamado)}_${safeFilePart(serial)}_${machineType.finalStatus}.pdf",
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = { buildAndShareFinalPdf() },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgilDark,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD7DBE1)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("GERAR PDF ÚNICO E ENVIAR PELO WHATSAPP")
        }

        if (!ready) {
            Text(
                text = "Para liberar o envio, confirme a série e conclua todos os documentos.",
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DocumentCard(
    label: String,
    scanned: ScannedDocument?,
    onScan: () -> Unit
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
                text = if (scanned != null) "✓ $label" else label,
                color = AgilDark,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (scanned != null) {
                    "${scanned.pageCount} página(s) digitalizada(s)"
                } else {
                    "Pode conter uma ou várias fotos"
                },
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgilBlue,
                    contentColor = AgilDark
                )
            ) {
                Text(if (scanned != null) "Refazer" else "Digitalizar")
            }
        }
    }
}

private fun safeFilePart(value: String): String =
    value.trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9_-]"), "_")
        .ifBlank { "SEM_IDENTIFICACAO" }

private fun shareSinglePdf(
    context: Context,
    file: File,
    message: String
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    fun buildIntent(packageName: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
            if (packageName != null) setPackage(packageName)
        }

    try {
        context.startActivity(buildIntent("com.whatsapp"))
    } catch (_: Exception) {
        try {
            context.startActivity(buildIntent("com.whatsapp.w4b"))
        } catch (_: Exception) {
            context.startActivity(
                Intent.createChooser(buildIntent(null), "Enviar PDF")
            )
        }
    }
}

private fun detectSerialFromPages(
    context: Context,
    uris: List<Uri>,
    onResult: (String?) -> Unit
) {
    detectBarcodeSerial(
        context = context,
        uris = uris,
        index = 0
    ) { barcodeResult ->
        if (!barcodeResult.isNullOrBlank()) {
            onResult(barcodeResult)
        } else {
            detectTextSerial(
                context = context,
                uris = uris,
                index = 0,
                onResult = onResult
            )
        }
    }
}

private fun detectBarcodeSerial(
    context: Context,
    uris: List<Uri>,
    index: Int,
    onResult: (String?) -> Unit
) {
    if (index >= uris.size) {
        onResult(null)
        return
    }

    val scanner = BarcodeScanning.getClient()

    try {
        val image = InputImage.fromFilePath(context, uris[index])
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val candidate = barcodes
                    .asSequence()
                    .mapNotNull { it.rawValue }
                    .mapNotNull { normalizeSerialCandidate(it) }
                    .firstOrNull()

                scanner.close()

                if (candidate != null) {
                    onResult(candidate)
                } else {
                    detectBarcodeSerial(context, uris, index + 1, onResult)
                }
            }
            .addOnFailureListener {
                scanner.close()
                detectBarcodeSerial(context, uris, index + 1, onResult)
            }
    } catch (_: Exception) {
        scanner.close()
        detectBarcodeSerial(context, uris, index + 1, onResult)
    }
}

private fun detectTextSerial(
    context: Context,
    uris: List<Uri>,
    index: Int,
    onResult: (String?) -> Unit
) {
    if (index >= uris.size) {
        onResult(null)
        return
    }

    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    try {
        val image = InputImage.fromFilePath(context, uris[index])
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val candidate = extractSerialFromText(result.text)
                recognizer.close()

                if (candidate != null) {
                    onResult(candidate)
                } else {
                    detectTextSerial(context, uris, index + 1, onResult)
                }
            }
            .addOnFailureListener {
                recognizer.close()
                detectTextSerial(context, uris, index + 1, onResult)
            }
    } catch (_: Exception) {
        recognizer.close()
        detectTextSerial(context, uris, index + 1, onResult)
    }
}

private fun extractSerialFromText(text: String): String? {
    val normalizedText = text.uppercase()
    val labeledPattern = Regex(
        """(?:SERIAL(?:\s+NUMBER)?|SÉRIE|SERIE|S/N|SN)\s*[:#-]?\s*([A-Z0-9]{8,24})"""
    )

    labeledPattern.find(normalizedText)?.groupValues?.getOrNull(1)?.let {
        return normalizeSerialCandidate(it)
    }

    val candidates = Regex("""\b[A-Z0-9]{10,18}\b""")
        .findAll(normalizedText)
        .map { it.value }
        .mapNotNull { normalizeSerialCandidate(it) }
        .filter { value ->
            value.any { it.isLetter() } && value.any { it.isDigit() }
        }
        .distinct()
        .toList()

    return if (candidates.size == 1) candidates.first() else null
}

private fun normalizeSerialCandidate(raw: String): String? {
    val value = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

    if (value.length !in 8..24) return null
    if (value.startsWith("HTTP") || value.startsWith("WWW")) return null
    if (!value.any { it.isDigit() }) return null

    return value
}
