package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private FusedLocationProviderClient fused;
    private GeofencingClient geofencing;
    private SharedPreferences prefs;
    private TextView status;
    private EditText endereco;
    private Spinner raio;
    private CheckBox soNoite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fused = LocationServices.getFusedLocationProviderClient(this);
        geofencing = LocationServices.getGeofencingClient(this);
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        montarTela();
        pedirPermissoesBasicas();
    }

    private void montarTela() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(32));
        root.setBackgroundColor(Color.rgb(246, 246, 246));
        scroll.addView(root);

        TextView titulo = texto("🏠 CHEGADA CASA", 29, true);
        titulo.setGravity(Gravity.CENTER);
        root.addView(titulo);

        TextView sub = texto("Defina sua residência e o raio de acionamento", 15, false);
        sub.setTextColor(Color.DKGRAY);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(6), 0, dp(20));
        root.addView(sub, subParams);

        root.addView(texto("1. Residência", 20, true));
        endereco = new EditText(this);
        endereco.setHint("Rua, número, bairro, cidade - MG");
        endereco.setText(prefs.getString("endereco", ""));
        endereco.setMinLines(2);
        root.addView(endereco);

        Button buscar = botao("BUSCAR ENDEREÇO");
        buscar.setOnClickListener(v -> buscarEndereco());
        root.addView(buscar, paramsBotao());

        Button mapa = botao("MARCAR / AJUSTAR NO MAPA");
        mapa.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        root.addView(mapa, paramsBotao());

        Button atual = botao("USAR LOCALIZAÇÃO ATUAL");
        atual.setOnClickListener(v -> usarLocalizacaoAtual());
        root.addView(atual, paramsBotao());

        LinearLayout.LayoutParams sec = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sec.setMargins(0, dp(22), 0, dp(8));
        root.addView(texto("2. Raio de chegada", 20, true), sec);

        raio = new Spinner(this);
        String[] opcoes = {"100 metros", "200 metros", "300 metros"};
        raio.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcoes));
        int salvo = prefs.getInt("raio", 200);
        raio.setSelection(salvo == 100 ? 0 : salvo == 300 ? 2 : 1);
        root.addView(raio);

        soNoite = new CheckBox(this);
        soNoite.setText("Somente à noite (18h às 6h)");
        soNoite.setTextSize(17);
        soNoite.setChecked(prefs.getBoolean("so_noite", false));
        root.addView(soNoite);

        root.addView(texto("3. eWeLink", 20, true), sec);
        TextView ewStatus = texto("Status: aguardando aprovação da conta de desenvolvedor eWeLink", 16, false);
        ewStatus.setTextColor(Color.DKGRAY);
        root.addView(ewStatus);

        Button ew = botao("CONECTAR AO EWELINK");
        ew.setOnClickListener(v -> Toast.makeText(this,
                "Assim que o eWeLink liberar seu APP ID, este botão abrirá o login oficial e mostrará suas lâmpadas.",
                Toast.LENGTH_LONG).show());
        root.addView(ew, paramsBotao());

        root.addView(texto("4. Automação", 20, true), sec);
        Button ativar = botao("ATIVAR AUTOMAÇÃO");
        ativar.setOnClickListener(v -> ativarGeofence());
        root.addView(ativar, paramsBotao());

        Button testar = botao("TESTAR ALERTA AGORA");
        testar.setOnClickListener(v -> GeofenceReceiver.mostrarNotificacao(this,
                "Teste aprovado",
                "O aplicativo está pronto para detectar sua chegada."));
        root.addView(testar, paramsBotao());

        status = texto("", 16, false);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status);

        TextView autor = texto("Cilas Souza — versão de teste", 12, false);
        autor.setTextColor(Color.GRAY);
        autor.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, dp(24), 0, 0);
        root.addView(autor, ap);

        setContentView(scroll);
        atualizarStatus();
    }

    private TextView texto(String s, int tamanho, boolean negrito) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tamanho);
        t.setTextColor(Color.BLACK);
        if (negrito) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private LinearLayout.LayoutParams paramsBotao() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        p.setMargins(0, dp(9), 0, 0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void pedirPermissoesBasicas() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private void buscarEndereco() {
        String q = endereco.getText().toString().trim();
        if (q.length() < 5) {
            Toast.makeText(this, "Digite o endereço completo da residência.", Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("Procurando endereço...");
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("pt", "BR"));
                List<Address> r = geocoder.getFromLocationName(q, 5);
                runOnUiThread(() -> {
                    if (r == null || r.isEmpty()) {
                        status.setText("Endereço não encontrado. Tente rua, número, bairro, cidade e estado, ou marque no mapa.");
                        return;
                    }
                    Address a = r.get(0);
                    String desc = a.getAddressLine(0) != null ? a.getAddressLine(0) : q;
                    salvarCasa(a.getLatitude(), a.getLongitude(), desc);
                    endereco.setText(desc);
                    status.setText("Residência localizada. Use MARCAR / AJUSTAR NO MAPA para conferir o ponto.");
                });
            } catch (IOException e) {
                runOnUiThread(() -> status.setText("Não consegui consultar o endereço. Confira sua internet ou marque no mapa."));
            }
        }).start();
    }

    private void usarLocalizacaoAtual() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pedirPermissoesBasicas();
            return;
        }
        status.setText("Obtendo localização atual...");
        CancellationTokenSource cts = new CancellationTokenSource();
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        status.setText("Não consegui obter a localização. Ative o GPS e tente novamente.");
                        return;
                    }
                    salvarCasa(loc.getLatitude(), loc.getLongitude(), "Localização atual salva");
                    endereco.setText("Localização atual salva");
                    status.setText("Localização atual definida como residência.");
                })
                .addOnFailureListener(e -> status.setText("Erro de localização: " + e.getMessage()));
    }

    private void salvarCasa(double lat, double lon, String desc) {
        prefs.edit()
                .putLong("lat", Double.doubleToRawLongBits(lat))
                .putLong("lon", Double.doubleToRawLongBits(lon))
                .putString("endereco", desc)
                .putBoolean("casa_definida", true)
                .putBoolean("ativa", false)
                .putBoolean("dentro", false)
                .apply();
    }

    private int raioSelecionado() {
        int p = raio.getSelectedItemPosition();
        return p == 0 ? 100 : p == 2 ? 300 : 200;
    }

    private boolean temBackground() {
        if (Build.VERSION.SDK_INT < 29) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void abrirPermissaoSempre() {
        Toast.makeText(this, "Em Localização, escolha PERMITIR O TEMPO TODO e volte ao aplicativo.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void ativarGeofence() {
        if (!prefs.getBoolean("casa_definida", false)) {
            Toast.makeText(this, "Primeiro defina sua residência pelo endereço, mapa ou localização atual.", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pedirPermissoesBasicas();
            return;
        }
        if (!temBackground()) {
            if (Build.VERSION.SDK_INT == 29) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 102);
            } else {
                abrirPermissaoSempre();
            }
            return;
        }

        int metros = raioSelecionado();
        prefs.edit().putInt("raio", metros).putBoolean("so_noite", soNoite.isChecked()).putBoolean("dentro", false).apply();
        double lat = Double.longBitsToDouble(prefs.getLong("lat", 0));
        double lon = Double.longBitsToDouble(prefs.getLong("lon", 0));

        Geofence g = new Geofence.Builder()
                .setRequestId("casa")
                .setCircularRegion(lat, lon, metros)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest req = new GeofencingRequest.Builder().addGeofence(g).build();
        try {
            geofencing.removeGeofences(getPendingIntent()).addOnCompleteListener(t -> {
                try {
                    geofencing.addGeofences(req, getPendingIntent())
                            .addOnSuccessListener(v -> {
                                prefs.edit().putBoolean("ativa", true).apply();
                                atualizarStatus();
                                Toast.makeText(this, "Automação ativada!", Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(e -> status.setText("Não foi possível ativar: " + e.getMessage()));
                } catch (SecurityException e) {
                    status.setText("Permissão de localização insuficiente.");
                }
            });
        } catch (SecurityException e) {
            status.setText("Permissão de localização insuficiente.");
        }
    }

    private PendingIntent getPendingIntent() {
        Intent i = new Intent(this, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(this, 77, i, flags);
    }

    private void atualizarStatus() {
        if (!prefs.getBoolean("casa_definida", false)) {
            status.setText("Residência ainda não definida. Você pode digitar o endereço mesmo estando longe de casa.");
            return;
        }
        String casa = prefs.getString("endereco", "Ponto salvo no mapa");
        if (prefs.getBoolean("ativa", false)) {
            status.setText("AUTOMAÇÃO ATIVA — raio de " + prefs.getInt("raio", 200) + " m.\nCasa: " + casa);
        } else {
            status.setText("Casa definida: " + casa + "\nConfira no mapa e depois ative a automação.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && endereco != null && prefs.getBoolean("casa_definida", false)) {
            String e = prefs.getString("endereco", "");
            if (!e.isEmpty()) endereco.setText(e);
        }
        if (status != null) atualizarStatus();
    }
}
