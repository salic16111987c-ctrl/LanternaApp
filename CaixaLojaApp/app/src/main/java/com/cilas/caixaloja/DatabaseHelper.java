package com.cilas.caixaloja;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "caixa_loja.db";
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE movements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "movement_date TEXT UNIQUE NOT NULL," +
                "cash REAL NOT NULL DEFAULT 0," +
                "card REAL NOT NULL DEFAULT 0," +
                "total REAL NOT NULL DEFAULT 0," +
                "updated_at TEXT NOT NULL)");

        db.execSQL("CREATE TABLE expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "expense_date TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "amount REAL NOT NULL DEFAULT 0," +
                "month_ref TEXT NOT NULL," +
                "created_at TEXT NOT NULL)");

        db.execSQL("CREATE TABLE monthly_closings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "month_ref TEXT UNIQUE NOT NULL," +
                "profit REAL NOT NULL DEFAULT 0," +
                "expenses_total REAL NOT NULL DEFAULT 0," +
                "net_profit REAL NOT NULL DEFAULT 0," +
                "updated_at TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS monthly_closings");
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS movements");
        onCreate(db);
    }

    public void upsertMovement(String date, double cash, double card) {
        ContentValues values = new ContentValues();
        values.put("movement_date", date);
        values.put("cash", cash);
        values.put("card", card);
        values.put("total", cash + card);
        values.put("updated_at", LocalDateTime.now().toString());
        getWritableDatabase().insertWithOnConflict(
                "movements", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Summary getSummary(String startDate, String endDate) {
        double cash = 0;
        double card = 0;
        double total = 0;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(cash),0), COALESCE(SUM(card),0), COALESCE(SUM(total),0) " +
                        "FROM movements WHERE movement_date BETWEEN ? AND ?",
                new String[]{startDate, endDate});
        if (cursor.moveToFirst()) {
            cash = cursor.getDouble(0);
            card = cursor.getDouble(1);
            total = cursor.getDouble(2);
        }
        cursor.close();
        return new Summary(cash, card, total);
    }

    public void addExpense(String date, String description, double amount, String monthRef) {
        ContentValues values = new ContentValues();
        values.put("expense_date", date);
        values.put("description", description);
        values.put("amount", amount);
        values.put("month_ref", monthRef);
        values.put("created_at", LocalDateTime.now().toString());
        getWritableDatabase().insert("expenses", null, values);
    }

    public double getExpensesTotal(String monthRef) {
        double total = 0;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE month_ref = ?",
                new String[]{monthRef});
        if (cursor.moveToFirst()) total = cursor.getDouble(0);
        cursor.close();
        return total;
    }

    public String getExpensesText(String monthRef) {
        StringBuilder text = new StringBuilder();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT expense_date, description, amount FROM expenses WHERE month_ref = ? ORDER BY expense_date, id",
                new String[]{monthRef});
        while (cursor.moveToNext()) {
            text.append(cursor.getString(0))
                    .append("  •  ")
                    .append(cursor.getString(1))
                    .append("  •  R$ ")
                    .append(formatNumber(cursor.getDouble(2)))
                    .append("\n");
        }
        cursor.close();
        if (text.length() == 0) return "Nenhuma despesa lançada neste mês.";
        return text.toString().trim();
    }

    public void upsertClosing(String monthRef, double profit) {
        double expenses = getExpensesTotal(monthRef);
        ContentValues values = new ContentValues();
        values.put("month_ref", monthRef);
        values.put("profit", profit);
        values.put("expenses_total", expenses);
        values.put("net_profit", profit - expenses);
        values.put("updated_at", LocalDateTime.now().toString());
        getWritableDatabase().insertWithOnConflict(
                "monthly_closings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public double getClosingProfit(String monthRef) {
        double value = 0;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT profit FROM monthly_closings WHERE month_ref = ?",
                new String[]{monthRef});
        if (cursor.moveToFirst()) value = cursor.getDouble(0);
        cursor.close();
        return value;
    }

    public void exportCsv(ContentResolver resolver, Uri uri) throws Exception {
        try (OutputStream out = resolver.openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("MOVIMENTO DIARIO\n");
            writer.write("Data;Dinheiro;Cartao;Total\n");

            Cursor movements = getReadableDatabase().rawQuery(
                    "SELECT movement_date, cash, card, total FROM movements ORDER BY movement_date", null);
            while (movements.moveToNext()) {
                writer.write(csv(movements.getString(0)) + ";" +
                        formatNumber(movements.getDouble(1)) + ";" +
                        formatNumber(movements.getDouble(2)) + ";" +
                        formatNumber(movements.getDouble(3)) + "\n");
            }
            movements.close();

            writer.write("\nFECHAMENTO MENSAL\n");
            writer.write("Mes;Lucro informado;Despesas;Lucro liquido\n");
            Cursor closings = getReadableDatabase().rawQuery(
                    "SELECT month_ref, profit, expenses_total, net_profit FROM monthly_closings ORDER BY month_ref", null);
            while (closings.moveToNext()) {
                writer.write(csv(closings.getString(0)) + ";" +
                        formatNumber(closings.getDouble(1)) + ";" +
                        formatNumber(closings.getDouble(2)) + ";" +
                        formatNumber(closings.getDouble(3)) + "\n");
            }
            closings.close();

            writer.write("\nDESPESAS\n");
            writer.write("Data;Descricao;Valor;Mes de referencia\n");
            Cursor expenses = getReadableDatabase().rawQuery(
                    "SELECT expense_date, description, amount, month_ref FROM expenses ORDER BY expense_date, id", null);
            while (expenses.moveToNext()) {
                writer.write(csv(expenses.getString(0)) + ";" +
                        csv(expenses.getString(1)) + ";" +
                        formatNumber(expenses.getDouble(2)) + ";" +
                        csv(expenses.getString(3)) + "\n");
            }
            expenses.close();
            writer.flush();
        }
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(";") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String formatNumber(double value) {
        return String.format(new Locale("pt", "BR"), "%.2f", value);
    }

    public static class Summary {
        public final double cash;
        public final double card;
        public final double total;

        public Summary(double cash, double card, double total) {
            this.cash = cash;
            this.card = card;
            this.total = total;
        }
    }
}
