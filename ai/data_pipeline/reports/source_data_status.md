# Tình trạng nguồn dữ liệu — cào đủ chưa?

Cập nhật: 2026-08-06. Trả lời câu hỏi "còn cần cào gì nữa không".

---

## Đã đủ ✅

| Nguồn | Có | Cần | Dùng cho |
|---|---:|---:|---|
| Wordlist (CEFR-J + Octanove + NGSL/TSL/BSL/NAWL) | 8 844 mục `(lemma, pos, CEFR)` | 3 000 | Phase 4–5 |
| IPA (CMUdict qua `eng-to-ipa`) | **98.8%** phủ 3 000 từ seed | ~95% | Phase 5 |
| Grammar profile (CEFR-J, map sang EGP) | 499 mẫu câu | ~90 concept | Phase 6 |
| Taxonomy concept | 171 (150 lá) | ≥100 | Toàn bộ |

**Wordlist dư gần 3 lần chỉ tiêu.** Không cần cào thêm.

**IPA:** chỉ 35/3000 từ không tra được, **toàn bộ ở band C1** — chủ yếu là trạng từ
phái sinh (`animatedly`, `chaotically`, `compliantly`) và từ ghép (`cost-effective`).
Suy ra được từ dạng gốc, hoặc gắn `ipa_verified: false` để review. Không phải blocker.

> Phát hiện phụ: seed có `chauffer` — đây là lỗi chính tả của `chauffeur` trong nguồn
> Octanove. Cần lọc ở Phase 5, đừng sinh flashcard cho từ sai chính tả.

---

## Không cào được — phải viết hoặc phải mua ❌

### Passage cho Part 7

Đã thử **Wikinews** (CC BY 2.5, có API chuẩn, lấy được 50 bài mục "Economy and
business"). Kết luận: **không dùng được.**

| Vấn đề | Chi tiết |
|---|---|
| Sai thể loại | Tin tức về chính trị, thể thao, giải trí — không phải văn bản công việc |
| Sai độ dài | Bài mẫu 291 từ; Part 7 single cần 150–250 |
| Sai văn phong | Văn báo chí, không phải email/memo/thông báo nội bộ |

Part 7 cần các thể loại: `email`, `memo`, `notice`, `invoice`, `schedule`,
`advertisement`, `chat_message`, `form`. Đây là **văn bản ngắn nội bộ doanh nghiệp
với tên công ty hư cấu** — thứ không tồn tại dưới dạng corpus mở, và nếu có thật thì
lại vướng bản quyền hoặc quyền riêng tư (vd Enron email corpus là thư thật của người
thật).

**Kết luận: passage Part 7 phải viết tay.** Cào không giải quyết được nút thắt này.

### Audio (Phase 8)

Không cào được — cần TTS engine sinh ra. Blocker **B5** chưa chốt.

### Ảnh Part 1 (Phase 8)

6 câu Part 1 cần ảnh mô tả cảnh công sở/đời thường. Ảnh CC0 có (Unsplash, Pexels)
nhưng phải chọn thủ công cho khớp nội dung câu hỏi — cào hàng loạt không dùng được.
Blocker **B6**.

---

## Việc còn lại không phụ thuộc cào

| Phase | Việc | Chặn bởi |
|---|---|---|
| 7 | Part 7 — 54 câu, ~21 passage, mỗi câu cần `evidence_span` | Không gì, chỉ là khối lượng |
| 5 | Flashcard 3000 từ | Không gì |
| 6 | Grammar bank ~90 point × 12 câu | Không gì |
| 8 | Listening | B5 (TTS), B6 (ảnh) |
| 9 | Speaking/Writing | Không gì |
| 10 | Assessment prompt | Không gì |
