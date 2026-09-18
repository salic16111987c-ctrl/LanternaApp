package com.cilas.oncardiagnostico

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Interface simples: uma sessao, captura em primeiro plano, envio do TXT completo. */
class MainActivity : Activity() {
    private val clock = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var finish: Button
    private lateinit var share: Button
    private val refresh = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("capture", MODE_PRIVATE)
            val running = prefs.getBoolean("running", false)
            val uri = prefs.getString("saved_uri", null)
            start.isEnabled = !running
            finish.isEnabled = running
            share.isEnabled = !running && uri != null
            val tail = try {
                File(filesDir, "oncar_samsung_session.txt").takeIf { it.exists() }
                    ?.readLines()?.takeLast(16)?.joinToString("\n") ?: "Sem captura ainda."
            } catch (_: Exception) { "O arquivo local ainda nao esta disponivel." }
            status.text = (if (running) "CAPTURA ATIVA: pode abrir o OnCar original.\n" else if (uri != null) "RELATORIO SALVO: toque em COMPARTILHAR TXT.\n" else "Aguardando inicio.\n") + "\nULTIMOS EVENTOS:\n" + tail
            clock.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            this.text = value; textSize = size; setPadding(4, 8, 4, 12)
            root.addView(this)
        }
        fun button(value: String, task: () -> Unit): Button = Button(this).apply {
            this.text = value; setOnClickListener { task() }; root.addView(this)
        }
        text("OnCar • Referencia Samsung", 25f)
        text("UMA UNICA SESSAO | HB20S blueMedia | Carro PARADO.\n\nInstale no Samsung que ja espelhava. Este app NAO espelha: registra a conexao do OnCar original enquanto voce troca de aplicativo. Nao coleta senhas nem envia dados sozinho.", 15f)
        button("1. AUTORIZAR PERMISSOES") { askPermissions() }
        start = button("2. INICIAR CAPTURA") { command(CaptureService.START); Toast.makeText(this, "Captura iniciada. Agora use OnCar original.", Toast.LENGTH_LONG).show() }
        button("3. MARCAR: VOU ABRIR O ONCAR") { command(CaptureService.MARK, "Vou abrir o OnCar original / iniciar na central") }
        button("4. MARCAR: ESPELHAMENTO FUNCIONOU") { command(CaptureService.MARK, "RESULTADO: espelhamento FUNCIONOU") }
        button("5. MARCAR: APARECEU ERRO") { command(CaptureService.MARK, "RESULTADO: central mostrou ERRO") }
        button("CAPTURAR ESTADO AGORA") { command(CaptureService.SNAPSHOT) }
        finish = button("6. FINALIZAR E SALVAR TXT") { command(CaptureService.STOP); Toast.makeText(this, "Salvando em Downloads. Aguarde alguns segundos.", Toast.LENGTH_LONG).show() }
        share = button("7. COMPARTILHAR TXT COMPLETO") { shareTxt() }
        button("RECUPERAR RELATORIO, SE O APP FECHOU") { command(CaptureService.RECOVER) }
        text("Roteiro: autorize → inicie → marque que vai abrir → abra o Quick Connect/OnCar ORIGINAL do Samsung e conecte à blueMedia → volte e marque FUNCIONOU ou ERRO → finalize → compartilhe o arquivo .txt. Deixe a notificacao da captura ativa. Nao feche o app pela opcao Forcar parada.", 14f)
        text("Coleta: Android/modelo, permissões, versoes de apps relacionados, estados/UUIDs Bluetooth relacionados, redes e rotas IP, interfaces Wi-Fi/hotspot, eventos Wi-Fi Direct e alteracoes cronologicas. Limitacoes: Android nao fornece senhas, pacotes de outros apps ou logs internos da central a aplicativos comuns. Confira o TXT antes de compartilhar: contem IPs e identificadores da central.", 12f)
        status = text("Aguardando...", 13f).apply { setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun command(action: String, marker: String? = null) {
        val i = Intent(this, CaptureService::class.java).setAction(action)
        if (marker != null) i.putExtra("marker", marker)
        try {
            if (action == CaptureService.START && Build.VERSION.SDK_INT >= 26) startForegroundService(i)
            else startService(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Nao foi possivel iniciar: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askPermissions() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            p.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) Toast.makeText(this, "Permissoes concedidas. Ative tambem a Localizacao do sistema para Wi-Fi Direct.", Toast.LENGTH_LONG).show()
        else requestPermissions(missing.toTypedArray(), 901)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 901) Toast.makeText(this, "Permissoes autorizadas: ${results.count { it == PackageManager.PERMISSION_GRANTED }}/${results.size}. Ative a Localizacao nas Configuracoes.", Toast.LENGTH_LONG).show()
    }

    private fun shareTxt() {
        val value = getSharedPreferences("capture", MODE_PRIVATE).getString("saved_uri", null)
        if (value == null) { Toast.makeText(this, "Finalize para salvar o TXT primeiro.", Toast.LENGTH_LONG).show(); return }
        val uri = Uri.parse(value)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "Relatorio OnCar Samsung", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(i, "Enviar arquivo TXT para ChatGPT")) }
        catch (_: Exception) { Toast.makeText(this, "Arquivo salvo em Downloads; envie-o pelo gerenciador de arquivos.", Toast.LENGTH_LONG).show() }
    }
    override fun onResume() { super.onResume(); clock.removeCallbacks(refresh); clock.post(refresh) }
    override fun onPause() { clock.removeCallbacks(refresh); super.onPause() }
}
