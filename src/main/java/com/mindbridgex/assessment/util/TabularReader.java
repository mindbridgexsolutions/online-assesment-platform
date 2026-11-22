package com.mindbridgex.assessment.util;

import com.mindbridgex.assessment.exception.AppException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the first sheet of an .xlsx file, the first table of a .docx file, or a CSV file into header-keyed rows (headers lower-cased, spaces -> _). */
public final class TabularReader {

    private TabularReader() {
    }

    public static List<Map<String, String>> read(MultipartFile file, int maxRows) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".xlsx")) {
                return readXlsx(in, maxRows);
            }
            if (name.endsWith(".docx")) {
                return readDocx(in, maxRows);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return readCsv(in, maxRows);
            }
            if (name.endsWith(".pdf")) {
                throw AppException.bad("A PDF cannot be imported as a table because its columns cannot be read reliably. "
                        + "Copy the table into a Word (.docx), Excel (.xlsx) or CSV file and upload that instead.");
            }
            throw AppException.bad("Unsupported file type. Upload a .csv, .xlsx or Word (.docx) file.");
        } catch (IOException e) {
            throw AppException.bad("The file could not be read: " + e.getMessage());
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw AppException.bad("The file is not a valid spreadsheet / CSV document.");
        }
    }

    private static List<Map<String, String>> readCsv(InputStream in, int maxRows) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).setIgnoreHeaderCase(true).build();
        InputStreamReader reader = new InputStreamReader(stripBom(in), StandardCharsets.UTF_8);
        try (CSVParser parser = format.parse(reader)) {
            for (CSVRecord rec : parser) {
                if (rows.size() >= maxRows) {
                    throw AppException.bad("Too many rows: at most " + maxRows + " rows per file.");
                }
                Map<String, String> row = new LinkedHashMap<>();
                parser.getHeaderMap().keySet().forEach(h -> {
                    if (h != null && rec.isSet(h)) {
                        row.put(normalise(h), rec.get(h));
                    }
                });
                if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static List<Map<String, String>> readXlsx(InputStream in, int maxRows) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return rows;
            }
            List<String> names = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                names.add(cell == null ? "" : normalise(formatter.formatCellValue(cell)));
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row xr = sheet.getRow(r);
                if (xr == null) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw AppException.bad("Too many rows: at most " + maxRows + " rows per file.");
                }
                Map<String, String> row = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < names.size(); c++) {
                    Cell cell = xr.getCell(c);
                    String v = cell == null ? "" : cellText(cell, formatter);
                    if (!v.isBlank()) {
                        any = true;
                    }
                    if (!names.get(c).isEmpty()) {
                        row.put(names.get(c), v);
                    }
                }
                if (any) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Reads the first table of a Word document: first row = column headings, one record per following row. */
    private static List<Map<String, String>> readDocx(InputStream in, int maxRows) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {
            if (doc.getTables().isEmpty()) {
                throw AppException.bad("The Word document contains no table. Put the data in a table whose first row holds the column headings.");
            }
            org.apache.poi.xwpf.usermodel.XWPFTable table = doc.getTables().get(0);
            List<org.apache.poi.xwpf.usermodel.XWPFTableRow> trs = table.getRows();
            if (trs.isEmpty()) {
                return rows;
            }
            List<String> names = new ArrayList<>();
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell c : trs.get(0).getTableCells()) {
                names.add(normalise(c.getText()));
            }
            for (int r = 1; r < trs.size(); r++) {
                if (rows.size() >= maxRows) {
                    throw AppException.bad("Too many rows: at most " + maxRows + " rows per file.");
                }
                List<org.apache.poi.xwpf.usermodel.XWPFTableCell> cells = trs.get(r).getTableCells();
                Map<String, String> row = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < names.size() && c < cells.size(); c++) {
                    String v = cells.get(c).getText().replace('\u00a0', ' ').trim();
                    if (!v.isBlank()) {
                        any = true;
                    }
                    if (!names.get(c).isEmpty()) {
                        row.put(names.get(c), v);
                    }
                }
                if (any) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() == CellType.NUMERIC && !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            double d = cell.getNumericCellValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static String normalise(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s]+", "_");
    }

    private static InputStream stripBom(InputStream in) throws IOException {
        java.io.PushbackInputStream pb = new java.io.PushbackInputStream(in, 3);
        byte[] bom = new byte[3];
        int n = pb.read(bom, 0, 3);
        if (n == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
            return pb;
        }
        if (n > 0) {
            pb.unread(bom, 0, n);
        }
        return pb;
    }
}
