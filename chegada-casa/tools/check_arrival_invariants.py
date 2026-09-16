#!/usr/bin/env python3
"""Conservative structural regression checks; NOT a replacement for an on-device test."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/cilassouza/chegadacasa'

def read(name):
    return (src / name).read_text(encoding='utf-8')

def check(label, condition):
    if not condition:
        raise AssertionError(label)
    print('PASS:', label)

controller = read('ArrivalController.java')
diagnostic = read('ArrivalDiagnosticActivity.java')
receiver = read('GeofenceReceiver.java')
monitor = read('ArrivalMonitorService.java')
speech = read('SpeechEngine.java')
boot = read('BootReceiver.java')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')

simulation = controller.split('public static synchronized void simulateArrival(Context context)', 1)[1].split('private static void dispatchLights', 1)[0]
check('Simulation reaches SAME light command pipeline as GPS',
      'dispatchLights(app, p, false, true)' in simulation and 'dispatchLights(app, p, gate, false)' in controller)
check('Simulation cannot reset geofence / GPS / gate state',
      all(text not in simulation for text in ['handleArrival(', 'handleExit(', 'pulseGate(', 'showGateConfirmation(',
                                          'putBoolean("dentro"', 'putBoolean("outside_observed"',
                                          'putLong("ultimo_alerta"', 'putBoolean("gate_pending"',
                                          'monitor_fix_at', 'monitor_distance']))
check('Simulation is user-confirmed and explicitly warns of REAL switching',
      all(text in diagnostic for text in ['SIMULAR CHEGADA E ACENDER LUZES', 'new AlertDialog.Builder(this)',
                                          'SIMULAR E ACENDER', 'ArrivalController.simulateArrival(this)',
                                          'O portão NÃO será acionado']))
check('Simulation has preflight for login and selected lamps',
      'EwelinkApi.hasSession(this)' in diagnostic and 'EwelinkApi.selectedCount(this)' in diagnostic)
check('Simulation exposes a separate status without claiming physical confirmation',
      'simulation_last_result' in controller and 'simulation_last_result' in diagnostic and
      'não comprova que a luz acendeu fisicamente' in diagnostic)
check('Geofence and live GPS use one state machine',
      'ArrivalController.handleArrival(context)' in receiver and
      'ArrivalController.handleExit(context)' in receiver and
      'ArrivalController.handleArrival(this)' in monitor and
      'ArrivalController.handleExit(this)' in monitor)
check('GPS rejects stale/inaccurate fixes and watchdog retries',
      all(x in monitor for x in ['getElapsedRealtimeNanos()', 'accuracy >', 'WATCHDOG_MS',
                                'STALE_MS', 'restartUpdates()', 'startForeground(']))

# A prior regression marked the phone inside while suppressing an arrival during
# the one-minute cooldown; no further GPS fix could fire an arrival until EXIT.
cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('if (p.getBoolean("so_noite", false))', 1)[0]
check('Cooldown preserves outside/inside state so GPS retries after expiration',
      'putBoolean("dentro"' not in cooldown and
      'putBoolean("outside_observed"' not in cooldown and
      'putLong("ultimo_alerta"' not in cooldown and
      'secondsRemaining' in cooldown and 'return;' in cooldown and
      'if (!inside && outside) ArrivalController.handleArrival(this)' in monitor)
check('A real arrival marks inside and only then updates cooldown',
      'putBoolean("dentro", true)' in controller and
      'putLong("ultimo_alerta", now)' in controller and
      'dispatchLights(app, p, gate, false);' in controller)
check('Speech status reports start / completion / failure',
      all(x in speech for x in ['onStart(', 'onDone(', 'onError(', 'tts_state']))
check('Boot receiver restores geofence without auto-starting restricted foreground service',
      'addGeofences(request, pi)' in boot and 'ArrivalMonitorService.start(' not in boot)
check('Required manifest permissions, services, boot and voice engine visibility',
      all(x in manifest for x in ['ACCESS_FINE_LOCATION', 'ACCESS_BACKGROUND_LOCATION',
                                 'POST_NOTIFICATIONS', 'FOREGROUND_SERVICE_LOCATION',
                                 'RECEIVE_BOOT_COMPLETED', 'android.intent.action.TTS_SERVICE',
                                 '.ArrivalMonitorService', '.BootReceiver', '.GeofenceReceiver']))
check('APK has expected separate identity and version',
      "applicationId 'com.cilassouza.chegadacasa.fast'" in gradle and
      "versionName '2.0-correcaochegada'" in gradle and 'versionCode 5' in gradle)
print('STRUCTURAL CHECKS PASSED. Real GPS, Android power behavior, cloud and bulbs need device tests.')
