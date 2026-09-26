package com.englow3.tutor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds what is sent to the model. Pure, like the quiz grader and the dictation scorer, so what the tutor is asked can
 * be checked without a provider - which matters more here than elsewhere, because the request is the only part of this
 * feature the project controls.
 */
public final class TutorPrompt {

    /**
     * Bumped when the instructions or the shape of the request change. Stored on every message, so an answer that reads
     * oddly can be traced to the rules it was produced under rather than guessed at.
     */
    public static final String VERSION = "tutor-chat-v1";

    /**
     * How many earlier turns travel with a question.
     * <p>
     * Bounded because the whole transcript is re-sent every time: without a limit, the hundredth question in a thread
     * costs a hundred times the first and eventually exceeds what the model accepts. Ten turns is enough to follow a
     * line of questioning and short of paying to re-read a conversation from last week.
     */
    static final int MAX_HISTORY_TURNS = 10;

    /**
     * A hard ceiling on the transcript, independent of the turn count. One learner pasting an essay could otherwise
     * push a ten-turn history past the adapter's limit and have the request refused rather than answered.
     */
    static final int MAX_HISTORY_CHARACTERS = 12_000;

    /**
     * What the tutor is for, and what it must not do.
     * <p>
     * The instruction against inventing an answer is the load-bearing one. A model that guesses a grammar rule sounds
     * exactly like one that knows it, and a learner has no way to tell the difference - which is why the reporting path
     * exists alongside this.
     */
    public static final String SYSTEM_PROMPT = """
            You are an English tutor for Vietnamese learners on a study platform.

            Your job:
            - Answer questions about English vocabulary, grammar, pronunciation and usage.
            - Explain why an answer is right or wrong, not just which one it is.
            - Correct a learner's sentence or paragraph when they ask, showing what changed and why.
            - Practise conversation in a role or situation when the learner asks for one.

            How to answer:
            - Reply in the language the learner used. If they write Vietnamese, answer in Vietnamese \
            but keep English examples in English.
            - Be concise. A learner reading on a phone will not finish six paragraphs.
            - Give a short example wherever one makes the point faster than an explanation.
            - If you are not certain, say so plainly. Do not invent a rule, an etymology or a \
            citation - a confident wrong answer is worse here than "I am not sure", because the \
            learner has no way to tell them apart.
            - Stay on English learning. If asked about something else, say so briefly and offer to \
            help with English instead.""";

    private TutorPrompt() {
    }

    /** One earlier turn, reduced to what the model needs: who spoke and what they said. */
    public record Turn(boolean fromLearner, String content) {
    }

    /**
     * The transcript plus the new question.
     * <p>
     * History is flattened into one prompt rather than sent as structured turns because the adapter takes a system
     * prompt and a user prompt, and keeping this module's request in that shape means it works against any provider the
     * adapter is pointed at.
     */
    public static String userPrompt(List<Turn> history, String question) {
        List<Turn> recent = trim(history);
        if (recent.isEmpty()) {
            return question.strip();
        }

        StringBuilder prompt = new StringBuilder("Earlier in this conversation:\n\n");
        for (Turn turn : recent) {
            prompt.append(turn.fromLearner() ? "Learner: " : "Tutor: ").append(turn.content().strip()).append("\n\n");
        }

        return prompt.append("The learner now asks:\n\n").append(question.strip()).toString();
    }

    /**
     * The last few turns, newest kept.
     * <p>
     * Trimmed from the front: the turn immediately before the question is the one it most likely refers to, and
     * dropping that to keep the opening of a long thread would lose the thing that makes the question make sense.
     */
    static List<Turn> trim(List<Turn> history) {
        List<Turn> kept = new ArrayList<>();
        int characters = 0;

        for (int i = history.size() - 1; i >= 0 && kept.size() < MAX_HISTORY_TURNS; i--) {
            Turn turn = history.get(i);
            if (turn.content() == null || turn.content().isBlank()) {
                // A turn that failed has nothing to contribute, and sending "Tutor: " with nothing after it
                // would teach the model that answering with silence is one of its options.
                continue;
            }
            characters += turn.content().length();
            if (characters > MAX_HISTORY_CHARACTERS && !kept.isEmpty()) {
                break;
            }
            kept.add(turn);
        }

        return kept.reversed();
    }
}
