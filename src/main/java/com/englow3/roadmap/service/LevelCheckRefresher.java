package com.englow3.roadmap.service;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LevelCheck;

/**
 * Brings a level check up to date with the grades that exist: attaches the learner's sitting and spoken answer, records
 * each part's band, and finishes the check once it can - recording the level and settling the milestone it closes. Used
 * when a grade is announced, by the periodic sweep, and when the learner looks, so the result does not wait for anyone
 * to open a page. Joins the caller's transaction; the check must be locked.
 */
public interface LevelCheckRefresher {

    /**
     * @param speakingFailed
     *            the spoken answer could not be graded
     * @param provisionalLevel
     *            the band from the written parts alone while the spoken part is outstanding
     */
    record Outcome(boolean speakingFailed, CefrLevel provisionalLevel) {
    }

    Outcome refresh(LevelCheck check);
}
