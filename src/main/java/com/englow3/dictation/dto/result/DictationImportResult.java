package com.englow3.dictation.dto.result;

import java.util.List;

import com.englow3.dictation.helper.DictationImport;

/**
 * What a shadowing batch would do, or did.
 *
 * @param sentenceCount
 *            lines across every lesson. Reported alongside the lesson count because thirty clips of four lines and
 *            thirty of forty are the same number of lessons and very different amounts of work.
 */
public record DictationImportResult(boolean committed, int acceptedCount, int sentenceCount, int rejectedCount,
        List<DictationImport.Rejection> rejections) {

    public static DictationImportResult of(DictationImport.Report report, boolean committed) {
        return new DictationImportResult(committed, report.acceptedCount(), report.sentenceCount(),
                report.rejectedCount(), report.rejections());
    }
}
