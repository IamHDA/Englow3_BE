package com.englow3.exam.dto.result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.englow3.exam.entity.QuestionType;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.spreadsheet.SpreadsheetTable;

/**
 * What an admin's CSV/XLSX upload becomes - questions the file's rows could be turned into, and every row that could
 * not, with a reason. Neither list is ever persisted here: the frontend merges {@code questions} into the part it is
 * composing and saves everything together through the existing {@code PUT /{id}/content}. {@code exam} has exactly
 * one content write path ({@code AdminExamService.replaceContent}); a second one that persists rows straight from a
 * spreadsheet is exactly the write path {@code query/} being read-only exists to keep out (see
 * {@code docs/module-map.md}).
 * <p>
 * {@link SpreadsheetTable} only knows how to line file rows up under a header by name; the column contract itself -
 * five columns, no difficulty/skill/score because the composing screen already asks for those per part and guessing
 * them here would silently pick wrong ones - is decided entirely here, in {@link #of(SpreadsheetTable)}. A row the
 * contract cannot make sense of is not an exception to throw - it becomes one entry in {@link #errors()}, and the rest
 * of the file still comes back usable, the same way the design's AI-generated source needs review before anything is
 * kept.
 */
public record QuestionImportResult(List<ImportedQuestion> questions, List<RowError> errors) {

    /** A TOEIC paper's own row cap (7 parts) is nowhere near this; it exists to bound one response, not paper size. */
    private static final int MAX_ROWS = 500;

    /** Matches the design mock's own {@code const LETTERS = 'ABCDEFGH'} - eight options is already generous for TOEIC. */
    private static final String LETTERS = "ABCDEFGH";

    private static final String COL_CONTENT = "Nội dung";
    private static final String COL_TYPE = "Dạng câu";
    private static final String COL_OPTIONS = "Phương án";
    private static final String COL_CORRECT = "Đáp án đúng";
    private static final String COL_EXPLANATION = "Giải thích";

    public static QuestionImportResult of(SpreadsheetTable table) {
        Set<String> missing = table.missingColumns(Set.of(COL_CONTENT, COL_TYPE, COL_OPTIONS, COL_CORRECT));
        if (!missing.isEmpty()) {
            throw new BadRequestException("IMPORT_COLUMN_MISSING",
                    "Thiếu cột bắt buộc: %s".formatted(String.join(", ", missing)));
        }

        List<List<String>> body = table.dataRows();
        long dataRowCount = body.stream().filter(row -> !SpreadsheetTable.isBlankRow(row)).count();
        if (dataRowCount > MAX_ROWS) {
            throw new BadRequestException("IMPORT_TOO_MANY_ROWS",
                    "The file has %d rows, more than the %d allowed per import".formatted(dataRowCount, MAX_ROWS));
        }

        List<ImportedQuestion> questions = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int rowNumber = 1; // the header itself is row 1, so the first data row is row 2
        for (List<String> row : body) {
            rowNumber++;
            if (SpreadsheetTable.isBlankRow(row)) {
                continue;
            }
            try {
                questions.add(parseRow(rowNumber, row, table));
            } catch (RowParseException e) {
                errors.add(new RowError(rowNumber, e.code, e.getMessage()));
            }
        }
        return new QuestionImportResult(questions, errors);
    }

    private static ImportedQuestion parseRow(int rowNumber, List<String> row, SpreadsheetTable table) {
        String content = table.cell(row, COL_CONTENT);
        if (content.isBlank()) {
            throw new RowParseException("IMPORT_ROW_CONTENT_REQUIRED", "Nội dung câu hỏi không được để trống");
        }

        QuestionType questionType = parseQuestionType(table.cell(row, COL_TYPE));

        List<String> options = Arrays.stream(table.cell(row, COL_OPTIONS).split(";")).map(String::trim)
                .filter(value -> !value.isEmpty()).toList();
        if (options.size() < 2) {
            throw new RowParseException("IMPORT_ROW_OPTIONS_INSUFFICIENT",
                    "Cần ít nhất 2 phương án, ngăn cách bằng dấu ;");
        }
        if (options.size() > LETTERS.length()) {
            throw new RowParseException("IMPORT_ROW_OPTIONS_TOO_MANY",
                    "Không quá %d phương án cho một câu hỏi".formatted(LETTERS.length()));
        }

        Set<Integer> correctIndexes = parseCorrectAnswers(table.cell(row, COL_CORRECT), options.size());
        if (correctIndexes.isEmpty()) {
            throw new RowParseException("IMPORT_ROW_CORRECT_ANSWER_REQUIRED", "Cần chỉ ra ít nhất một đáp án đúng");
        }
        // Mirrors the same rule Exam.publish(...) enforces at the tree level (QuestionRepository
        // .findIncompleteQuestionOrderNos): a single-choice question cannot carry more than one correct option.
        if (questionType == QuestionType.SINGLE_CHOICE && correctIndexes.size() > 1) {
            throw new RowParseException("IMPORT_ROW_SINGLE_CHOICE_MULTIPLE_ANSWERS",
                    "Dạng câu một đáp án đúng nhưng cột đáp án đúng liệt kê nhiều hơn một chữ cái");
        }

        String explanation = blankToNull(table.cell(row, COL_EXPLANATION));

        List<ImportedOption> importedOptions = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            importedOptions.add(new ImportedOption(options.get(i), correctIndexes.contains(i)));
        }
        return new ImportedQuestion(rowNumber, questionType, content, explanation, importedOptions);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static QuestionType parseQuestionType(String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SINGLE", "SINGLE_CHOICE" -> QuestionType.SINGLE_CHOICE;
            case "MULTIPLE", "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            default -> throw new RowParseException("IMPORT_ROW_QUESTION_TYPE_INVALID",
                    "Dạng câu '%s' không hợp lệ, dùng single hoặc multiple".formatted(raw));
        };
    }

    private static Set<Integer> parseCorrectAnswers(String raw, int optionCount) {
        if (raw.isBlank()) {
            return Set.of();
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String letter = token.trim().toUpperCase(Locale.ROOT);
            int index = letter.length() == 1 ? LETTERS.indexOf(letter.charAt(0)) : -1;
            if (index < 0) {
                throw new RowParseException("IMPORT_ROW_CORRECT_ANSWER_INVALID",
                        "'%s' không phải một chữ cái phương án hợp lệ".formatted(token.trim()));
            }
            if (index >= optionCount) {
                throw new RowParseException("IMPORT_ROW_CORRECT_ANSWER_INVALID",
                        "Đáp án '%s' vượt quá số phương án (%d)".formatted(letter, optionCount));
            }
            indexes.add(index);
        }
        return indexes;
    }

    public record ImportedQuestion(int rowNumber, QuestionType questionType, String content, String explanation,
            List<ImportedOption> options) {
    }

    public record ImportedOption(String content, boolean correct) {
    }

    public record RowError(int rowNumber, String code, String message) {
    }

    /** Internal only - caught row-by-row in {@link #of}, never escapes this record. */
    private static final class RowParseException extends RuntimeException {
        private final String code;

        RowParseException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
