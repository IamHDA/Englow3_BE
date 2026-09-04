# Cấu trúc bộ đề

Thiết kế cho Phase 7 (Reading) và Phase 8 (Listening). Chốt trước khi sinh câu nào,
vì đổi sau khi đã có dữ liệu sẽ phải regenerate (work order §3.4).

---

## 1. Nguyên tắc: ngân hàng câu tách khỏi bộ đề

Work order §2.4 và lỗi P1-12 nói rõ: `item_id` là khoá **ổn định, tái sử dụng qua
nhiều đề**; `position` là vị trí **trong một đề cụ thể**, không phải khoá.

Nên **không** lưu mỗi bộ đề thành một bản sao câu hỏi. Một câu nằm ở 3 bộ đề mà lưu
3 lần thì sửa 1 chỗ là lệch 2 chỗ, và `stable_id` mất luôn ý nghĩa idempotent.

```
NGÂN HÀNG (bank/)          nguồn chân lý của nội dung câu hỏi
       ↑ tham chiếu bằng item_id / group_id
BỘ ĐỀ (sets/)              chỉ là thứ tự + vị trí, không chứa nội dung
```

Một câu bị sửa → sửa ở bank, mọi bộ đề tham chiếu nó tự đúng theo.
Một câu bị loại → validator báo mọi bộ đề đang trỏ tới nó.

## 2. Cấu trúc thư mục

```
output/exams/
├── bank/                                  ngân hàng câu hỏi
│   ├── reading/
│   │   ├── exam_reading_part5_001.json    30 ExamItem độc lập (1 câu / group)
│   │   ├── exam_reading_part6_001.json    4 ExamGroup × 4 câu
│   │   └── exam_reading_part7_001.json    ExamGroup single/double/triple
│   └── listening/
│       ├── exam_listening_part1_001.json  6 ExamGroup × 1 câu
│       ├── exam_listening_part2_001.json  25 ExamGroup × 1 câu
│       ├── exam_listening_part3_001.json  13 ExamGroup × 3 câu
│       └── exam_listening_part4_001.json  10 ExamGroup × 3 câu
└── sets/
    ├── set_001.json                       manifest, KHÔNG chứa nội dung câu
    ├── set_002.json
    └── set_003.json
```

Tên file trong `bank/` giữ đúng quy ước §3.3 của work order
(`<module>_<part>_<seq>.json`). Thư mục `bank/` và `sets/` là bổ sung, cần Owner
xác nhận vì §3.3 không nói tới hai tầng này.

## 3. Thành phần một bộ đề đầy đủ — 200 câu

| Part | Kỹ năng | Câu | Group | Câu/group | Đáp án | Cần |
|---|---|---:|---:|---|---:|---|
| 1 | L | 6 | 6 | 1 | 4 | ảnh + audio |
| 2 | L | 25 | 25 | 1 | **3** | audio |
| 3 | L | 39 | 13 | 3 | 4 | audio, 2–3 giọng |
| 4 | L | 30 | 10 | 3 | 4 | audio, 1 giọng |
| | **Listening** | **100** | **54** | | | |
| 5 | R | 30 | 30 | 1 | 4 | — |
| 6 | R | 16 | 4 | 4 | 4 | 1 passage/group |
| 7 | R | 54 | 15 | 2–5 | 4 | 1–3 passage/group |
| | **Reading** | **100** | **49** | | | |
| | **TỔNG** | **200** | **103** | | | |

Part 7 chia nhỏ theo work order Phase 7:

| Dạng | Câu | Group | Passage/group |
|---|---:|---:|---|
| Single passage | 29 | 9 | 1 |
| Double passage | 10 | 3 | 2 |
| Triple passage | 15 | 3 | 3 |

## 4. Manifest của bộ đề

`sets/set_001.json` — chỉ tham chiếu, không lặp nội dung:

```json
{
  "set_id": "set_001",
  "title": "Đề luyện theo định dạng TOEIC số 1",
  "schema_version": "1.0.0",
  "is_ai_generated": true,
  "generated_by": "<model + version>",
  "generated_at": "<UTC ISO-8601>",
  "review_status": "draft",
  "total_questions": 200,
  "sections": [
    {
      "section": "listening",
      "parts": [
        {
          "part_number": 1,
          "group_refs": [
            {"group_id": "grp_<hash>", "position_start": 1, "position_end": 1}
          ]
        }
      ]
    }
  ]
}
```

`title` dùng "Đề luyện theo định dạng TOEIC" theo §0.7 — TOEIC® là nhãn hiệu ETS,
không được dùng "TOEIC Practice Test" trần.

`total_questions` do **pipeline đếm lại** từ bank, không phải LLM khai (lỗi P0-5).
Lệch là reject.

## 5. Cần bao nhiêu bộ đề

Đây là lời giải cho blocker **B7** (grammar chỉ đạt ~3.2 item/concept, cần 10–30).

| Domain | Concept lá | Item/bộ đề | Cần cho 10 item/concept | Số bộ đề |
|---|---:|---:|---:|---:|
| reading (`rc_*`) | 9 | 54 (Part 7) | 90 | **2** |
| listening (`lc_*`) | 11 | 100 | 110 | **2** |
| grammar (`gram_*`) | 90 | 46 (Part 5+6) | 900 | **20** ❌ |

20 bộ đề là không khả thi. Nên grammar **không** giải bằng bộ đề mà giải bằng
Phase 6:

| Nguồn | Công thức | Item grammar |
|---|---|---:|
| Phase 6 `quick_exercises` | 90 concept × 12 câu | 1080 |
| 3 bộ đề Part 5+6 | 3 × 46 | 138 |
| | **Tổng** | **1218** → **13.5 item/concept** ✅ |

**Khuyến nghị: 3 bộ đề đầy đủ + nâng `quick_exercises` từ 5 lên 12 câu/point, và mở
grammar syllabus từ B1–C1 ra A1–C1.** Work order Phase 4 hiện chỉ định syllabus
B1–C1, để hở 41 concept A1–A2 không có item nào.

## 6. Quy tắc dùng chung câu giữa các bộ đề

- Câu **được phép** xuất hiện ở nhiều bộ đề — đó là lý do tách bank khỏi set.
- Nhưng một học viên không nên gặp lại câu cũ trong bộ đề kế tiếp. Chống trùng là
  việc của tầng phục vụ đề lúc chạy, không phải của pipeline dữ liệu.
- Trong **cùng một bộ đề**, `item_id` không được lặp. Validator phải chặn.
- `position` liên tục 1–100 trong mỗi section, không nhảy số, không trùng.

## 7. Điều validator phải kiểm (Phase 3)

- Mọi `group_id` / `item_id` trong manifest tồn tại trong bank
- Số câu đếm lại từ bank khớp `total_questions`
- Đúng số câu mỗi part theo bảng §3
- `position` liên tục và duy nhất trong mỗi section
- Part 2 đúng 3 lựa chọn, các part khác đúng 4 (§2.5)
- Part 7 có 1–3 passage, không được 4 (lỗi P0-3)
- Mọi item Part 7 có `evidence_span` trỏ đúng offset trong passage
- Double/triple passage có ≥2 câu `rc_cross_reference`
- Không có câu nào trong bank bị mồ côi (không bộ đề nào dùng) — cảnh báo, không phải lỗi

## 8. Chưa quyết

| # | Câu hỏi | Chặn |
|---|---|---|
| 1 | Có chấp nhận hai tầng `bank/` + `sets/` không, hay bám đúng §3.3 dạng phẳng? | Phase 7 |
| 2 | 3 bộ đề có đủ không, hay muốn nhiều hơn? | Phase 7 |
| 3 | Nâng `quick_exercises` 5 → 12 và mở syllabus ra A1–C1? (blocker B7) | Phase 4, 6 |
