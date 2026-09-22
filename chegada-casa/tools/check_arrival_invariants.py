#!/usr/bin/env python3
"""Conservative source-level safety checks; NOT a substitute for real-car tests."""
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
car = read('ChegadaCarAppService.java')
car_notice = read('GateCarNotification.java')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
car_descriptor = (root / 'app/src/main/res/xml/automotive_app_desc.xml').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')

simulation = controller.split('public static synchronized void simulateArrival(Context context)', 1)[1].split('private static void dispatchLights', 1)[0]
check('Manual simulation exercises SAME lamp path as real arrival',
      'dispatchLights(app, p, false, true)' in simulation and 'dispatchLights(app, p, gate, false)' in controller)
check('Simulation cannot modify GPS, cooldown or gate state',
      all(text not in simulation for text in ['handleArrival(', 'handleExit(', 'pulseGate(', 'showGateConfirmation(',
                                          'putBoolean("dentro"', 'putBoolean("outside_observed"',
                                          'putLong("ultimo_alerta"', 'putBoolean("gate_pending"',
                                          'monitor_fix_at', 'monitor_distance']))
check('Simulation requires confirmation; no gate opening',
      all(text in diagnostic for text in ['SIMULAR CHEGADA E ACENDER LUZES', 'new AlertDialog.Builder(this)',
                                          'SIMULAR E ACENDER', 'ArrivalController.simulateArrival(this)',
                                          'O portão NÃO será acionado']))
check('Lamp test checks session and selection, logs result',
      all(text in diagnostic for text in ['EwelinkApi.hasSession(this)', 'EwelinkApi.selectedCount(this)',
                                           'simulation_last_result']) and 'simulation_last_result' in controller)
check('GPS and geofence share state machine with mock flag',
      'ArrivalController.handleArrival(context, trigger.isFromMockProvider())' in receiver and
      'ArrivalController.handleArrival(this, mock)' in monitor and
      'ArrivalController.handleExit(context)' not in receiver and 'ArrivalController.handleExit(this)' in monitor)
check('Live GPS flags mock positions and cancels existing gate confirmation',
      all(text in monitor for text in ['loc.isFromMockProvider()', 'putBoolean("monitor_mock", mock)',
                                      'putBoolean("gate_pending", false)', 'putBoolean("gate_origin_mock", true)',
                                      '.cancel(2002)']))
check('Only real arrivals enable gate and car notification',
      '!mockLocation &&' in controller and 'putBoolean("gate_origin_mock", mockLocation)' in controller and
      'if (gate) showGateConfirmation(app)' in controller and
      'GateCarNotification.show(context)' in controller and
      'dispatchLights(app, p, false, true)' in simulation)
check('Receiver checks authorized non-mock unexpired request before pulse',
      all(text in receiver for text in ['gate_origin_mock', 'gate_pending_at', 'GATE_CONFIRM_WINDOW_MS',
                                       'EwelinkApi.hasSession(context)', 'EwelinkApi.hasGate(context)',
                                       'p.getBoolean("ativa", false)']))
check('Android Auto notification includes two user actions and safe fallback',
      all(text in car_notice for text in ['gateConfirmationIsPending(context)',
                                          'CarAppExtender.Builder', 'CarPendingIntent.getCarApp',
                                          'CarNotificationManager.from(app).notify',
                                          '"SIM, ABRIR"', '"NÃO ABRIR"',
                                          'ACTION_OPEN_GATE', 'ACTION_CANCEL_GATE',
                                          'nm.notify(NOTIFICATION_ID, notice.build())']))
check('Car interface shows only pending real-arrival YES/NO, no bypass',
      all(text in car for text in ['gateConfirmationIsPending(ctx)',
                                   '"SIM, ABRIR"', '"NÃO ABRIR"',
                                   'ACTION_OPEN_GATE', 'ACTION_CANCEL_GATE',
                                   'gateConfirmationIsPending(getCarContext())',
                                   'main.postDelayed(this, 1500L)', 'invalidate()']) and
      'EwelinkApi.pulseGate(' not in car and 'GateConfirmScreen' not in car)
check('Car descriptor includes template and notification capabilities',
      '<uses name="template" />' in car_descriptor and '<uses name="notification" />' in car_descriptor)
check('No car notification survives a real exit',
      'GateCarNotification.cancel(app)' in controller and
      'putBoolean("gate_pending", false)' in controller)
check('Voice needs an explicit microphone tap, exact yes, and fails closed',
      all(text in voice for text in ['speak.setOnClickListener(v -> listen())',
                                    'RecognizerIntent.ACTION_RECOGNIZE_SPEECH',
                                    'heard.equals("sim abrir portao")', 'heard.equals("nao")',
                                    'gateConfirmationIsPending(this)', 'kg.isDeviceLocked()',
                                    'gate_origin_mock', 'ACTION_CANCEL_GATE', 'ACTION_OPEN_GATE']) and
      'startActivityForResult(recognize, VOICE_REQUEST)' in voice)
check('Voice activity is internal',
      'android:name=".GateVoiceActivity" android:exported="false"' in manifest)
check('Volume changed only through explicit user action',
      'volumeButton.setOnClickListener' in diagnostic and
      'audio.adjustStreamVolume(AudioManager.STREAM_MUSIC' in diagnostic and
      'SpeechEngine.mediaVolumePercent(this)' in diagnostic and
      'TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f' in speech and
      'AudioAttributes.USAGE_MEDIA' in speech)
check('2km motion-based adaptive GPS independently of time schedule',
      all(x in monitor for x in ['FAR_INTERVAL_MS = 60000L', 'NEAR_INTERVAL_MS = 20000L',
                                'HOME_INTERVAL_MS = 30000L', 'INTERVAL_MS = 5000L',
                                'PRIORITY_BALANCED_POWER_ACCURACY', 'PRIORITY_HIGH_ACCURACY',
                                'motion_until', 'motion_toward', 'distance > 2600',
                                'distance <= 2000', 'distance <= 250', 'desiredProfile()',
                                'profileMode != desiredProfile()', 'handler.post(this::ensureProfile)'])
      and 'so_noite' not in monitor)
check('GPS rejects stale/inaccurate fixes and watchdog retries',
      all(text in monitor for text in ['getElapsedRealtimeNanos()', 'accuracy >', 'WATCHDOG_MS',
                                      'STALE_MS', 'restartUpdates()', 'startForeground(']))
cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('boolean gate =', 1)[0]
check('Cooldown cannot swallow arrival',
      'putBoolean("dentro"' not in cooldown and 'putBoolean("outside_observed"' not in cooldown and
      'putLong("ultimo_alerta"' not in cooldown and 'secondsRemaining' in cooldown and
      'if (!inside && outside && consecutiveInside >= 2)' in monitor)
check('Night setting affects only lamps; portão prompt remains 24h',
      'boolean lampTime = !p.getBoolean("so_noite", false) || hour >= 18 || hour < 6;' in controller
      and 'if (!simulated && !lampTime)' in controller
      and 'if (gate) showGateConfirmation(app)' in controller
      and 'if (p.getBoolean("so_noite", false))' not in controller.split('public static synchronized void handleArrival',1)[1].split('public static synchronized void simulateArrival',1)[0])
check('Outer geofence never triggers the gate or lights',
      'addGeofence(g).addGeofence(outer)' in (root / 'app/src/main/java/com/cilassouza/chegadacasa/MainActivity.java').read_text(encoding='utf-8')
      and 'addGeofence(geofence).addGeofence(outer)' in boot
      and 'if (!homeFence) return;' in receiver
      and '"aproximacao_2500m".equals(fence.getRequestId())' in receiver)
gate_test = read('GateTestMode.java')
check('Fake GPS can authorize gate ONLY with explicit short one-shot test',
      all(s in gate_test for s in ['WINDOW_MS = 10L * 60L * 1000L',
                                   'isArmed(SharedPreferences p)',
                                   'isAuthorizedPending(SharedPreferences p)',
                                   'gate_test_pending', 'gate_origin_mock',
                                   'monitor_mock', 'MAX_MOCK_FIX_AGE_MS',
                                   'boolean consume(SharedPreferences p)', '.commit()'])
      and 'GateTestMode.isArmed(p)' in controller
      and 'gate && fakeTest' in controller
      and 'GateTestMode.isAuthorizedPending(prefs)' in monitor
      and 'GateTestMode.consume(p)' in receiver
      and 'if (mockTest && !GateTestMode.consume(p))' in receiver)
check('Gate test needs an onsite user opt-in, has cancel, and physical YES only',
      all(s in diagnostic for s in ['armFakeGpsGateTest()', 'ESTOU NO LOCAL — ARMAR TESTE',
                                    'GateTestMode.arm(this)', 'GateTestMode.cancel(this)',
                                    'SIM, o portão FÍSICO'])
      and 'EwelinkApi.pulseGate(' not in diagnostic
      and 'if (mockTest && !GateTestMode.consume(p))' in receiver
      and 'EwelinkApi.pulseGate(' in receiver)
check('Car and voice explain physical mock test; normal mock cannot open',
      'TESTE GPS: abrir portão REAL?' in car_notice
      and 'GateTestMode.isAuthorizedPending(p)' in voice
      and 'GateTestMode.isAuthorizedPending(p)' in receiver
      and '(!mockLocation && !p.getBoolean("monitor_mock", false)) || fakeTest' in controller)
check('Speech emits start/done/error diagnostic',
      all(text in speech for text in ['onStart(', 'onDone(', 'onError(', 'tts_state']))
check('Boot restores geofence without restricted microphone start',
      'addGeofences(request, pi)' in boot and 'ArrivalMonitorService.start(' not in boot and
      'GateVoiceActivity' not in boot)
check('Manifest contains required permissions and services',
      all(text in manifest for text in ['ACCESS_FINE_LOCATION', 'ACCESS_BACKGROUND_LOCATION',
                                       'POST_NOTIFICATIONS', 'FOREGROUND_SERVICE_LOCATION',
                                       'RECEIVE_BOOT_COMPLETED', 'android.intent.action.TTS_SERVICE',
                                       '.ArrivalMonitorService', '.BootReceiver', '.GeofenceReceiver',
                                       'androidx.car.app.category.IOT']))
check('APK version and package stay update-compatible',
      "applicationId 'com.cilassouza.chegadacasa.fast'" in gradle and
      "versionName '2.6-antifalsas-chegadas'" in gradle and 'versionCode 11' in gradle and
      "implementation 'androidx.core:core:1.15.0'" in gradle)
check('Fence exit alone cannot rearm arrival',
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
print('STRUCTURAL CHECKS PASSED. Real GPS, car host and physical gate still need supervised tests.')
