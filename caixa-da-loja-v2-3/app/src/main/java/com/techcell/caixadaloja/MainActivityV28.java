package com.techcell.caixadaloja;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * V28: mascara monetaria do Caixa.
 * O operador digita somente numeros e os dois ultimos digitos sao os centavos.
 * Exemplos: 1 -> 0,01 | 12 -> 0,12 | 123 -> 1,23 | 1234 -> 12,34.
 */
public class MainActivityV28 extends MainActivityV27 {
    private final WeakHashMap<EditText, Boolean> camposPreparados = new WeakHashMap<>();

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        prepararCamposMoeda(view);
    }

    private void prepararCamposMoeda(View view) {
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            int classe = edit.getInputType() & InputType.TYPE_MASK_CLASS;
            if (classe == InputType.TYPE_CLASS_NUMBER && !camposPreparados.containsKey(edit)) {
                aplicarMascaraCentavos(edit);
                camposPreparados.put(edit, Boolean.TRUE);
            }
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) view;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                prepararCamposMoeda(grupo.getChildAt(i));
            }
        }
    }

    private void aplicarMascaraCentavos(EditText edit) {
        // Teclado somente numerico: o usuario nao precisa digitar ponto ou virgula.
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);

        final NumberFormat formato = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        formato.setGroupingUsed(true);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);

        edit.addTextChangedListener(new TextWatcher() {
            private boolean ajustando;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (ajustando) return;

                String digitos = s == null ? "" : s.toString().replaceAll("[^0-9]", "");
                String novo;
                if (digitos.isEmpty()) {
                    novo = "";
                } else {
                    // Evita estouro em digitacoes muito longas sem quebrar o campo.
                    if (digitos.length() > 15) digitos = digitos.substring(0, 15);
                    long centavos;
                    try {
                        centavos = Long.parseLong(digitos);
                    } catch (Throwable ignored) {
                        centavos = 0L;
                    }
                    novo = centavos == 0L ? "" : formato.format(centavos / 100.0d);
                }

                if (novo.equals(s == null ? "" : s.toString())) return;

                ajustando = true;
                edit.setText(novo);
                edit.setSelection(edit.getText().length());
                ajustando = false;
            }
        });
    }
}
