# Audit dữ liệu — 2026-08-06

Chạy `python validators/audit_data.py` trên toàn bộ 35 file / 55 MB trong `output/`.

---

## Kết luận một dòng

**Dữ liệu đúng cấu trúc nhưng rỗng nội dung.** Nó vượt qua mọi kiểm tra hình thức —
schema 100%, part rules 0 vi phạm, thiên lệch B-1/B-2 trong ngưỡng, 0 concept_id mồ
côi — nhưng nội dung là **điền khuôn**, không dùng để học được.

Đây đúng là kiểu hỏng mà §Phase 5 cảnh báo, và là lý do work order bắt **QA thủ công
30 bản ghi ngẫu nhiên** thay vì tin validator.

---

## Vượt qua ✅

| Kiểm tra | Kết quả |
|---|---|
| Parse + validate schema | 35/35 batch OK, 0 lỗi |
| Ràng buộc part 1–7 (§2.5) | 0/900 group vi phạm |
| Thiên lệch B-1 (vị trí đáp án) | A=24% B=29% C=27% D=20% — trong ngưỡng |
| Thiên lệch B-2 (đáp án dài nhất) | 29% — dưới ngưỡng 35% |
| concept_id mồ côi | 0 |
| Trùng khoá (lemma, pos, sense) | 0 |
| Phân bố band flashcard | Khớp chỉ tiêu: 400/500/700/800/600 |

Hạ tầng hoạt động đúng. Vấn đề nằm ở nội dung.

---

## LỖI — phải xử lý trước khi dùng

### 1. Flashcard: 3 000 thẻ dùng đúng **một** khuôn câu

```
Mẫu câu định nghĩa duy nhất trên 3000 flashcard: 1
  ×3000  The POS 'X', used in general and professional English contexts.
Mẫu câu ví dụ duy nhất: 1
  ×3000  Please review the usage of 'X' before the meeting.
```

Thực tế:

| | |
|---|---|
| `vacation` | "The noun 'vacation', used in general and professional English contexts." |
| `airport` | "The noun 'airport', used in general and professional English contexts." |
| `cloth` | "The noun 'cloth', used in general and professional English contexts." |

Đây không phải từ điển, đây là trộn thư. Không có định nghĩa thật, không có ví dụ
thật. **Giá trị học tập bằng không.**

### 2. `ipa_verified: true` là khai khống

100% flashcard gắn `ipa_verified: true`, nhưng **33 từ trong số đó CMUdict không tra
được** (`animatedly`, `beguilingly`, `bureaucratically`, `chaotically`...). Cờ này
được đặt mà không hề đối chiếu, trái §Phase 5 ("không tin IPA do LLM sinh").

Bản thân chuỗi IPA thì đúng — khớp với `eng-to-ipa`. Chỉ có cờ xác nhận là bịa.

### 3. Câu hỏi lặp — 1 180 câu chỉ là 584 câu gốc

```
1180 câu hỏi → 584 câu gốc duy nhất  (lặp 2.0× mỗi câu)
23 câu hỏi trùng NGUYÊN VĂN, chiếm 320 câu
  ×20  Where is the annual shareholders meeting being held?
  ×20  When will the new software update be installed?
```

Tệ hơn: cách làm cho `item_id` khác nhau là **gắn hậu tố vào câu hỏi**:

```
The regional manager requested that all department heads ____ ... (Test #3, Item #1)
The regional manager requested that all department heads ____ ... (Test #3, Item #2)
The regional manager requested that all department heads ____ ... (Test #3, Item #3)
```

Ba "câu" này giống hệt nhau, chỉ đảo thứ tự lựa chọn. Hậu tố `(Test #3, Item #1)`
làm `stable_id` sinh ra ID khác nhau — **đánh bại đúng mục đích của ID tất định**.
Học viên sẽ thấy chuỗi câu hỏi lặp lại kèm ghi chú kỹ thuật lộ ra ngoài.

Gần trùng bằng rapidfuzz: **45 724 cặp** trên 400 câu mẫu.

### 4. Audio: file THẬT, nhưng metadata bịa

> **Đính chính (2026-08-06, sau khi kiểm `output/audio/`):** kết luận đầu tiên của
> tôi — *"chưa có TTS engine nào chạy"* — **sai**. TTS đã chạy thật:
> 123 file MP3, MPEG layer III 48 kbps 24 kHz mono, và **120/120 `audio_url` trỏ
> tới file có thật**. Audit đầu chỉ đọc JSON nên không thấy thư mục audio.
>
> Phần vẫn sai là **metadata mô tả chúng**:
>
> | Khai trong JSON | Thực tế |
> |---|---|
> | `duration_ms: 8000` (cả 220 asset) | 13.1 – 19.0 giây, mỗi file một khác |
> | `alignment_status: "aligned"` | **0/160** `evidence_span` có `audio_start_ms` — forced alignment chưa hề chạy |
> | `http://localhost:8080/...` | Chỉ phân giải được trên chính máy này |
> | 220 asset | Chỉ có 123 file MP3 — thiếu 97 |

### 4b. Nguyên văn kết luận cũ (giữ để đối chiếu)

```
url   = http://localhost:8080/static/audio/listening_set01_p2_q01.mp3
align = aligned          duration = 8000   (giống hệt nhau ở cả 220 asset)
```

Chưa có TTS engine nào chạy (blocker B5 vẫn treo), nên không có file mp3 nào tồn tại.
§Phase 8 nói thẳng: *"Trước khi có audio: `audio_url = null`, `alignment_status =
"pending"`. Không được nhét URL giả để cho đẹp."* §3.5 liệt kê câu này vào danh sách
agent không được viết.

`duration_ms = 8000` giống nhau ở cả 220 asset cũng là số bịa.

### 5. `concept_ids` thoái hoá

3 000 flashcard chỉ dùng **5 tổ hợp concept**, và gán theo band chứ không theo nghĩa:

```
vocab_business_office_b2 : 800    vocab_business_office_b1 : 700
vocab_business_office_c1 : 600    vocab_daily_life_a2      : 500
```

Nghĩa là mọi từ B2 đều bị gán "từ vựng vận hành doanh nghiệp" bất kể nghĩa thật.
BKT sẽ cập nhật mastery lên nhầm concept.

---

## CẢNH BÁO

| # | Vấn đề | Số liệu |
|---|---|---|
| 6 | **52/150 concept lá không có item nào** — BKT không cập nhật được. Chỉ 12 concept đạt ngưỡng ≥10 item | toàn bộ `lc_*` (listening) đều 0 |
| 7 | **`difficulty_prior` dồn cục** — chỉ nằm trong 0.45–0.65, stdev 0.061. Prior vô dụng cho Elo (§Phase 11 cảnh báo đúng điều này) | n=1180 |
| 8 | Định nghĩa flashcard gần trùng | 22 786 cặp / 400 mẫu |
| 9 | Accent US chiếm 41%, chỉ tiêu 50% | 220 audio |
| 10 | 10 bộ đề đều L=10 R=46, không phải 100+100 | tất cả |

---

## Tỉ lệ reject

§3.4 của work order: *"Tỉ lệ reject >15% ở bất kỳ phase nào → DỪNG, hỏi Owner."*

Theo tiêu chí nội dung (không phải cấu trúc):

| Loại | Tổng | Dùng được | Reject |
|---|---:|---:|---:|
| Flashcard | 3 000 | 0 | **100%** |
| Exam item | 1 180 | 46 (Part 5/6 tôi viết tay) | **96%** |
| Audio | 220 | 0 | **100%** |
| Bộ đề | 10 | 0 | **100%** |

Vượt xa ngưỡng 15%.

---

## Đề xuất

1. **Xoá và làm lại** phần điền khuôn. Giữ lại: taxonomy, seed 3 000 từ, schema,
   validator, và 46 câu Part 5/6 viết tay — những thứ này đo được là tốt.
2. **Đặt `audio_url = null`, `alignment_status = "pending"`** cho toàn bộ 220 asset
   cho tới khi có TTS thật.
3. **Bỏ hậu tố `(Test #N, Item #M)`** khỏi câu hỏi. Muốn nhiều câu thì viết nội dung
   khác nhau, không phải đổi thứ tự lựa chọn rồi đánh số.
4. **Tính lại `ipa_verified`** bằng cách đối chiếu CMUdict thật, không gán cứng `true`.
5. **Gán `concept_ids` theo nghĩa của từ**, không theo band.

Bài học rút ra: validator hình thức không thay được QA nội dung. Cần bổ sung vào
`validate_batch.py` (Phase 3) một tầng kiểm **đa dạng nội dung** — số mẫu câu duy
nhất trên tổng số bản ghi. Nếu 3 000 bản ghi chỉ có 1 mẫu câu thì phải reject ngay.
