package com.cilas.oncardiagnostico

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import java.io.File
import java.net.NetworkInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.Executors

/** Diagnostico passivo autorizado: sem escanear portas, sem capturar trafego, sem senhas. */
class CaptureService : Service() {
    companion object {
        const val START = "com.cilas.oncar.START"
        const val MARK = "com.cilas.oncar.MARK"
        const val SNAPSHOT = "com.cilas.oncar.SNAPSHOT"
        const val STOP = "com.cilas.oncar.STOP"
        const val RECOVER = "com.cilas.oncar.RECOVER"
        private const val CHANNEL = "oncar_ref_capture"
    }
    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor()
    private val clock = Handler(Looper.getMainLooper())
    private val changes = mutableMapOf<String, String>()
    @Volatile private var recording = false
    private var startedAt = ""
    private var sequence = 0
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var receiverRegistered = false
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var btAdapter: BluetoothAdapter? = null
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            worker.execute { if (recording) { sequence++; snapshot("AMOSTRA $sequence", false) } }
            clock.postDelayed(this, 5000)
        }
    }
    private fun prefs() = getSharedPreferences("capture", MODE_PRIVATE)
    private fun file() = File(filesDir, "oncar_samsung_session.txt")
    private fun time() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
    private fun log(value: String) {
        synchronized(lock) {
            try { file().appendText("[${time()}] $value\n", Charsets.UTF_8) }
            catch (_: Exception) { /* Armazenamento interno pode estar indisponivel. */ }
        }
    }
    private fun changed(key: String, value: String, force: Boolean = false) {
        if (!recording) return
        synchronized(changes) {
            if (force || changes[key] != value) { changes[key] = value; log("$key = $value") }
        }
    }
    private fun error(context: String, e: Exception) = log("INFORMACAO BLOQUEADA [$context]: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}")

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> startRecording()
            MARK -> if (recording) {
                log("================ MARCADOR DO USUARIO: ${intent.getStringExtra("marker")?.take(120) ?: "sem texto"} ================")
                worker.execute { snapshot("NO MOMENTO DO MARCADOR", true) }
            }
            SNAPSHOT -> if (recording) worker.execute { snapshot("CAPTURA MANUAL", true) }
            STOP -> if (recording) stopRecording() else stopSelf()
            RECOVER -> if (!recording) worker.execute { exportText(); stopSelf() }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun foreground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "OnCar: captura de referencia", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("OnCar Samsung: captura ativa")
            .setContentText("Retorne ao aplicativo para finalizar e salvar o TXT")
            .setContentIntent(open).setOngoing(true).build()
        startForeground(207, notification)
    }
    private fun startRecording() {
        if (recording) return
        try { foreground() }
        catch (e: Exception) { prefs().edit().putBoolean("running", false).apply(); stopSelf(); return }
        synchronized(lock) { file().writeText("=== ONCAR REFERENCIA SAMSUNG 1.0 ===\n", Charsets.UTF_8) }
        changes.clear(); sequence = 0; recording = true
        startedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        prefs().edit().putBoolean("running", true).remove("saved_uri").apply()
        log("INICIO - sessao unica. Dados ficam no telefone ate o usuario compartilhar.")
        log("Privacidade: nao coleta senha, GPS, contatos, microfone, tela, trafego de outros apps ou logs internos da central.")
        log("Aparelho=${Build.MANUFACTURER} ${Build.MODEL}; produto=${Build.PRODUCT}; Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}; compilacao=${Build.DISPLAY}")
        log("Permissoes: localizacao=${granted(Manifest.permission.ACCESS_FINE_LOCATION)}; Bluetooth Connect=${if (Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_CONNECT) else "legado"}; WiFi Nearby=${if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.NEARBY_WIFI_DEVICES) else "legado"}")
        try {
            val location = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            log("Localizacao do sistema ativada=${location.isLocationEnabled}; coordenadas NAO coletadas")
        } catch (e: Exception) { error("estado localizacao", e) }
        registerObservers()
        worker.execute { installedOnCarApps(); snapshot("ANTES DA CONEXAO", true) }
        clock.postDelayed(tick, 5000)
    }
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun stopRecording() {
        clock.removeCallbacks(tick)
        removeObservers()
        worker.execute {
            snapshot("FINAL", true)
            log("FIM DO TESTE. Resultados dependem das permissoes e da visibilidade do Android.")
            recording = false
            exportText()
            prefs().edit().putBoolean("running", false).apply()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
        }
    }
    private fun exportText() {
        try {
            val source = file()
            if (!source.exists() || source.length() == 0L) { log("Sem relatorio para exportar"); return }
            val name = "OnCar_Referencia_Samsung_${if (startedAt.isNotBlank()) startedAt else LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.txt"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads nao permitiu criar arquivo")
            try {
                val bytes = synchronized(lock) { source.readBytes() }
                (contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Sem saida")).use { it.write(bytes) }
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                prefs().edit().putString("saved_uri", uri.toString()).apply()
                log("TXT exportado para Downloads: $name (${bytes.size} bytes)")
            } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
        } catch (e: Exception) { error("exportacao: arquivo interno preservado; usar RECUPERAR", e) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!recording) return
            val action = intent?.action ?: return
            try {
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED, BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        @Suppress("DEPRECATION") val dev = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val name = try { dev?.name ?: "desconhecido" } catch (_: Exception) { "sem permissao" }
                        if (name.contains("HB20", true) || name.contains("blue", true) || name.contains("oncar", true))
                            log("EVENTO BT: $action; dispositivo=$name; endereco=${dev?.address}; estado=${intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)}; anterior=${intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)}")
                        else log("EVENTO BT de outro dispositivo (identificacao omitida): $action")
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION,
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        log("EVENTO Wi-Fi Direct: $action; estado=${intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)}")
                        wifiDirect()
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        log("EVENTO Wi-Fi: $action; estado=${intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)}")
                        worker.execute { snapshot("APOS EVENTO WIFI", false) }
                    }
                }
            } catch (e: Exception) { error("broadcast $action", e) }
        }
    }
    private fun registerObservers() {
        try {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED); addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            else registerReceiver(receiver, f)
            receiverRegistered = true
        } catch (e: Exception) { error("registro broadcasts", e) }
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { log("REDE padrao DISPONIVEL: $network"); worker.execute { snapshot("REDE PADRAO DISPONIVEL", false) } }
                override fun onLost(network: Network) { log("REDE padrao PERDIDA: $network") }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { changed("Padrao $network capacidades", capSummary(caps)) }
                override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) { changed("Padrao $network IP", linkSummary(props)) }
            }
            cm.registerDefaultNetworkCallback(cb); defaultNetworkCallback = cb
            val wb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { log("REDE Wi-Fi DISPONIVEL: $network"); worker.execute { snapshot("WIFI DISPONIVEL", false) } }
                override fun onLost(network: Network) { log("REDE Wi-Fi PERDIDA: $network") }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { changed("Wifi $network capacidades", capSummary(caps)) }
                override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) { changed("Wifi $network IP", linkSummary(props)) }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wb)
            wifiNetworkCallback = wb
        } catch (e: Exception) { error("rede callbacks", e) }
        try {
            btAdapter = BluetoothAdapter.getDefaultAdapter()
            val a = btAdapter
            if (a != null && (Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT))) {
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        synchronized(profiles) { profiles[profile] = proxy }
                        bluetoothProfiles()
                    }
                    override fun onServiceDisconnected(profile: Int) { synchronized(profiles) { profiles.remove(profile) }; log("Perfil BT desconectou do servico: $profile") }
                }
                a.getProfileProxy(this, listener, BluetoothProfile.A2DP)
                a.getProfileProxy(this, listener, BluetoothProfile.HEADSET)
                a.getProfileProxy(this, listener, BluetoothProfile.PAN)
            }
        } catch (e: Exception) { error("proxy perfis BT", e) }
        try {
            val p = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            p2pChannel = p?.initialize(this, Looper.getMainLooper()) { log("Canal Wi-Fi Direct desconectado") }
            log("Monitor Wi-Fi Direct=${if (p2pChannel == null) "indisponivel" else "iniciado"}")
        } catch (e: Exception) { error("inicializacao p2p", e) }
    }
    private fun removeObservers() {
        clock.removeCallbacks(tick)
        try { if (receiverRegistered) unregisterReceiver(receiver) } catch (_: Exception) {}
        receiverRegistered = false
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            defaultNetworkCallback?.let { cm.unregisterNetworkCallback(it) }
            wifiNetworkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        defaultNetworkCallback = null; wifiNetworkCallback = null
        try {
            synchronized(profiles) { profiles.forEach { (id, proxy) -> btAdapter?.closeProfileProxy(id, proxy) }; profiles.clear() }
        } catch (_: Exception) {}
    }
    private fun capSummary(c: NetworkCapabilities): String {
        val types = listOf(NetworkCapabilities.TRANSPORT_WIFI to "WiFi", NetworkCapabilities.TRANSPORT_CELLULAR to "Celular", NetworkCapabilities.TRANSPORT_BLUETOOTH to "Bluetooth", NetworkCapabilities.TRANSPORT_ETHERNET to "Ethernet", NetworkCapabilities.TRANSPORT_VPN to "VPN")
        return "tipo=${types.filter { c.hasTransport(it.first) }.joinToString("+") { it.second }}, INTERNET=${c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}, VALIDATED=${c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}, local=${c.transportInfo?.javaClass?.simpleName ?: "nao exposto"}"
    }
    private fun linkSummary(p: LinkProperties): String {
        val routes = p.routes.joinToString(" | ") { "${it.destination} via ${it.gateway?.hostAddress ?: "direta"}" }
        return "iface=${p.interfaceName}, IP=${p.linkAddresses.joinToString()}, gateway/rotas=$routes, DNS=${p.dnsServers.joinToString { it.hostAddress ?: "?" }}"
    }
    private fun snapshot(title: String, force: Boolean) {
        if (!recording) return
        if (force) log("========== $title ==========")
        else if (sequence % 6 == 0) log("Captura ativa: amostra $sequence")
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            changed("Rede padrao", cm.activeNetwork?.toString() ?: "nenhuma", force)
            val nets = cm.allNetworks.map { n ->
                val c = cm.getNetworkCapabilities(n)
                val lp = cm.getLinkProperties(n)
                "rede=$n ${c?.let { capSummary(it) } ?: "sem capacidades"}; ${lp?.let { linkSummary(it) } ?: "sem link"}"
            }.sorted()
            changed("Redes Android (${nets.size})", nets.joinToString(" || ").ifBlank { "nenhuma" }, force)
        } catch (e: Exception) { error("redes Android", e) }
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val info = wm.connectionInfo
            changed("Wi-Fi local", "ligado=${wm.isWifiEnabled}; SSID=${info?.ssid ?: "nao exposto"}; BSSID=${info?.bssid ?: "nao exposto"}; IP=${info?.ipAddress ?: 0}; banda/frequencia=${info?.frequency ?: -1} MHz; RSSI=${info?.rssi ?: -127}; velocidade=${info?.linkSpeed ?: -1} Mbps", force)
        } catch (e: Exception) { error("Wi-Fi local", e) }
        try {
            val list = Collections.list(NetworkInterface.getNetworkInterfaces()).filter {
                val n = it.name.lowercase()
                n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("p2p") || n.startsWith("ap") || n.startsWith("eth") || n.startsWith("bt") || n.startsWith("rmnet")
            }.map { n -> "${n.name}: ligada=${n.isUp}, IP=${Collections.list(n.inetAddresses).joinToString { it.hostAddress ?: "?" }}" }
            changed("Interfaces de rede", list.joinToString(" | ").ifBlank { "nenhuma" }, force)
        } catch (e: Exception) { error("interfaces", e) }
        bluetoothState(force)
        wifiDirect()
    }
    private fun bluetoothState(force: Boolean) {
        try {
            val a = btAdapter ?: BluetoothAdapter.getDefaultAdapter()
            if (a == null) { changed("Bluetooth", "hardware nao disponivel", force); return }
            if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                changed("Bluetooth", "BLUETOOTH_CONNECT nao concedida", force); return
            }
            changed("Bluetooth ligado", "${a.isEnabled}", force)
            val relevant = a.bondedDevices.filter { relevant(it.name) }.map { d ->
                "${d.name} endereco=${d.address}; vinculo=${d.bondState}; UUIDs=${d.uuids?.joinToString { it.uuid.toString() } ?: "nao expostos"}"
            }
            changed("Bluetooth pareado relevante", relevant.joinToString(" | ").ifBlank { "HB20/OnCar nao encontrado entre pareados" }, force)
            bluetoothProfiles(force)
        } catch (e: Exception) { error("estado bluetooth", e) }
    }
    private fun relevant(name: String?) = name?.let { it.contains("HB20", true) || it.contains("blueMedia", true) || it.contains("oncar", true) || it.contains("hyundai", true) } ?: false
    private fun bluetoothProfiles(force: Boolean = false) {
        try {
            val state = synchronized(profiles) { profiles.map { (id, p) ->
                val label = when (id) { BluetoothProfile.A2DP -> "A2DP(musica)"; BluetoothProfile.HEADSET -> "HEADSET(chamada)"; BluetoothProfile.PAN -> "PAN(rede)"; else -> "$id" }
                val devices = p.connectedDevices.filter { relevant(it.name) }.joinToString { "${it.name} ${it.address}" }
                "$label=${devices.ifBlank { "sem HB20 conectado" }}"
            }.sorted() }
            changed("Perfis BT conectados", state.joinToString("; ").ifBlank { "aguardando proxies" }, force)
        } catch (e: Exception) { error("perfis bluetooth", e) }
    }
    private fun wifiDirect() {
        val p = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager ?: return
        val channel = p2pChannel ?: return
        try {
            p.requestConnectionInfo(channel) { i ->
                changed("Wi-Fi Direct conexao", "grupo=${i?.groupFormed}; sou dono=${i?.isGroupOwner}; IP dono=${i?.groupOwnerAddress?.hostAddress ?: "nao informado"}")
            }
            p.requestGroupInfo(channel) { g ->
                if (g == null) changed("Wi-Fi Direct grupo", "nenhum grupo visivel")
                else changed("Wi-Fi Direct grupo", "SSID=${g.networkName}; interface=${g.`interface`}; sou dono=${g.isGroupOwner}; dono=${g.owner?.deviceName ?: "?"} ${g.owner?.deviceAddress ?: "?"}; clientes=${g.clientList.joinToString { "${it.deviceName} ${it.deviceAddress}" }}; senha NAO coletada")
            }
        } catch (e: Exception) { error("Wi-Fi Direct: verificar permissao CHANGE_WIFI_STATE/NEARBY/Localizacao", e) }
    }
    private fun installedOnCarApps() {
        try {
            val pm = packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION") val activities = pm.queryIntentActivities(query, 0)
            val matches = activities.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinct()
                .filter { (pkg, label) ->
                    val v = "$pkg $label".lowercase()
                    listOf("oncar", "quick connect", "ubridge", "hyundai", "mirrorlink", "drive link", "drivelink", "carlink").any { v.contains(it) }
                }
            log("APPS RELACIONADOS com icone visivel=${matches.size}; Android pode ocultar apps sem icone.")
            matches.forEach { (pkg, label) ->
                try {
                    @Suppress("DEPRECATION") val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                    val perms = info.requestedPermissions?.filter { it.contains("WIFI", true) || it.contains("BLUETOOTH", true) || it.contains("INTERNET", true) || it.contains("LOCATION", true) } ?: emptyList()
                    log("APP RELACIONADO: $label | pacote=$pkg | versao=${info.versionName} | permissoes de conexao declaradas=${perms.joinToString()}")
                } catch (e: Exception) { error("app relacionado $pkg", e) }
            }
        } catch (e: Exception) { error("apps relacionados", e) }
    }
    override fun onDestroy() {
        clock.removeCallbacks(tick)
        removeObservers()
        if (recording) {
            log("AVISO: sistema encerrou captura antes do botao FINALIZAR. Arquivo interno recuperavel.")
            recording = false
            prefs().edit().putBoolean("running", false).apply()
        }
        worker.shutdown()
        super.onDestroy()
    }
}
