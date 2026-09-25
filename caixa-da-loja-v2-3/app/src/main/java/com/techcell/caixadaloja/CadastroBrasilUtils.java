package com.techcell.caixadaloja;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BooleanSupplier;

public final class CadastroBrasilUtils {
    private CadastroBrasilUtils() {}

    public static class CepData {
        public String cep = "";
        public String logradouro = "";
        public String bairro = "";
        public String municipio = "";
        public String uf = "";
        public String complemento = "";
    }

    public static class CnpjData {
        public String cnpj = "";
        public String razaoSocial = "";
        public String nomeFantasia = "";
        public String cep = "";
        public String logradouro = "";
        public String numero = "";
        public String complemento = "";
        public String bairro = "";
        public String municipio = "";
        public String uf = "";
        public String telefone = "";
        public String email = "";
        public String situacao = "";
    }

    public interface CepCallback {
        void onSuccess(CepData data);
        void onError(String mensagem);
    }

    public interface CnpjCallback {
        void onSuccess(CnpjData data);
        void onError(String mensagem);
    }

    public static String apenasDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("[^0-9]", "");
    }

    public static String formatarCep(String valor) {
        String d = limitar(apenasDigitos(valor), 8);
        if (d.length() <= 5) return d;
        return d.substring(0,5) + "-" + d.substring(5);
    }

    public static String formatarCpf(String valor) {
        String d = limitar(apenasDigitos(valor), 11);
        StringBuilder s = new StringBuilder();
        for (int i=0; i<d.length(); i++) {
            if (i == 3 || i == 6) s.append('.');
            if (i == 9) s.append('-');
            s.append(d.charAt(i));
        }
        return s.toString();
    }

    public static String formatarCnpj(String valor) {
        String d = limitar(apenasDigitos(valor), 14);
        StringBuilder s = new StringBuilder();
        for (int i=0; i<d.length(); i++) {
            if (i == 2 || i == 5) s.append('.');
            if (i == 8) s.append('/');
            if (i == 12) s.append('-');
            s.append(d.charAt(i));
        }
        return s.toString();
    }

    public static String formatarDocumento(String valor, boolean cnpj) {
        return cnpj ? formatarCnpj(valor) : formatarCpf(valor);
    }

    private static String limitar(String s, int max) {
        return s.length() > max ? s.substring(0,max) : s;
    }

    public static void aplicarMascaraCep(EditText campo) {
        aplicarMascara(campo, () -> formatarCep(campo.getText().toString()));
    }

    public static void aplicarMascaraCnpj(EditText campo) {
        aplicarMascara(campo, () -> formatarCnpj(campo.getText().toString()));
    }

    public static void aplicarMascaraDocumento(EditText campo, BooleanSupplier usarCnpj) {
        aplicarMascara(campo, () -> formatarDocumento(
                campo.getText().toString(), usarCnpj.getAsBoolean()));
    }

    public static void reformatarDocumento(EditText campo, boolean cnpj) {
        String novo = formatarDocumento(campo.getText().toString(), cnpj);
        if (!novo.equals(campo.getText().toString())) {
            campo.setText(novo);
            campo.setSelection(novo.length());
        }
    }

    private interface Formatador { String formatar(); }

    private static void aplicarMascara(EditText campo, Formatador formatador) {
        campo.addTextChangedListener(new TextWatcher() {
            boolean alterando = false;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) {
                if (alterando) return;
                String atual = e.toString();
                String novo = formatador.formatar();
                if (!atual.equals(novo)) {
                    alterando = true;
                    campo.setText(novo);
                    campo.setSelection(novo.length());
                    alterando = false;
                }
            }
        });
    }

    public static boolean cpfValido(String valor) {
        String cpf = apenasDigitos(valor);
        if (cpf.length() != 11 || todosIguais(cpf)) return false;
        int soma = 0;
        for (int i=0; i<9; i++) soma += (cpf.charAt(i)-'0') * (10-i);
        int d1 = 11 - (soma % 11);
        if (d1 >= 10) d1 = 0;
        if (d1 != cpf.charAt(9)-'0') return false;

        soma = 0;
        for (int i=0; i<10; i++) soma += (cpf.charAt(i)-'0') * (11-i);
        int d2 = 11 - (soma % 11);
        if (d2 >= 10) d2 = 0;
        return d2 == cpf.charAt(10)-'0';
    }

    public static boolean cnpjValido(String valor) {
        String cnpj = apenasDigitos(valor);
        if (cnpj.length() != 14 || todosIguais(cnpj)) return false;
        int[] p1 = {5,4,3,2,9,8,7,6,5,4,3,2};
        int[] p2 = {6,5,4,3,2,9,8,7,6,5,4,3,2};

        int soma = 0;
        for (int i=0; i<12; i++) soma += (cnpj.charAt(i)-'0') * p1[i];
        int r = soma % 11;
        int d1 = r < 2 ? 0 : 11-r;
        if (d1 != cnpj.charAt(12)-'0') return false;

        soma = 0;
        for (int i=0; i<13; i++) soma += (cnpj.charAt(i)-'0') * p2[i];
        r = soma % 11;
        int d2 = r < 2 ? 0 : 11-r;
        return d2 == cnpj.charAt(13)-'0';
    }

    private static boolean todosIguais(String s) {
        if (s.isEmpty()) return true;
        char c = s.charAt(0);
        for (int i=1; i<s.length(); i++) if (s.charAt(i) != c) return false;
        return true;
    }

    public static void buscarCep(Activity activity, String valor, CepCallback callback) {
        String cep = apenasDigitos(valor);
        if (cep.length() != 8) {
            callback.onError("CEP inválido.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://viacep.com.br/ws/" + cep + "/json/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "TechCellACS/1.0 Android");

                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);

                String json = ler(conn);
                JSONObject o = new JSONObject(json);
                if (o.optBoolean("erro", false)) throw new Exception("CEP não encontrado");

                CepData d = new CepData();
                d.cep = o.optString("cep", cep);
                d.logradouro = o.optString("logradouro", "");
                d.bairro = o.optString("bairro", "");
                d.municipio = o.optString("localidade", "");
                d.uf = o.optString("uf", "");
                d.complemento = o.optString("complemento", "");

                activity.runOnUiThread(() -> callback.onSuccess(d));
            } catch (Exception ex) {
                activity.runOnUiThread(() -> callback.onError("Não foi possível consultar o CEP."));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public static void buscarCnpj(Activity activity, String valor, CnpjCallback callback) {
        String cnpj = apenasDigitos(valor);
        if (!cnpjValido(cnpj)) {
            callback.onError("CNPJ inválido.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://brasilapi.com.br/api/cnpj/v1/" + cnpj);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "TechCellACS-Android/1.0");

                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);

                String json = ler(conn);
                JSONObject o = new JSONObject(json);

                CnpjData d = new CnpjData();
                d.cnpj = o.optString("cnpj", cnpj);
                d.razaoSocial = o.optString("razao_social", "");
                d.nomeFantasia = o.optString("nome_fantasia", "");
                d.cep = apenasDigitos(o.optString("cep", ""));
                d.logradouro = o.optString("logradouro", "");
                d.numero = o.optString("numero", "");
                d.complemento = o.optString("complemento", "");
                d.bairro = o.optString("bairro", "");
                d.municipio = o.optString("municipio", "");
                d.uf = o.optString("uf", "");
                d.telefone = o.optString("ddd_telefone_1", "");
                d.email = o.optString("email", "");
                d.situacao = o.optString("descricao_situacao_cadastral", "");

                activity.runOnUiThread(() -> callback.onSuccess(d));
            } catch (Exception ex) {
                activity.runOnUiThread(() -> callback.onError(
                        "CNPJ válido, mas a consulta automática não respondeu."));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static String ler(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder s = new StringBuilder();
        String linha;
        while ((linha = br.readLine()) != null) s.append(linha);
        br.close();
        return s.toString();
    }
}
