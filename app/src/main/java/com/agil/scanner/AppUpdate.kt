package com.agil.scanner

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API =
    "https://api.github.com/repos/moniramos-maker/AgilScanner/releases/latest"

private data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

private fun currentVersionCode(context: Context): Int {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
        @Suppress("DEPRECATION")
        info.versionCode
    }
}

private fun downloadedApkVersionCode(context: Context): Int? {
    val file = apkFile(context)
    if (!file.exists() || file.length() == 0L) return null
    val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
        @Suppress("DEPRECATION")
        info.versionCode
    }
}

private fun clearStaleDownloadedApk(context: Context) {
    val downloadedVersion = downloadedApkVersionCode(context) ?: return
    if (downloadedVersion <= currentVersionCode(context)) {
        apkFile(context).delete()
    }
}

private fun checkForUpdate(): AppUpdateInfo? {
    val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 12000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "AgilScanner-Android")
    }

    return try {
        if (conn.responseCode !in 200..299) return null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name", "")
        val versionCode = Regex("(\\d+)").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        AppUpdateInfo(
            versionCode = versionCode,
            versionName = tag.removePrefix("v").removePrefix("V"),
            apkUrl = apkUrl,
            notes = json.optString("body", "")
        )
    } finally {
        conn.disconnect()
    }
}

private fun apkFile(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "AgilScanner-update.apk")
}

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

private fun openInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun installDownloadedApk(context: Context): Boolean {
    val file = apkFile(context)
    if (!file.exists() || file.length() == 0L) return false

    if (!canInstallPackages(context)) {
        openInstallPermission(context)
        return false
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    return true
}

private fun startApkDownload(
    context: Context,
    info: AppUpdateInfo
): Long {
    val file = apkFile(context)
    if (file.exists()) file.delete()

    val request = DownloadManager.Request(Uri.parse(info.apkUrl))
        .setTitle("Atualizando ÁGIL Scanner")
        .setDescription("Baixando versão ${info.versionName}")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationUri(Uri.fromFile(file))
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return manager.enqueue(request)
}

@Composable
fun AppUpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    var checking by remember { mutableStateOf(true) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        clearStaleDownloadedApk(context)
        Thread {
            val result = try { checkForUpdate() } catch (_: Exception) { null }
            activity.runOnUiThread {
                update = result?.takeIf { it.versionCode > currentVersionCode(context) }
                checking = false
            }
        }.start()
    }

    DisposableEffect(downloadId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (downloadId != null && id == downloadId) {
                    downloading = false
                    message = "Download concluído. Toque em INSTALAR ATUALIZAÇÃO para continuar."
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    if (checking || update == null) {
        content()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandHeader("Atualização do aplicativo")
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Nova versão disponível",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Versão ${update!!.versionName}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Existe uma versão mais nova. Você pode atualizar agora ou entrar no aplicativo e atualizar depois.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (update!!.notes.isNotBlank()) {
                    Text(
                        update!!.notes.take(600),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (message.isNotBlank()) {
                    Text(
                        message,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        clearStaleDownloadedApk(context)
                        val localApk = apkFile(context)
                        val localVersion = downloadedApkVersionCode(context)

                        if (localApk.exists() && localVersion != null && localVersion > currentVersionCode(context)) {
                            if (canInstallPackages(context)) {
                                val opened = installDownloadedApk(context)
                                message = if (opened) {
                                    "Instalador aberto. Conclua a instalação e depois abra o ÁGIL Scanner novamente."
                                } else {
                                    "Não foi possível abrir o instalador."
                                }
                            } else {
                                openInstallPermission(context)
                                message = "Autorize esta fonte. Depois volte ao ÁGIL Scanner e toque em INSTALAR ATUALIZAÇÃO."
                            }
                        } else {
                            if (localApk.exists()) localApk.delete()
                            try {
                                downloading = true
                                message = "Baixando atualização..."
                                downloadId = startApkDownload(context, update!!)
                            } catch (e: Exception) {
                                downloading = false
                                message = "Não foi possível iniciar o download."
                                Toast.makeText(context, e.message ?: "Erro", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF101722),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        when {
                            downloading -> "BAIXANDO..."
                            downloadedApkVersionCode(context)?.let { it > currentVersionCode(context) } == true -> "INSTALAR ATUALIZAÇÃO"
                            else -> "BAIXAR E INSTALAR"
                        }
                    )
                }

                Button(
                    onClick = {
                        update = null
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE5E7EB),
                        contentColor = Color(0xFF101722)
                    )
                ) {
                    Text("ENTRAR NO APP E ATUALIZAR DEPOIS")
                }
            }
        }
    }
}
