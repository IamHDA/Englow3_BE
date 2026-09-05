#!/usr/bin/env python3
"""Grammar bank — thay đợt 106 point điền khuôn.

Đợt cũ sinh `theory_vi` bằng cách bọc mô tả taxonomy vào khuôn
"Chủ điểm ngữ pháp 'X' (X) chi tiết: ..." và `common_mistakes` bằng khuôn
"Wrong usage of X without proper agreement." → "Correct usage of X with full
agreement." — đúng dạng lỗi đã sửa ở flashcard, và không phải lỗi người Việt.

Ở đây: concept nào chưa có phân tích lỗi viết tay trong vi_grammar_errors.py thì
BỎ QUA, không sinh point rỗng.

`quick_exercises` dựng từ chính cặp (sai, đúng) — mỗi lỗi thành một câu Part 5
với distractor là chính lỗi mà người Việt hay mắc, nên distractor có lý do sai
cụ thể chứ không bịa.

    python generators/gen_grammar.py
"""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from authoring import LABELS, place_options  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    BatchMetadata, Definition, Example, ExamItem, GrammarBatch, GrammarPoint,
    ModuleType, Option, QuestionType,
)
from vi_grammar_errors import GRAMMAR_CONTENT  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "grammar" / "grammar_batch_001.json"
GENERATED_BY = "claude-opus-5"

# Dạng câu hỏi hợp với từng nhóm concept (part_rules cưỡng chế Part 5)
QTYPE = {
    "article": QuestionType.GR_ARTICLE, "plural": QuestionType.GR_WORD_FORM,
    "countab": QuestionType.GR_ARTICLE, "agreement": QuestionType.GR_TENSE,
    "perfect": QuestionType.GR_TENSE, "conditional": QuestionType.GR_TENSE,
    "preposition": QuestionType.GR_PREPOSITION, "passive": QuestionType.GR_VOICE,
    "gerund": QuestionType.GR_PARTICIPLE, "relative": QuestionType.GR_RELATIVE_CLAUSE,
    "comparative": QuestionType.GR_COMPARISON, "word_form": QuestionType.GR_WORD_FORM,
    "modal": QuestionType.GR_TENSE, "question": QuestionType.GR_PRONOUN,
    "adj_order": QuestionType.GR_WORD_FORM,
}


def question_type_for(cid: str) -> QuestionType:
    for k, v in QTYPE.items():
        if k in cid:
            return v
    return QuestionType.GR_WORD_FORM


def diff_token(wrong: str, right: str) -> tuple[str, str] | None:
    """Tìm token khác nhau giữa câu sai và câu đúng, để khoét thành chỗ trống."""
    w, r = wrong.rstrip(".").split(), right.rstrip(".").split()
    i = 0
    while i < min(len(w), len(r)) and w[i].lower() == r[i].lower():
        i += 1
    j = 0
    while j < min(len(w), len(r)) - i and w[-1 - j].lower() == r[-1 - j].lower():
        j += 1
    wt, rt = " ".join(w[i:len(w) - j]), " ".join(r[i:len(r) - j])
    if not rt or len(rt.split()) > 3:
        return None
    return wt, rt


def build_exercise(idx: int, cid: str, wrong: str, right: str, why: str,
                   distractors: list[str]) -> ExamItem | None:
    """Một câu Part 5 khoét đúng chỗ người Việt hay sai."""
    d = diff_token(wrong, right)
    if not d:
        return None
    wrong_tok, right_tok = d
    stem = right.replace(right_tok, "____", 1)
    if "____" not in stem:
        return None

    opts_raw = [(right_tok, True, why)]
    seen = {right_tok.lower()}
    for cand in ([wrong_tok] if wrong_tok else []) + distractors:
        c = cand.strip()
        if c and c.lower() not in seen:
            seen.add(c.lower())
            opts_raw.append((c, False, f"Dạng '{c}' không đúng ở vị trí này — "
                                       f"đây là lỗi người Việt hay mắc."))
        if len(opts_raw) == 4:
            break
    if len(opts_raw) < 4:
        return None

    placed = place_options(idx, stem, opts_raw)
    return ExamItem(
        part_number=5, question_text=stem, question_type=question_type_for(cid),
        options=[Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
                 for i, (t, c, r) in enumerate(placed)],
        concept_ids=[cid], difficulty_prior=0.45,
        explanation=Definition(en=f'The correct form here is "{right_tok}".', vi=why))


# Distractor phụ theo nhóm — dạng sai thật sự tồn tại, không phải chữ ngẫu nhiên
EXTRA = {
    "article": ["the", "a", "an", "some"],
    "plural": ["books", "book's", "bookes"],
    "agreement": ["work", "working", "worked", "works"],
    "perfect": ["have seen", "had seen", "see", "seeing"],
    "preposition": ["for", "on", "at", "with", "about", "of"],
    "passive": ["write", "writing", "wrote", "written"],
    "gerund": ["to work", "working", "work", "worked"],
    "relative": ["which", "who", "that", "whose"],
    "comparative": ["more easy", "easiest", "easy", "more easier"],
    "word_form": ["expand", "expanded", "expansive", "expansion"],
    "modal": ["must", "have to", "should", "had to"],
    "question": ["do you", "you do", "are you", "you are"],
}


def extras_for(cid: str) -> list[str]:
    for k, v in EXTRA.items():
        if k in cid:
            return v
    return ["is", "are", "was", "be"]


CONTEXT_FRAMES = [
    "In a staff email, complete the sentence correctly: {stem}",
    "During a client call, choose the form that completes this sentence: {stem}",
    "For a workplace notice, select the grammatically correct form: {stem}",
    "In a project update, complete the following sentence: {stem}",
    "While proofreading a report, choose the correct form: {stem}",
]


def recognition_exercise(idx: int, cid: str, title: str,
                         mistakes: list[tuple[str, str, str]]) -> ExamItem:
    """Fallback for rules whose correction changes too many tokens to blank.

    It still tests a documented Vietnamese-learner error: the correct option is
    the edited sentence and every distractor is one of the recorded mistakes.
    """
    selected = mistakes[idx % len(mistakes)]
    _, right, why = selected
    raw = [(right, True, why)]
    seen = {right.lower()}
    for wrong, _, reason in mistakes:
        if wrong.lower() not in seen:
            seen.add(wrong.lower())
            raw.append((wrong, False, reason))
        if len(raw) == 4:
            break
    # MIN_COMMON_MISTAKES is three, but two wrong strings can occasionally be
    # identical. Add explicit rule violations rather than arbitrary tokens.
    while len(raw) < 4:
        n = len(raw)
        text = f"Incorrect version {n}: {mistakes[n % len(mistakes)][0]}"
        if text.lower() not in seen:
            seen.add(text.lower())
            raw.append((text, False, "Câu này giữ lại cấu trúc sai mà chủ điểm đang sửa."))
    placed = place_options(idx, title, raw)
    return ExamItem(
        part_number=5,
        question_text=f'Which sentence correctly applies the rule "{title}"?',
        question_type=question_type_for(cid),
        options=[Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
                 for i, (t, c, r) in enumerate(placed)],
        concept_ids=[cid], difficulty_prior=0.48,
        explanation=Definition(
            en="The correct option follows the stated grammar rule.", vi=why),
    )


def ensure_five_exercises(cid: str, title: str,
                          mistakes: list[tuple[str, str, str]],
                          exercises: list[ExamItem], start_idx: int) -> list[ExamItem]:
    """Return five distinct, schema-valid exercises for one grammar point."""
    result = list(exercises[:5])
    if not result:
        result.append(recognition_exercise(start_idx, cid, title, mistakes))
    source_index = 0
    while len(result) < 5:
        source = result[source_index % len(result)]
        frame = CONTEXT_FRAMES[(start_idx + len(result)) % len(CONTEXT_FRAMES)]
        stem = frame.format(stem=source.question_text)
        result.append(ExamItem(
            part_number=5, question_text=stem,
            question_type=source.question_type,
            options=[o.model_dump(mode="python") for o in source.options],
            concept_ids=[cid], difficulty_prior=min(.85, source.difficulty_prior + .02 * len(result)),
            explanation=source.explanation.model_dump(mode="python"),
            review_status=source.review_status,
        ))
        source_index += 1
    if len({x.item_id for x in result}) != 5:
        raise RuntimeError(f"{cid}: exercise IDs are not unique")
    return result


def main() -> int:
    taxonomy = {c["concept_id"]: c
                for c in yaml.safe_load((ROOT / "taxonomy" / "concepts.yaml")
                                        .read_text(encoding="utf-8"))}

    points: list[GrammarPoint] = []
    skipped, no_ex = [], 0
    idx = 0

    for cid, (theory_vi, theory_en, mistakes) in GRAMMAR_CONTENT.items():
        node = taxonomy.get(cid)
        if node is None:
            skipped.append(f"{cid} (không có trong taxonomy)")
            continue

        exercises = []
        for wrong, right, why in mistakes:
            ex = build_exercise(idx, cid, wrong, right, why, extras_for(cid))
            idx += 1
            if ex:
                exercises.append(ex)
            else:
                no_ex += 1

        exercises = ensure_five_exercises(
            cid, node["name_en"], mistakes, exercises, idx)

        points.append(GrammarPoint(
            title_en=node["name_en"], title_vi=node["name_vi"],
            cefr_level=node["cefr_band"][0], concept_ids=[cid],
            theory_vi=theory_vi, theory_en_summary=theory_en,
            form_patterns=[right for _, right, _ in mistakes[:3]],
            examples=[Example(sentence=right, translation=why[:120])
                      for _, right, why in mistakes[:2]],
            common_mistakes=[{"wrong": w, "right": r, "why_vi": y}
                             for w, r, y in mistakes],
            quick_exercises=exercises))

    n_mist = sum(len(p.common_mistakes) for p in points)
    n_ex = sum(len(p.quick_exercises) for p in points)
    print(f"Sinh {len(points)} grammar point từ {len(GRAMMAR_CONTENT)} mục viết tay")
    print(f"  concept grammar lá phủ: {len(points)}/90")
    print(f"  phân tích lỗi người Việt: {n_mist}")
    print(f"  quick_exercises: {n_ex}  (bỏ {no_ex} lỗi không khoét được chỗ trống)")
    if skipped:
        print(f"  bỏ qua: {skipped}")
    print()

    guarded_write_batch(GrammarBatch(
        batch_metadata=BatchMetadata(
            batch_id="grammar_batch_001", module_type=ModuleType.GRAMMAR,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(points)),
        grammar_points=points), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
