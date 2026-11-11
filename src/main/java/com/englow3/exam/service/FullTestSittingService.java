package com.englow3.exam.service;

import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.FullTestSittingResult;

/** A learner sitting an IELTS full test. */
public interface FullTestSittingService {

    /** Starts a sitting, or returns the one still open for this full test. IELTS learners only. */
    FullTestSittingResult start(UUID fullTestId);

    /** The caller's sitting, brought up to date with every part graded so far. */
    FullTestSittingResult view(UUID sittingId);

    List<FullTestSittingResult> mine();

    List<FullTestSittingResult> mine(int page, int size);

    /** Brings one sitting up to date. For the listener and the sweep; checks no caller. */
    void refresh(UUID sittingId);

    /** Brings the learner's unsettled sittings up to date, after one of their parts was scored or graded. */
    void refreshFor(UUID userId);
}
