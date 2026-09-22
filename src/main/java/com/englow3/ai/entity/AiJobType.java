package com.englow3.ai.entity;

/**
 * What kind of work a job carries. One value for now, and the enum exists anyway because the column does: the queue is
 * shared infrastructure, and a worker that assumed every row was speech would have to be rewritten the first time it
 * was not.
 */
public enum AiJobType {

    /** Score a recording against a reference sentence. Target is a speaking attempt. */
    SPEECH_ASSESSMENT
}
