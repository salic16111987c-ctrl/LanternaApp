#!/usr/bin/env python3
"""v2.6: Do not let geofence jitter rearm arrival; expire stale gate alerts."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/cilassouza/chegadacasa'


def patch(file, before, after):
    path = (src / file) if file.endswith('.java') else (root / file)
    text = path.read_text(encoding='utf-8')
    if after in text:
        print('ALREADY PATCHED', path.name)
        return
    count = text.count(before)
    if count != 1:
        raise AssertionError(f'Expected one anchor, got {count}: {path} / {before[:100]}')
    path.write_text(text.replace(before, after, 1), encoding='utf-8')
    print('PATCHED', path.name)


patch('GeofenceReceiver.java',
'''        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            ArrivalController.handleExit(context);
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
''',
'''        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // A geofence EXIT alone can be GPS drift while the owner is at home.
            // Only the monitor may rearm a real arrival after sustained outside fixes.
            p.edit().putString("arrival_last_event",
                    "Cerca sinalizou saída; aguardando GPS confirmar afastamento").apply();
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
''')

patch('GeofenceReceiver.java',
'''            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());
''',
'''            // ENTER is only a backup after the monitor confirmed a genuine exit.
            // Never turn on lights or prompt a physical gate on an unverified fence bounce.
            if (!p.getBoolean("outside_observed", false) || trigger == null
                    || !trigger.hasAccuracy()
                    || trigger.getAccuracy() > Math.max(60f, p.getInt("raio", 100) * 0.4f)) {
                p.edit().putString("arrival_last_event",
                        "Cerca entrou; chegada sem saída confirmada/posição precisa: ignorada").apply();
                return;
            }
            float[] homeDistance = new float[1];
            Location.distanceBetween(trigger.getLatitude(), trigger.getLongitude(),
                    Double.longBitsToDouble(p.getLong("lat", 0L)),
                    Double.longBitsToDouble(p.getLong("lon", 0L)), homeDistance);
            if (!p.getBoolean("casa_definida", false)
                    || homeDistance[0] > Math.max(50, p.getInt("raio", 100))) return;
            ArrivalController.handleArrival(context, trigger.isFromMockProvider());
''')

patch('ArrivalMonitorService.java',
'''    private int consecutiveOutside;
    private int profileMode = -1;
''',
'''    private int consecutiveOutside;
    private long outsideCandidateElapsed;
    private int consecutiveInside;
    private int profileMode = -1;
''')

patch('ArrivalMonitorService.java',
'''            if (!foreground) return;
            long elapsed = SystemClock.elapsedRealtime();
''',
'''            if (!foreground) return;
            // A five-minute confirmation must not linger in notifications for 22 minutes.
            if (prefs.getBoolean("gate_pending", false)
                    && !GeofenceReceiver.gateConfirmationIsPending(ArrivalMonitorService.this)) {
                prefs.edit().putBoolean("gate_pending", false)
                        .putBoolean("gate_test_pending", false)
                        .remove("gate_pending_at")
                        .putString("gate_voice_status", "Confirmação expirada; portão não acionado")
                        .apply();
                GateCarNotification.cancel(ArrivalMonitorService.this);
            }
            long elapsed = SystemClock.elapsedRealtime();
''')

patch('ArrivalMonitorService.java',
'''        if (accuracy > Math.max(75f, radius * 0.6f)) {
            prefs.edit().putString("monitor_state", "Aguardando GPS mais preciso: ±" + acc + " m").apply();
            return;
        }
        float margin = Math.max(60f, Math.min(120f, radius * 0.3f));
        if (distance > radius + margin) {
            consecutiveOutside++;
            if (consecutiveOutside >= 2) {
                if (prefs.getBoolean("dentro", false)) ArrivalController.handleExit(this);
                else if (!prefs.getBoolean("outside_observed", false)) {
                    prefs.edit().putBoolean("outside_observed", true)
                            .putString("arrival_last_event", "Saída confirmada: " + dist + " m").apply();
                }
            }
            // Home -> outside: switch to fast immediately after two reliable outside fixes.
            handler.post(this::ensureProfile);
            firstFix = false;
            return;
        }
        consecutiveOutside = 0;
        if (distance <= radius) {
            boolean inside = prefs.getBoolean("dentro", false);
            boolean outside = prefs.getBoolean("outside_observed", false);
            if (!inside && outside) ArrivalController.handleArrival(this, mock);
''',
'''        if (accuracy > Math.max(75f, radius * 0.6f)) {
            consecutiveOutside = 0;
            outsideCandidateElapsed = 0L;
            consecutiveInside = 0;
            prefs.edit().putString("monitor_state", "Aguardando GPS mais preciso: ±" + acc + " m").apply();
            return;
        }
        // Hysteresis: a brief location jump beyond the home circle must not
        // rearm arrival and toggle actual lights while the owner remains home.
        float margin = Math.max(100f, Math.min(250f, radius * 0.5f));
        if (distance > radius + margin) {
            if (consecutiveOutside == 0) outsideCandidateElapsed = SystemClock.elapsedRealtime();
            consecutiveOutside++;
            consecutiveInside = 0;
            if (consecutiveOutside >= 3 && outsideCandidateElapsed > 0L
                    && SystemClock.elapsedRealtime() - outsideCandidateElapsed >= 90000L) {
                if (prefs.getBoolean("dentro", false)) ArrivalController.handleExit(this);
                else if (!prefs.getBoolean("outside_observed", false)) {
                    prefs.edit().putBoolean("outside_observed", true)
                            .putString("arrival_last_event", "Saída sustentada confirmada: " + dist + " m").apply();
                }
            }
            handler.post(this::ensureProfile);
            firstFix = false;
            return;
        }
        consecutiveOutside = 0;
        outsideCandidateElapsed = 0L;
        consecutiveInside = distance <= radius ? consecutiveInside + 1 : 0;
        if (distance <= radius) {
            boolean inside = prefs.getBoolean("dentro", false);
            boolean outside = prefs.getBoolean("outside_observed", false);
            if (!inside && outside && consecutiveInside >= 2)
                ArrivalController.handleArrival(this, mock);
''')

patch('GateCarNotification.java',
'''                .setAutoCancel(false).setOngoing(false)
''',
'''                .setTimeoutAfter(5L * 60L * 1000L)
                .setAutoCancel(false).setOngoing(false)
''')

patch('app/build.gradle',
'''        versionCode 10
        versionName '2.5-teste-portao-fake-gps'
''',
'''        versionCode 11
        versionName '2.6-antifalsas-chegadas'
''')

patch('ArrivalDiagnosticActivity.java',
'''        title.setText("DIAGNÓSTICO E VOZ — v2.5");
''',
'''        title.setText("DIAGNÓSTICO E VOZ — v2.6");
''')

patch('tools/check_arrival_invariants.py',
'''      'ArrivalController.handleExit(context)' in receiver and 'ArrivalController.handleExit(this)' in monitor)''',
'''      'ArrivalController.handleExit(context)' not in receiver and 'ArrivalController.handleExit(this)' in monitor)''')
patch('tools/check_arrival_invariants.py',
'''      "versionName '2.5-teste-portao-fake-gps'" in gradle and 'versionCode 10' in gradle and''',
'''      "versionName '2.6-antifalsas-chegadas'" in gradle and 'versionCode 11' in gradle and''')
patch('tools/check_arrival_invariants.py',
'''print('STRUCTURAL CHECKS PASSED. Car host display rules, real gate and driving behavior need supervised tests.')''',
'''check('Fence exit alone cannot rearm arrival',
      'ArrivalController.handleExit(context)' not in receiver
      and 'Cerca sinalizou saída; aguardando GPS confirmar afastamento' in receiver)
check('False departures need sustained GPS and a hysteresis margin',
      'outsideCandidateElapsed' in monitor and 'consecutiveOutside >= 3' in monitor
      and '>= 90000L' in monitor and 'Math.max(100f, Math.min(250f, radius * 0.5f))' in monitor)
check('Arrival needs two inside GPS fixes and a confirmed exit',
      'consecutiveInside >= 2' in monitor and 'outside_observed' in receiver)
check('Gate notifications have a bounded lifetime',
      '.setTimeoutAfter(5L * 60L * 1000L)' in car_notice
      and 'Confirmação expirada; portão não acionado' in monitor)
print('STRUCTURAL CHECKS PASSED. Real GPS, car host and physical gate still need supervised tests.')''')
