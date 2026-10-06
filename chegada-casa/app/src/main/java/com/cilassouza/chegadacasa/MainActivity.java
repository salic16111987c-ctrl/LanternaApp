package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private FusedLocationProviderClient fused;
    private GeofencingClient geofencing;
    private SharedPreferences prefs;
    private TextView status;
    private TextView ewStatus;
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
    scroll.setFillViewport(true);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(16), dp(18), dp(16), dp(34));
    root.setBackgroundColor(Color.rgb(244, 247, 251));
    scroll.addView(root);

    TextView titulo = uiText("Chegada Casa Rápido", 27, Color.rgb(20, 32, 52), true);
    titulo.setGravity(Gravity.CENTER);
    root.addView(titulo);

    TextView sub = uiText("Automação de chegada • GPS inteligente • eWeLink", 14,
            Color.rgb(92, 105, 126), false);
    sub.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    subParams.setMargins(0, dp(5), 0, dp(18));
    root.addView(sub, subParams);

    LinearLayout casaCard = uiCard("Residência", "Defina o ponto usado para calcular sua chegada.");
    endereco = new EditText(this);
    endereco.setHint("Rua, número, bairro, cidade - MG");
    endereco.setText(prefs.getString("endereco", ""));
    endereco.setTextSize(16);
    endereco.setMinLines(2);
    endereco.setPadding(dp(14), dp(11), dp(14), dp(11));
    endereco.setBackground(uiBackground(Color.WHITE, Color.rgb(208, 215, 226), 12));
    LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    addressParams.setMargins(0, dp(10), 0, 0);
    casaCard.addView(endereco, addressParams);

    Button buscar = uiButton("Buscar endereço", true);
    buscar.setOnClickListener(v -> buscarEndereco());
    Button mapa = uiButton("Ajustar no mapa", false);
    mapa.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
    addButtonRow(casaCard, buscar, mapa);

    Button atual = uiButton("Usar localização atual", false);
    atual.setOnClickListener(v -> usarLocalizacaoAtual());
    addWideButton(casaCard, atual);
    root.addView(casaCard, cardParams());

    LinearLayout raioCard = uiCard("Raio e horário", "Ajuste a distância de chegada e quando as lâmpadas podem ligar.");
    TextView raioLabel = uiText("Raio de chegada", 13, Color.rgb(76, 91, 112), true);
    raioCard.addView(raioLabel);
    raio = new Spinner(this);
    String[] opcoes = {"100 metros", "200 metros", "300 metros"};
    raio.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcoes));
    int salvo = prefs.getInt("raio", 100);
    raio.setSelection(salvo == 200 ? 1 : salvo == 300 ? 2 : 0);
    raio.setPadding(dp(10), dp(5), dp(10), dp(5));
    raio.setBackground(uiBackground(Color.WHITE, Color.rgb(208, 215, 226), 12));
    LinearLayout.LayoutParams raioParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
    raioParams.setMargins(0, dp(7), 0, dp(7));
    raioCard.addView(raio, raioParams);

    soNoite = new CheckBox(this);
    soNoite.setText("Somente lâmpadas à noite (18h às 6h) — portão continua 24 horas");
    soNoite.setTextSize(14);
    soNoite.setTextColor(Color.rgb(48, 60, 78));
    soNoite.setChecked(prefs.getBoolean("so_noite", false));
    raioCard.addView(soNoite);
    TextView gpsNote = uiText("GPS inteligente economiza bateria longe de casa e aumenta a precisão na aproximação. O portão só abre após confirmação explícita.",
            12, Color.rgb(102, 114, 132), false);
    gpsNote.setPadding(0, dp(4), 0, 0);
    raioCard.addView(gpsNote);
    root.addView(raioCard, cardParams());

    LinearLayout ewCard = uiCard("eWeLink", "Conecte sua conta e escolha separadamente luzes e portão.");
    ewStatus = uiInfo("");
    ewCard.addView(ewStatus);

    Button conectar = uiButton("Conectar ao eWeLink", true);
    conectar.setOnClickListener(v -> mostrarEtapaOAuth());
    addWideButton(ewCard, conectar);

    Button escolher = uiButton("Escolher lâmpadas", false);
    escolher.setOnClickListener(v -> carregarDispositivos());
    Button portao = uiButton("Escolher portão", false);
    portao.setOnClickListener(v -> carregarPortao());
    addButtonRow(ewCard, escolher, portao);

    Button testarEw = uiButton("Testar lâmpadas eWeLink", false);
    testarEw.setOnClickListener(v -> testarEwelink());
    addWideButton(ewCard, testarEw);
    root.addView(ewCard, cardParams());

    LinearLayout autoCard = uiCard("Automação", "Ative o monitoramento e confira o funcionamento em segundo plano.");
    status = uiInfo("");
    autoCard.addView(status);

    Button ativar = uiButton("Ativar automação", true);
    ativar.setOnClickListener(v -> ativarGeofence());
    addWideButton(autoCard, ativar);

    Button bateria = uiButton("Segundo plano / bateria", false);
    bateria.setOnClickListener(v -> abrirConfiguracaoBateria());
    Button testar = uiButton("Testar notificação", false);
    testar.setOnClickListener(v -> GeofenceReceiver.mostrarNotificacao(this,
            "Teste aprovado", "O aplicativo está pronto para detectar sua chegada."));
    addButtonRow(autoCard, bateria, testar);
    root.addView(autoCard, cardParams());

    TextView autor = uiText("Cilas Souza • Chegada Casa Rápido v2.8", 12,
            Color.rgb(125, 137, 154), false);
    autor.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    ap.setMargins(0, dp(10), 0, 0);
    root.addView(autor, ap);

    setContentView(scroll);
    atualizarStatus();
    atualizarEwelinkStatus();
}

private TextView uiText(String value, int size, int color, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
    return view;
}

private LinearLayout uiCard(String title, String subtitle) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(16), dp(15), dp(16), dp(16));
    card.setBackground(uiBackground(Color.WHITE, Color.rgb(226, 231, 239), 18));
    card.setElevation(dp(2));

    TextView heading = uiText(title, 19, Color.rgb(28, 39, 58), true);
    card.addView(heading);
    TextView description = uiText(subtitle, 13, Color.rgb(100, 112, 130), false);
    description.setPadding(0, dp(3), 0, dp(8));
    card.addView(description);
    return card;
}

private TextView uiInfo(String value) {
    TextView info = uiText(value, 14, Color.rgb(54, 67, 87), false);
    info.setPadding(dp(12), dp(11), dp(12), dp(11));
    info.setBackground(uiBackground(Color.rgb(248, 250, 253), Color.rgb(227, 232, 240), 12));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.setMargins(0, dp(2), 0, dp(2));
    info.setLayoutParams(p);
    return info;
}

private Button uiButton(String label, boolean primary) {
    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setTextSize(14);
    button.setTypeface(Typeface.DEFAULT_BOLD);
    button.setGravity(Gravity.CENTER);
    button.setPadding(dp(10), 0, dp(10), 0);
    if (primary) {
        button.setTextColor(Color.WHITE);
        button.setBackground(uiBackground(Color.rgb(27, 91, 198), Color.rgb(27, 91, 198), 12));
    } else {
        button.setTextColor(Color.rgb(35, 58, 93));
        button.setBackground(uiBackground(Color.rgb(246, 249, 253), Color.rgb(207, 216, 229), 12));
    }
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    return button;
}

private void addWideButton(LinearLayout parent, Button button) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
    p.setMargins(0, dp(9), 0, 0);
    parent.addView(button, p);
}

private void addButtonRow(LinearLayout parent, Button left, Button right) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
    rp.setMargins(0, dp(9), 0, 0);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    lp.setMargins(0, 0, dp(4), 0);
    LinearLayout.LayoutParams rr = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    rr.setMargins(dp(4), 0, 0, 0);
    row.addView(left, lp);
    row.addView(right, rr);
    parent.addView(row, rp);
}

private LinearLayout.LayoutParams cardParams() {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.setMargins(0, 0, 0, dp(12));
    return p;
}

private android.graphics.drawable.GradientDrawable uiBackground(int fill, int stroke, int radiusDp) {
    android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
    drawable.setColor(fill);
    drawable.setCornerRadius(dp(radiusDp));
    drawable.setStroke(dp(1), stroke);
    return drawable;
}

    private void mostrarEtapaOAuth() {
        if (EwelinkApi.hasSession(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("eWeLink conectado")
                    .setMessage("A autorização da conta já está salva no aparelho. Agora você pode escolher as lâmpadas e, separadamente, o portão.")
                    .setPositiveButton("OK", null)
                    .setNegativeButton("DESCONECTAR", (d, w) -> {
                        EwelinkApi.clearSession(this);
                        atualizarEwelinkStatus();
                    })
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("APPID aprovado")
                .setMessage("Sua aplicação do eWeLink já está aprovada e o APPID já foi configurado no Chegada Casa. A camada que lista e aciona os dispositivos também já está pronta. Falta somente concluir o login OAuth para o eWeLink entregar o token da sua conta.\n\nO SEGREDO DO APP não será colocado dentro do APK nem no GitHub.")
                .setPositiveButton("ENTENDI", null)
                .show();
    }

    private void carregarDispositivos() {
        if (!EwelinkApi.hasSession(this)) {
            Toast.makeText(this, "Primeiro precisamos concluir o login OAuth do eWeLink.", Toast.LENGTH_LONG).show();
            return;
        }
        ewStatus.setText("Consultando suas lâmpadas/dispositivos no eWeLink...");
        EwelinkApi.fetchDevices(this, new EwelinkApi.DevicesCallback() {
            @Override
            public void onSuccess(List<EwelinkApi.Device> devices) {
                runOnUiThread(() -> mostrarDispositivos(devices));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ewStatus.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void mostrarDispositivos(List<EwelinkApi.Device> devices) {
        if (devices.isEmpty()) {
            ewStatus.setText("Nenhum dispositivo liga/desliga compatível apareceu nessa conta eWeLink.");
            return;
        }

        String gateId = EwelinkApi.getGateDeviceId(this);
        String[] nomes = new String[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            EwelinkApi.Device d = devices.get(i);
            String marcaPortao = gateId.equals(d.id) ? "  • PORTÃO — não abre automaticamente" : "";
            nomes[i] = d.name + (d.online ? "  • online" : "  • offline") + marcaPortao;
        }

        new AlertDialog.Builder(this)
                .setTitle("Quais lâmpadas devem ligar automaticamente?")
                .setMultiChoiceItems(nomes, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("SALVAR", (dialog, which) -> {
                    EwelinkApi.saveSelected(this, devices, checked);
                    atualizarEwelinkStatus();
                    Toast.makeText(this, "Lâmpadas automáticas salvas. O portão configurado é sempre ignorado aqui.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void carregarPortao() {
        if (!EwelinkApi.hasSession(this)) {
            Toast.makeText(this, "Primeiro conecte sua conta eWeLink.", Toast.LENGTH_LONG).show();
            return;
        }
        ewStatus.setText("Consultando dispositivos para escolher o portão...");
        EwelinkApi.fetchDevices(this, new EwelinkApi.DevicesCallback() {
            @Override
            public void onSuccess(List<EwelinkApi.Device> devices) {
                runOnUiThread(() -> mostrarPortoes(devices));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ewStatus.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void mostrarPortoes(List<EwelinkApi.Device> devices) {
        if (devices.isEmpty()) {
            ewStatus.setText("Nenhum dispositivo compatível apareceu para configurar como portão.");
            return;
        }

        List<String> labels = new ArrayList<>();
        List<EwelinkApi.Device> targets = new ArrayList<>();
        List<Integer> outlets = new ArrayList<>();

        for (EwelinkApi.Device d : devices) {
            int canais = Math.max(1, d.channels);
            if (canais == 1) {
                labels.add(d.name + (d.online ? "  • online" : "  • offline"));
                targets.add(d);
                outlets.add(0);
            } else {
                for (int ch = 0; ch < canais; ch++) {
                    labels.add(d.name + " — canal " + (ch + 1) + (d.online ? "  • online" : "  • offline"));
                    targets.add(d);
                    outlets.add(ch);
                }
            }
        }

        int[] escolhido = {-1};
        new AlertDialog.Builder(this)
                .setTitle("Qual dispositivo abre o portão?")
                .setSingleChoiceItems(labels.toArray(new String[0]), -1,
                        (dialog, which) -> escolhido[0] = which)
                .setPositiveButton("SALVAR PORTÃO", (dialog, which) -> {
                    if (escolhido[0] < 0) {
                        Toast.makeText(this, "Selecione o dispositivo/canal do portão.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    EwelinkApi.saveGate(this, targets.get(escolhido[0]), outlets.get(escolhido[0]));
                    atualizarEwelinkStatus();
                    Toast.makeText(this,
                            "Portão salvo. Ele só será acionado depois que você tocar em ABRIR PORTÃO na confirmação de chegada.",
                            Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("REMOVER PORTÃO", (dialog, which) -> {
                    EwelinkApi.clearGate(this);
                    atualizarEwelinkStatus();
                    Toast.makeText(this, "Portão removido da automação.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void testarEwelink() {
        if (!EwelinkApi.hasSession(this)) {
            Toast.makeText(this, "eWeLink ainda não autorizado.", Toast.LENGTH_LONG).show();
            return;
        }
        if (EwelinkApi.selectedCount(this) == 0) {
            Toast.makeText(this, "Primeiro escolha as lâmpadas.", Toast.LENGTH_LONG).show();
            return;
        }
        ewStatus.setText("Enviando comando de teste às lâmpadas...");
        EwelinkApi.turnOnSelected(this, new EwelinkApi.TextCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    ewStatus.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ewStatus.setText(message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void atualizarEwelinkStatus() {
        if (ewStatus == null) return;
        if (EwelinkApi.hasSession(this)) {
            int n = EwelinkApi.selectedCount(this);
            String portao = EwelinkApi.hasGate(this) ? EwelinkApi.getGateName(this) : "não configurado";
            ewStatus.setText("✓ Conta eWeLink autorizada\n✓ APPID configurado\nLâmpadas automáticas: " + n + "\nPortão com confirmação: " + portao);
        } else {
            ewStatus.setText("✓ Conta de desenvolvedor aprovada\n✓ APPID configurado\n✓ OAuth 2.0 cadastrado\nAguardando concluir o login da conta eWeLink");
        }
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
        return p == 1 ? 200 : p == 2 ? 300 : 100;
    }

    private boolean temBackground() {
        if (Build.VERSION.SDK_INT < 29) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void abrirPermissaoSempre() {
        Toast.makeText(this, "Em Localização, escolha PERMITIR O TEMPO TODO e volte ao aplicativo.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void abrirConfiguracaoBateria() {
        new AlertDialog.Builder(this)
                .setTitle("Segundo plano")
                .setMessage("Para a chegada funcionar com a tela apagada, deixe a localização como PERMITIR O TEMPO TODO e remova restrições de bateria do Chegada Casa. Na próxima tela procure Bateria / Economia de bateria e escolha SEM RESTRIÇÕES, se essa opção existir no aparelho.")
                .setPositiveButton("ABRIR CONFIGURAÇÕES", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("DEPOIS", null)
                .show();
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

        Geofence outer = new Geofence.Builder()
                .setRequestId("aproximacao_2500m")
                .setCircularRegion(lat, lon, 2500f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest req = new GeofencingRequest.Builder()
                .addGeofence(g).addGeofence(outer).build();
        try {
            geofencing.removeGeofences(getPendingIntent()).addOnCompleteListener(t -> {
                try {
                    geofencing.addGeofences(req, getPendingIntent())
                            .addOnSuccessListener(v -> {
                                prefs.edit().putBoolean("ativa", true).apply();
                                atualizarStatus();
                                Toast.makeText(this, "Automação ativada em " + metros + " m!", Toast.LENGTH_LONG).show();
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
            status.setText("AUTOMAÇÃO ATIVA — raio de " + prefs.getInt("raio", 100) + " m.\nCasa: " + casa);
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
        atualizarEwelinkStatus();
    }
}
