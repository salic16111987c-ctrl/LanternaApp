package com.cilassouza.chegadacasa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.model.Action;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.validation.HostValidator;

public class ChegadaCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // O APK atual é de teste/sideload. Antes de publicar na Play Store,
        // restringir aos hosts oficiais conforme a validação exigida pela Google.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new GateHomeScreen(getCarContext());
            }
        };
    }

    private static class GateHomeScreen extends Screen {
        GateHomeScreen(@NonNull CarContext carContext) {
            super(carContext);
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            CarContext ctx = getCarContext();

            if (!EwelinkApi.hasSession(ctx)) {
                return new MessageTemplate.Builder(
                        "Conecte sua conta eWeLink pelo aplicativo Chegada Casa no celular.")
                        .setTitle("Chegada Casa")
                        .build();
            }

            if (!EwelinkApi.hasGate(ctx)) {
                return new MessageTemplate.Builder(
                        "Abra o Chegada Casa no celular e use ESCOLHER PORTÃO para definir o dispositivo do eWeLink.")
                        .setTitle("Portão não configurado")
                        .build();
            }

            String nome = EwelinkApi.getGateName(ctx);
            if (GeofenceReceiver.gateConfirmationIsPending(ctx)) {
                return new MessageTemplate.Builder(
                        "Chegada detectada perto de casa. Deseja abrir " + nome + "?")
                        .setTitle("Confirmar abertura")
                        .addAction(new Action.Builder()
                                .setTitle("ABRIR PORTÃO")
                                .setOnClickListener(this::confirmarChegada)
                                .build())
                        .addAction(new Action.Builder()
                                .setTitle("NÃO ABRIR")
                                .setOnClickListener(this::cancelarChegada)
                                .build())
                        .build();
            }

            return new MessageTemplate.Builder(
                    nome + " está configurado. A abertura sempre exige uma confirmação antes de enviar o comando.")
                    .setTitle("Chegada Casa")
                    .addAction(new Action.Builder()
                            .setTitle("ABRIR PORTÃO")
                            .setOnClickListener(() -> getScreenManager().push(
                                    new GateConfirmScreen(getCarContext())))
                            .build())
                    .build();
        }

        private void confirmarChegada() {
            Intent i = new Intent(getCarContext(), GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_OPEN_GATE);
            getCarContext().sendBroadcast(i);
            CarToast.makeText(getCarContext(), "Comando do portão enviado", CarToast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::invalidate, 900L);
        }

        private void cancelarChegada() {
            Intent i = new Intent(getCarContext(), GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_CANCEL_GATE);
            getCarContext().sendBroadcast(i);
            CarToast.makeText(getCarContext(), "Portão não será aberto", CarToast.LENGTH_SHORT).show();
            invalidate();
        }
    }

    private static class GateConfirmScreen extends Screen {
        private boolean enviando = false;
        private String status = "Confirme somente se for seguro abrir o portão agora.";

        GateConfirmScreen(@NonNull CarContext carContext) {
            super(carContext);
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            MessageTemplate.Builder b = new MessageTemplate.Builder(status)
                    .setTitle("Abrir " + EwelinkApi.getGateName(getCarContext()));

            if (!enviando) {
                b.addAction(new Action.Builder()
                        .setTitle("CONFIRMAR ABERTURA")
                        .setOnClickListener(this::abrir)
                        .build());
                b.addAction(new Action.Builder()
                        .setTitle("CANCELAR")
                        .setOnClickListener(() -> getScreenManager().pop())
                        .build());
            }
            return b.build();
        }

        private void abrir() {
            if (enviando) return;
            enviando = true;
            status = "Enviando comando ao eWeLink...";
            invalidate();

            EwelinkApi.pulseGate(getCarContext().getApplicationContext(), new EwelinkApi.TextCallback() {
                @Override
                public void onSuccess(String message) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        status = "Portão acionado.";
                        CarToast.makeText(getCarContext(), "Portão acionado", CarToast.LENGTH_SHORT).show();
                        invalidate();
                    });
                }

                @Override
                public void onError(String message) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        enviando = false;
                        status = "Falha: " + message;
                        CarToast.makeText(getCarContext(), "Falha ao acionar o portão", CarToast.LENGTH_SHORT).show();
                        invalidate();
                    });
                }
            });
        }
    }
}
