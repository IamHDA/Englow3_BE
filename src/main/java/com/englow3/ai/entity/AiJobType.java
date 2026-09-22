package com.englow3.ai.entity;

/**
 * What kind of work a job carries. The queue is shared infrastructure and dispatches on this: a handler registers for
 * one value and never sees the others, which is what lets a second kind of work be added without touching the first.
 */
public enum AiJobType {

    /** Score a recording against a reference sentence. Target is a speaking attempt. */
    SPEECH_ASSESSMENT,

    /** Answer a learner's question in a tutor conversation. Target is the pending assistant message. */
    TUTOR_REPLY
}
