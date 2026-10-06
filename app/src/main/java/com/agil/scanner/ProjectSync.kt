package com.agil.scanner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val SYNC_PREFS = "agil_project_sync"
private const val SYNC_ITEMS = "pending_items"
private const val SYNC_WORK = "agil_project_upload_queue"

private data class PendingUpload(
    val path: String,
    val chamado: String,
    val prefixo: String,
    val agencia: String,
    val serial: String
)

private fun loadPending(context: Context): MutableList<PendingUpload> {
    val raw = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        .getString(SYNC_ITEMS, "[]") ?: "[]"
    val list = mutableListOf<PendingUpload>()
    try {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val path = item.optString("path")
            val chamado = item.optString("chamado")
            if (path.isNotBlank() && chamado.isNotBlank()) {
                list.add(
                    PendingUpload(
                        path = path,
                        chamado = chamado,
                        prefixo = item.optString("prefixo"),
                        agencia = item.optString("agencia"),
                        serial = item.optString("serial")
                    )
                )
            }
        }
    } catch (_: Exception) {
    }
    return list
}

private fun savePending(context: Context, items: List<PendingUpload>) {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("path", item.path)
                .put("chamado", item.chamado)
                .put("prefixo", item.prefixo)
                .put("agencia", item.agencia)
                .put("serial", item.serial)
        )
    }
    context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(SYNC_ITEMS, array.toString())
        .apply()
}

fun queueProjectUpload(
    context: Context,
    file: File,
    chamado: String,
    prefixo: String,
    agencia: String,
    serial: String
) {
    val items = loadPending(context)
    if (items.none { it.path == file.absolutePath }) {
        items.add(
            PendingUpload(
                path = file.absolutePath,
                chamado = chamado.trim(),
                prefixo = prefixo.trim(),
                agencia = agencia.trim(),
                serial = serial.trim()
            )
        )
        savePending(context, items)
    }
    scheduleProjectSync(context)
}

private fun removePending(context: Context, path: String) {
    val items = loadPending(context).filterNot { it.path == path }
    savePending(context, items)
}

fun pendingProjectUploadCount(context: Context): Int = loadPending(context).size

fun scheduleProjectSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<ProjectUploadWorker>()
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        SYNC_WORK,
        ExistingWorkPolicy.REPLACE,
        request
    )
}

fun uploadProjectFileOnce(
    context: Context,
    file: File,
    chamado: String,
    prefixo: String,
    agencia: String,
    serial: String
): Boolean {
    if (!file.exists() || file.length() == 0L) return false

    val connection = (URL(CLOUD_UPLOAD_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15000
        readTimeout = 30000
        doOutput = true
        setRequestProperty("Content-Type", "application/pdf")
        setRequestProperty("X-Upload-Key", CLOUD_UPLOAD_KEY)
        setRequestProperty("X-Device-Id", agilDeviceId(context))
        setRequestProperty("X-Chamado", chamado.trim())
        setRequestProperty("X-Prefixo", prefixo.trim())
        setRequestProperty("X-Agencia", agencia.trim())
        setRequestProperty("X-Serial", serial.trim().uppercase())
        setRequestProperty("X-File-Name", file.name)
        setFixedLengthStreamingMode(file.length())
    }

    return try {
        file.inputStream().use { input ->
            connection.outputStream.use { output ->
                input.copyTo(output)
            }
        }
        connection.responseCode in 200..299
    } catch (_: Exception) {
        false
    } finally {
        connection.disconnect()
    }
}

class ProjectUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val items = loadPending(applicationContext)
        if (items.isEmpty()) return Result.success()

        var hadFailure = false

        items.forEach { item ->
            val file = File(item.path)
            if (!file.exists()) {
                removePending(applicationContext, item.path)
            } else {
                val ok = uploadProjectFileOnce(
                    context = applicationContext,
                    file = file,
                    chamado = item.chamado,
                    prefixo = item.prefixo,
                    agencia = item.agencia,
                    serial = item.serial
                )
                if (ok) {
                    removePending(applicationContext, item.path)
                } else {
                    hadFailure = true
                }
            }
        }

        return if (hadFailure) Result.retry() else Result.success()
    }
}
