#!/usr/bin/env python3
"""Nạp một bài kiểm tra đầu vào (exam PLACEMENT) tạm thời vào bảng đề của app.

Đề viết tay ngay trong file này: 30 câu trắc nghiệm một đáp án, độ khó tăng dần từ A1 tới C1, chia ba part -
ngữ pháp, từ vựng, đọc hiểu. Chỉ có kỹ năng Reading vì phần nghe cần audio trong bucket. Mỗi câu 1 điểm; điểm phần trăm
được PlacementRecorder đổi ra trình độ (>=90 C2, >=75 C1, >=60 B2, >=45 B1, >=25 A2, còn lại A1).

Không xoá gì: id sinh cố định từ tên đề, chạy lại thì bỏ qua đề đã có.

Chạy:
    python generators/load_placement_test.py --dry-run
    python generators/load_placement_test.py --dsn <postgres dsn> --status PUBLISHED
    python generators/load_placement_test.py --env-file ../.env --status PUBLISHED   # DB của backend
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import uuid
from decimal import Decimal

NAMESPACE = uuid.UUID("2c9e6f4a-3b1d-4e8a-9f27-5d0c8b1a6e34")
EXAM_KEY = "placement-test-temporary-v1"
POINT = Decimal("1.0")

# (part title, instruction, passage or None, [(content, level, [options], correct index, explanation)])
PARTS = [
    (
        "Part 1: Grammar",
        "Choose the word or phrase that best completes each sentence.",
        None,
        [
            (
                "She ___ a teacher.",
                "A1",
                ["am", "is", "are", "be"],
                1,
                "Chủ ngữ số ít ngôi thứ ba (she) đi với 'is'.",
            ),
            (
                "There ___ two apples on the table.",
                "A1",
                ["is", "are", "be", "has"],
                1,
                "'There are' dùng với danh từ số nhiều (two apples).",
            ),
            (
                "I usually ___ to school by bus.",
                "A1",
                ["go", "goes", "going", "went"],
                0,
                "Thói quen ở hiện tại đơn; chủ ngữ 'I' dùng động từ nguyên mẫu 'go'.",
            ),
            (
                "We ___ dinner when the phone rang.",
                "A2",
                ["were having", "had", "have", "are having"],
                0,
                "Hành động đang diễn ra (quá khứ tiếp diễn) thì bị một hành động khác xen vào (quá khứ đơn).",
            ),
            (
                "This is the ___ film I have ever seen.",
                "A2",
                ["best", "better", "good", "most good"],
                0,
                "So sánh nhất của 'good' là 'the best', thường đi với 'have ever seen'.",
            ),
            (
                "He has lived here ___ 2015.",
                "A2",
                ["since", "for", "from", "at"],
                0,
                "'since' + mốc thời gian; 'for' + khoảng thời gian.",
            ),
            (
                "If it rains tomorrow, we ___ at home.",
                "B1",
                ["will stay", "would stay", "stayed", "stay"],
                0,
                "Câu điều kiện loại 1: If + hiện tại đơn, will + động từ nguyên mẫu.",
            ),
            (
                "The report ___ by the manager yesterday.",
                "B1",
                ["was written", "wrote", "has written", "is writing"],
                0,
                "Bị động ở quá khứ đơn (yesterday): was/were + V3.",
            ),
            (
                "I'm not used to ___ up so early.",
                "B1",
                ["getting", "get", "got", "be getting"],
                0,
                "'be used to' + V-ing: quen với việc gì.",
            ),
            (
                "She asked me where ___.",
                "B1",
                ["I lived", "did I live", "do I live", "lived I"],
                0,
                "Câu tường thuật: không đảo ngữ, lùi thì (where I lived).",
            ),
            (
                "If I ___ harder, I would have passed the exam.",
                "B2",
                ["had studied", "studied", "have studied", "would study"],
                0,
                "Câu điều kiện loại 3: If + quá khứ hoàn thành, would have + V3.",
            ),
            (
                "By the time we arrived, the train ___.",
                "B2",
                [
                    "had already left",
                    "already left",
                    "has already left",
                    "was already leaving",
                ],
                0,
                "Hành động xảy ra trước một mốc trong quá khứ dùng quá khứ hoàn thành.",
            ),
            (
                "Not until the results were published ___ how serious the problem was.",
                "C1",
                ["did we realise", "we realised", "we did realise", "realised we"],
                0,
                "Đảo ngữ sau 'Not until ...': trợ động từ đứng trước chủ ngữ (did we realise).",
            ),
            (
                "It's high time the government ___ action on air pollution.",
                "C1",
                ["took", "takes", "will take", "has taken"],
                0,
                "'It's high time + S + quá khứ đơn': đã đến lúc phải làm gì.",
            ),
        ],
    ),
    (
        "Part 2: Vocabulary",
        "Choose the word or phrase that best fits each sentence.",
        None,
        [
            (
                "Choose the word that is opposite in meaning to 'hot'.",
                "A1",
                ["cold", "warm", "big", "fast"],
                0,
                "'cold' (lạnh) trái nghĩa với 'hot' (nóng).",
            ),
            (
                "I need to ___ an appointment with the dentist.",
                "A2",
                ["make", "do", "take", "have"],
                0,
                "Cụm cố định: 'make an appointment' (đặt lịch hẹn).",
            ),
            (
                "Could you ___ the lights when you leave?",
                "A2",
                ["turn off", "turn up", "take off", "put up"],
                0,
                "'turn off' = tắt (đèn, thiết bị).",
            ),
            (
                "The museum is very popular with ___; thousands visit it every summer.",
                "B1",
                ["tourists", "passengers", "customers", "patients"],
                0,
                "Người đi tham quan là 'tourists'; 'passengers' là hành khách trên phương tiện.",
            ),
            (
                "He was so ___ that he fell asleep during the meeting.",
                "B1",
                ["exhausted", "exhausting", "excited", "exciting"],
                0,
                "Tính từ đuôi -ed tả cảm giác của người: 'exhausted' = kiệt sức.",
            ),
            (
                "The company had to ___ the launch of its new product until next year.",
                "B2",
                ["postpone", "prevent", "delete", "refuse"],
                0,
                "'postpone ... until' = hoãn lại đến khi.",
            ),
            (
                "The new policy had a significant ___ on sales.",
                "B2",
                ["impact", "affect", "effort", "result"],
                0,
                "'have an impact on' = tác động tới; 'affect' là động từ.",
            ),
            (
                "The evidence was ___: nobody could argue with it.",
                "C1",
                ["indisputable", "negligible", "ambiguous", "tentative"],
                0,
                "'indisputable' = không thể chối cãi, khớp với 'nobody could argue with it'.",
            ),
        ],
    ),
    (
        "Part 3: Reading - Mai's plan",
        "Read the text and answer the questions.",
        "Mai works at a small bakery in Da Nang. She starts work at 5 a.m. because the bread must be ready "
        "before the shop opens at 7. In the afternoon, when the shop is quiet, she takes English lessons online. "
        "She wants to open her own café next year, and she hopes many tourists will visit it.",
        [
            (
                "What time does the shop open?",
                "A2",
                ["At 7 a.m.", "At 5 a.m.", "In the afternoon", "At noon"],
                0,
                "'before the shop opens at 7' - cửa hàng mở lúc 7 giờ.",
            ),
            (
                "Why does Mai start work so early?",
                "A2",
                [
                    "The bread must be ready before the shop opens.",
                    "She has English lessons in the morning.",
                    "The shop is busiest at 5 a.m.",
                    "She lives far from the bakery.",
                ],
                0,
                "Bài viết nói bánh phải xong trước giờ mở cửa.",
            ),
            (
                "When does Mai study English?",
                "A2",
                ["In the afternoon", "Before work", "Late at night", "At the weekend"],
                0,
                "'In the afternoon, when the shop is quiet, she takes English lessons online.'",
            ),
            (
                "What does Mai want to do next year?",
                "B1",
                [
                    "Open her own café",
                    "Become an English teacher",
                    "Move to another city",
                    "Work in a bigger bakery",
                ],
                0,
                "'She wants to open her own café next year.'",
            ),
        ],
    ),
    (
        "Part 4: Reading - Remote work",
        "Read the text and answer the questions.",
        "Remote work was once seen as a privilege offered only by forward-thinking companies. Today it is "
        "widespread, yet its effects remain hotly debated. Supporters argue that employees save hours of commuting "
        "and can organise their day more efficiently. Critics counter that the absence of face-to-face contact "
        "weakens team spirit and makes it harder for junior staff to learn from experienced colleagues. Most "
        "researchers now agree that the outcome depends less on where people work than on how well managers adapt "
        "their methods: clear goals and regular feedback appear to matter more than physical presence.",
        [
            (
                "What is the main idea of the text?",
                "B2",
                [
                    "Whether remote work succeeds depends mainly on how it is managed.",
                    "Remote work should be banned in most companies.",
                    "Only large companies can offer remote work.",
                    "Commuting is the biggest problem for employees.",
                ],
                0,
                "Câu cuối: kết quả phụ thuộc vào cách quản lý hơn là nơi làm việc.",
            ),
            (
                "According to the critics, who loses most from remote work?",
                "B2",
                [
                    "Junior staff",
                    "Managers",
                    "Researchers",
                    "Forward-thinking companies",
                ],
                0,
                "'makes it harder for junior staff to learn from experienced colleagues'.",
            ),
            (
                "The word 'counter' in the text is closest in meaning to ___.",
                "C1",
                [
                    "reply by disagreeing",
                    "count again",
                    "agree completely",
                    "calculate carefully",
                ],
                0,
                "'Critics counter that...' = những người phản đối đáp lại bằng ý kiến trái chiều.",
            ),
            (
                "What can be inferred about remote work in the past?",
                "C1",
                [
                    "It was relatively uncommon.",
                    "It was more popular than it is today.",
                    "It was not allowed by law.",
                    "Nobody criticised it.",
                ],
                0,
                "'once seen as a privilege offered only by forward-thinking companies' - trước đây còn hiếm.",
            ),
        ],
    ),
]

DIFFICULTY = {"A1": "EASY", "A2": "EASY", "B1": "MEDIUM", "B2": "HARD", "C1": "HARD"}
CATEGORY = {0: "GRAMMAR", 1: "VOCABULARY", 2: "READING", 3: "READING"}


def uid(*parts: str) -> str:
    return str(uuid.uuid5(NAMESPACE, "/".join(parts)))


def build() -> dict:
    """Rows for every table, in insert order. Pure: no database access."""
    exam_id = uid(EXAM_KEY)
    section_id = uid(exam_id, "READING")
    rows: dict = {k: [] for k in ("parts", "sets", "questions", "options")}
    for part_index, (title, instruction, passage, questions) in enumerate(PARTS):
        part_id = uid(exam_id, "part", str(part_index))
        set_id = uid(exam_id, "set", str(part_index))
        rows["parts"].append(
            {
                "id": part_id,
                "exam_section_id": section_id,
                "order_no": part_index + 1,
                "title": title,
                "instruction": instruction,
            }
        )
        rows["sets"].append(
            {
                "id": set_id,
                "section_part_id": part_id,
                "order_no": 1,
                "content": passage,
                "metadata": json.dumps({"source": EXAM_KEY}),
            }
        )
        for q_order, (content, level, options, correct, explanation) in enumerate(
            questions, 1
        ):
            question_id = uid(set_id, "question", str(q_order))
            rows["questions"].append(
                {
                    "id": question_id,
                    "question_set_id": set_id,
                    "question_type": "SINGLE_CHOICE",
                    "content": content,
                    "difficulty_level": DIFFICULTY[level],
                    "skill_type": "READING",
                    "question_category": CATEGORY[part_index],
                    "order_no": q_order,
                    "max_raw_score": POINT,
                    "explanation": explanation,
                    "metadata": json.dumps({"cefr": level}),
                }
            )
            for o_order, option in enumerate(options):
                rows["options"].append(
                    {
                        "id": uid(question_id, str(o_order)),
                        "question_id": question_id,
                        "content": option,
                        "order_no": o_order + 1,
                        "is_correct": o_order == correct,
                        "explanation": None,
                    }
                )
    total = POINT * len(rows["questions"])
    rows["section"] = {"id": section_id, "exam_id": exam_id, "max_raw_score": total}
    rows["exam"] = {
        "id": exam_id,
        "max_raw_score": total,
        "question_count": len(rows["questions"]),
    }
    return rows


def check(rows: dict) -> list[str]:
    problems = []
    for q in rows["questions"]:
        options = [o for o in rows["options"] if o["question_id"] == q["id"]]
        if len(options) != 4 or sum(o["is_correct"] for o in options) != 1:
            problems.append(
                f"{q['content']!r}: needs 4 options and exactly one correct"
            )
        if len({o["content"] for o in options}) != len(options):
            problems.append(f"{q['content']!r}: duplicate option text")
    return problems


def insert(cur, rows: dict, status: str, author_sql: str) -> bool:
    exam = rows["exam"]
    cur.execute("select 1 from exams where id = %s", (exam["id"],))
    if cur.fetchone():
        return False
    cur.execute(
        f"""insert into exams (id, title, description, exam_type, certificate_type, certificate_variant,
                        target_level, duration_seconds, max_raw_score, pass_score, status, version_number,
                        created_by_user_id, published_at)
                    values (%s, %s, %s, 'PLACEMENT', null, null, null, 1500, %s, null, %s, 1, ({author_sql}),
                            case when %s = 'PUBLISHED' then now() end)""",
        (
            exam["id"],
            "Bài kiểm tra đầu vào",
            "30 câu ngữ pháp, từ vựng và đọc hiểu từ A1 tới C1, làm trong 25 phút. Kết quả dùng để gợi ý "
            "trình độ bắt đầu.",
            exam["max_raw_score"],
            status,
            status,
        ),
    )
    cur.execute(
        """insert into exam_sections (id, exam_id, section_type, order_no, max_raw_score, is_scored_by_criteria)
                   values (%(id)s, %(exam_id)s, 'READING', 1, %(max_raw_score)s, false)""",
        rows["section"],
    )
    for r in rows["parts"]:
        cur.execute(
            """insert into section_parts (id, exam_section_id, order_no, title, instruction)
                       values (%(id)s, %(exam_section_id)s, %(order_no)s, %(title)s, %(instruction)s)""",
            r,
        )
    for r in rows["sets"]:
        cur.execute(
            """insert into question_sets (id, section_part_id, order_no, content, metadata)
                       values (%(id)s, %(section_part_id)s, %(order_no)s, %(content)s, %(metadata)s::jsonb)""",
            r,
        )
    for r in rows["questions"]:
        cur.execute(
            """insert into questions (id, question_set_id, question_type, content, difficulty_level,
                           skill_type, question_category, order_no, max_raw_score, explanation, metadata)
                       values (%(id)s, %(question_set_id)s, %(question_type)s, %(content)s, %(difficulty_level)s,
                           %(skill_type)s, %(question_category)s, %(order_no)s, %(max_raw_score)s, %(explanation)s,
                           %(metadata)s::jsonb)""",
            r,
        )
    for r in rows["options"]:
        cur.execute(
            """insert into question_options (id, question_id, content, order_no, is_correct, explanation)
                       values (%(id)s, %(question_id)s, %(content)s, %(order_no)s, %(is_correct)s, %(explanation)s)""",
            r,
        )
    return True


def dsn_from_env_file(path: str) -> str:
    """The backend's JDBC settings as a libpq DSN. Nothing read here is printed."""
    import re
    from urllib.parse import quote

    env = {}
    for line in open(path, encoding="utf-8").read().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip('"')
    match = re.match(
        r"jdbc:postgresql://([^:/?]+):?(\d+)?/([^?]+)", env.get("DB_URL", "")
    )
    if not match:
        raise SystemExit("DB_URL trong file .env không phải JDBC PostgreSQL")
    user, password = (
        quote(env.get("DB_USERNAME", ""), safe=""),
        quote(env.get("DB_PASSWORD", ""), safe=""),
    )
    sslmode = "disable" if match.group(1) in ("localhost", "127.0.0.1") else "require"
    return f"postgresql://{user}:{password}@{match.group(1)}:{match.group(2) or 5432}/{match.group(3)}?sslmode={sslmode}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--dsn",
        default=os.environ.get("LOAD_DSN"),
        help="postgres DSN (hoặc biến LOAD_DSN)",
    )
    parser.add_argument(
        "--env-file", help="đọc DB_URL, DB_USERNAME, DB_PASSWORD từ .env của backend"
    )
    parser.add_argument("--schema", default="englow3")
    parser.add_argument("--status", default="DRAFT", choices=["DRAFT", "PUBLISHED"])
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    rows = build()
    problems = check(rows)
    print(
        f"Bài kiểm tra đầu vào: {len(rows['parts'])} part, {rows['exam']['question_count']} câu, "
        f"tối đa {rows['exam']['max_raw_score']} điểm"
    )
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    if args.dry_run:
        return 0
    if args.env_file:
        args.dsn = dsn_from_env_file(args.env_file)
    if not args.dsn:
        print("Thiếu --dsn, --env-file hoặc LOAD_DSN", file=sys.stderr)
        return 2

    import psycopg2

    connection = psycopg2.connect(args.dsn)
    try:
        with connection, connection.cursor() as cur:
            cur.execute(f"set search_path = {args.schema}")
            added = insert(
                cur,
                rows,
                args.status,
                "select id from users where role = 'ADMIN' order by created_at limit 1",
            )
        print("Đã thêm đề." if added else "Đề đã có, bỏ qua.")
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
