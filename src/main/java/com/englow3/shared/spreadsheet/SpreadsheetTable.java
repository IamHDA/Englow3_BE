package com.englow3.shared.spreadsheet;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.englow3.shared.error.BadRequestException;

/**
 * Rows read from an uploaded CSV/XLSX file (see {@link SpreadsheetReader}), with the header matched by normalized
 * name - diacritics stripped, case folded, whitespace collapsed - rather than by position, so reordering columns in a
 * spreadsheet tool never breaks an import. This is the one place that decides when two header strings count as the
 * same column; it carries no opinion about which columns matter or what a row means - that is entirely the caller's
 * business vocabulary, the same line {@code shared/storage/ObjectStorageClient} draws for object keys.
 */
public final class SpreadsheetTable {

    private final List<List<String>> dataRows;
    private final Map<String, Integer> columnIndex;

    private SpreadsheetTable(List<List<String>> dataRows, Map<String, Integer> columnIndex) {
        this.dataRows = dataRows;
        this.columnIndex = columnIndex;
    }

    public static SpreadsheetTable of(List<List<String>> rows) {
        if (rows.isEmpty()) {
            throw new BadRequestException("SPREADSHEET_HEADER_REQUIRED", "The uploaded file has no header row");
        }
        return new SpreadsheetTable(rows.subList(1, rows.size()), buildColumnIndex(rows.get(0)));
    }

    /**
     * Every row after the header, in file order and never filtered - a caller reporting per-row errors needs the row
     * numbers the file actually has, blank rows included.
     */
    public List<List<String>> dataRows() {
        return dataRows;
    }

    /** Which of {@code requiredColumns} (any spelling that normalizes the same as a header cell) this file lacks. */
    public Set<String> missingColumns(Set<String> requiredColumns) {
        return requiredColumns.stream().filter(column -> !columnIndex.containsKey(normalize(column)))
                .collect(Collectors.toSet());
    }

    /**
     * The trimmed value under {@code columnName} for one row of {@link #dataRows()}, or {@code ""} if the column is
     * absent or the row is shorter than the header - a ragged row is the caller's decision to reject, not this class's.
     */
    public String cell(List<String> row, String columnName) {
        Integer index = columnIndex.get(normalize(columnName));
        if (index == null || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    public static boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    private static Map<String, Integer> buildColumnIndex(List<String> headerRow) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String key = normalize(headerRow.get(i));
            if (!key.isEmpty()) {
                index.putIfAbsent(key, i);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    /**
     * Strips combining accents but leaves {@code đ}/{@code Đ} alone - {@code Normalizer} treats it as its own letter,
     * not an accented "d", so a Vietnamese header would otherwise never match itself.
     */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }
}
