package com.techcell.caixadaloja;

import android.app.Application;

/**
 * Inicializa a instância Firebase usada pela nova camada de nuvem do Tech Cell.
 * A configuração fica centralizada em TechCellCloudSync para evitar divergência
 * de chave/API entre o bootstrap, o login e a sincronização.
 */
public class TechCellApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            TechCellCloudSync.app(this);
        } catch (Throwable ignored) {
            // A tela de Nuvem mostra a mensagem detalhada se a inicialização falhar.
        }
    }
}
