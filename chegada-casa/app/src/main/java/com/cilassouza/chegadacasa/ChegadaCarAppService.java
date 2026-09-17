package com.cilassouza.chegadacasa;

import android.content.Intent;
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
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Android Auto IoT screen. Visibility in front of Maps is controlled by the host. */
public class ChegadaCarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        // Test APK only. Before production, allow-list approved host certificates.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull @Override public Session onCreateSession() {
        return new Session() {
            @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
                return new GateHomeScreen(getCarContext());
            }
        };
    }

    private static final class GateHomeScreen extends Screen {
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean active;
        private boolean lastPending;
        private final Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!active) return;
                boolean pending = GeofenceReceiver.gateConfirmationIsPending(getCarContext());
                if (lastPending != pending) {
                    lastPending = pending;
                    invalidate();
                }
                // Check only while this app is visible on the car screen.
                main.postDelayed(this, 1500L);
            }
        };

        GateHomeScreen(@NonNull CarContext carContext) {
            super(carContext);
            getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override public void onStart(@NonNull LifecycleOwner owner) {
                    active = true;
                    lastPending = GeofenceReceiver.gateConfirmationIsPending(getCarContext());
                    invalidate();
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, 1500L);
                }
                @Override public void onStop(@NonNull LifecycleOwner owner) {
                    active = false;
                    main.removeCallbacks(refresh);
                }
                @Override public void onDestroy(@NonNull LifecycleOwner owner) {
                    active = false;
                    main.removeCallbacks(refresh);
                }
            });
        }

        @NonNull @Override public Template onGetTemplate() {
            CarContext ctx = getCarContext();
            if (!EwelinkApi.hasSession(ctx)) {
                return new MessageTemplate.Builder(
                        "Conecte sua conta eWeLink no Chegada Casa do celular.")
                        .setTitle("Chegada Casa").build();
            }
            if (!EwelinkApi.hasGate(ctx)) {
                return new MessageTemplate.Builder(
                        "Configure o dispositivo do portão no aplicativo do celular.")
                        .setTitle("Portão não configurado").build();
            }
            String name = EwelinkApi.getGateName(ctx);
            if (GeofenceReceiver.gateConfirmationIsPending(ctx)) {
                return new MessageTemplate.Builder(
                        "Chegada REAL detectada. Deseja abrir " + name + "? "
                        + "Confirme somente se o portão estiver livre e for seguro.")
                        .setTitle("Abrir portão? SIM ou NÃO")
                        .addAction(new Action.Builder()
                                .setTitle("SIM, ABRIR")
                                .setOnClickListener(this::approve)
                                .build())
                        .addAction(new Action.Builder()
                                .setTitle("NÃO ABRIR")
                                .setOnClickListener(this::decline)
                                .build())
                        .build();
            }
            return new MessageTemplate.Builder(
                    name + " configurado. Aguarde uma nova chegada real para responder "
                    + "SIM ou NÃO. Testes simulados não permitem abrir o portão.")
                    .setTitle("Chegada Casa — aguardando")
                    .build();
        }

        private void approve() {
            // Never call EwelinkApi.pulseGate directly from the car UI.
            // Recheck freshness, origin, account and one-time authorization in receiver.
            if (!GeofenceReceiver.gateConfirmationIsPending(getCarContext())) {
                CarToast.makeText(getCarContext(), "Confirmação expirada; portão não aberto",
                        CarToast.LENGTH_SHORT).show();
                invalidate();
                return;
            }
            getCarContext().sendBroadcast(new Intent(getCarContext(), GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_OPEN_GATE));
            CarToast.makeText(getCarContext(), "Solicitação enviada; confira o resultado",
                    CarToast.LENGTH_SHORT).show();
            main.postDelayed(this::invalidate, 800L);
        }

        private void decline() {
            if (!GeofenceReceiver.gateConfirmationIsPending(getCarContext())) {
                invalidate();
                return;
            }
            getCarContext().sendBroadcast(new Intent(getCarContext(), GeofenceReceiver.class)
                    .setAction(GeofenceReceiver.ACTION_CANCEL_GATE));
            CarToast.makeText(getCarContext(), "NÃO: portão não será aberto",
                    CarToast.LENGTH_SHORT).show();
            main.postDelayed(this::invalidate, 300L);
        }
    }
}
