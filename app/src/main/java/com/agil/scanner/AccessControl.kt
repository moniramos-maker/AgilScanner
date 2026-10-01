package com.agil.scanner

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val ACCESS_BASE_URL = "https://agilscanner.vercel.app"
private val AccessBg = Color(0xFFF4F6F9)
private val AccessDark = Color(0xFF101722)
private val AccessMuted = Color(0xFF687384)

private data class TechnicianProfile(
    val name: String,
    val company: String,
    val phone: String,
    val region: String,
    val project: String
)

private fun deviceId(context: Context): String {
    val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    val source = raw + "|" + context.packageName
    return MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun prefs(context: Context) =
    context.getSharedPreferences("agil_access", Context.MODE_PRIVATE)

private fun loadProfile(context: Context): TechnicianProfile? {
    val p = prefs(context)
    val name = p.getString("name", null) ?: return null
    return TechnicianProfile(
        name = name,
        company = p.getString("company", "") ?: "",
        phone = p.getString("phone", "") ?: "",
        region = p.getString("region", "") ?: "",
        project = p.getString("project", "Banco do Brasil - SONDA") ?: "Banco do Brasil - SONDA"
    )
}

private fun saveProfile(context: Context, p: TechnicianProfile) {
    prefs(context).edit()
        .putString("name", p.name)
        .putString("company", p.company)
        .putString("phone", p.phone)
        .putString("region", p.region)
        .putString("project", p.project)
        .apply()
}

private fun saveApprovedCache(context: Context, approved: Boolean) {
    prefs(context).edit()
        .putBoolean("lastKnownApproved", approved)
        .putLong("lastApprovalCheckAt", System.currentTimeMillis())
        .apply()
}

private fun hasApprovedCache(context: Context): Boolean =
    prefs(context).getBoolean("lastKnownApproved", false)


private fun postJson(path: String, json: JSONObject): JSONObject {
    val conn = (URL(ACCESS_BASE_URL + path).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15000
        readTimeout = 20000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    conn.outputStream.use { it.write(json.toString().toByteArray()) }
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    conn.disconnect()
    return JSONObject(if (text.isBlank()) "{}" else text)
}

private fun registerDevice(context: Context, profile: TechnicianProfile): String {
    val json = JSONObject()
        .put("deviceId", deviceId(context))
        .put("name", profile.name)
        .put("company", profile.company)
        .put("phone", profile.phone)
        .put("region", profile.region)
        .put("project", profile.project)
        .put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL)
    val result = postJson("/api/device-register", json)
    if (!result.optBoolean("ok", false)) error(result.optString("error", "Falha no cadastro."))
    return result.optString("status", "pending")
}

private fun checkDevice(context: Context): String {
    val json = JSONObject().put("deviceId", deviceId(context))
    val result = postJson("/api/device-status", json)
    if (!result.optBoolean("ok", false)) error(result.optString("error", "Falha ao validar acesso."))
    return result.optString("status", "unregistered")
}

@Composable
fun AccessControlledApp(content: @Composable (offlineOnly: Boolean) -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(loadProfile(context)) }
    var status by remember { mutableStateOf(if (profile == null) "unregistered" else "checking") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun refreshStatus() {
        busy = true
        message = ""
        Thread {
            try {
                val s = checkDevice(context)
                (context as? android.app.Activity)?.runOnUiThread {
                    if (s == "approved") saveApprovedCache(context, true)
                    if (s == "blocked" || s == "rejected" || s == "expired") saveApprovedCache(context, false)
                    status = s
                    busy = false
                }
            } catch (_: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    if (hasApprovedCache(context)) {
                        message = "Sem conexão. Modo offline liberado somente para digitalização local."
                        status = "offline-approved"
                    } else {
                        message = "Não foi possível validar o acesso. Verifique a internet."
                        status = "error"
                    }
                    busy = false
                }
            }
        }.start()
    }

    LaunchedEffect(profile) {
        if (profile != null) refreshStatus()
    }

    if (status == "approved") {
        content(false)
        return
    }

    if (status == "offline-approved") {
        content(true)
        return
    }

    AccessScreen(
        profile = profile,
        status = status,
        busy = busy,
        message = message,
        onSubmit = { p ->
            busy = true
            message = ""
            Thread {
                try {
                    val s = registerDevice(context, p)
                    saveProfile(context, p)
                    (context as? android.app.Activity)?.runOnUiThread {
                        profile = p
                        status = s
                        busy = false
                    }
                } catch (e: Exception) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        message = e.message ?: "Não foi possível enviar o cadastro."
                        busy = false
                    }
                }
            }.start()
        },
        onRefresh = { refreshStatus() }
    )
}

@Composable
private fun AccessScreen(
    profile: TechnicianProfile?,
    status: String,
    busy: Boolean,
    message: String,
    onSubmit: (TechnicianProfile) -> Unit,
    onRefresh: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var company by remember(profile) { mutableStateOf(profile?.company ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var region by remember(profile) { mutableStateOf(profile?.region ?: "") }
    var project by remember(profile) { mutableStateOf(profile?.project ?: "Banco do Brasil - SONDA") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AccessBg)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ÁGIL SCANNER", color = AccessDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text("Controle de acesso", color = AccessMuted)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (status) {
                    "pending", "checking", "error" -> {
                        Text(
                            if (status == "checking") "Validando seu acesso..."
                            else "Cadastro aguardando aprovação da Ágil.",
                            fontWeight = FontWeight.Bold,
                            color = AccessDark
                        )
                        profile?.let {
                            Text("${it.name}\n${it.region} • ${it.project}", color = AccessMuted)
                        }
                        if (message.isNotBlank()) Text(message, color = AccessMuted)
                        Button(
                            onClick = onRefresh,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccessDark, contentColor = Color.White)
                        ) { Text(if (busy) "VALIDANDO..." else "VERIFICAR LIBERAÇÃO") }
                    }

                    "blocked", "rejected", "expired" -> {
                        val title = when (status) {
                            "expired" -> "Acesso expirado"
                            "rejected" -> "Cadastro não autorizado"
                            else -> "Acesso bloqueado"
                        }
                        Text(title, fontWeight = FontWeight.Bold, color = AccessDark, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Este aparelho não está autorizado a utilizar o ÁGIL Scanner. Entre em contato com a Ágil.",
                            color = AccessMuted
                        )
                        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("VERIFICAR NOVAMENTE") }
                    }

                    else -> {
                        Text("Solicitar acesso", fontWeight = FontWeight.Bold, color = AccessDark, style = MaterialTheme.typography.titleLarge)
                        Text("Preencha seus dados. O acesso será liberado pela equipe Ágil.", color = AccessMuted)
                        OutlinedTextField(name, { name = it }, label = { Text("Nome do técnico") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(company, { company = it }, label = { Text("Empresa / Prestador") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(region, { region = it }, label = { Text("Estado / Região") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(project, { project = it }, label = { Text("Projeto") }, modifier = Modifier.fillMaxWidth())

                        Button(
                            onClick = {
                                if (name.isNotBlank() && region.isNotBlank() && project.isNotBlank()) {
                                    onSubmit(TechnicianProfile(name.trim(), company.trim(), phone.trim(), region.trim(), project.trim()))
                                }
                            },
                            enabled = !busy && name.isNotBlank() && region.isNotBlank() && project.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccessDark, contentColor = Color.White)
                        ) { Text(if (busy) "ENVIANDO..." else "ENVIAR PARA APROVAÇÃO") }

                        if (message.isNotBlank()) {
                            Text(message, color = AccessMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
