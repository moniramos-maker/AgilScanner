package com.agil.scanner

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
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
import androidx.compose.runtime.LaunchedEffect
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
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
        scheduleProjectSync(applicationContext)
        setContent {
            AppUpdateGate {
                AccessControlledApp { offlineOnly -> AgilScannerApp(offlineOnly) }
            }
        }
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
    RAT("RAT de Atendimento", "RAT"),
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
    RAT,
    FILES,
    OFFLINE
}

@Composable
private fun AgilScannerApp(offlineOnly: Boolean = false) {
    var machineType by remember { mutableStateOf<MachineType?>(null) }
    var homeMode by remember { mutableStateOf<HomeMode?>(null) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AgilBackground
        ) {
            when {
                homeMode == HomeMode.OFFLINE -> {
                    OfflineScannerScreen(
                        onBack = {
                            homeMode = null
                            machineType = null
                        }
                    )
                }

                homeMode == HomeMode.RAT -> {
                    RatScreen(
                        onBack = {
                            homeMode = null
                            machineType = null
                        }
                    )
                }

                homeMode == HomeMode.FILES -> {
                    FileLibraryScreen(
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
                        offlineProjectMode = offlineOnly,
                        onSelect = {
                            homeMode = HomeMode.EQUIPMENT
                            machineType = it
                        },
                        onRat = {
                            homeMode = HomeMode.RAT
                            machineType = null
                        },
                        onFiles = {
                            homeMode = HomeMode.FILES
                            machineType = null
                        },
                        onOffline = {
                            homeMode = HomeMode.OFFLINE
                            machineType = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BrandHeader(subtitle: String? = null) {
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
    offlineProjectMode: Boolean,
    onSelect: (MachineType) -> Unit,
    onRat: () -> Unit,
    onFiles: () -> Unit,
    onOffline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader("Digitalização padronizada de OS em PDF")
        Spacer(Modifier.height(20.dp))

        if (offlineProjectMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6))
            ) {
                Text(
                    text = "Sem internet: você pode continuar os atendimentos normalmente. Os arquivos do projeto ficarão pendentes e serão enviados automaticamente quando a conexão voltar.",
                    modifier = Modifier.padding(16.dp),
                    color = AgilDark,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(18.dp))
        }

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
                        text = "Depois da liberação da OS, informe o chamado e o app unifica RAT + evidências",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFiles() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AgilBlueSoft),
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
                        .background(AgilDark, RoundedCornerShape(10.dp))
                )

                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "ARQUIVOS SALVOS",
                        color = AgilDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Reenviar, apagar arquivos ou remover páginas",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOffline() },
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
                        .background(Color(0xFF6B7280), RoundedCornerShape(10.dp))
                )

                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "DIGITALIZAÇÃO LOCAL",
                        color = AgilDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Documento particular/local: salvar somente no aparelho e nunca enviar para a nuvem",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun FileLibraryScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler { onBack() }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pageToDelete by remember { mutableStateOf("") }
    var currentEditPage by remember { mutableStateOf(1) }
    var eraseFile by remember { mutableStateOf<File?>(null) }
    var erasePage by remember { mutableStateOf(1) }
    var eraseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ?: context.filesDir

    if (eraseFile != null && eraseBitmap != null) {
        ManualEraserEditor(
            bitmap = requireNotNull(eraseBitmap),
            pageNumber = erasePage,
            onCancel = {
                eraseFile = null
                eraseBitmap = null
            },
            onSave = { edited ->
                try {
                    replacePdfPageWithBitmap(requireNotNull(eraseFile), erasePage, edited)
                    eraseFile = null
                    eraseBitmap = null
                    refreshKey++
                    Toast.makeText(context, "Página corrigida e salva.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao salvar edição: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
        return
    }

    val files = remember(refreshKey) {
        if (!outputDir.exists()) {
            emptyList()
        } else {
            outputDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader("Arquivos salvos")

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DOCUMENTOS",
                    color = AgilDark,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${files.size} arquivo(s) salvo(s) no aparelho",
                    color = AgilMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(onClick = onBack) {
                Text("← Voltar")
            }
        }

        if (files.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    text = "Nenhum PDF salvo ainda.",
                    modifier = Modifier.padding(18.dp),
                    color = AgilMuted
                )
            }
        }

        files.forEach { file ->
            val pages = getPdfPageCount(file)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = file.name,
                        color = AgilDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$pages página(s)",
                        color = AgilMuted,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                shareSinglePdf(
                                    context = context,
                                    file = file,
                                    message = file.nameWithoutExtension
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AgilDark,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Enviar")
                        }

                        OutlinedButton(
                            onClick = {
                                selectedFile = if (selectedFile == file) null else file
                                currentEditPage = 1
                                pageToDelete = ""
                            }
                        ) {
                            Text("Editar páginas")
                        }
                    }

                    if (selectedFile == file) {
                        val activePage = currentEditPage.coerceIn(1, pages.coerceAtLeast(1))
                        val preview = remember(file.absolutePath, file.lastModified(), activePage, refreshKey) {
                            runCatching { renderPdfPage(file, activePage, 110f) }.getOrNull()
                        }

                        Text(
                            text = "Página $activePage de $pages",
                            color = AgilDark,
                            fontWeight = FontWeight.SemiBold
                        )

                        preview?.let { bitmap ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Prévia da página $activePage",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(360.dp)
                                        .padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentEditPage = (activePage - 1).coerceAtLeast(1) },
                                enabled = activePage > 1,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("← Anterior")
                            }
                            OutlinedButton(
                                onClick = { currentEditPage = (activePage + 1).coerceAtMost(pages) },
                                enabled = activePage < pages,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Próxima →")
                            }
                        }

                        Text(
                            text = "Ajustes da página",
                            color = AgilMuted,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val original = renderPdfPage(file, activePage)
                                        val rotated = rotateBitmap90(original)
                                        replacePdfPageWithBitmap(file, activePage, rotated)
                                        refreshKey++
                                        Toast.makeText(context, "Página girada.", Toast.LENGTH_SHORT).show()
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Erro ao girar: ${ex.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("GIRAR")
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val original = renderPdfPage(file, activePage)
                                        val enhanced = enhanceDocumentBitmap(original, false)
                                        replacePdfPageWithBitmap(file, activePage, enhanced)
                                        refreshKey++
                                        Toast.makeText(context, "Leitura melhorada.", Toast.LENGTH_SHORT).show()
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Erro no filtro: ${ex.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("MELHORAR")
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val original = renderPdfPage(file, activePage)
                                        val bw = enhanceDocumentBitmap(original, true)
                                        replacePdfPageWithBitmap(file, activePage, bw)
                                        refreshKey++
                                        Toast.makeText(context, "Filtro P&B aplicado.", Toast.LENGTH_SHORT).show()
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Erro no filtro: ${ex.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("P&B")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        eraseBitmap = renderPdfPage(file, activePage)
                                        eraseFile = file
                                        erasePage = activePage
                                    } catch (ex: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Erro ao abrir página: ${ex.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("BORRACHA")
                            }

                            Button(
                                onClick = {
                                    if (pages <= 1) {
                                        Toast.makeText(
                                            context,
                                            "O PDF tem apenas uma página. Apague o arquivo inteiro se necessário.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        try {
                                            removePdfPage(file, activePage)
                                            currentEditPage = activePage.coerceAtMost(pages - 1)
                                            refreshKey++
                                            Toast.makeText(context, "Página removida.", Toast.LENGTH_SHORT).show()
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Erro ao remover página: ${ex.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = pages > 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AgilBlue,
                                    contentColor = AgilDark
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("EXCLUIR")
                            }
                        }

                        Text(
                            text = "Para refazer o enquadramento de uma folha, digitalize ou escolha novamente a foto pelo fluxo do scanner; ele faz o recorte e a perspectiva automaticamente.",
                            color = AgilMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (file.delete()) {
                                if (selectedFile == file) selectedFile = null
                                refreshKey++
                                Toast.makeText(
                                    context,
                                    "Arquivo apagado.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Não foi possível apagar o arquivo.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Apagar arquivo")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ManualEraserEditor(
    bitmap: Bitmap,
    pageNumber: Int,
    onCancel: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var brushSize by remember { mutableStateOf(34f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgilBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Borracha manual — página $pageNumber",
            color = AgilDark,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Passe o dedo sobre a área que deseja apagar. A região ficará branca.",
            color = AgilMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                strokes.add(mutableListOf(start))
                            },
                            onDrag = { change, _ ->
                                strokes.lastOrNull()?.add(change.position)
                                change.consume()
                            }
                        )
                    }
            ) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

                strokes.forEach { stroke ->
                    if (stroke.size == 1) {
                        drawCircle(
                            color = Color.White,
                            radius = brushSize / 2f,
                            center = stroke.first()
                        )
                    } else {
                        for (i in 1 until stroke.size) {
                            drawLine(
                                color = Color.White,
                                start = stroke[i - 1],
                                end = stroke[i],
                                strokeWidth = brushSize
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { brushSize = (brushSize - 8f).coerceAtLeast(14f) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Borracha -")
            }
            OutlinedButton(
                onClick = { brushSize = (brushSize + 8f).coerceAtMost(90f) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Borracha +")
            }
            OutlinedButton(
                onClick = { strokes.clear() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpar")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@Button

                    val edited = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = AndroidCanvas(edited)
                    val paint = AndroidPaint().apply {
                        color = android.graphics.Color.WHITE
                        style = AndroidPaint.Style.STROKE
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                        isAntiAlias = true
                    }

                    val sx = edited.width.toFloat() / canvasSize.width.toFloat()
                    val sy = edited.height.toFloat() / canvasSize.height.toFloat()

                    strokes.forEach { stroke ->
                        if (stroke.size == 1) {
                            paint.style = AndroidPaint.Style.FILL
                            canvas.drawCircle(
                                stroke.first().x * sx,
                                stroke.first().y * sy,
                                (brushSize / 2f) * ((sx + sy) / 2f),
                                paint
                            )
                            paint.style = AndroidPaint.Style.STROKE
                        } else {
                            paint.strokeWidth = brushSize * ((sx + sy) / 2f)
                            for (i in 1 until stroke.size) {
                                canvas.drawLine(
                                    stroke[i - 1].x * sx,
                                    stroke[i - 1].y * sy,
                                    stroke[i].x * sx,
                                    stroke[i].y * sy,
                                    paint
                                )
                            }
                        }
                    }

                    onSave(edited)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgilDark,
                    contentColor = Color.White
                )
            ) {
                Text("SALVAR")
            }
        }
    }
}

private fun renderPdfPage(file: File, pageNumber: Int, dpi: Float = 220f): Bitmap {
    PDDocument.load(file).use { document ->
        require(pageNumber in 1..document.numberOfPages) { "Página inválida." }
        val renderer = PDFRenderer(document)
        return renderer.renderImageWithDPI(
            pageNumber - 1,
            dpi,
            ImageType.RGB
        )
    }
}

private fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun enhanceDocumentBitmap(bitmap: Bitmap, blackAndWhite: Boolean): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)

    val contrast = if (blackAndWhite) 1.42f else 1.14f
    val brightness = if (blackAndWhite) 8f else 5f
    val translate = (-0.5f * contrast + 0.5f) * 255f + brightness

    val colorMatrix = android.graphics.ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )

    if (blackAndWhite) {
        val saturation = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        saturation.postConcat(colorMatrix)
        colorMatrix.set(saturation)
    }

    val paint = AndroidPaint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    }

    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return output
}

private fun replacePdfPageWithBitmap(
    file: File,
    pageNumber: Int,
    bitmap: Bitmap
) {
    val temp = File(file.parentFile, "${file.nameWithoutExtension}_borracha.pdf")

    PDDocument.load(file).use { document ->
        require(pageNumber in 1..document.numberOfPages) { "Página inválida." }

        val page = document.getPage(pageNumber - 1)
        val image = LosslessFactory.createFromImage(document, bitmap)

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.OVERWRITE,
            true,
            true
        ).use { content ->
            content.drawImage(
                image,
                0f,
                0f,
                page.mediaBox.width,
                page.mediaBox.height
            )
        }

        document.save(temp)
    }

    if (!file.delete()) {
        temp.delete()
        error("Não foi possível substituir o PDF original.")
    }

    if (!temp.renameTo(file)) {
        error("Não foi possível finalizar o PDF editado.")
    }
}

private fun createPdfFromImageUris(
    context: Context,
    uris: List<Uri>,
    output: File
) {
    require(uris.isNotEmpty()) { "Nenhuma foto selecionada." }

    PDDocument().use { document ->
        uris.forEach { uri ->
            val bitmap = context.contentResolver.openInputStream(uri).use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            } ?: error("Não foi possível abrir uma das imagens.")

            val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
            )
            document.addPage(page)

            val image = LosslessFactory.createFromImage(document, bitmap)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height
            val scale = minOf(
                pageWidth / bitmap.width.toFloat(),
                pageHeight / bitmap.height.toFloat()
            )
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val x = (pageWidth - drawWidth) / 2f
            val y = (pageHeight - drawHeight) / 2f

            PDPageContentStream(document, page).use { content ->
                content.drawImage(image, x, y, drawWidth, drawHeight)
            }

            bitmap.recycle()
        }

        document.save(output)
    }
}

private fun mergePdfUris(
    context: Context,
    uris: List<Uri>,
    output: File
) {
    require(uris.isNotEmpty()) { "Nenhum PDF selecionado." }

    val tempFiles = mutableListOf<File>()
    try {
        val merger = PDFMergerUtility()
        uris.forEachIndexed { index, uri ->
            val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}_$index.pdf")
            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(temp).use { out ->
                    requireNotNull(input) { "Não foi possível abrir um dos PDFs." }
                    input.copyTo(out)
                }
            }
            tempFiles.add(temp)
            merger.addSource(temp)
        }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
    } finally {
        tempFiles.forEach { it.delete() }
    }
}

private fun createPdfFromBitmap(
    bitmap: Bitmap,
    output: File
) {
    PDDocument().use { document ->
        val page = com.tom_roush.pdfbox.pdmodel.PDPage(
            com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
        )
        document.addPage(page)

        val image = LosslessFactory.createFromImage(document, bitmap)
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height

        val scale = minOf(
            pageWidth / bitmap.width.toFloat(),
            pageHeight / bitmap.height.toFloat()
        )

        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val x = (pageWidth - drawWidth) / 2f
        val y = (pageHeight - drawHeight) / 2f

        PDPageContentStream(document, page).use { content ->
            content.drawImage(image, x, y, drawWidth, drawHeight)
        }

        document.save(output)
    }
}

private fun getPdfPageCount(file: File): Int =
    try {
        PDDocument.load(file).use { it.numberOfPages }
    } catch (_: Exception) {
        0
    }

private fun removePdfPage(file: File, pageNumber: Int) {
    val temp = File(file.parentFile, "${file.nameWithoutExtension}_editando.pdf")

    PDDocument.load(file).use { document ->
        require(pageNumber in 1..document.numberOfPages) { "Página inválida." }
        require(document.numberOfPages > 1) { "O PDF precisa manter pelo menos uma página." }

        document.removePage(pageNumber - 1)
        document.save(temp)
    }

    if (!file.delete()) {
        temp.delete()
        error("Não foi possível substituir o arquivo original.")
    }

    if (!temp.renameTo(file)) {
        error("Não foi possível finalizar a edição do PDF.")
    }
}

@Composable
private fun RatScreen(
    onBack: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler { onBack() }

    var avulsa by remember { mutableStateOf(false) }
    var chamado by remember { mutableStateOf("") }
    var prefixoAvulso by remember { mutableStateOf("") }
    var ratFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var cloudStatus by remember { mutableStateOf<String?>(null) }

    val records = remember(chamado) {
        if (chamado.isBlank()) emptyList() else installedEvidenceForCall(context, chamado)
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember { GmsDocumentScanning.getClient(options) }

    fun ratOutputFile(): File {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!outputDir.exists()) outputDir.mkdirs()

        return if (avulsa) {
            File(outputDir, "${safeFilePart(prefixoAvulso)}_RAT_RETIRADA_AVULSA.pdf")
        } else {
            File(outputDir, "${safeFilePart(chamado)}_RAT.pdf")
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdf = scanResult?.pdf

            if (pdf?.uri != null) {
                try {
                    val output = ratOutputFile()
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

    val importRatPdfLauncher = rememberLauncherForActivityResult(
        contract = GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            try {
                val output = ratOutputFile()
                mergePdfUris(context, uris, output)
                ratFile = output
                pageCount = getPdfPageCount(output)
                Toast.makeText(context, "${uris.size} PDF(s) importado(s).", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao importar PDFs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startRatScan() {
        if (avulsa) {
            if (prefixoAvulso.isBlank()) {
                Toast.makeText(context, "Informe o prefixo da agência.", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (chamado.isBlank()) {
                Toast.makeText(context, "Informe o número do chamado.", Toast.LENGTH_SHORT).show()
                return
            }
            if (records.isEmpty()) {
                Toast.makeText(
                    context,
                    "Não encontrei evidências de impressora instalada para este chamado neste aparelho.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
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

    fun finalizeNormalRat() {
        val rat = ratFile ?: return
        if (records.isEmpty()) {
            Toast.makeText(
                context,
                "Não encontrei evidências de instalação para este chamado.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val finalFiles = mutableListOf<File>()

            records.forEach { record ->
                val evidenceFile = File(record.filePath)
                if (!evidenceFile.exists()) return@forEach

                val finalPdf = File(
                    outputDir,
                    "${safeFilePart(record.chamado)}_${safeFilePart(record.serial)}_INSTALADA.pdf"
                )

                val merger = PDFMergerUtility()
                merger.addSource(rat)
                merger.addSource(evidenceFile)
                merger.destinationFileName = finalPdf.absolutePath
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())

                finalFiles.add(finalPdf)

                uploadPdfToCloud(
                    context = context,
                    file = finalPdf,
                    chamado = record.chamado,
                    prefixo = record.prefixo,
                    agencia = record.cidade,
                    serial = record.serial,
                    onStatus = { status -> cloudStatus = status }
                )
            }

            if (finalFiles.isEmpty()) {
                Toast.makeText(context, "Nenhum PDF final foi gerado.", Toast.LENGTH_LONG).show()
                return
            }

            if (finalFiles.size == 1) {
                shareSinglePdf(
                    context = context,
                    file = finalFiles.first(),
                    message = "Chamado ${chamado.trim()} - RAT + evidências da instalação"
                )
            } else {
                shareMultiplePdfs(
                    context = context,
                    files = finalFiles,
                    message = "Chamado ${chamado.trim()} - RAT + evidências das instalações"
                )
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Erro ao gerar PDF final: ${e.message}",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    avulsa = false
                    ratFile = null
                    pageCount = 0
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!avulsa) AgilDark else Color(0xFFE5E7EB),
                    contentColor = if (!avulsa) Color.White else AgilDark
                )
            ) {
                Text("RAT DO CHAMADO")
            }

            Button(
                onClick = {
                    avulsa = true
                    ratFile = null
                    pageCount = 0
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (avulsa) AgilDark else Color(0xFFE5E7EB),
                    contentColor = if (avulsa) Color.White else AgilDark
                )
            ) {
                Text("RAT AVULSA")
            }
        }

        if (!avulsa) {
            OutlinedTextField(
                value = chamado,
                onValueChange = {
                    chamado = it
                    ratFile = null
                    pageCount = 0
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número do chamado") },
                supportingText = {
                    Text(
                        if (chamado.isBlank()) {
                            "Digite o chamado para localizar automaticamente as evidências da impressora instalada."
                        } else if (records.isEmpty()) {
                            "Nenhuma evidência instalada localizada neste aparelho."
                        } else {
                            "${records.size} impressora(s) instalada(s) localizada(s): " +
                                records.joinToString(", ") { it.serial }
                        }
                    )
                },
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = prefixoAvulso,
                onValueChange = {
                    prefixoAvulso = it
                    ratFile = null
                    pageCount = 0
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prefixo / número da agência") },
                supportingText = {
                    Text("Use somente para RAT avulsa de desinstalação, sem chamado.")
                },
                singleLine = true
            )
        }

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
                    } else if (avulsa) {
                        "Digitalize a RAT avulsa da retirada."
                    } else {
                        "A RAT será unificada automaticamente com as evidências encontradas pelo chamado."
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

                OutlinedButton(
                    onClick = {
                        if (
                            (avulsa && prefixoAvulso.isNotBlank()) ||
                            (!avulsa && chamado.isNotBlank() && records.isNotEmpty())
                        ) {
                            importRatPdfLauncher.launch("application/pdf")
                        } else {
                            startRatScan()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("IMPORTAR PDF DA RAT")
                }
            }
        }

        if (avulsa) {
            Button(
                onClick = {
                    ratFile?.let {
                        shareSinglePdf(
                            context = context,
                            file = it,
                            message = "RAT avulsa de desinstalação - Agência ${prefixoAvulso.trim()}"
                        )
                    }
                },
                enabled = prefixoAvulso.isNotBlank() && ratFile != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgilDark,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFD7DBE1)
                )
            ) {
                Text("ENVIAR RAT AVULSA PELO WHATSAPP")
            }
        } else {
            Button(
                onClick = { finalizeNormalRat() },
                enabled = chamado.isNotBlank() && records.isNotEmpty() && ratFile != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgilDark,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFD7DBE1)
                )
            ) {
                Text("UNIFICAR RAT + EVIDÊNCIAS E ENVIAR")
            }
        }

        cloudStatus?.let { status ->
            Text(
                text = status,
                color = if (status.contains("✓")) Color(0xFF2E7D32) else AgilMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Voltar")
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

    BackHandler { onNewSession() }

    var chamado by remember { mutableStateOf("") }
    var prefixo by remember { mutableStateOf("") }
    var agencia by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var serialInfo by remember { mutableStateOf("A série será tentada automaticamente ao digitalizar.") }
    var cloudStatus by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var selectedImportType by remember { mutableStateOf<DocumentType?>(null) }

    val savedFiles = remember { mutableStateMapOf<DocumentType, ScannedDocument>() }
    val requiredDocuments = documentsFor(machineType)

    LaunchedEffect(chamado, machineType) {
        if (chamado.isBlank()) return@LaunchedEffect
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        DocumentType.entries.forEach { type ->
            val file = File(
                outputDir,
                "${safeFilePart(chamado)}_${machineType.suffix}_${type.suffix}.pdf"
            )
            if (file.exists() && file.length() > 0L) {
                savedFiles[type] = ScannedDocument(file, getPdfPageCount(file))
            }
        }
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
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

    val importPhotosLauncher = rememberLauncherForActivityResult(
        contract = GetMultipleContents()
    ) { uris ->
        val type = selectedImportType
        if (uris.isNotEmpty() && type != null) {
            try {
                val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
                if (!outputDir.exists()) outputDir.mkdirs()
                val output = File(
                    outputDir,
                    "${safeFilePart(chamado)}_${machineType.suffix}_${type.suffix}.pdf"
                )
                createPdfFromImageUris(context, uris, output)
                savedFiles[type] = ScannedDocument(output, getPdfPageCount(output))

                if (serial.isBlank()) {
                    detectSerialFromPages(context, uris) { detected ->
                        if (!detected.isNullOrBlank() && serial.isBlank()) {
                            serial = detected
                            serialInfo = "Série identificada automaticamente. Confirme antes de enviar."
                        }
                    }
                }

                Toast.makeText(context, "${uris.size} foto(s) importada(s).", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao importar fotos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importPdfsLauncher = rememberLauncherForActivityResult(
        contract = GetMultipleContents()
    ) { uris ->
        val type = selectedImportType
        if (uris.isNotEmpty() && type != null) {
            try {
                val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
                if (!outputDir.exists()) outputDir.mkdirs()
                val output = File(
                    outputDir,
                    "${safeFilePart(chamado)}_${machineType.suffix}_${type.suffix}.pdf"
                )
                mergePdfUris(context, uris, output)
                savedFiles[type] = ScannedDocument(output, getPdfPageCount(output))
                Toast.makeText(context, "${uris.size} PDF(s) importado(s).", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao importar PDFs: ${e.message}", Toast.LENGTH_LONG).show()
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

    fun importPhotos(type: DocumentType) {
        // Usa o scanner do Google também para fotos da galeria.
        // Assim a folha passa por detecção de bordas, recorte e correção de perspectiva.
        startScan(type)
    }

    fun importPdfs(type: DocumentType) {
        if (chamado.isBlank()) {
            Toast.makeText(context, "Informe o número do chamado.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedImportType = type
        importPdfsLauncher.launch("application/pdf")
    }

    fun validateBaseData(): Boolean {
        if (prefixo.isBlank() || agencia.isBlank()) {
            Toast.makeText(context, "Informe o prefixo e a cidade como estão no cronograma.", Toast.LENGTH_LONG).show()
            return false
        }

        if (serial.isBlank()) {
            Toast.makeText(context, "Confirme ou informe a série.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (requiredDocuments.any { !savedFiles.containsKey(it) }) {
            Toast.makeText(context, "Digitalize todos os documentos obrigatórios.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    fun buildEvidencePdfAndShare() {
        if (!validateBaseData()) return

        try {
            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir

            val safeCall = safeFilePart(chamado)
            val safeSerial = safeFilePart(serial.uppercase())
            val evidencePdf = File(
                outputDir,
                "${safeCall}_${safeSerial}_EVIDENCIAS.pdf"
            )

            val merger = PDFMergerUtility()
            requiredDocuments.forEach { document ->
                merger.addSource(requireNotNull(savedFiles[document]).file)
            }
            merger.destinationFileName = evidencePdf.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())

            saveEvidenceRecord(
                context = context,
                chamado = chamado,
                prefixo = prefixo,
                cidade = agencia,
                serial = serial,
                file = evidencePdf,
                instalada = machineType == MachineType.NOVA
            )

            uploadPdfToCloud(
                context = context,
                file = evidencePdf,
                chamado = chamado,
                prefixo = prefixo,
                agencia = agencia,
                serial = serial,
                onStatus = { status -> cloudStatus = status }
            )

            shareSinglePdf(
                context = context,
                file = evidencePdf,
                message = "Chamado ${chamado.trim()} - Série ${serial.trim().uppercase()} - evidências"
            )

            Toast.makeText(
                context,
                if (machineType == MachineType.NOVA) {
                    "Evidências salvas. Quando a OS for liberada, use a aba RAT e informe este chamado."
                } else {
                    "Evidências da retirada salvas e enviadas."
                },
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Erro ao gerar evidências: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val count = requiredDocuments.count { savedFiles.containsKey(it) }
    val total = requiredDocuments.size
    val evidenceReady = count == total && serial.isNotBlank() && prefixo.isNotBlank() && agencia.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onNewSession) {
                Text("← Voltar")
            }
        }

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
                    Text("Menu")
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
            value = prefixo,
            onValueChange = { prefixo = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prefixo da agência") },
            placeholder = { Text("Ex.: 0929") },
            singleLine = true
        )

        OutlinedTextField(
            value = agencia,
            onValueChange = { agencia = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cidade") },
            placeholder = { Text("Exatamente como está no cronograma") },
            supportingText = { Text("A pasta será criada como: PREFIXO - CIDADE") },
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
            text = "Fotos da galeria passam pelo tratamento de documento: detecção da folha, recorte e correção de perspectiva. PDFs continuam podendo ser importados diretamente.",
            color = AgilMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        requiredDocuments.forEach { document ->
            DocumentCard(
                label = document.labelFor(machineType),
                scanned = savedFiles[document],
                onScan = { startScan(document) },
                onImportPhotos = { importPhotos(document) },
                onImportPdfs = { importPdfs(document) }
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
            onClick = { buildEvidencePdfAndShare() },
            enabled = evidenceReady,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgilDark,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD7DBE1)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("GERAR PDF E ENVIAR PELO WHATSAPP")
        }

        cloudStatus?.let { status ->
            Text(
                text = status,
                color = if (status.contains("✓")) Color(0xFF2E7D32) else AgilMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        if (!evidenceReady) {
            val pendencias = buildList {
                if (prefixo.isBlank()) add("prefixo")
                if (agencia.isBlank()) add("cidade")
                if (serial.isBlank()) add("serial")
                if (count < total) {
                    val faltando = requiredDocuments
                        .filter { !savedFiles.containsKey(it) }
                        .joinToString(", ") { it.labelFor(machineType) }
                    add(faltando.ifBlank { "documentos obrigatórios" })
                }
            }
            Text(
                text = "Para liberar o envio, falta: " + pendencias.joinToString(", ") + ".",
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (machineType == MachineType.NOVA) {
            Text(
                text = "Depois de enviar as evidências ao banco e liberar a OS, use a aba RAT e informe o mesmo chamado.",
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
    onScan: () -> Unit,
    onImportPhotos: () -> Unit,
    onImportPdfs: () -> Unit
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

            Text(
                text = "Ou use uma foto existente / importe PDFs:",
                color = AgilMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportPhotos,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("FOTO / GALERIA")
                }

                OutlinedButton(
                    onClick = onImportPdfs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("PDFs")
                }
            }
        }
    }
}

const val CLOUD_UPLOAD_URL = "https://agilscanner.vercel.app/api/upload"
const val CLOUD_UPLOAD_KEY = "AgilScan_2026#Upload\$Cloud!9X7K2M"

private fun safeFilePart(value: String): String =
    value.trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9_-]"), "_")
        .ifBlank { "SEM_IDENTIFICACAO" }

private fun uploadPdfToCloud(
    context: Context,
    file: File,
    chamado: String,
    prefixo: String,
    agencia: String,
    serial: String,
    onStatus: (String) -> Unit
) {
    onStatus("Enviando para a nuvem…")

    Thread {
        val ok = uploadProjectFileOnce(
            context = context,
            file = file,
            chamado = chamado,
            prefixo = prefixo,
            agencia = agencia,
            serial = serial
        )

        if (ok) {
            (context as? Activity)?.runOnUiThread {
                onStatus("Sincronizado ✓")
                Toast.makeText(context, "Salvo na nuvem ✓", Toast.LENGTH_SHORT).show()
            }
        } else {
            queueProjectUpload(
                context = context,
                file = file,
                chamado = chamado,
                prefixo = prefixo,
                agencia = agencia,
                serial = serial
            )
            (context as? Activity)?.runOnUiThread {
                onStatus("Pendente de sincronização")
                Toast.makeText(
                    context,
                    "Sem conexão. O arquivo ficou salvo e será enviado automaticamente quando a internet voltar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }.start()
}

fun shareSinglePdf(
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

fun shareMultiplePdfs(
    context: Context,
    files: List<File>,
    message: String
) {
    if (files.isEmpty()) return

    val uris = ArrayList<Uri>()
    files.forEach { file ->
        uris.add(
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        )
    }

    fun buildIntent(packageName: String?): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName != null) setPackage(packageName)
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
