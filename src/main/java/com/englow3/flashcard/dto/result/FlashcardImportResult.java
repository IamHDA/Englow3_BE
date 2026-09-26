package com.englow3.flashcard.dto.result;

import java.util.List;

import com.englow3.flashcard.helper.FlashcardImport;

/**
 * What a file would do, or what it did.
 *
 * @param committed
 *            false on a dry run. The one field that tells an author whether they are looking at a proposal or at
 *            something that already happened - without it the two responses are identical and the screen has to
 *            remember which button was pressed.
 * @param rejections
 *            every row that could not be read, with its position in the file, so an author can go and fix them rather
 *            than being told a number
 */
public record FlashcardImportResult(boolean committed, int acceptedCount, int rejectedCount,
        List<FlashcardImport.Rejection> rejections) {

    public static FlashcardImportResult of(FlashcardImport.Report report, boolean committed) {
        return new FlashcardImportResult(committed, report.acceptedCount(), report.rejectedCount(),
                report.rejections());
    }
}
