# Chuẩn chất lượng bộ đề

Mục tiêu: học viên làm xong thấy **giống thi thật**, không phải làm bài tập.
Nội dung viết mới hoàn toàn (§0.4 cấm sao chép đề ETS thật) nhưng độ khó, độ tự nhiên
và cách gài bẫy phải ngang đề thật.

Áp dụng ở Phase 7 (Reading) và Phase 8 (Listening).

---

## 1. Distractor là chỗ quyết định, không phải passage

Đề dở lộ ra ở đáp án sai chứ không ở đoạn văn. Ba lỗi làm đề mất giá trị ngay:

| Lỗi | Ví dụ | Vì sao hỏng |
|---|---|---|
| Distractor vô nghĩa | Hỏi giờ họp, đáp án sai là "a blue elephant" | Loại được không cần đọc |
| Distractor sai hiển nhiên | Ba đáp án cùng nghĩa, một cái lạc hẳn | Đoán được bằng loại trừ |
| Distractor không có lý do sai | Sai chỉ vì "không đúng" | Không dạy được gì khi review |

**Yêu cầu:** mỗi distractor phải nhắm vào **một hiểu lầm cụ thể** và ghi rõ trong
`rationale_vi`. Các kiểu bẫy thật của TOEIC:

- Đúng từ vựng, sai thì (nhìn thấy "will" trong bài, nhưng bài hỏi việc đã xong)
- Đúng thông tin, sai người/phòng ban (bài nhắc hai người, đáp án gán nhầm)
- Lặp nguyên văn từ trong bài nhưng lệch ý (bẫy word-matching)
- Đúng về mặt suy luận đời thường, nhưng bài không nói (bẫy ngoài văn bản)

## 2. Đáp án đúng không được lặp nguyên văn

Đề thật hầu như luôn diễn đạt lại. Đáp án trùng khớp từ ngữ với passage thì học viên
quét từ khoá là xong, không cần hiểu.

**Kiểm bằng code:** độ trùng lặp từ giữa đáp án đúng và câu chứa bằng chứng phải
**thấp hơn** trung bình của các distractor. Cao hơn → cảnh báo, vì đó là bẫy ngược.

## 3. `evidence_span` là bắt buộc, tính bằng code

Mỗi câu Part 7 phải trỏ được vào đúng đoạn ký tự chứa đáp án, tính bằng
**string-match trong code** chứ không để LLM tự khai offset — nó sẽ khai sai.

**Không định vị được câu chứa đáp án nghĩa là câu hỏi không hợp lệ**, không phải
"câu hỏi khó". Đây là lưới chắn mạnh nhất chống câu hỏi mơ hồ.

## 4. Ba loại thiên lệch thống kê — bổ sung ngoài work order

Đây là chỗ đề tự sinh hay lộ nhất, và work order chưa nhắc. Đề thật kiểm soát cả ba;
đề sinh máy nếu không kiểm sẽ dính hết.

| # | Thiên lệch | Vì sao xảy ra | Ngưỡng chặn |
|---|---|---|---|
| B-1 | **Vị trí đáp án** dồn vào B/C | LLM có thiên hướng đặt đáp án đúng ở giữa | Mỗi nhãn A–D chiếm 20–30% trên toàn bộ đề |
| B-2 | **Đáp án đúng dài nhất** | Đáp án đúng cần đủ chữ để chính xác; distractor viết cho xong | Tỉ lệ "đáp án đúng là lựa chọn dài nhất" ≤ 35% (ngẫu nhiên là 25%) |
| B-3 | **Trùng từ với passage cao nhất** | Xem mục 2 | Tỉ lệ "đáp án đúng trùng từ nhiều nhất" ≤ 35% |

Học viên luyện nhiều sẽ **học được** ba mẹo này và ăn điểm mà không cần đọc — lúc đó
bộ đề vừa vô dụng để luyện, vừa cho điểm ảo cao hơn thực lực.

Ba kiểm tra này phải nằm trong `validators/`, chạy trên **cả bộ đề** chứ không phải
từng câu, vì đó là thống kê phân bố.

## 5. Passage phải giống văn bản công việc thật

- Bối cảnh business trung tính; tên công ty và tên người **hư cấu** (§Phase 7)
- Độ dài: Part 6 ~120–160 từ · Part 7 single ~150–250 · mỗi passage của multi ~100–180
- Đúng thể loại: email có dòng tiêu đề và chữ ký, thông báo có ngày và người nhận,
  hoá đơn có mã và số tiền
- Double/triple passage: ≥2 câu bắt buộc đọc chéo ≥2 văn bản (`rc_cross_reference`)

## 6. Sinh hai bước, không gộp

1. Sinh passage (1 lần gọi)
2. Sinh câu hỏi cho passage đó (lần gọi **riêng**, truyền passage vào context)

Gộp một lần gọi thì LLM viết passage sao cho vừa với câu hỏi nó đã nghĩ sẵn — ra đoạn
văn gượng, thông tin nhồi vào chỉ để hỏi được. Tách ra thì passage phải đứng vững một
mình trước, giống văn bản thật.

## 7. Điều KHÔNG thể làm giả: độ khó thật

`difficulty_prior` do LLM gán chỉ là phỏng đoán. Độ khó thật chỉ biết được khi có
người làm bài.

Vì vậy `irt_params.calibration_status` khởi tạo là `"uncalibrated"` và **không được**
khai là đã hiệu chuẩn. Lộ trình:

```
uncalibrated  →  provisional (≥30 lượt trả lời)  →  calibrated (≥200 lượt)
```

Cho tới khi có người học thật, mọi con số độ khó chỉ là ước lượng — báo cáo đúng như
vậy, không tô hồng.

## 8. QA thủ công vẫn bắt buộc

Máy kiểm được định dạng, thống kê, và offset. Máy **không** kiểm được câu hỏi có tự
nhiên không, bẫy có công bằng không, passage đọc có gượng không.

GATE 7 in 15 câu ngẫu nhiên cho Owner đọc. Không đạt → sửa prompt và **chạy lại toàn
bộ**, không vá lẻ (§Phase 5 áp dụng tương tự cho Phase 7).
