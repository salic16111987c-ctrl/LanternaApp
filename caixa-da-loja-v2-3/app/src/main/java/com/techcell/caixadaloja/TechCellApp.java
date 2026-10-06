package com.techcell.caixadaloja;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Inicializa a instância Firebase usada pela nova camada de nuvem do Tech Cell.
 * Mantém a configuração separada das integrações legadas do aplicativo.
 */
public class TechCellApp extends Application {
    private static final String APP_NAME = "TECHCELL_CLOUD";
    private static final String PROJECT_ID = "caixa-da-loja-5dd34";
    private static final String API_KEY = "AIzaSyAcMqWWeaEKfdjIST0NSwkXWWsbSt6iY2k";
    private static final String APPLICATION_ID = "1:243340178302:web:09e0bc0265edc2d2cab92f";
    private static final String STORAGE_BUCKET = "caixa-da-loja-5dd34.firebasestorage.app";

    @Override public void onCreate() {
        super.onCreate();
        inicializarNuvem();
    }

    private void inicializarNuvem() {
        try {
            FirebaseApp.getInstance(APP_NAME);
            return;
        } catch (IllegalStateException ignored) {
        }

        try {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApiKey(API_KEY)
                    .setApplicationId(APPLICATION_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build();
            FirebaseApp.initializeApp(getApplicationContext(), options, APP_NAME);
        } catch (Throwable ignored) {
            // A tela de Nuvem mostra a mensagem detalhada se a inicialização falhar.
        }
    }
}
