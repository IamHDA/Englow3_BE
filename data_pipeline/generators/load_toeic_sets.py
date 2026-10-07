#!/usr/bin/env python3
"""Nạp các đề TOEIC đầy đủ (output/exams/individual_sets/toeic_practice_test_*.json) vào bảng đề của app.

Mỗi đề thành một exam MOCK TOEIC LR: hai kỹ năng (Listening, Reading), bảy part đúng tên TOEIC, mỗi group thành một
question_set (audio, ảnh, đoạn văn), mỗi câu 4,95 điểm để mỗi kỹ năng cộng đúng 495 và cả đề 990.

Part 1 và 2 giống đề thật: đáp án chỉ nghe, không in chữ. Lựa chọn để trống, chỉ còn chữ cái A-D; câu nói nằm trong
giải thích, xem được sau khi nộp. Script nghe (transcript) lưu ở metadata của question_set, không gửi cho người học lúc làm bài.

Không xoá gì: đề nào đã có (cùng id, sinh từ batch_id) thì bỏ qua. Media không tải lên ở đây - xem --print-media.

Chạy:
    python generators/load_toeic_sets.py --source <thư mục individual_sets> --dsn <postgres dsn> --status PUBLISHED
    python generators/load_toeic_sets.py --source ... --dry-run          # chỉ kiểm tra, không ghi
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import uuid
from decimal import Decimal
from pathlib import Path

NAMESPACE = uuid.UUID("8f3d6c1e-6a52-4c1b-9d0a-3e5f2b7c9a10")
POINTS = Decimal("4.95")
PART_TITLES = {
    1: "Part 1: Photographs",
    2: "Part 2: Question-Response",
    3: "Part 3: Conversations",
    4: "Part 4: Talks",
    5: "Part 5: Incomplete Sentences",
    6: "Part 6: Text Completion",
    7: "Part 7: Reading Comprehension",
}
PROMPTS = {
    1: "Look at the picture and listen to the four statements. Choose the one that best describes the picture.",
    2: "Listen to the question or statement and the three responses. Choose the best response.",
}


def uid(*parts: str) -> str:
    return str(uuid.uuid5(NAMESPACE, "/".join(parts)))


def difficulty(prior: float | None) -> str:
    if prior is None:
        return "MEDIUM"
    return "EASY" if prior < 0.33 else "MEDIUM" if prior < 0.66 else "HARD"


def object_key(url: str | None) -> str | None:
    """http://localhost:9000/images/toeic/listening/part1/x.jpg -> toeic/listening/part1/x.jpg (bucket `exams`)."""
    if not url:
        return None
    path = url.split("://", 1)[-1].split("/", 1)[1]
    return path.split("/", 1)[1] if path.startswith("images/") else path


def build(document: dict) -> dict:
    """One test file -> rows for every table, in insert order. Pure: no database access."""
    meta, test = document["batch_metadata"], document["sets"][0]
    exam_id = uid("exam", meta["batch_id"])
    groups = {group["group_id"]: group for group in document["groups"]}
    rows: dict[str, list[dict]] = {k: [] for k in ("sections", "parts", "sets", "questions", "options")}

    for section_order, (skill, entries) in enumerate((("LISTENING", test["listening"]), ("READING", test["reading"])), 1):
        section_id = uid(exam_id, skill)
        rows["sections"].append({"id": section_id, "exam_id": exam_id, "section_type": skill, "order_no": section_order,
                                 "max_raw_score": POINTS * len(entries)})
        ordered_groups: list[str] = []
        for entry in entries:
            if entry["group_id"] not in ordered_groups:
                ordered_groups.append(entry["group_id"])
        part_ids: dict[int, str] = {}
        set_counter: dict[int, int] = {}
        for group_id in ordered_groups:
            group = groups[group_id]
            part = group["part_number"]
            if part not in part_ids:
                part_ids[part] = uid(exam_id, "part", str(part))
                rows["parts"].append({"id": part_ids[part], "exam_section_id": section_id,
                                      "order_no": len(part_ids), "title": PART_TITLES[part]})
            set_counter[part] = set_counter.get(part, 0) + 1
            set_id = uid(exam_id, "set", group_id)
            audio = group.get("audio") or {}
            passages = sorted(group.get("passages") or [], key=lambda p: p.get("order", 0))
            rows["sets"].append({
                "id": set_id, "section_part_id": part_ids[part], "order_no": set_counter[part],
                "content": "\n\n".join(p["text"].strip() for p in passages) or None,
                "audio_object_key": object_key(audio.get("audio_url")),
                "image_object_key": object_key(group.get("image_url")),
                "metadata": json.dumps({"source_group_id": group_id, "transcript": audio.get("script")},
                                       ensure_ascii=False),
            })
            for q_order, question in enumerate(group["questions"], 1):
                question_id = uid(exam_id, "question", question["item_id"])
                text = question.get("question_text")
                if part in PROMPTS:
                    content = PROMPTS[part]
                elif text and text.startswith("Chỗ trống"):
                    content = "Blank " + text.removeprefix("Chỗ trống").strip()
                else:
                    content = text or f"Question {q_order}"
                explanation = (question.get("explanation") or {}).get("vi") or (question.get("explanation") or {}).get("en")
                if part in PROMPTS:
                    # The options were only heard; the review spells out what each one said.
                    heard = "\n".join(f"({o['label']}) {o['text']}" for o in question["options"])
                    asked = f"Câu hỏi: “{text}”\n" if part == 2 and text else ""
                    explanation = f"{asked}{heard}\n\n{explanation or ''}".strip()
                rows["questions"].append({
                    "id": question_id, "question_set_id": set_id, "question_type": "SINGLE_CHOICE",
                    "content": content, "difficulty_level": difficulty(question.get("difficulty_prior")),
                    "skill_type": skill, "question_category": question.get("question_type"), "order_no": q_order,
                    "max_raw_score": POINTS, "explanation": explanation,
                    "metadata": json.dumps({"source_item_id": question["item_id"]}),
                })
                for o_order, option in enumerate(question["options"], 1):
                    spoken = part in PROMPTS
                    rationale = option.get("rationale_vi")
                    rows["options"].append({
                        "id": uid(question_id, option["label"]), "question_id": question_id,
                        "content": "" if spoken else option["text"], "order_no": o_order,
                        "is_correct": bool(option["is_correct"]),
                        "explanation": (f"“{option['text']}” — {rationale}" if spoken else rationale),
                    })

    total = sum(s["max_raw_score"] for s in rows["sections"])
    rows["exam"] = {"id": exam_id, "title": test["title"],
                    "description": "Đề luyện 200 câu theo đúng 7 part của TOEIC Listening & Reading: có audio, hình, "
                                   "đáp án và giải thích từng lựa chọn.",
                    "max_raw_score": total, "question_count": len(rows["questions"])}
    return rows


def check(rows: dict) -> list[str]:
    problems = []
    if rows["exam"]["question_count"] != 200:
        problems.append(f"{rows['exam']['title']}: {rows['exam']['question_count']} questions, expected 200")
    if rows["exam"]["max_raw_score"] != Decimal("990.00"):
        problems.append(f"{rows['exam']['title']}: max score {rows['exam']['max_raw_score']}, expected 990")
    if len(rows["parts"]) != 7:
        problems.append(f"{rows['exam']['title']}: {len(rows['parts'])} parts, expected 7")
    for q in rows["questions"]:
        correct = sum(1 for o in rows["options"] if o["question_id"] == q["id"] and o["is_correct"])
        if correct != 1:
            problems.append(f"question {q['id']} has {correct} correct options")
    return problems


def insert(cur, rows: dict, status: str, author_sql: str) -> bool:
    exam = rows["exam"]
    cur.execute("select 1 from exams where id = %s", (exam["id"],))
    if cur.fetchone():
        return False
    cur.execute(f"""insert into exams (id, title, description, exam_type, certificate_type, certificate_variant,
                        target_level, duration_seconds, max_raw_score, pass_score, status, version_number,
                        created_by_user_id, published_at)
                    values (%s, %s, %s, 'MOCK', 'TOEIC', 'LR', 'B1', 7200, %s, null, %s, 1, ({author_sql}),
                            case when %s = 'PUBLISHED' then now() end)""",
                (exam["id"], exam["title"], exam["description"], exam["max_raw_score"], status, status))
    for r in rows["sections"]:
        cur.execute("""insert into exam_sections (id, exam_id, section_type, order_no, max_raw_score,
                           is_scored_by_criteria) values (%(id)s, %(exam_id)s, %(section_type)s, %(order_no)s,
                           %(max_raw_score)s, false)""", r)
    for r in rows["parts"]:
        cur.execute("""insert into section_parts (id, exam_section_id, order_no, title)
                       values (%(id)s, %(exam_section_id)s, %(order_no)s, %(title)s)""", r)
    for r in rows["sets"]:
        cur.execute("""insert into question_sets (id, section_part_id, order_no, content, audio_object_key,
                           image_object_key, metadata) values (%(id)s, %(section_part_id)s, %(order_no)s, %(content)s,
                           %(audio_object_key)s, %(image_object_key)s, %(metadata)s::jsonb)""", r)
    for r in rows["questions"]:
        cur.execute("""insert into questions (id, question_set_id, question_type, content, difficulty_level,
                           skill_type, question_category, order_no, max_raw_score, explanation, metadata)
                       values (%(id)s, %(question_set_id)s, %(question_type)s, %(content)s, %(difficulty_level)s,
                           %(skill_type)s, %(question_category)s, %(order_no)s, %(max_raw_score)s, %(explanation)s,
                           %(metadata)s::jsonb)""", r)
    for r in rows["options"]:
        cur.execute("""insert into question_options (id, question_id, content, order_no, is_correct, explanation)
                       values (%(id)s, %(question_id)s, %(content)s, %(order_no)s, %(is_correct)s, %(explanation)s)""",
                    r)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", required=True, help="thư mục chứa toeic_practice_test_*.json")
    parser.add_argument("--dsn", default=os.environ.get("LOAD_DSN"), help="postgres DSN (hoặc biến LOAD_DSN)")
    parser.add_argument("--schema", default="englow3")
    parser.add_argument("--status", default="DRAFT", choices=["DRAFT", "PUBLISHED"])
    parser.add_argument("--author-email", default=None, help="người tạo; mặc định admin đầu tiên")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--print-media", action="store_true", help="in các object key media cần có trong bucket exams")
    args = parser.parse_args()

    files = sorted(glob.glob(str(Path(args.source) / "toeic_practice_test_*.json")))
    built = [build(json.loads(Path(f).read_text(encoding="utf-8"))) for f in files]
    problems = [p for rows in built for p in check(rows)]
    for rows in built:
        print(f"{rows['exam']['title']}: {len(rows['parts'])} part, {rows['exam']['question_count']} câu, "
              f"tối đa {rows['exam']['max_raw_score']}")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    if args.print_media:
        keys = sorted({k for rows in built for s in rows["sets"] for k in (s["audio_object_key"], s["image_object_key"]) if k})
        print("\n".join(keys))
    if args.dry_run:
        return 0
    if not args.dsn:
        print("Thiếu --dsn hoặc LOAD_DSN", file=sys.stderr)
        return 2

    import psycopg2

    author = ("select id from users where email = %s" % psycopg2.extensions.adapt(args.author_email).getquoted().decode()
              if args.author_email else "select id from users where role = 'ADMIN' order by created_at limit 1")
    connection = psycopg2.connect(args.dsn)
    try:
        with connection, connection.cursor() as cur:
            cur.execute(f"set search_path = {args.schema}")
            added = sum(insert(cur, rows, args.status, author) for rows in built)
        print(f"Đã thêm {added} đề, bỏ qua {len(built) - added} đề đã có.")
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
