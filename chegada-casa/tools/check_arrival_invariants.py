#!/usr/bin/env python3
"""Conservative source-level safety checks; NOT a replacement for on-device validation."""
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
voice = read('GateVoiceActivity.java')
boot = read('BootReceiver.java')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')

simulation = controller.split('public static synchronized void simulateArrival(Context context)', 1)[1].split('private static void dispatchLights', 1)[0]
check('Manual simulation exercises the SAME light command pipeline as real arrival',
      'dispatchLights(app, p, false, true)' in simulation and 'dispatchLights(app, p, gate, false)' in controller)
check('Simulation cannot reset geofence, cooldown or gate',
      all(text not in simulation for text in ['handleArrival(', 'handleExit(', 'pulseGate(', 'showGateConfirmation(',
                                          'putBoolean("dentro"', 'putBoolean("outside_observed"',
                                          'putLong("ultimo_alerta"', 'putBoolean("gate_pending"',
                                          'monitor_fix_at', 'monitor_distance']))
check('Simulation requires confirmation and warns real switching never opens gate',
      all(text in diagnostic for text in ['SIMULAR CHEGADA E ACENDER LUZES', 'new AlertDialog.Builder(this)',
                                          'SIMULAR E ACENDER', 'ArrivalController.simulateArrival(this)',
                                          'O portão NÃO será acionado']))
check('Simulation checks session and selected lamps and logs separate result',
      all(text in diagnostic for text in ['EwelinkApi.hasSession(this)', 'EwelinkApi.selectedCount(this)',
                                           'simulation_last_result']) and
      'simulation_last_result' in controller and
      'não comprova que a luz acendeu fisicamente' in diagnostic)
check('GPS and geofence feed same controller including origin mock flag',
      'ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider())' in receiver and
      'ArrivalController.handleArrival(this, mock)' in monitor and
      'ArrivalController.handleExit(context)' in receiver and
      'ArrivalController.handleExit(this)' in monitor)
check('GPS persists mock flag and revokes pending gate on mock fix',
      all(text in monitor for text in ['loc.isFromMockProvider()', 'putBoolean("monitor_mock", mock)',
                                      'putBoolean("gate_pending", false)', 'putBoolean("gate_origin_mock", true)',
                                      '.cancel(2002)']))
check('Controller never offers gate on mock and can never be prompted by simulation',
      '!mockLocation &&' in controller and 'putBoolean("gate_origin_mock", mockLocation)' in controller and
      'if (gate) showGateConfirmation(app)' in controller and
      'dispatchLights(app, p, false, true)' in simulation)
check('Gate receiver rejects mock, expired, inactive or unconfigured confirmations',
      all(text in receiver for text in ['gate_origin_mock', 'gate_pending_at', 'GATE_CONFIRM_WINDOW_MS',
                                       'EwelinkApi.hasSession(context)', 'EwelinkApi.hasGate(context)',
                                       'p.getBoolean("ativa", false)']))
check('Gate notification has voice and explicit no/open actions',
      all(text in controller for text in ['GateVoiceActivity.class', 'RESPONDER POR VOZ',
                                         'ABRIR PORTÃO', 'NÃO ABRIR']))
check('Voice recognition only after microphone tap; exact positive phrase and fail closed',
      all(text in voice for text in ['speak.setOnClickListener(v -> listen())',
                                    'RecognizerIntent.ACTION_RECOGNIZE_SPEECH',
                                    'heard.equals("sim abrir portao")',
                                    'heard.equals("nao")',
                                    'gateConfirmationIsPending(this)',
                                    'kg.isDeviceLocked()',
                                    'gate_origin_mock',
                                    'ACTION_CANCEL_GATE', 'ACTION_OPEN_GATE']) and
      'startActivityForResult(recognize, VOICE_REQUEST)' in voice)
check('Gate voice activity internal only',
      'android:name=".GateVoiceActivity" android:exported="false"' in manifest)
check('Real media volume is adjustable only after user taps volume button',
      'volumeButton.setOnClickListener' in diagnostic and
      'audio.adjustStreamVolume(AudioManager.STREAM_MUSIC' in diagnostic and
      'SpeechEngine.mediaVolumePercent(this)' in diagnostic and
      'TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f' in speech and
      'AudioAttributes.USAGE_MEDIA' in speech)
check('GPS rejects stale/inaccurate fixes and watchdog retries',
      all(text in monitor for text in ['getElapsedRealtimeNanos()', 'accuracy >', 'WATCHDOG_MS',
                                      'STALE_MS', 'restartUpdates()', 'startForeground(']))

# Previously cooldown incorrectly marked an ignored arrival as inside, swallowing next entry.
cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('if (p.getBoolean("so_noite", false))', 1)[0]
check('Cooldown never swallows a pending arrival',
      'putBoolean("dentro"' not in cooldown and 'putBoolean("outside_observed"' not in cooldown and
      'putLong("ultimo_alerta"' not in cooldown and 'secondsRemaining' in cooldown and
      'if (!inside && outside) ArrivalController.handleArrival(this, mock)' in monitor)
check('Speech reports actual start / completion / errors',
      all(text in speech for text in ['onStart(', 'onDone(', 'onError(', 'tts_state']))
check('Boot receiver restores geofence without forbidden background microphone start',
      'addGeofences(request, pi)' in boot and 'ArrivalMonitorService.start(' not in boot and
      'GateVoiceActivity' not in boot)
check('Required manifest permissions, services and TTS visibility',
      all(text in manifest for text in ['ACCESS_FINE_LOCATION', 'ACCESS_BACKGROUND_LOCATION',
                                       'POST_NOTIFICATIONS', 'FOREGROUND_SERVICE_LOCATION',
                                       'RECEIVE_BOOT_COMPLETED', 'android.intent.action.TTS_SERVICE',
                                       '.ArrivalMonitorService', '.BootReceiver', '.GeofenceReceiver']))
check('APK version and package identity',
      "applicationId 'com.cilassouza.chegadacasa.fast'" in gradle and
      "versionName '2.1-voz-portao-volume'" in gradle and 'versionCode 6' in gradle)
print('STRUCTURAL CHECKS PASSED. Device audio, voice recognizer, real GPS and actual gate still need controlled real tests.')
