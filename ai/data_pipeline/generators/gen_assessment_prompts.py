#!/usr/bin/env python3
"""Create versioned Speaking/Writing assessment prompts and 10 QA fixtures."""

from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    AssessmentPrompt, AssessmentPromptBatch, AssessmentResult, BatchMetadata,
    ModuleType,
)

OUT = ROOT / "output" / "prompts" / "assessment_prompts_batch_001.json"
FIXTURES = ROOT / "output" / "prompts" / "assessment_calibration_cases.json"
REPORT = ROOT / "reports" / "assessment_calibration_status.json"
SPEAKING_RUBRIC = "rub_7f7150238f8ee7a2"
WRITING_RUBRIC = "rub_9ba126dd65c25a80"

COMMON = """
Return one JSON object that validates against AssessmentResult and no prose
outside that object. Score every rubric dimension from 0.0 to 5.0. For each
dimension, quote a short exact span from the learner response as evidence and
give actionable feedback in Vietnamese. Do not reward length by itself. Do not
invent words, acoustic properties, task requirements, or errors that are not in
the supplied evidence. Every error must include its exact span, a correction,
and a valid taxonomy concept_id. Suggest at most three next_concepts, ordered by
learning value. The overall band must reflect the supplied rubric weights and
must not exceed the strongest dimension by more than 0.5. If evidence is
missing, say so in feedback and score only what can be supported.
""".strip()

SPEAKING_PROMPT = f"""You are a strict English speaking assessor for a
TOEIC-format practice platform. Use rubric {SPEAKING_RUBRIC}, version 1.0.0.
The input contains the task prompt, transcript, response duration, and optional
measured delivery observations. Pronunciation, stress, intonation, and fluency
must be scored from audio-derived observations; never infer them from spelling
or grammar in the transcript. Content, grammar, and vocabulary must be scored
against the task and transcript. A memorized but off-topic response cannot earn
a content score above 2.0. Treat filled pauses and self-corrections as fluency
evidence only when they are explicitly observed. {COMMON}"""

WRITING_PROMPT = f"""You are a strict English writing assessor for a
TOEIC-format practice platform. Use rubric {WRITING_RUBRIC}, version 1.0.0.
The input contains the exact task, learner response, task type, and word limits.
Check every requested action before scoring task response. Evaluate paragraph
organization, logical reference, grammar, vocabulary precision, spelling, and
punctuation independently. Do not treat rare vocabulary as automatically good,
and do not penalize a valid concise response when the task has no minimum word
count. Any quoted evidence must occur verbatim in the learner response. {COMMON}"""


def scores(names: list[str], values: list[float], quote: str):
    return [{"dimension": n, "score": v, "evidence_quote": quote,
             "feedback_vi": "Điểm được neo vào bằng chứng trích nguyên văn; cần đối chiếu descriptor liền kề khi review."}
            for n, v in zip(names, values)]


SPEAKING_DIMS = ["pronunciation", "intonation_stress", "fluency", "grammar", "vocabulary", "content"]
WRITING_DIMS = ["task_response", "organization", "coherence", "grammar", "vocabulary", "mechanics"]

CASES = [
    ("writing", "Write an email confirming a changed delivery date and offer one solution.",
     "Dear Ms. Lee, I am writing to confirm that your order will arrive on 18 May, two days later than planned. We can provide express installation at no charge. Please tell me whether that solution suits your schedule. Kind regards, Ana",
     [4.8, 4.5, 4.5, 4.6, 4.4, 4.8], 4.6, []),
    ("writing", "Explain whether companies should allow remote work.",
     "Companies should allow remote work because it reduces commuting time and helps employees focus. However, teams still need scheduled meetings and clear goals. A hybrid policy gives people flexibility while preserving cooperation.",
     [4.2, 4.0, 4.2, 4.3, 4.0, 4.6], 4.2, []),
    ("writing", "Request information about a training course: schedule, cost, and registration.",
     "Hello, I want course. When start? How much? Register me. Thanks.",
     [2.6, 2.0, 2.0, 1.8, 2.0, 2.8], 2.2,
     [{"type": "grammar", "span": "I want course", "correction": "I am interested in the course", "concept_id": "wr_grammar"}]),
    ("writing", "Describe a picture of two colleagues reviewing a chart.",
     "Two colleagues are reviewing a chart beside a conference table. One woman is pointing to a rising line while her coworker takes notes.",
     [4.5, 4.0, 4.2, 4.5, 4.1, 4.8], 4.4, []),
    ("writing", "Write an email declining an invitation and suggest another date.",
     "I like the new office. The weather was warm last year. Computers are useful for work.",
     [1.0, 1.5, 1.2, 3.2, 2.8, 3.5], 1.8, []),
    ("speaking", "Recommend one way to improve customer service.",
     "I recommend a short weekly review of difficult customer cases. It would help staff share solutions and respond more consistently.",
     [4.2, 4.0, 4.1, 4.3, 4.2, 4.5], 4.2, []),
    ("speaking", "Describe a busy train platform.",
     "Several passengers are waiting near the doors, and a station employee is helping a traveler with a suitcase.",
     [3.8, 3.5, 3.7, 4.2, 3.8, 4.2], 3.9, []),
    ("speaking", "Give directions from the lobby to Conference Room C.",
     "Go straight past reception, take the elevator to the third floor, and turn left. Conference Room C is opposite the kitchen.",
     [4.4, 4.1, 4.3, 4.5, 4.0, 4.8], 4.4, []),
    ("speaking", "Explain a problem with an online order.",
     "My order arrive yesterday but two item is missing. I need you send them soon because our event is Friday.",
     [3.0, 2.8, 2.9, 2.0, 2.8, 4.0], 2.9,
     [{"type": "grammar", "span": "two item is missing", "correction": "two items are missing", "concept_id": "sp_grammar"}]),
    ("speaking", "State whether you prefer working alone or in a team and explain why.",
     "The city has many restaurants and the buses run every day. Last weekend I watched a film with my cousin.",
     [3.5, 3.2, 3.4, 3.8, 3.4, 1.0], 2.7, []),
]


def main() -> int:
    now = dt.datetime.now(dt.UTC)
    prompts = [
        AssessmentPrompt(prompt_id="assess_speaking_v1", target="speaking",
                         rubric_ref=SPEAKING_RUBRIC, system_prompt=SPEAKING_PROMPT,
                         version="1.0.0", review_status="draft"),
        AssessmentPrompt(prompt_id="assess_writing_v1", target="writing",
                         rubric_ref=WRITING_RUBRIC, system_prompt=WRITING_PROMPT,
                         version="1.0.0", review_status="draft"),
    ]
    batch = AssessmentPromptBatch(
        batch_metadata=BatchMetadata(
            batch_id="assessment_prompts_batch_001",
            module_type=ModuleType.ASSESSMENT_PROMPT,
            generated_by="gen_assessment_prompts.py/editorial-v1",
            generated_at=now, review_status="draft", total_records=len(prompts)),
        prompts=prompts,
    )
    guarded_write_batch(batch, OUT)

    fixtures = []
    for index, (target, task, response, values, overall, errors) in enumerate(CASES, start=1):
        dims = SPEAKING_DIMS if target == "speaking" else WRITING_DIMS
        quote = response.split(".")[0].strip()
        expected = AssessmentResult(
            overall_band=overall, dimension_scores=scores(dims, values, quote),
            errors=errors,
            next_concepts=[errors[0]["concept_id"]] if errors else [],
        )
        fixtures.append({
            "case_id": f"cal_{index:02d}", "target": target,
            "prompt_id": f"assess_{target}_v1", "task": task,
            "learner_response": response,
            "delivery_observations": ({
                "source": "curated_fixture", "audio_available": True,
                "note": "Expected delivery bands are fixture labels, not model measurements."
            } if target == "speaking" else None),
            "expected_result": expected.model_dump(mode="json"),
        })
    FIXTURES.write_text(json.dumps({
        "schema_version": "1.0.0", "generated_at": now.isoformat(),
        "purpose": "Offline schema and regression fixtures; not empirical model calibration.",
        "total_cases": len(fixtures), "cases": fixtures,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({
        "generated_at": now.isoformat(), "fixture_count": len(fixtures),
        "schema_validated": True, "live_provider_runs": 0,
        "three_run_variance": None,
        "status": "pending_live_provider_and_human_adjudication",
        "reason": "No assessment provider credentials or human gold labels are available.",
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(prompts)} prompts and {len(fixtures)} calibration fixtures")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
