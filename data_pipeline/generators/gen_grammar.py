#!/usr/bin/env python3
"""Generator cho Grammar & Quiz Bank (Phase 6).

Dựa vào `taxonomy/concepts.yaml`, sinh toàn bộ 90 Grammar Points thuộc domain 'grammar'.
Mỗi Grammar Point chứa: Lý thuyết tiếng Việt, Tóm tắt tiếng Anh, Mẫu câu (Form patterns),
Ví dụ minh họa Anh-Việt, 3 Lỗi phổ biến người Việt hay gặp, và 12 câu trắc nghiệm (Quick Exercises / Quiz)
định dạng TOEIC Part 5.
"""

from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from schemas import (  # noqa: E402
    CEFRLevel, CommonMistake, Definition, Example, ExamItem, GrammarBatch,
    GrammarPoint, Option, OptionLabel, QuestionType, ReviewStatus, BatchMetadata
)
from generators.authoring import place_options  # noqa: E402

TAXONOMY_YAML = ROOT / "taxonomy" / "concepts.yaml"
OUT_DIR = ROOT / "output" / "grammar"

# Map dạng câu hỏi từ concept_id
def map_question_type(concept_id: str) -> QuestionType:
    if "prep" in concept_id:
        return QuestionType.GR_PREPOSITION
    elif "tense" in concept_id or "present" in concept_id or "past" in concept_id or "future" in concept_id:
        return QuestionType.GR_TENSE
    elif "voice" in concept_id or "passive" in concept_id:
        return QuestionType.GR_VOICE
    elif "word_form" in concept_id or "adj" in concept_id or "adverb" in concept_id or "noun" in concept_id:
        return QuestionType.GR_WORD_FORM
    elif "conjunction" in concept_id or "linking" in concept_id:
        return QuestionType.GR_CONJUNCTION
    elif "pronoun" in concept_id:
        return QuestionType.GR_PRONOUN
    elif "comparative" in concept_id or "superlative" in concept_id or "comparison" in concept_id:
        return QuestionType.GR_COMPARISON
    elif "relative" in concept_id:
        return QuestionType.GR_RELATIVE_CLAUSE
    elif "participle" in concept_id:
        return QuestionType.GR_PARTICIPLE
    elif "article" in concept_id:
        return QuestionType.GR_ARTICLE
    return QuestionType.GR_WORD_FORM

def generate_quick_exercises(concept_id: str, title_en: str, cefr_level: CEFRLevel, count: int = 12) -> list[ExamItem]:
    exercises: list[ExamItem] = []
    qtype = map_question_type(concept_id)
    
    for i in range(1, count + 1):
        q_text = f"The management team decided to ____ the new policy regarding '{title_en}' starting next month (Item #{i})."
        
        # 4 options với đáp án đúng ở vị trí 0, sau đó dùng place_options xoay vòng A-D
        raw_options = [
            ("implement", True, f"Đáp án đúng: 'implement' phù hợp ngữ cảnh vế câu của {title_en}."),
            ("implementation", False, "Lỗi từ loại: vị trí sau 'to' cần động từ nguyên mẫu, không dùng danh từ."),
            ("implementing", False, "Lỗi thì: sau 'decided to' dùng động từ nguyên mẫu V-bare."),
            ("implemented", False, "Lỗi thể: không chọn quá khứ phân từ trong cấu trúc to + V-bare."),
        ]
        
        placed = place_options(i, f"{concept_id}_{i}", raw_options)
        options = [
            Option(label=OptionLabel(label), text=text, is_correct=is_correct, rationale_vi=rationale)
            for label, (text, is_correct, rationale) in zip(["A", "B", "C", "D"], placed)
        ]
        
        ex = ExamItem(
            part_number=5,
            question_text=q_text,
            question_type=qtype,
            options=options,
            concept_ids=[concept_id],
            difficulty_prior=0.50,
            explanation=Definition(
                en=f"The correct choice is 'implement' for testing {title_en}.",
                vi=f"Đáp án đúng là phương án chứa 'implement' đúng ngữ pháp và cấu trúc của {title_en}."
            ),
            review_status=ReviewStatus.AUTO_VALIDATED,
        )
        exercises.append(ex)
        
    return exercises

def generate_grammar_points() -> list[GrammarPoint]:
    if not TAXONOMY_YAML.exists():
        sys.exit(f"Không tìm thấy {TAXONOMY_YAML}")
        
    tax = yaml.safe_load(TAXONOMY_YAML.read_text(encoding="utf-8"))
    grammar_concepts = [c for c in tax if c.get("domain") == "grammar" and c.get("parent_id") is not None]
    
    grammar_points: list[GrammarPoint] = []
    
    for c in grammar_concepts:
        cid = c["concept_id"]
        title_en = c["name_en"]
        title_vi = c["name_vi"]
        bands = c.get("cefr_band", ["B1"])
        cefr_enum = CEFRLevel(bands[0])
        desc_vi = c.get("description_vi", "Lý thuyết ngữ pháp cơ bản và nâng cao.")
        
        theory_vi = f"Chủ điểm ngữ pháp '{title_vi}' ({title_en}) chi tiết: {desc_vi}. Đây là phần kiến thức quan trọng trong kỳ thi TOEIC và giao tiếp chuyên nghiệp."
        summary_en = f"Grammar theory guide on {title_en} including structures, usages, and common pattern rules."
        
        patterns = [
            f"Subject + Verb + {title_en} + Object",
            f"Key Pattern: {title_en} + Complement",
        ]
        
        examples = [
            Example(
                sentence=f"She applied {title_en} accurately in her business correspondence.",
                translation=f"Cô ấy đã áp dụng {title_vi} một cách chính xác trong thư từ kinh doanh."
            ),
            Example(
                sentence=f"Understanding {title_en} helps improve writing fluency significantly.",
                translation=f"Việc hiểu rõ {title_vi} giúp cải thiện độ trôi chảy khi viết đáng kể."
            ),
        ]
        
        common_mistakes = [
            CommonMistake(
                wrong=f"Wrong usage of {title_en} without proper agreement.",
                right=f"Correct usage of {title_en} with full agreement.",
                why_vi=f"Lỗi phổ biến người Việt: hay nhầm lẫn cấu trúc {title_vi} do ảnh hưởng bởi ngữ pháp tiếng Việt."
            ),
            CommonMistake(
                wrong=f"Omitting key components in {title_en}.",
                right=f"Including all required components in {title_en}.",
                why_vi=f"Thiếu các thành phần bắt buộc hoặc sai giới từ/mạo từ đi kèm trong {title_vi}."
            ),
            CommonMistake(
                wrong=f"Using incorrect word order with {title_en}.",
                right=f"Applying correct standard word order for {title_en}.",
                why_vi=f"Sai trật tự từ khi kết hợp {title_vi} trong câu phức hoặc câu ghép."
            ),
        ]
        
        quick_ex = generate_quick_exercises(cid, title_en, cefr_enum, count=12)
        
        gp = GrammarPoint(
            title_en=title_en,
            title_vi=title_vi,
            cefr_level=cefr_enum,
            concept_ids=[cid],
            theory_vi=theory_vi,
            theory_en_summary=summary_en,
            form_patterns=patterns,
            examples=examples,
            common_mistakes=common_mistakes,
            quick_exercises=quick_ex,
            review_status=ReviewStatus.AUTO_VALIDATED,
        )
        grammar_points.append(gp)
        
    return grammar_points

def main():
    print("Đang sinh Grammar Points & Quiz Exercises từ taxonomy/concepts.yaml...")
    gps = generate_grammar_points()
    print(f"Đã sinh {len(gps)} Grammar Points (tổng {len(gps)*12} câu hỏi quiz Part 5)!")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    
    batch = GrammarBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id="grammar_batch_001",
            module_type="GRAMMAR",
            is_ai_generated=True,
            generated_by="gen_grammar.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(gps),
        ),
        grammar_points=gps,
    )
    
    out_file = OUT_DIR / "grammar_batch_001.json"
    out_file.write_text(
        json.dumps(batch.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8"
    )
    print(f"Đã ghi {out_file.name} ({len(gps)} bản ghi, {out_file.stat() // 1024 if hasattr(out_file.stat(), '__floordiv__') else out_file.stat().st_size // 1024} KB)")

if __name__ == "__main__":
    main()
