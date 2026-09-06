package com.englow3.shared.spreadsheet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.shared.error.BadRequestException;

/**
 * An uploaded CSV or XLSX file as a {@link SpreadsheetTable} - it is told nothing about what any column means and
 * decides nothing about whether the content is usable. That is the same line
 * {@code shared/storage/ObjectStorageClient} draws (it moves bytes, it does not know what a key means), and it is why
 * this belongs in {@code shared} at all: strip the column vocabulary out of a spreadsheet import and what is left is
 * file format handling with no business reason to change. Whoever imports something decides what the columns are - see
 * {@code exam.dto.result.QuestionImportResult}.
 */
@Component
public class SpreadsheetReader {

    public SpreadsheetTable read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("SPREADSHEET_REQUIRED", "No file was uploaded");
        }
        String extension = extensionOf(file.getOriginalFilename());
        List<List<String>> rows;
        try {
            rows = switch (extension) {
                case "csv" -> readCsv(file);
                case "xlsx" -> readXlsx(file);
                default -> throw new BadRequestException("SPREADSHEET_TYPE_NOT_SUPPORTED",
                        "Only CSV and XLSX files are supported, got '%s'".formatted(extension));
            };
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // Anything the format libraries raise on a malformed file - a renamed .txt, a truncated workbook - is a bad
            // upload, not a server fault, so it must not reach the catch-all handler as a 500.
            throw new BadRequestException("SPREADSHEET_UNREADABLE",
                    "The uploaded file could not be read as %s".formatted(extension.toUpperCase(Locale.ROOT)));
        }
        return SpreadsheetTable.of(rows);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the whole file into memory rather than streaming record by record: the multipart limit already bounds it to
     * 12 MB ({@code application.yml}), and the caller has to cap the row count after parsing anyway, so a more careful
     * reader would buy nothing.
     */
    private static List<List<String>> readCsv(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        int offset = hasUtf8Bom(bytes) ? 3 : 0;
        List<List<String>> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes, offset, bytes.length - offset),
                StandardCharsets.UTF_8); CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {
            for (CSVRecord record : parser) {
                List<String> values = new ArrayList<>(record.size());
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);
                    values.add(value == null ? "" : value.trim());
                }
                rows.add(values);
            }
        }
        return rows;
    }

    /** Excel writes a UTF-8 BOM when saving as CSV; left in place it would corrupt the first header name. */
    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }

    /**
     * {@code row.getCell(i, CREATE_NULL_AS_BLANK)} by index up to {@code getLastCellNum()}, not the default cell
     * iterator: the iterator silently skips a cell POI considers never written, which would shift every column after a
     * genuinely blank one into the wrong position.
     */
    private static List<List<String>> readXlsx(MultipartFile file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int lastCellNum = row.getLastCellNum();
                List<String> values = new ArrayList<>(Math.max(lastCellNum, 0));
                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    values.add(formatter.formatCellValue(cell).trim());
                }
                rows.add(values);
            }
        }
        return rows;
    }
}
