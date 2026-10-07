package com.agil.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val EVIDENCE_PREFS = "agil_evidence_registry"
private const val EVIDENCE_ITEMS = "items"

data class EvidenceRecord(
    val chamado: String,
    val prefixo: String,
    val cidade: String,
    val serial: String,
    val filePath: String,
    val instalada: Boolean
)

fun loadEvidenceRecords(context: Context): MutableList<EvidenceRecord> {
    val raw = context.getSharedPreferences(EVIDENCE_PREFS, Context.MODE_PRIVATE)
        .getString(EVIDENCE_ITEMS, "[]") ?: "[]"
    val result = mutableListOf<EvidenceRecord>()

    runCatching {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val record = EvidenceRecord(
                chamado = item.optString("chamado"),
                prefixo = item.optString("prefixo"),
                cidade = item.optString("cidade"),
                serial = item.optString("serial"),
                filePath = item.optString("filePath"),
                instalada = item.optBoolean("instalada", false)
            )
            if (
                record.chamado.isNotBlank() &&
                record.serial.isNotBlank() &&
                record.filePath.isNotBlank()
            ) {
                result.add(record)
            }
        }
    }

    return result
}

fun saveEvidenceRecord(
    context: Context,
    chamado: String,
    prefixo: String,
    cidade: String,
    serial: String,
    file: File,
    instalada: Boolean
) {
    val normalizedCall = chamado.trim()
    val normalizedSerial = serial.trim().uppercase()

    val items = loadEvidenceRecords(context)
        .filterNot {
            it.chamado == normalizedCall &&
                it.serial == normalizedSerial &&
                it.instalada == instalada
        }
        .toMutableList()

    items.add(
        EvidenceRecord(
            chamado = normalizedCall,
            prefixo = prefixo.trim(),
            cidade = cidade.trim(),
            serial = normalizedSerial,
            filePath = file.absolutePath,
            instalada = instalada
        )
    )

    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("chamado", item.chamado)
                .put("prefixo", item.prefixo)
                .put("cidade", item.cidade)
                .put("serial", item.serial)
                .put("filePath", item.filePath)
                .put("instalada", item.instalada)
        )
    }

    context.getSharedPreferences(EVIDENCE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(EVIDENCE_ITEMS, array.toString())
        .apply()
}

fun installedEvidenceForCall(
    context: Context,
    chamado: String
): List<EvidenceRecord> =
    loadEvidenceRecords(context)
        .filter {
            it.instalada &&
                it.chamado == chamado.trim() &&
                File(it.filePath).exists()
        }
