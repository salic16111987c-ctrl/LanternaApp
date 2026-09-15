package com.cilas.caixaloja;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 7001;
    private static final int COLOR_PRIMARY = Color.rgb(22, 91, 112);
    private static final int COLOR_DARK = Color.rgb(28, 40, 48);
    private static final int COLOR_BG = Color.rgb(245, 247, 248);

    private DatabaseHelper db;
    private CloudSyncManager cloud;
    private boolean onHome = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        cloud = new CloudSyncManager(this);
        showHome();
    }

    private LinearLayout createScreen(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(27);
        heading.setTextColor(COLOR_DARK);
        heading.setTypeface(null, 1);
        root.addView(heading);

        TextView sub = body(subtitle);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);
        return root;
    }

    private void showHome() {
        onHome = true;
        LinearLayout root = createScreen(
                "Caixa da Loja",
                "Caixa local + sincronização em nuvem.");

        addSectionTitle(root, "Nuvem");
        if (!cloud.isConfigured()) {
            root.addView(body("A nuvem ainda não foi configurada. O app continua funcionando localmente."));
            Button config = primaryButton("CONFIGURAR NUVEM");
            config.setOnClickListener(v -> showCloudConfig());
            root.addView(config, marginTop(10));
        } else if (!cloud.isLoggedIn()) {
            root.addView(body("Projeto Firebase configurado. Faça login para sincronizar os dois celulares."));
            Button login = primaryButton("ENTRAR NA NUVEM");
            login.setOnClickListener(v -> showCloudLogin());
            root.addView(login, marginTop(10));

            Button config = secondaryButton("ALTERAR CONFIGURAÇÃO DA NUVEM");
            config.setOnClickListener(v -> showCloudConfig());
            root.addView(config, marginTop(8));
        } else {
            root.addView(body("Conectado: " + cloud.getEmail() + "\nPerfil: " + cloud.getRole() + "\nLoja: " + cloud.getStoreId()));
            Button sync = primaryButton("SINCRONIZAR AGORA");
            sync.setOnClickListener(v -> cloud.pullAll(db, (ok, message) -> runOnUiThread(() ->
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show())));
            root.addView(sync, marginTop(10));

            Button logout = secondaryButton("SAIR DA NUVEM");
            logout.setOnClickListener(v -> {
                cloud.logout();
                showHome();
            });
            root.addView(logout, marginTop(8));
        }

        addSectionTitle(root, "Telefone do caixa");
        root.addView(body("Use esta área para lançar a entrada em dinheiro e cartão do dia."));
        Button caixa = primaryButton("ABRIR CAIXA");
        caixa.setOnClickListener(v -> showCaixa());
        root.addView(caixa, marginTop(12));

        addSectionTitle(root, "Telefone do administrador");
        root.addView(body("Consulta de dia, semana, mês e ano, fechamento mensal, despesas e relatório Excel."));
        Button admin = primaryButton("ABRIR ADMINISTRADOR");
        if (cloud.isLoggedIn() && !cloud.isAdmin()) {
            admin.setEnabled(false);
            admin.setText("ADMINISTRADOR - ACESSO RESTRITO");
        } else {
            admin.setOnClickListener(v -> showAdmin());
        }
        root.addView(admin, marginTop(12));

        TextView note = body("Sem internet, os lançamentos continuam salvos neste aparelho. Quando a nuvem estiver configurada e conectada, os dados serão enviados para o Firebase.");
        note.setPadding(0, dp(28), 0, 0);
        root.addView(note);
    }

    private void showCloudConfig() {
        onHome = false;
        LinearLayout root = createScreen(
                "Configurar Nuvem",
                "Use os dados do aplicativo Android criado no Firebase.");

        EditText projectId = field("Project ID", InputType.TYPE_CLASS_TEXT);
        projectId.setText(cloud.getProjectId());
        root.addView(projectId);

        EditText appId = field("App ID", InputType.TYPE_CLASS_TEXT);
        appId.setText(cloud.getAppId());
        root.addView(appId, marginTop(10));

        EditText apiKey = field("API Key", InputType.TYPE_CLASS_TEXT);
        apiKey.setText(cloud.getApiKey());
        root.addView(apiKey, marginTop(10));

        TextView info = body("Esses identificadores vêm do projeto Firebase. Eles configuram qual banco o aplicativo deve usar. Senhas dos usuários não ficam gravadas nessa tela.");
        info.setPadding(0, dp(12), 0, 0);
        root.addView(info);

        Button save = primaryButton("SALVAR CONFIGURAÇÃO");
        save.setOnClickListener(v -> {
            if (projectId.getText().toString().trim().isEmpty()
                    || appId.getText().toString().trim().isEmpty()
                    || apiKey.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Preencha Project ID, App ID e API Key.", Toast.LENGTH_LONG).show();
                return;
            }
            cloud.saveConfig(apiKey.getText().toString(), appId.getText().toString(), projectId.getText().toString());
            Toast.makeText(this, "Configuração salva.", Toast.LENGTH_LONG).show();
            showHome();
        });
        root.addView(save, marginTop(18));
        addBack(root);
    }

    private void showCloudLogin() {
        onHome = false;
        LinearLayout root = createScreen(
                "Entrar na Nuvem",
                "Entre com o usuário criado no Firebase Authentication.");

        EditText email = field("E-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email);

        EditText password = field("Senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, marginTop(10));

        Button login = primaryButton("ENTRAR");
        login.setOnClickListener(v -> {
            if (email.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty()) {
                Toast.makeText(this, "Informe e-mail e senha.", Toast.LENGTH_LONG).show();
                return;
            }
            login.setEnabled(false);
            cloud.login(email.getText().toString(), password.getText().toString(), (ok, message) -> runOnUiThread(() -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (ok) {
                    cloud.pullAll(db, (syncOk, syncMessage) -> runOnUiThread(this::showHome));
                } else {
                    login.setEnabled(true);
                }
            }));
        });
        root.addView(login, marginTop(18));
        addBack(root);
    }

    private void showCaixa() {
        onHome = false;
        LinearLayout root = createScreen(
                "Lançamento do Caixa",
                "Informe quanto entrou em dinheiro e quanto entrou no cartão.");

        EditText date = field("Data (AAAA-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        date.setText(LocalDate.now().toString());
        root.addView(date);

        EditText cash = field("Entrada em dinheiro", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(cash, marginTop(12));

        EditText card = field("Entrada em cartão", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(card, marginTop(12));

        TextView total = bigValue("Total do dia: R$ 0,00");
        root.addView(total, marginTop(20));

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                double value = parseMoney(cash.getText().toString()) + parseMoney(card.getText().toString());
                total.setText("Total do dia: " + money(value));
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        cash.addTextChangedListener(watcher);
        card.addTextChangedListener(watcher);

        Button save = primaryButton("SALVAR MOVIMENTO DO DIA");
        save.setOnClickListener(v -> {
            try {
                String d = date.getText().toString().trim();
                LocalDate.parse(d);
                double cashValue = parseMoney(cash.getText().toString());
                double cardValue = parseMoney(card.getText().toString());
                db.upsertMovement(d, cashValue, cardValue);
                total.setText("Total do dia: " + money(cashValue + cardValue));
                cloud.uploadMovement(d, cashValue, cardValue, (ok, message) -> runOnUiThread(() ->
                        Toast.makeText(this, ok ? "Movimento salvo e sincronizado." : message, Toast.LENGTH_LONG).show()));
                if (!cloud.isLoggedIn()) {
                    Toast.makeText(this, "Movimento salvo neste aparelho.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Confira a data. Use AAAA-MM-DD.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(save, marginTop(18));

        Button today = secondaryButton("USAR DATA DE HOJE");
        today.setOnClickListener(v -> date.setText(LocalDate.now().toString()));
        root.addView(today, marginTop(10));
        addBack(root);
    }

    private void showAdmin() {
        if (cloud.isLoggedIn() && !cloud.isAdmin()) {
            Toast.makeText(this, "Este usuário não tem acesso de administrador.", Toast.LENGTH_LONG).show();
            showHome();
            return;
        }

        onHome = false;
        LinearLayout root = createScreen(
                "Administrador",
                "Acompanhe o movimento e faça o fechamento mensal.");

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        Button day = smallButton("DIA");
        Button week = smallButton("SEMANA");
        Button month = smallButton("MÊS");
        Button year = smallButton("ANO");
        filters.addView(day, weighted());
        filters.addView(week, weighted());
        filters.addView(month, weighted());
        filters.addView(year, weighted());
        root.addView(filters);

        TextView summary = bigValue("");
        root.addView(summary, marginTop(18));

        Runnable showDay = () -> {
            LocalDate now = LocalDate.now();
            renderSummary(summary, "Hoje - " + now, now, now);
        };
        day.setOnClickListener(v -> showDay.run());
        week.setOnClickListener(v -> {
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(DayOfWeek.MONDAY);
            renderSummary(summary, "Semana " + start + " a " + start.plusDays(6), start, start.plusDays(6));
        });
        month.setOnClickListener(v -> {
            YearMonth ym = YearMonth.now();
            renderSummary(summary, "Mês " + ym, ym.atDay(1), ym.atEndOfMonth());
        });
        year.setOnClickListener(v -> {
            int y = LocalDate.now().getYear();
            renderSummary(summary, "Ano " + y, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        });
        showDay.run();

        if (cloud.isLoggedIn()) {
            Button sync = secondaryButton("ATUALIZAR DADOS DA NUVEM");
            sync.setOnClickListener(v -> cloud.pullAll(db, (ok, message) -> runOnUiThread(() -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                showDay.run();
            })));
            root.addView(sync, marginTop(10));
            cloud.pullAll(db, (ok, message) -> runOnUiThread(showDay));
        }

        addSectionTitle(root, "Fechamento do mês");
        Button closing = primaryButton("LUCRO E DESPESAS DO MÊS");
        closing.setOnClickListener(v -> showMonthlyClosing());
        root.addView(closing, marginTop(10));

        addSectionTitle(root, "Relatórios");
        Button export = primaryButton("SALVAR PLANILHA EXCEL (.XLSX)");
        export.setOnClickListener(v -> startExport());
        root.addView(export, marginTop(10));

        TextView info = body("Ao tocar no botão, o Android abrirá a tela Salvar como. A planilha tem 3 abas: Movimento Diário, Fechamento Mensal e Despesas.");
        info.setPadding(0, dp(8), 0, 0);
        root.addView(info);
        addBack(root);
    }

    private void renderSummary(TextView view, String label, LocalDate start, LocalDate end) {
        DatabaseHelper.Summary s = db.getSummary(start.toString(), end.toString());
        view.setText(label + "\n\n" +
                "Dinheiro: " + money(s.cash) + "\n" +
                "Cartão: " + money(s.card) + "\n" +
                "Total: " + money(s.total));
    }

    private void showMonthlyClosing() {
        if (cloud.isLoggedIn() && !cloud.isAdmin()) {
            Toast.makeText(this, "Somente o administrador pode alterar o fechamento.", Toast.LENGTH_LONG).show();
            showHome();
            return;
        }

        onHome = false;
        LinearLayout root = createScreen(
                "Fechamento Mensal",
                "Informe o lucro do seu sistema e lance as despesas do mês.");

        EditText month = field("Mês de referência (AAAA-MM)", InputType.TYPE_CLASS_DATETIME);
        month.setText(YearMonth.now().toString());
        root.addView(month);

        EditText profit = field("Lucro informado pelo sistema", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(profit, marginTop(12));

        TextView expensesList = body("");
        TextView expensesTotal = bigValue("");
        TextView net = bigValue("");

        Button load = secondaryButton("CARREGAR ESTE MÊS");
        load.setOnClickListener(v -> refreshClosing(month, profit, expensesList, expensesTotal, net, true));
        root.addView(load, marginTop(10));

        addSectionTitle(root, "Adicionar despesa");
        EditText expenseDate = field("Data da despesa (AAAA-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        expenseDate.setText(LocalDate.now().toString());
        root.addView(expenseDate);

        EditText description = field("Descrição da despesa", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(description, marginTop(10));

        EditText amount = field("Valor da despesa", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(amount, marginTop(10));

        Button addExpense = primaryButton("ADICIONAR DESPESA");
        addExpense.setOnClickListener(v -> {
            try {
                String ref = month.getText().toString().trim();
                YearMonth.parse(ref);
                String d = expenseDate.getText().toString().trim();
                LocalDate.parse(d);
                String desc = description.getText().toString().trim();
                double value = parseMoney(amount.getText().toString());
                if (desc.isEmpty() || value <= 0) {
                    Toast.makeText(this, "Informe descrição e valor maior que zero.", Toast.LENGTH_LONG).show();
                    return;
                }
                String cloudId = db.addExpense(d, desc, value, ref);
                cloud.uploadExpense(cloudId, d, desc, value, ref, (ok, message) -> runOnUiThread(() -> {
                    if (!ok && cloud.isLoggedIn()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }));
                description.setText("");
                amount.setText("");
                refreshClosing(month, profit, expensesList, expensesTotal, net, false);
                Toast.makeText(this, "Despesa adicionada.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Confira o mês e a data.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(addExpense, marginTop(12));

        addSectionTitle(root, "Despesas lançadas");
        root.addView(expensesList);
        root.addView(expensesTotal, marginTop(12));
        root.addView(net, marginTop(8));

        profit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String ref = month.getText().toString().trim();
                double exp = ref.matches("\\d{4}-\\d{2}") ? db.getExpensesTotal(ref) : 0;
                net.setText("Resultado líquido: " + money(parseMoney(profit.getText().toString()) - exp));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button save = primaryButton("SALVAR FECHAMENTO DO MÊS");
        save.setOnClickListener(v -> {
            try {
                String ref = month.getText().toString().trim();
                YearMonth.parse(ref);
                double p = parseMoney(profit.getText().toString());
                db.upsertClosing(ref, p);
                double expenses = db.getExpensesTotal(ref);
                cloud.uploadClosing(ref, p, expenses, (ok, message) -> runOnUiThread(() -> {
                    if (!ok && cloud.isLoggedIn()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }));
                refreshClosing(month, profit, expensesList, expensesTotal, net, false);
                Toast.makeText(this, "Fechamento mensal salvo.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Confira o mês. Use AAAA-MM.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(save, marginTop(18));

        refreshClosing(month, profit, expensesList, expensesTotal, net, true);
        addBack(root);
    }

    private void refreshClosing(EditText month, EditText profit, TextView list,
                                TextView total, TextView net, boolean loadSavedProfit) {
        try {
            String ref = month.getText().toString().trim();
            YearMonth.parse(ref);
            if (loadSavedProfit) {
                double saved = db.getClosingProfit(ref);
                if (saved != 0) profit.setText(String.format(Locale.US, "%.2f", saved));
            }
            double exp = db.getExpensesTotal(ref);
            list.setText(db.getExpensesText(ref));
            total.setText("Total de despesas: " + money(exp));
            net.setText("Resultado líquido: " + money(parseMoney(profit.getText().toString()) - exp));
        } catch (Exception e) {
            list.setText("Mês inválido. Use AAAA-MM.");
            total.setText("Total de despesas: R$ 0,00");
            net.setText("Resultado líquido: R$ 0,00");
        }
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, "Caixa_da_Loja_" + LocalDate.now() + ".xlsx");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                XlsxExporter.export(db, getContentResolver(), uri);
                Toast.makeText(this, "Planilha Excel salva com sucesso.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível salvar a planilha Excel.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addBack(LinearLayout root) {
        Button back = secondaryButton("VOLTAR AO INÍCIO");
        back.setOnClickListener(v -> showHome());
        root.addView(back, marginTop(26));
    }

    @Override
    public void onBackPressed() {
        if (onHome) super.onBackPressed();
        else showHome();
    }

    private void addSectionTitle(LinearLayout root, String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(18);
        view.setTextColor(COLOR_DARK);
        view.setTypeface(null, 1);
        view.setPadding(0, dp(24), 0, dp(8));
        root.addView(view);
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.DKGRAY);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private TextView bigValue(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(20);
        view.setTextColor(COLOR_DARK);
        view.setTypeface(null, 1);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackgroundColor(Color.WHITE);
        return view;
    }

    private EditText field(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(17);
        edit.setTextColor(COLOR_DARK);
        edit.setHintTextColor(Color.GRAY);
        edit.setInputType(inputType);
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        edit.setBackgroundColor(Color.WHITE);
        edit.setSingleLine(true);
        return edit;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(COLOR_PRIMARY);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_DARK);
        button.setBackgroundColor(Color.LTGRAY);
        button.setMinHeight(dp(48));
        return button;
    }

    private Button smallButton(String text) {
        Button button = primaryButton(text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        return button;
    }

    private LinearLayout.LayoutParams marginTop(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(value);
        return p;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private double parseMoney(String value) {
        if (value == null) return 0;
        String clean = value.trim().replace("R$", "").replace(" ", "");
        if (clean.isEmpty()) return 0;
        try {
            if (clean.contains(",")) clean = clean.replace(".", "").replace(",", ".");
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    private String money(double value) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(value);
    }
}
