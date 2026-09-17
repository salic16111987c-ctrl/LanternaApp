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
      'ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider())' in receiver and
      'ArrivalController.handleArrival(this, mock)' in monitor and
      'ArrivalController.handleExit(context)' in receiver and 'ArrivalController.handleExit(this)' in monitor)
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
check('Daytime and home use low-power location; nighttime outside retains fast GPS',
      all(x in monitor for x in ['DAY_INTERVAL_MS = 120000L', 'HOME_INTERVAL_MS = 30000L',
                                'INTERVAL_MS = 5000L', 'PRIORITY_BALANCED_POWER_ACCURACY',
                                'PRIORITY_HIGH_ACCURACY', 'desiredProfile()',
                                'profileMode != desiredProfile()', 'handler.post(this::ensureProfile)',
                                'profileInterval(profileMode) * 3L']))
check('GPS rejects stale/inaccurate fixes and watchdog retries',
      all(text in monitor for text in ['getElapsedRealtimeNanos()', 'accuracy >', 'WATCHDOG_MS',
                                      'STALE_MS', 'restartUpdates()', 'startForeground(']))
cooldown = controller.split('if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {', 1)[1].split('if (p.getBoolean("so_noite", false))', 1)[0]
check('Cooldown cannot swallow arrival',
      'putBoolean("dentro"' not in cooldown and 'putBoolean("outside_observed"' not in cooldown and
      'putLong("ultimo_alerta"' not in cooldown and 'secondsRemaining' in cooldown and
      'if (!inside && outside) ArrivalController.handleArrival(this, mock)' in monitor)
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
      "versionName '2.3-gps-economico'" in gradle and 'versionCode 8' in gradle and
      "implementation 'androidx.core:core:1.15.0'" in gradle)
print('STRUCTURAL CHECKS PASSED. Car host display rules, real gate and driving behavior need supervised tests.')
