#!/usr/bin/env python3
"""Fail-closed, idempotent v2.5 patch: optional one-shot mock-GPS REAL gate test.

Only the user can arm the temporary test from the unlocked diagnostics screen.
Mock GPS outside that opt-in NEVER enables physical gate commands. CI checks
this code before building. Real hardware and car display still need onsite tests.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/cilassouza/chegadacasa'

def change(path, before, after):
    text = path.read_text(encoding='utf-8')
    if after in text:
        return
    count = text.count(before)
    if count != 1:
        raise RuntimeError(f'{path.name}: expected one anchor, got {count}: {before[:90]!r}')
    path.write_text(text.replace(before, after, 1), encoding='utf-8')
    print('PATCH', path.name, before[:42])

monitor = src / 'ArrivalMonitorService.java'
change(monitor,
       '''        if (mock) evidence.putBoolean("gate_pending", false)
                .putBoolean("gate_origin_mock", true).remove("gate_pending_at");
        evidence.apply();
        if (mock) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2002);''',
       '''        // Never clear an explicitly armed, still-valid ONE-SHOT gate-test prompt.
        // Normal fake GPS continues to cancel all ordinary gate confirmations.
        boolean authorizedTestPrompt = mock && GateTestMode.isAuthorizedPending(prefs);
        if (mock && !authorizedTestPrompt) evidence.putBoolean("gate_pending", false)
                .putBoolean("gate_origin_mock", true)
                .putBoolean("gate_test_pending", false).remove("gate_pending_at");
        evidence.apply();
        if (mock && !authorizedTestPrompt)
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2002);''')
change(monitor,
       '(mock ? "GPS FICTÍCIO — portão bloqueado — " : "GPS OK — ")',
       '(mock ? "GPS FICTÍCIO — portão bloqueado, exceto TESTE armado — " : "GPS OK — ")')

controller = src / 'ArrivalController.java'
change(controller,
       '''        boolean gate = !mockLocation && !p.getBoolean("monitor_mock", false)
                && EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);''',
       '''        // A fake location may offer the REAL gate confirmation ONLY if the user
        // explicitly armed a short, single-use test while watching the physical gate.
        long fixAge = now - p.getLong("monitor_fix_at", 0L);
        boolean fakeTest = mockLocation && p.getBoolean("monitor_mock", false)
                && fixAge >= 0L && fixAge <= 60000L && GateTestMode.isArmed(p);
        boolean gate = ((!mockLocation && !p.getBoolean("monitor_mock", false)) || fakeTest)
                && EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);''')
change(controller,
       '''        if (gate) editor.putBoolean("gate_pending", true).putLong("gate_pending_at", now);
        else editor.putBoolean("gate_pending", false).remove("gate_pending_at");''',
       '''        editor.putBoolean("gate_test_pending", gate && fakeTest);
        if (gate && fakeTest) editor.putBoolean("gate_test_armed", false); // one arrival only
        if (gate) editor.putBoolean("gate_pending", true).putLong("gate_pending_at", now);
        else editor.putBoolean("gate_pending", false).remove("gate_pending_at");''')
change(controller,
       '''        p.edit().putBoolean("dentro", false).putBoolean("outside_observed", true)
                .putBoolean("gate_pending", false).putBoolean("gate_origin_mock", true)''',
       '''        p.edit().putBoolean("dentro", false).putBoolean("outside_observed", true)
                .putBoolean("gate_pending", false).putBoolean("gate_test_pending", false)
                .putBoolean("gate_origin_mock", true)''')

receiver = src / 'GeofenceReceiver.java'
change(receiver,
       '''        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Unknown origin is treated as untrusted for gate safety. Lights still work.
            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());''',
       '''        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // An early mock geofence event must wait for the GPS monitor to verify it.
            if (GateTestMode.isArmed(p) && (trigger == null || trigger.isFromMockProvider())
                    && !p.getBoolean("monitor_mock", false)) {
                p.edit().putString("arrival_last_event", "Teste fake GPS: aguardando posição do monitor").apply();
                return;
            }
            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());''')
change(receiver,
       '''        long age = System.currentTimeMillis() - at;
        if (!p.getBoolean("ativa", false) || !p.getBoolean("gate_pending", false)
                || p.getBoolean("gate_origin_mock", true)''',
       '''        long age = System.currentTimeMillis() - at;
        boolean mockTest = p.getBoolean("gate_origin_mock", true)
                && GateTestMode.isAuthorizedPending(p);
        if (!p.getBoolean("ativa", false) || !p.getBoolean("gate_pending", false)
                || (p.getBoolean("gate_origin_mock", true) && !mockTest)''')
change(receiver,
       '''        // User explicitly tapped ABRIR or spoke the exact phrase after opening microphone.
        p.edit().putBoolean("gate_pending", false).remove("gate_pending_at")''',
       '''        // Even in test mode, mock GPS alone never sends a pulse: an explicit SIM
        // is mandatory, and the temporary permit is CONSUMED before any hardware call.
        if (mockTest && !GateTestMode.consume(p)) {
            cancelarNotificacaoPortao(context);
            mostrarNotificacao(context, "Teste cancelado", "Autorização de teste expirada ou não pôde ser consumida.");
            return;
        }
        p.edit().putBoolean("gate_pending", false).remove("gate_pending_at")''')
change(receiver,
       '''                .putBoolean("gate_pending", false).remove("gate_pending_at")
                .putString("gate_voice_status", "Resposta NÃO; nenhum comando enviado").apply();''',
       '''                .putBoolean("gate_pending", false).putBoolean("gate_test_pending", false)
                .putBoolean("gate_test_armed", false).putLong("gate_test_until", 0L)
                .remove("gate_pending_at")
                .putString("gate_voice_status", "Resposta NÃO; nenhum comando enviado").apply();''')
change(receiver,
       '''        return p.getBoolean("ativa", false) && !p.getBoolean("gate_origin_mock", true)
                && p.getBoolean("gate_pending", false) && at > 0L && age >= 0L''',
       '''        return p.getBoolean("ativa", false)
                && (!p.getBoolean("gate_origin_mock", true) || GateTestMode.isAuthorizedPending(p))
                && p.getBoolean("gate_pending", false) && at > 0L && age >= 0L''')

voice = src / 'GateVoiceActivity.java'
change(voice,
       '''                || !p.getBoolean("ativa", false) || p.getBoolean("gate_origin_mock", true)
                || !EwelinkApi.hasSession(this) || !EwelinkApi.hasGate(this)) {''',
       '''                || !p.getBoolean("ativa", false)
                || (p.getBoolean("gate_origin_mock", true) && !GateTestMode.isAuthorizedPending(p))
                || !EwelinkApi.hasSession(this) || !EwelinkApi.hasGate(this)) {''')
change(voice,
       '''                    + "GPS fictício nunca habilita a confirmação do portão.");''',
       '''                    + "GPS fictício exige MODO TESTE ativado, válido e de uso único.");''')

notification = src / 'GateCarNotification.java'
change(notification,
       '''        String name = EwelinkApi.getGateName(app);
        NotificationCompat.Builder notice =''',
       '''        String name = EwelinkApi.getGateName(app);
        boolean fakeTest = GateTestMode.isAuthorizedPending(
                app.getSharedPreferences("config", Context.MODE_PRIVATE));
        String question = fakeTest ? "TESTE GPS: abrir portão REAL?" : "Deseja abrir o portão?";
        NotificationCompat.Builder notice =''')
change(notification,
       '''                .setContentTitle("Deseja abrir o portão?")
                .setContentText(name + " — escolha SIM ou NÃO")''',
       '''                .setContentTitle(question)
                .setContentText(name + (fakeTest ? " — TESTE: SIM abre fisicamente" : " — escolha SIM ou NÃO"))''')
change(notification,
       '''                        "Chegada detectada. Confirme apenas se for seguro abrir " + name + "."))''',
       '''                        (fakeTest ? "MODO TESTE COM GPS FICTÍCIO. SIM abrirá o portão REAL. "
                                : "Chegada detectada. ")
                                + "Confirme apenas se for seguro abrir " + name + "."))''')
change(notification,
       '''                    .setContentTitle("Chegada em casa — portão")
                    .setContentText("Abrir " + name + "? Confirmação obrigatória.")''',
       '''                    .setContentTitle(fakeTest ? "TESTE GPS — PORTÃO REAL" : "Chegada em casa — portão")
                    .setContentText("Abrir " + name + "? "
                            + (fakeTest ? "SIM abre fisicamente!" : "Confirmação obrigatória."))''')

diagnostic = src / 'ArrivalDiagnosticActivity.java'
change(diagnostic,
       'import android.content.Context;\n',
       'import android.content.Context;\nimport android.app.KeyguardManager;\n')
change(diagnostic,
       'title.setText("DIAGNÓSTICO E VOZ — v2.4");',
       'title.setText("DIAGNÓSTICO E VOZ — v2.5");')
change(diagnostic,
       '''        root.addView(simulateButton, 6);

        Button startButton = new Button(this);''',
       '''        root.addView(simulateButton, 6);

        Button gateTestButton = new Button(this);
        gateTestButton.setText("🧪 ARMAR TESTE DO PORTÃO COM FAKE GPS (10 MIN / 1 VEZ)");
        gateTestButton.setOnClickListener(v -> armFakeGpsGateTest());
        root.addView(gateTestButton, 7);

        Button cancelGateTestButton = new Button(this);
        cancelGateTestButton.setText("CANCELAR TESTE DO PORTÃO");
        cancelGateTestButton.setOnClickListener(v -> {
            GateTestMode.cancel(this);
            updateDiagnostics();
            Toast.makeText(this, "Teste do portão cancelado.", Toast.LENGTH_LONG).show();
        });
        root.addView(cancelGateTestButton, 8);

        Button startButton = new Button(this);''')
change(diagnostic,
       '''        root.addView(startButton, 7);
        updateDiagnostics();''',
       '''        root.addView(startButton, 9);
        updateDiagnostics();''')
change(diagnostic,
       '''    /** Does not fake GPS, reset cooldown, change inside/outside or pulse the gate. */''',
       '''    private void armFakeGpsGateTest() {
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        KeyguardManager lock = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (lock != null && lock.isDeviceLocked()) {
            Toast.makeText(this, "Desbloqueie o celular antes de armar o teste.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!p.getBoolean("ativa", false) || !EwelinkApi.hasSession(this)
                || !EwelinkApi.hasGate(this)) {
            new AlertDialog.Builder(this).setTitle("Teste não disponível")
                    .setMessage("Ative a automação e configure a conta eWeLink e o portão primeiro.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("TESTE REAL DO PORTÃO COM GPS FICTÍCIO")
                .setMessage("ATENÇÃO: ao simular a rota de saída e retorno, o app mostrará a pergunta SIM/NÃO. "
                        + "Se você confirmar SIM, o portão FÍSICO poderá abrir de verdade! "
                        + "Arme apenas estando diante do portão, com a passagem desimpedida, "
                        + "observando o equipamento e sem crianças, pessoas ou veículos na trajetória. "
                        + "Este modo autoriza uma ÚNICA chegada simulada durante 10 minutos. "
                        + "Sem o seu SIM, nenhum comando será enviado. Confirmar que está no local e armar?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ESTOU NO LOCAL — ARMAR TESTE", (dialog, which) -> {
                    GateTestMode.arm(this);
                    updateDiagnostics();
                    Toast.makeText(this, "TESTE ARMADO por 10 minutos e 1 chegada. SIM abre o portão REAL!",
                            Toast.LENGTH_LONG).show();
                }).show();
    }

    /** Does not fake GPS, reset cooldown, change inside/outside or pulse the gate. */''')
change(diagnostic,
       '''                "\\nGPS fictício: " + (p.getBoolean("monitor_mock", false) ? "SIM — portão BLOQUEADO" : "não") +''',
       '''                "\\nGPS fictício: " + (p.getBoolean("monitor_mock", false) ? "SIM" : "não") +
                " | teste portão: " + (GateTestMode.isAuthorizedPending(p) ? "PERGUNTA PENDENTE — SIM ABRE REAL"
                        : GateTestMode.isArmed(p) ? "ARMADO 10 MIN / 1 VEZ" : "DESARMADO") +''')
change(diagnostic,
       '''                "\\nPortão por voz: somente após chegada REAL; toque RESPONDER POR VOZ na notificação, "''',
       '''                "\\nPortão por voz: chegada REAL ou teste GPS temporariamente armado; toque RESPONDER POR VOZ na notificação, "''')

gradle = root / 'app/build.gradle'
change(gradle,
       "        versionCode 9\n        versionName '2.4-gps-2km-portao24h'",
       "        versionCode 10\n        versionName '2.5-teste-portao-fake-gps'")

tests = root / 'tools/check_arrival_invariants.py'
change(tests,
       "\"versionName '2.4-gps-2km-portao24h'\" in gradle and 'versionCode 9' in gradle",
       "\"versionName '2.5-teste-portao-fake-gps'\" in gradle and 'versionCode 10' in gradle")
change(tests,
       '''check('Mock GPS drives lamp route but cannot authorize gate',
      '!mockLocation && !p.getBoolean("monitor_mock", false)' in controller
      and 'dispatchLights(app, p, gate, false)' in controller
      and 'gate_origin_mock' in receiver)''',
       '''gate_test = read('GateTestMode.java')
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
      and '(!mockLocation && !p.getBoolean("monitor_mock", false)) || fakeTest' in controller)''')
# Preserve the legacy normal-mock cancellation safety check and default flow.
print('SUCCESS: v2.5 explicit mock-GPS gate test patch and CI checks ready.')
