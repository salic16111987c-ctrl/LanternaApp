package com.cilassouza.chegadacasa;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/** A tap on the gate notification opens this screen. No ambient/background listening.
 * Only the exact spoken authorization phrase can request the existing guarded gate receiver.
 */
public final class GateVoiceActivity extends Activity {
    private static final int VOICE_REQUEST = 8124;
    private TextView status;
    private boolean listening;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 40, 28, 24);
        root.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this);
        title.setText("CONFIRMAR PORTÃO POR VOZ");
        title.setTextSize(22f);
        title.setTextColor(Color.BLACK);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(18f);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, 28, 0, 28);
        root.addView(status);
        Button speak = new Button(this);
        speak.setText("🎤 RESPONDER POR VOZ");
        speak.setOnClickListener(v -> listen());
        root.addView(speak, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Button decline = new Button(this);
        decline.setText("NÃO ABRIR PORTÃO");
        decline.setOnClickListener(v -> {
            if (GeofenceReceiver.gateConfirmationIsPending(this)) {
                sendBroadcast(new Intent(this, GeofenceReceiver.class)
                        .setAction(GeofenceReceiver.ACTION_CANCEL_GATE));
            }
            status.setText("Portão não aberto. Resposta NÃO registrada.");
            finish();
        });
        root.addView(decline);
        Button close = new Button(this);
        close.setText("FECHAR SEM ABRIR");
        close.setOnClickListener(v -> finish());
        root.addView(close);
        setContentView(root);
        if (!valid()) return;
        status.setText("Diga exatamente: SIM, ABRIR PORTÃO. Para recusar, diga NÃO. "
                + "Nenhum comando será enviado em caso de silêncio, erro ou frase diferente. "
                + "Toque no microfone para começar.");
        // The notification tap is the first intentional step; a separate microphone tap
        // prevents recognition from starting unexpectedly on an already-open activity.
    }

    private boolean valid() {
        SharedPreferences p = getSharedPreferences("config", MODE_PRIVATE);
        if (!GeofenceReceiver.gateConfirmationIsPending(this)
                || !p.getBoolean("ativa", false) || p.getBoolean("gate_origin_mock", true)
                || !EwelinkApi.hasSession(this) || !EwelinkApi.hasGate(this)) {
            status.setText("Confirmação indisponível ou expirada. Nenhum comando enviado. "
                    + "GPS fictício nunca habilita a confirmação do portão.");
            return false;
        }
        KeyguardManager kg = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (kg != null && kg.isDeviceLocked()) {
            status.setText("Desbloqueie o celular antes de responder por voz. "
                    + "Por segurança, o portão não abre com a tela bloqueada.");
            return false;
        }
        return true;
    }

    private void listen() {
        if (listening || !valid()) return;
        Intent recognize = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognize.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognize.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        recognize.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognize.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Diga: SIM, ABRIR PORTÃO ou NÃO");
        listening = true;
        status.setText("Aguardando sua resposta. Diga: SIM, ABRIR PORTÃO ou NÃO.");
        try {
            startActivityForResult(recognize, VOICE_REQUEST);
        } catch (ActivityNotFoundException | SecurityException e) {
            listening = false;
            status.setText("Reconhecimento de voz indisponível neste celular. "
                    + "Use os botões da notificação. Portão não acionado.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_REQUEST) return;
        listening = false;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("Não ouvi uma resposta. Nenhum comando enviado.");
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            status.setText("Nenhuma frase reconhecida. Portão não acionado.");
            return;
        }
        String heard = Normalizer.normalize(results.get(0).toLowerCase(new Locale("pt", "BR")),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        if (!valid()) return;
        if (heard.equals("sim abrir portao")) {
            status.setText("Frase de autorização reconhecida. Enviando solicitação do portão.");
            getSharedPreferences("config", MODE_PRIVATE).edit()
                    .putString("gate_voice_status", "Frase explícita confirmada; comando solicitado").apply();
            sendBroadcast(new Intent(this, GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_OPEN_GATE));
            SpeechEngine.speak(this, "Confirmação recebida. Solicitando abertura do portão.");
            finish();
        } else if (heard.equals("nao") || heard.equals("nao abrir portao")) {
            getSharedPreferences("config", MODE_PRIVATE).edit()
                    .putString("gate_voice_status", "Resposta NÃO; portão não aberto").apply();
            sendBroadcast(new Intent(this, GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_CANCEL_GATE));
            status.setText("Resposta NÃO. Portão não aberto.");
            finish();
        } else {
            status.setText("Ouvi: “" + results.get(0) + "”. Frase diferente da autorização. "
                    + "Nenhum comando enviado. Se desejar, toque no microfone novamente.");
        }
    }
}
