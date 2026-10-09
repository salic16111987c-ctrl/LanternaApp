package com.techcell.caixadaloja;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * V26: reforco de inicializacao do Caixa WebView.
 * Se o SDK web do Firebase ficar preso em "Conectando ao Firebase...",
 * limpa o cache e faz uma unica nova tentativa. Se ainda falhar, mostra
 * uma mensagem explicita em vez de deixar a tela travada indefinidamente.
 */
public class MainActivityV26 extends MainActivityV25 {
    private WebView caixaWebView;
    private int tentativasFirebase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        caixaWebView = localizarWebView(getWindow().getDecorView());
        if (caixaWebView == null) return;

        try {
            caixaWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        } catch (Throwable ignored) {}

        caixaWebView.postDelayed(this::verificarInicializacaoFirebase, 9000L);
    }

    private void verificarInicializacaoFirebase() {
        if (isFinishing() || caixaWebView == null) return;

        caixaWebView.evaluateJavascript(
                "(function(){try{return (document.body&&document.body.innerText||'').indexOf('Conectando ao Firebase...')>=0?'1':'0'}catch(e){return '0'}})();",
                resultado -> {
                    boolean travado = resultado != null && resultado.contains("1");
                    if (!travado) return;

                    if (tentativasFirebase == 0) {
                        tentativasFirebase++;
                        Toast.makeText(this, "Reconectando ao Firebase...", Toast.LENGTH_SHORT).show();
                        try { caixaWebView.clearCache(true); } catch (Throwable ignored) {}
                        caixaWebView.reload();
                        caixaWebView.postDelayed(this::verificarInicializacaoFirebase, 11000L);
                        return;
                    }

                    caixaWebView.evaluateJavascript(
                            "(function(){try{var s=document.querySelector('.sub');if(s)s.innerHTML='Não foi possível carregar o Firebase.<br><small>Toque no botão abaixo para tentar novamente.</small>';var p=document.querySelector('.login');if(p&&!document.getElementById('retryFirebase')){var b=document.createElement('button');b.id='retryFirebase';b.className='primary';b.style.marginTop='18px';b.style.width='100%';b.textContent='Tentar novamente';b.onclick=function(){location.reload()};p.appendChild(b)}}catch(e){}})();",
                            null);
                    Toast.makeText(this, "O Firebase não respondeu. Toque em Tentar novamente.", Toast.LENGTH_LONG).show();
                });
    }

    private WebView localizarWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            WebView w = localizarWebView(group.getChildAt(i));
            if (w != null) return w;
        }
        return null;
    }
}
