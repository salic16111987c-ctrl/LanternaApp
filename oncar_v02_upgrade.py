from pathlib import Path
root=Path('OnCarDiagnostico')
p=root/'android/app/src/main/java/com/cilas/oncardiagnostico/MainActivity.kt'
s=p.read_text()
def swap(old,new):
 global s
 assert old in s, 'Nao achou: '+old[:50]
 s=s.replace(old,new,1)
swap('import android.content.ContentValues','import android.content.ContentValues\nimport android.content.ClipData')
swap('import android.net.NetworkCapabilities','import android.net.NetworkCapabilities\nimport android.net.NetworkRequest\nimport android.net.Uri')
swap('import android.net.wifi.WifiManager','import android.net.wifi.WifiManager\nimport android.net.wifi.p2p.WifiP2pManager')
swap('import android.os.Environment','import android.os.Environment\nimport android.os.Handler\nimport android.os.Looper')
swap('Executors.newCachedThreadPool()', 'Executors.newSingleThreadExecutor()')
swap('    private var running = false\n    private var lastReport = ""\n    private var networkCallback: ConnectivityManager.NetworkCallback? = null','    @Volatile private var running = false\n    private var lastReport = ""\n    private var reportUri: Uri? = null\n    private var wifiCallback: ConnectivityManager.NetworkCallback? = null\n    private val clock = Handler(Looper.getMainLooper())\n    private var samples = 0\n    private val ticker = object : Runnable {\n        override fun run() {\n            if (!running) return\n            ioExecutor.execute { if (running) collectSnapshot("AMOSTRA AUTOMÁTICA ${++samples}") }\n            clock.postDelayed(this, 10000)\n        }\n    }\n    private var networkCallback: ConnectivityManager.NetworkCallback? = null')
swap('        stopObservers()\n        ioExecutor.shutdownNow()','        clock.removeCallbacks(ticker)\n        stopObservers()\n        ioExecutor.shutdownNow()')
swap('Redmi Note 13 → blueMedia HB20S', 'v0.2 • Redmi Note 13 → blueMedia HB20S')
swap('Esta primeira versão não espelha ainda. Ela registra Bluetooth, Wi‑Fi, IP, gateway e tenta identificar serviços/portas da central.', 'Diagnóstico apenas; NÃO espelha. Registra redes e possível Wi-Fi Direct. O gateway da rede doméstica NÃO é presumido como blueMedia.')
swap('3. TESTAR CENTRAL / REDE', '3. CAPTURAR REDES / WI-FI DIRECT')
swap('4. FINALIZAR E SALVAR RELATÓRIO','4. FINALIZAR E SALVAR TXT COMPLETO')
swap('COMPARTILHAR RELATÓRIO','5. ENVIAR ARQUIVO TXT')
swap('Como testar: pareie o Redmi com a blueMedia, toque em INICIAR, depois abra OnCar na central e aguarde cerca de 60 segundos. Em seguida toque em TESTAR CENTRAL e depois FINALIZAR.', 'Com o carro PARADO: inicie, tente OnCar na central, aguarde 30-60 segundos, toque em CAPTURAR e FINALIZAR. Envie o ARQUIVO TXT do Downloads, não copie o texto da tela.')
swap('O aplicativo só testa a rede local e os dispositivos aos quais o telefone está conectado.', 'Na tela aparecem só as últimas linhas. O arquivo TXT salvo tem o histórico completo.')
swap('        lastReport = ""\n        shareButton.isEnabled','        lastReport = ""\n        reportUri = null\n        samples = 0\n        clock.removeCallbacks(ticker)\n        shareButton.isEnabled')
swap('=== ONCAR DIAGNÓSTICO v0.1 ===','=== ONCAR DIAGNÓSTICO v0.2 ===')
swap('        ioExecutor.execute { collectSnapshot("SNAPSHOT INICIAL") }\n        render','        ioExecutor.execute { collectSnapshot("SNAPSHOT INICIAL"); wifiDirectInfo() }\n        clock.postDelayed(ticker, 10000)\n        render')
swap('            appendLog("Observador de rede ativo.")','            appendLog("Observador da rede padrão ativo.")')
swap('        }\n    }\n\n    private fun stopObservers()','''        }
        try {
            val request=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val cb=object:ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { appendLog("Rede Wi-Fi detectada: $network"); ioExecutor.execute { allNetworks() } }
                override fun onLost(network: Network) { appendLog("Rede Wi-Fi perdida: $network") }
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                    appendLog("Wi-Fi $network: iface=${lp.interfaceName}; IP=${lp.linkAddresses.joinToString { it.toString() }}")
                }
            }
            cm.registerNetworkCallback(request,cb)
            wifiCallback=cb
            appendLog("Observador de redes Wi-Fi adicionais ativo.")
        } catch (e:Exception) { appendLog("Observador Wi-Fi adicional indisponível: ${e.javaClass.simpleName}") }
    }

    private fun stopObservers()''')
swap('        networkCallback = null\n    }\n\n    private fun collectSnapshot','''        networkCallback = null
        wifiCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (_:Exception) {} }
        wifiCallback=null
    }

    private fun allNetworks() {
        try {
            val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            appendLog("Redes visíveis: ${cm.allNetworks.size}, ativa=${cm.activeNetwork}")
            cm.allNetworks.forEach { network ->
                val lp=cm.getLinkProperties(network)
                val caps=cm.getNetworkCapabilities(network)
                appendLog("Rede $network: ${caps?.let { transportNames(it) } ?: "?"}, iface=${lp?.interfaceName}, IP=${lp?.linkAddresses?.joinToString { it.toString() } ?: "?"}, gateway=${lp?.let { defaultGateway(it)?.hostAddress } ?: "não detectado"}")
            }
            appendLog("Nota: algumas interfaces Wi-Fi Direct não aparecem na API de redes.")
        } catch (e:Exception) { appendLog("Listagem de redes indisponível: ${e.javaClass.simpleName}") }
    }

    private fun wifiDirectInfo() {
        try {
            val p2p=getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (p2p==null) { appendLog("Wi-Fi Direct: serviço indisponível"); return }
            val channel=p2p.initialize(this,mainLooper,null)
            p2p.requestConnectionInfo(channel) { info ->
                appendLog("Wi-Fi Direct: grupo formado=${info.groupFormed}, dono=${info.isGroupOwner}, IP do dono=${info.groupOwnerAddress?.hostAddress ?: "não informado"}")
            }
            p2p.requestGroupInfo(channel) { group ->
                if (group==null) appendLog("Wi-Fi Direct: nenhum grupo visível")
                else appendLog("Wi-Fi Direct: nome=${group.networkName}, iface=${group.`interface`}, dono=${group.isGroupOwner}, clientes=${group.clientList.size}; senha NÃO registrada")
            }
        } catch (e:Exception) { appendLog("Wi-Fi Direct indisponível: ${e.javaClass.simpleName} (${e.message ?: ""})") }
    }

    private fun collectSnapshot''')
swap('        collectWifiAndNetwork()\n        collectBluetooth()','        collectWifiAndNetwork()\n        allNetworks()\n        collectBluetooth()')
swap('                if (ips.isNotBlank()) appendLog("Iface ${nif.name}: $ips")','                if (ips.isNotBlank()) appendLog("Iface ${nif.name}: $ips, ativa=${nif.isUp} (wlan1 não comprova ligação à blueMedia)")')
a=s.index('    private fun runCentralTest() {'); b=s.index('    private fun scanQuickPorts(',a)
s=s[:a]+'''    private fun runCentralTest() {
        if (!running) return
        testButton.isEnabled=false
        render("Capturando informações sem sondar o roteador residencial...")
        ioExecutor.execute {
            collectSnapshot("CAPTURA MANUAL")
            wifiDirectInfo()
            appendLog("Gateway da rede residencial NÃO é considerado central; portas NÃO sondadas.")
            runOnUiThread { testButton.isEnabled=running; render("Captura concluída. Finalize e envie o TXT inteiro.") }
        }
    }

'''+s[b:]
swap('        if (!running) return\n        ioExecutor.execute {\n            collectSnapshot("SNAPSHOT FINAL")','        if (!running) return\n        running=false\n        clock.removeCallbacks(ticker)\n        stopButton.isEnabled=false\n        testButton.isEnabled=false\n        ioExecutor.execute {\n            collectSnapshot("SNAPSHOT FINAL")')
swap('            appendLog("Fim: ${now()}")\n            stopObservers()','            wifiDirectInfo()\n            appendLog("Fim: ${now()}")\n            stopObservers()')
swap('                render(if (saved != null) "Relatório salvo em Downloads: $saved" else "Relatório pronto. Use COMPARTILHAR RELATÓRIO.")','                render(if (saved != null) "TXT COMPLETO salvo em Downloads: $saved. Toque em 5. ENVIAR ARQUIVO TXT." else "Falha ao salvar. Use botão 5 para compartilhar texto.")')
swap('                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }\n                name','                (contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Sem saída para arquivo")).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }\n                reportUri=uri\n                name')
a=s.index('    private fun shareReport() {');b=s.index('    private fun currentGateway()',a)
s=s[:a]+'''    private fun shareReport() {
        if (lastReport.isBlank()) return
        val uri=reportUri
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_SUBJECT,"Relatório OnCar v0.2")
            if (uri!=null) {
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newUri(contentResolver,"OnCar TXT",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else putExtra(Intent.EXTRA_TEXT,lastReport)
        }
        startActivity(Intent.createChooser(intent,"Enviar arquivo TXT completo"))
    }

'''+s[b:]
swap('            if (log.length > 250_000) log.delete(0, 50_000)','            // Histórico integral mantido até salvar; somente a visualização é resumida.')
swap('            val preview = synchronized(log) { log.takeLast(9000) }\n            status.text = preview','            val preview = synchronized(log) { log.takeLast(2300) }\n            status.text = "PRÉVIA (envie o TXT completo, não copie esta tela):\\n\\n$preview"')
swap('        val current = synchronized(log) { log.takeLast(6500) }','        val current = synchronized(log) { log.takeLast(2300) }')
swap('        status.text = "$message\\n\\n$current"','        status.text = "$message\\n\\nPRÉVIA DAS ÚLTIMAS LINHAS:\\n$current"')
p.write_text(s)
b=root/'android/app/build.gradle.kts';text=b.read_text()
assert 'versionCode = 1' in text
b.write_text(text.replace('versionCode = 1','versionCode = 2').replace('versionName = "0.1"','versionName = "0.2"'))
print('UPGRADE OK',len(s),s.count('\n'))