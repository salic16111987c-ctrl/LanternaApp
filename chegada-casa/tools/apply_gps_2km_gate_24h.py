#!/usr/bin/env python3
"""Idempotent, fail-closed v2.4 source update; compilation and tests run in CI.

This changes location REQUEST priority, never changes the phone's system GPS setting.
Mock GPS is suitable for light/location tests but NEVER for physical gate testing.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/cilassouza/chegadacasa'

def edit(path, before, after):
    text = path.read_text(encoding='utf-8')
    if after in text:
        return
    if text.count(before) != 1:
        raise RuntimeError(f'Expected exactly one source anchor in {path.name}: {before[:100]!r}, found {text.count(before)}')
    path.write_text(text.replace(before, after, 1), encoding='utf-8')
    print('PATCH:', path.name, before[:45])

def segment(path, start, end, replacement):
    text = path.read_text(encoding='utf-8')
    if replacement in text:
        return
    if text.count(start) != 1 or text.count(end) != 1:
        raise RuntimeError('Unexpected source section: ' + str(path))
    a = text.index(start)
    b = text.index(end, a)
    path.write_text(text[:a] + replacement + text[b:], encoding='utf-8')
    print('PATCH SECTION:', path.name)

service = src / 'ArrivalMonitorService.java'
edit(service,
     '    private static final long DAY_INTERVAL_MS = 120000L;\n    private static final int MODE_DAY = 0, MODE_HOME = 1, MODE_FAST = 2;',
     '    private static final long FAR_INTERVAL_MS = 60000L;\n    private static final long NEAR_INTERVAL_MS = 20000L;\n    private static final int MODE_FAR = 0, MODE_HOME = 1, MODE_NEAR = 2, MODE_FAST = 3;')
edit(service, '    private int profileMode = -1;',
     '    private int profileMode = -1;\n    private Location lastMotionFix;\n    private float lastMotionDistance = -1f;')
profile = '''    /** No clock-based throttling: the gate must remain available 24 hours. */
    private int desiredProfile() {
        if (prefs.getBoolean("dentro", false)) return MODE_HOME;
        int distance = prefs.getInt("monitor_distance", -1);
        int accuracy = prefs.getInt("monitor_accuracy", 9999);
        long fixAt = prefs.getLong("monitor_fix_at", 0L);
        // Missing/stale/coarse position: sample economically but more often until known.
        if (distance < 0 || accuracy > 600 || fixAt <= 0L
                || Math.abs(System.currentTimeMillis() - fixAt) > 180000L) return MODE_NEAR;
        if (distance > 2600) return MODE_FAR;
        if (distance <= 250) return MODE_FAST; // Never miss the final approach.
        boolean moving = System.currentTimeMillis() < prefs.getLong("motion_until", 0L);
        boolean approaching = prefs.getBoolean("motion_toward", false);
        if (moving && (distance <= 2000 || (distance <= 2500 && approaching))) return MODE_FAST;
        return MODE_NEAR;
    }

    private long profileInterval(int mode) {
        if (mode == MODE_FAR) return FAR_INTERVAL_MS;
        if (mode == MODE_HOME) return HOME_INTERVAL_MS;
        if (mode == MODE_NEAR) return NEAR_INTERVAL_MS;
        return INTERVAL_MS;
    }

    private String profileName(int mode) {
        if (mode == MODE_FAR) return "ECONÔMICO: ALÉM DE 2 KM";
        if (mode == MODE_HOME) return "ECONÔMICO: EM CASA";
        if (mode == MODE_NEAR) return "OBSERVANDO MOVIMENTO / APROXIMAÇÃO";
        return "GPS PRECISO: APROXIMAÇÃO";
    }

    private void ensureProfile() {
        if (!foreground || !prefs.getBoolean("ativa", false)) return;
        if (callback == null) beginUpdates();
        else if (profileMode != desiredProfile()) restartUpdates();
    }

'''
segment(service, '    private int desiredProfile() {', '    private final Runnable watchdog = new Runnable() {', profile)
edit(service, '''        int priority = mode == MODE_FAST ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;''',
     '''        int priority = mode == MODE_FAST ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;''') if False else None
movement = '''        // Motion inferred from speed or successive fresh locations; no permanent sensor polling.
        long nowMotion = System.currentTimeMillis();
        boolean moving = loc.hasSpeed() && accuracy <= 250f && loc.getSpeed() >= 1.3f;
        boolean toward = false;
        if (lastMotionFix != null) {
            long dtMs = (loc.getElapsedRealtimeNanos() - lastMotionFix.getElapsedRealtimeNanos()) / 1000000L;
            float step = loc.distanceTo(lastMotionFix);
            float threshold = Math.max(35f, (accuracy + lastMotionFix.getAccuracy()) * 0.8f);
            if (dtMs > 0L && dtMs <= 300000L && accuracy <= 400f
                    && lastMotionFix.hasAccuracy() && lastMotionFix.getAccuracy() <= 400f
                    && step >= threshold) {
                moving = true;
                toward = lastMotionDistance >= 0f
                        && lastMotionDistance - distance >= Math.max(25f, accuracy * 0.35f);
            }
        }
        lastMotionFix = new Location(loc);
        lastMotionDistance = distance;
        SharedPreferences.Editor motion = prefs.edit();
        if (moving) motion.putLong("motion_until", nowMotion + 120000L)
                .putBoolean("motion_toward", toward);
        else if (nowMotion > prefs.getLong("motion_until", 0L))
            motion.putBoolean("motion_toward", false);
        motion.apply();
'''
edit(service, '        String status = profileName(profileMode) + " — "',
     movement + '        String status = profileName(profileMode) + " — "')
edit(service, '        notifyStatus(status);\n        if (accuracy > Math.max(75f, radius * 0.6f)) {',
     '        notifyStatus(status);\n        // Switching to high precision must not wait for a fine-accuracy fix.\n        handler.post(this::ensureProfile);\n        if (accuracy > Math.max(75f, radius * 0.6f)) {')

controller = src / 'ArrivalController.java'
night_block = '''        if (p.getBoolean("so_noite", false)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!(hour >= 18 || hour < 6)) {
                p.edit().putBoolean("dentro", true).putBoolean("outside_observed", false)
                        .putString("arrival_last_event", time() + " — chegada detectada, mas SOMENTE À NOITE ativado")
                        .apply();
                return;
            }
        }
'''
edit(controller, night_block,
     '        // "Somente à noite" filters LAMPS only, never the arrival or gate.\n')
edit(controller,
     '        boolean gate = !mockLocation && EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);',
     '        boolean gate = !mockLocation && !p.getBoolean("monitor_mock", false)\n                && EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);')
# Keep simulation explicitly manual and independent of the night checkbox.
edit(controller,
     '        String origin = simulated ? "SIMULAÇÃO" : "CHEGADA";\n        boolean session = EwelinkApi.hasSession(app);',
     '''        String origin = simulated ? "SIMULAÇÃO" : "CHEGADA";
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean lampTime = !p.getBoolean("so_noite", false) || hour >= 18 || hour < 6;
        if (!simulated && !lampTime) {
            p.edit().putString("arrival_last_command", time()
                    + " — Lâmpadas não acionadas: somente 18h–6h; portão independente").apply();
            if (gate) SpeechEngine.speak(app,
                    "Chegada detectada. Lâmpadas fora do horário. Deseja abrir o portão? Responda SIM ou NÃO na notificação.");
            else SpeechEngine.speak(app, "Chegada detectada. Lâmpadas fora do horário configurado.");
            return;
        }
        boolean session = EwelinkApi.hasSession(app);''')

main = src / 'MainActivity.java'
edit(main, '        soNoite.setText("Somente à noite (18h às 6h)");',
     '        soNoite.setText("Somente LÂMPADAS à noite (18h às 6h) — portão 24 horas");')
edit(main, '        root.addView(soNoite);',
     '''        root.addView(soNoite);
        root.addView(texto("GPS inteligente: econômico em casa e longe; precisão durante o movimento na aproximação de 2 km. Portão somente com SIM explícito.", 13, false));''')
edit(main,
     '''        GeofencingRequest req = new GeofencingRequest.Builder().addGeofence(g).build();''',
     '''        Geofence outer = new Geofence.Builder()
                .setRequestId("aproximacao_2500m")
                .setCircularRegion(lat, lon, 2500f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest req = new GeofencingRequest.Builder()
                .addGeofence(g).addGeofence(outer).build();''')

boot = src / 'BootReceiver.java'
edit(boot,
     '''        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(0).addGeofence(geofence).build();''',
     '''        Geofence outer = new Geofence.Builder().setRequestId("aproximacao_2500m")
                .setCircularRegion(lat, lon, 2500f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(0).addGeofence(geofence).addGeofence(outer).build();''')

receiver = src / 'GeofenceReceiver.java'
edit(receiver, 'import android.location.Location;',
     'import android.location.Location;\nimport java.util.List;')
edit(receiver,
     '''        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            ArrivalController.handleExit(context);
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Unknown origin is treated as untrusted for gate safety. Lights still work.
            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());
        }''',
     '''        List<Geofence> fences = event.getTriggeringGeofences();
        boolean homeFence = false;
        boolean outerFence = false;
        if (fences != null) for (Geofence fence : fences) {
            if ("casa".equals(fence.getRequestId())) homeFence = true;
            if ("aproximacao_2500m".equals(fence.getRequestId())) outerFence = true;
        }
        if (outerFence) {
            boolean entered = event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER;
            p.edit().putBoolean("outer_inside", entered)
                    .putString("outer_geofence_status", entered
                            ? "Entrou na preparação 2,5 km" : "Saiu da preparação 2,5 km").apply();
        }
        // Outer 2.5-km fence is NEVER a lamp or gate arrival trigger.
        if (!homeFence) return;
        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            ArrivalController.handleExit(context);
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Unknown origin is treated as untrusted for gate safety. Lights still work.
            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());
        }''')

diagnostic = src / 'ArrivalDiagnosticActivity.java'
edit(diagnostic, '        title.setText("DIAGNÓSTICO E VOZ — v2.1");',
     '        title.setText("DIAGNÓSTICO E VOZ — v2.4");')
edit(diagnostic,
     '                "\\nMonitor: " + p.getString("monitor_state", "ainda não iniciou") +',
     '''                "\\nPerfil GPS: " + p.getString("monitor_profile", "ainda não definido") +
                " | movimento: " + (System.currentTimeMillis() < p.getLong("motion_until", 0L) ? "detectado" : "parado/indefinido") +
                " | aproximando: " + (p.getBoolean("motion_toward", false) ? "sim" : "não/indefinido") +
                "\\nCerca 2,5 km: " + p.getString("outer_geofence_status", "sem evento ainda") +
                "\\nMonitor: " + p.getString("monitor_state", "ainda não iniciou") +''')
edit(diagnostic,
     '                "\\nSomente à noite: " + (p.getBoolean("so_noite", false) ? "SIM — bloqueia 6h–18h" : "não") +',
     '                "\\nSomente LÂMPADAS 18h–6h: " + (p.getBoolean("so_noite", false) ? "SIM" : "NÃO") +')

version = root / 'app/build.gradle'
edit(version, "versionCode 8\n        versionName '2.3-gps-economico'",
     "versionCode 9\n        versionName '2.4-gps-2km-portao24h'")

tests = root / 'tools/check_arrival_invariants.py'
edit(tests,
     "'dispatchLights(app, p, false, true)' in simulation and 'dispatchLights(app, p, gate, false)' in controller",
     "'dispatchLights(app, p, false, true)' in simulation and 'dispatchLights(app, p, gate, false)' in controller") if False else None
edit(tests,
     "check('Daytime and home use low-power location; nighttime outside retains fast GPS',\n      all(x in monitor for x in ['DAY_INTERVAL_MS = 120000L', 'HOME_INTERVAL_MS = 30000L',\n                                'INTERVAL_MS = 5000L', 'PRIORITY_BALANCED_POWER_ACCURACY',\n                                'PRIORITY_HIGH_ACCURACY', 'desiredProfile()',\n                                'profileMode != desiredProfile()', 'handler.post(this::ensureProfile)',\n                                'profileInterval(profileMode) * 3L']))",
     "check('2km motion-based adaptive GPS independently of time schedule',\n      all(x in monitor for x in ['FAR_INTERVAL_MS = 60000L', 'NEAR_INTERVAL_MS = 20000L',\n                                'HOME_INTERVAL_MS = 30000L', 'INTERVAL_MS = 5000L',\n                                'PRIORITY_BALANCED_POWER_ACCURACY', 'PRIORITY_HIGH_ACCURACY',\n                                'motion_until', 'motion_toward', 'distance > 2600',\n                                'distance <= 2000', 'distance <= 250', 'desiredProfile()',\n                                'profileMode != desiredProfile()', 'handler.post(this::ensureProfile)'])\n      and 'so_noite' not in monitor)")
edit(tests,
     "cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('if (p.getBoolean(\"so_noite\", false))', 1)[0]",
     "cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('boolean gate =', 1)[0]")
edit(tests, "\"versionName '2.3-gps-economico'\" in gradle and 'versionCode 8' in gradle",
     "\"versionName '2.4-gps-2km-portao24h'\" in gradle and 'versionCode 9' in gradle")
check_extra = "check('Speech emits start/done/error diagnostic',"
extra = '''check('Night setting affects only lamps; portão prompt remains 24h',
      'boolean lampTime = !p.getBoolean("so_noite", false) || hour >= 18 || hour < 6;' in controller
      and 'if (!simulated && !lampTime)' in controller
      and 'if (gate) showGateConfirmation(app)' in controller
      and 'if (p.getBoolean("so_noite", false))' not in controller.split('public static synchronized void handleArrival',1)[1].split('public static synchronized void simulateArrival',1)[0])
check('Outer geofence never triggers the gate or lights',
      'addGeofence(g).addGeofence(outer)' in (root / 'app/src/main/java/com/cilassouza/chegadacasa/MainActivity.java').read_text(encoding='utf-8')
      and 'addGeofence(geofence).addGeofence(outer)' in boot
      and 'if (!homeFence) return;' in receiver
      and '"aproximacao_2500m".equals(fence.getRequestId())' in receiver)
check('Mock GPS drives lamp route but cannot authorize gate',
      '!mockLocation && !p.getBoolean("monitor_mock", false)' in controller
      and 'dispatchLights(app, p, gate, false)' in controller
      and 'gate_origin_mock' in receiver)
'''
edit(tests, check_extra, extra + check_extra)
print('V2.4 PATCH COMPLETE. Run structural checks AND Gradle compilation; real device and car tests still required.')
