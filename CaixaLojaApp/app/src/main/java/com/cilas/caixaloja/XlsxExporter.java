package com.cilas.caixaloja;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter() {}

    public static void export(DatabaseHelper helper, ContentResolver resolver, Uri uri) throws Exception {
        OutputStream raw = resolver.openOutputStream(uri, "w");
        if (raw == null) throw new IllegalStateException("Nao foi possivel abrir o arquivo");
        try (OutputStream out = raw; ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", movementSheet(helper));
            put(zip, "xl/worksheets/sheet2.xml", closingSheet(helper));
            put(zip, "xl/worksheets/sheet3.xml", expensesSheet(helper));
        }
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets>" +
                "<sheet name=\"Movimento Diario\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"Fechamento Mensal\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "<sheet name=\"Despesas\" sheetId=\"3\" r:id=\"rId3\"/>" +
                "</sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>" +
                "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"3\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "</cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
                "</styleSheet>";
    }

    private static String movementSheet(DatabaseHelper helper) {
        StringBuilder xml = sheetStart();
        rowStart(xml, 1);
        stringCell(xml, "A1", "Data", true);
        stringCell(xml, "B1", "Dinheiro", true);
        stringCell(xml, "C1", "Cartao", true);
        stringCell(xml, "D1", "Total", true);
        rowEnd(xml);

        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT movement_date, cash, card, total FROM movements ORDER BY movement_date", null);
        int row = 2;
        try {
            while (c.moveToNext()) {
                rowStart(xml, row);
                stringCell(xml, "A" + row, c.getString(0), false);
                numberCell(xml, "B" + row, c.getDouble(1));
                numberCell(xml, "C" + row, c.getDouble(2));
                numberCell(xml, "D" + row, c.getDouble(3));
                rowEnd(xml);
                row++;
            }
        } finally { c.close(); }
        return sheetEnd(xml);
    }

    private static String closingSheet(DatabaseHelper helper) {
        StringBuilder xml = sheetStart();
        rowStart(xml, 1);
        stringCell(xml, "A1", "Mes", true);
        stringCell(xml, "B1", "Lucro informado", true);
        stringCell(xml, "C1", "Despesas", true);
        stringCell(xml, "D1", "Lucro liquido", true);
        rowEnd(xml);

        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT month_ref, profit, expenses_total, net_profit FROM monthly_closings ORDER BY month_ref", null);
        int row = 2;
        try {
            while (c.moveToNext()) {
                rowStart(xml, row);
                stringCell(xml, "A" + row, c.getString(0), false);
                numberCell(xml, "B" + row, c.getDouble(1));
                numberCell(xml, "C" + row, c.getDouble(2));
                numberCell(xml, "D" + row, c.getDouble(3));
                rowEnd(xml);
                row++;
            }
        } finally { c.close(); }
        return sheetEnd(xml);
    }

    private static String expensesSheet(DatabaseHelper helper) {
        StringBuilder xml = sheetStart();
        rowStart(xml, 1);
        stringCell(xml, "A1", "Data", true);
        stringCell(xml, "B1", "Descricao", true);
        stringCell(xml, "C1", "Valor", true);
        stringCell(xml, "D1", "Mes de referencia", true);
        rowEnd(xml);

        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT expense_date, description, amount, month_ref FROM expenses ORDER BY expense_date, id", null);
        int row = 2;
        try {
            while (c.moveToNext()) {
                rowStart(xml, row);
                stringCell(xml, "A" + row, c.getString(0), false);
                stringCell(xml, "B" + row, c.getString(1), false);
                numberCell(xml, "C" + row, c.getDouble(2));
                stringCell(xml, "D" + row, c.getString(3), false);
                rowEnd(xml);
                row++;
            }
        } finally { c.close(); }
        return sheetEnd(xml);
    }

    private static StringBuilder sheetStart() {
        StringBuilder b = new StringBuilder(4096);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<cols><col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"4\" width=\"22\" customWidth=\"1\"/></cols>")
                .append("<sheetData>");
        return b;
    }

    private static String sheetEnd(StringBuilder b) {
        return b.append("</sheetData></worksheet>").toString();
    }

    private static void rowStart(StringBuilder b, int row) {
        b.append("<row r=\"").append(row).append("\">");
    }

    private static void rowEnd(StringBuilder b) {
        b.append("</row>");
    }

    private static void stringCell(StringBuilder b, String ref, String value, boolean header) {
        b.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
        if (header) b.append(" s=\"1\"");
        b.append("><is><t xml:space=\"preserve\">")
                .append(escape(value))
                .append("</t></is></c>");
    }

    private static void numberCell(StringBuilder b, String ref, double value) {
        b.append("<c r=\"").append(ref).append("\" s=\"2\"><v>")
                .append(String.format(Locale.US, "%.2f", value))
                .append("</v></c>");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
