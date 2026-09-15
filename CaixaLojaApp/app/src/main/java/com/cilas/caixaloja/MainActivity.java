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
import android.view.View;
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
    private boolean onHome = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
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
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(27);
        heading.setTextColor(COLOR_DARK);
        heading.setTypeface(null, 1);
        root.addView(heading);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(15);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);
        return root;
    }

    private void showHome() {
        onHome = true;
        LinearLayout root = createScreen(
                "Caixa da Loja",
                "Escolha como este aparelho será usado.");

        addSectionTitle(root, "Telefone do caixa");
        TextView caixaInfo = body("Lançamento simples do movimento do dia: dinheiro + cartão.");
        root.addView(caixaInfo);
        Button caixa = primaryButton("ABRIR CAIXA");
        caixa.setOnClickListener(v -> showCaixa());
        root.addView(caixa, marginTop(12));

        addSectionTitle(root, "Telefone do administrador");
        TextView adminInfo = body("Consulta por dia, semana, mês e ano, fechamento mensal, despesas e exportação para Excel.");
        root.addView(adminInfo);
        Button admin = primaryButton("ABRIR ADMINISTRADOR");
        admin.setOnClickListener(v -> showAdmin());
        root.addView(admin, marginTop(12));

        TextView note = body("Versão 1.0: os dados ficam salvos neste aparelho. A sincronização entre os dois telefones será ligada ao banco na nuvem na próxima etapa.");
        note.setPadding(0, dp(28), 0, 0);
        root.addView(note);
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
                LocalDate.parse(date.getText().toString().trim());
                double cashValue = parseMoney(cash.getText().toString());
                double cardValue = parseMoney(card.getText().toString());
                db.upsertMovement(date.getText().toString().trim(), cashValue, cardValue);
                Toast.makeText(this, "Movimento salvo com sucesso.", Toast.LENGTH_LONG).show();
                total.setText("Total do dia: " + money(cashValue + cardValue));
            } catch (Exception e) {
                Toast.makeText(this, "Confira a data. Use o formato AAAA-MM-DD.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(save, marginTop(18));

        Button today = secondaryButton("USAR DATA DE HOJE");
        today.setOnClickListener(v -> date.setText(LocalDate.now().toString()));
        root.addView(today, marginTop(10));

        addBack(root);
    }

    private void showAdmin() {
        onHome = false;
        LinearLayout root = createScreen(
                "Administrador",
                "Acompanhe o movimento e faça o fechamento mensal.");

        TextView summary = bigValue("");

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filters);

        Button day = smallButton("DIA");
        Button week = smallButton("SEMANA");
        Button month = smallButton("MÊS");
        Button year = smallButton("ANO");
        filters.addView(day, weighted());
        filters.addView(week, weighted());
        filters.addView(month, weighted());
        filters.addView(year, weighted());

        root.addView(summary, marginTop(18));

        Runnable showDay = () -> {
            LocalDate now = LocalDate.now();
            renderSummary(summary, "Hoje - " + now, now, now);
        };
        day.setOnClickListener(v -> showDay.run());
        week.setOnClickListener(v -> {
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);
            renderSummary(summary, "Semana " + start + " a " + end, start, end);
        });
        month.setOnClickListener(v -> {
            YearMonth ym = YearMonth.now();
            renderSummary(summary, "Mês " + ym, ym.atDay(1), ym.atEndOfMonth());
        });
        year.setOnClickListener(v -> {
            LocalDate now = LocalDate.now();
            renderSummary(summary, "Ano " + now.getYear(),
                    LocalDate.of(now.getYear(), 1, 1), LocalDate.of(now.getYear(), 12, 31));
        });
        showDay.run();

        addSectionTitle(root, "Fechamento do mês");
        Button closing = primaryButton("LUCRO E DESPESAS DO MÊS");
        closing.setOnClickListener(v -> showMonthlyClosing());
        root.addView(closing, marginTop(10));

        addSectionTitle(root, "Relatórios");
        Button export = primaryButton("EXPORTAR PARA EXCEL");
        export.setOnClickListener(v -> startExport());
        root.addView(export, marginTop(10));

        TextView exportInfo = body("O arquivo exportado é CSV compatível com Excel e contém movimento diário, fechamentos mensais e despesas.");
        exportInfo.setPadding(0, dp(8), 0, 0);
        root.addView(exportInfo);

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
        onHome = false;
        LinearLayout root = createScreen(
                "Fechamento Mensal",
                "Informe o lucro apurado no seu sistema e lance as despesas do mês.");

        EditText month = field("Mês de referência (AAAA-MM)", InputType.TYPE_CLASS_DATETIME);
        month.setText(YearMonth.now().toString());
        root.addView(month);

        EditText profit = field("Lucro informado pelo sistema", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(profit, marginTop(12));

        TextView expensesList = body("");
        TextView expensesTotal = bigValue("");
        TextView net = bigValue("");

        Button loadMonth = secondaryButton("CARREGAR ESTE MÊS");
        loadMonth.setOnClickListener(v -> refreshClosing(month, profit, expensesList, expensesTotal, net, true));
        root.addView(loadMonth, marginTop(10));

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
                    Toast.makeText(this, "Informe a descrição e um valor maior que zero.", Toast.LENGTH_LONG).show();
                    return;
                }
                db.addExpense(d, desc, value, ref);
                description.setText("");
                amount.setText("");
                refreshClosing(month, profit, expensesList, expensesTotal, net, false);
                Toast.makeText(this, "Despesa adicionada.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Confira o mês e a data informados.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(addExpense, marginTop(12));

        addSectionTitle(root, "Despesas lançadas");
        root.addView(expensesList);
        root.addView(expensesTotal, marginTop(12));
        root.addView(net, marginTop(8));

        TextWatcher profitWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String ref = month.getText().toString().trim();
                double exp = ref.matches("\\d{4}-\\d{2}") ? db.getExpensesTotal(ref) : 0;
                net.setText("Resultado líquido: " + money(parseMoney(profit.getText().toString()) - exp));
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        profit.addTextChangedListener(profitWatcher);

        Button saveClosing = primaryButton("SALVAR FECHAMENTO DO MÊS");
        saveClosing.setOnClickListener(v -> {
            try {
                String ref = month.getText().toString().trim();
                YearMonth.parse(ref);
                double p = parseMoney(profit.getText().toString());
                db.upsertClosing(ref, p);
                refreshClosing(month, profit, expensesList, expensesTotal, net, false);
                Toast.makeText(this, "Fechamento mensal salvo.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Confira o mês informado. Use AAAA-MM.", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(saveClosing, marginTop(18));

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
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "caixa_loja_" + LocalDate.now() + ".csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                db.exportCsv(getContentResolver(), uri);
                Toast.makeText(this, "Relatório exportado. Você pode abrir no Excel.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível exportar o relatório.", Toast.LENGTH_LONG).show();
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
        TextView text = new TextView(this);
        text.setText(title);
        text.setTextSize(18);
        text.setTextColor(COLOR_DARK);
        text.setTypeface(null, 1);
        text.setPadding(0, dp(24), 0, dp(8));
        root.addView(text);
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

    private EditText field(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(17);
        edit.setInputType(type);
        edit.setSingleLine(true);
        edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        edit.setBackgroundColor(Color.WHITE);
        return edit;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackgroundColor(COLOR_PRIMARY);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_DARK);
        button.setTextSize(14);
        button.setBackgroundColor(Color.LTGRAY);
        button.setMinHeight(dp(48));
        return button;
    }

    private Button smallButton(String text) {
        Button button = primaryButton(text);
        button.setTextSize(11);
        button.setMinHeight(dp(46));
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams marginTop(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        return params;
    }

    private double parseMoney(String raw) {
        if (raw == null) return 0;
        String value = raw.trim().replace("R$", "").replace(" ", "");
        if (value.isEmpty()) return 0;
        try {
            if (value.contains(",") && value.contains(".")) {
                value = value.replace(".", "").replace(",", ".");
            } else if (value.contains(",")) {
                value = value.replace(",", ".");
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private String money(double value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return nf.format(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
