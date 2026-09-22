# Chạy thử toàn hệ thống bằng tay

Dành cho người lần đầu dựng dự án này ở máy mình và muốn bấm qua hết mọi tính
năng. README nói cách khởi động backend; file này nói **bấm gì, theo thứ tự
nào, và mong đợi thấy gì** — kể cả những chỗ cố tình chưa chạy được.

Ba repo chạy cùng lúc: `Englow3_BE` (Spring, cổng 8080), `Englow3_FE/apps/bff`
(GraphQL, cổng 4000), `Englow3_FE/apps/web` (Next.js, cổng 3000).

---

## 1. Dựng hạ tầng

```bash
cd Englow3_BE
cp .env.example .env        # sửa phần Supabase theo dự án của bạn
docker compose up -d
```

Lệnh trên dựng PostgreSQL (5432), MinIO (9000, console 9001) và `ai_service`
(8001). `minio-init` tạo sẵn 6 bucket. Kiểm tra:

```bash
docker ps --format "{{.Names}}\t{{.Status}}"
```

Bốn container phải ở trạng thái `Up`. Nếu `minio-init` đã `Exited (0)` thì
đúng — nó chỉ chạy một lần rồi thoát.

> **Supabase**: dự án dùng Supabase thật cho xác thực, không có bản local. Cần
> một project Supabase (gói free đủ) và điền `SUPABASE_JWKS_URI`,
> `SUPABASE_ISSUER_URI` vào `.env` của BE, cùng `NEXT_PUBLIC_SUPABASE_URL` và
> `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` vào `apps/web/.env.local`.

## 2. Chạy ba ứng dụng

```bash
# Terminal 1 - backend
cd Englow3_BE && mvn spring-boot:run

# Terminal 2 - BFF
cd Englow3_FE && pnpm dev:bff

# Terminal 3 - web
cd Englow3_FE && pnpm dev:web
```

Kiểm tra nhanh trước khi mở trình duyệt:

```bash
curl -s http://localhost:8080/actuator/health          # {"status":"UP"}
curl -s -X POST http://localhost:4000/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{__typename}"}'                        # {"data":{"__typename":"Query"}}
```

## 3. Tạo tài khoản và phân quyền

Đăng ký một tài khoản qua giao diện web (`http://localhost:3000`). Lần đăng
nhập đầu tiên tạo dòng trong `englow3.users` với vai trò `LEARNER`.

Để có tài khoản quản trị, đổi vai trò thẳng trong cơ sở dữ liệu:

```sql
update englow3.users set role = 'ADMIN' where email = 'ban@example.com';
```

Trigger `trg_users_sync_role_to_auth` tự đẩy vai trò sang
`auth.users.raw_app_meta_data`, nơi Supabase đúc vào access token. **Phải đăng
xuất rồi đăng nhập lại** — token cũ vẫn mang vai trò cũ cho tới khi hết hạn.

Nên tạo ba tài khoản để thử đủ các nhánh quyền:

| Vai trò | Thấy gì |
|---|---|
| `LEARNER` | Chỉ phần học. Vào `/admin/*` bị từ chối. |
| `STAFF` | Soạn nội dung, gửi duyệt. Không thấy nút Duyệt / Phát hành. |
| `ADMIN` | Thấy và làm được tất cả. |

## 4. Nạp nội dung mẫu

Cơ sở dữ liệu mới trống trơn, nên mọi màn học đều rỗng. Sau khi backend đã chạy
ít nhất một lần (để Flyway tạo bảng):

```bash
psql "postgresql://postgres:postgres@localhost:5432/postgres" \
  -f docker/local-content-fixture.sql
```

Script yêu cầu đã có ít nhất một tài khoản — nếu chưa, nó báo lỗi rõ ràng thay
vì tự tạo một user mà Supabase không biết. Chạy lại nhiều lần không sao.

Nội dung nạp vào: 1 bộ 8 thẻ từ, 1 bài trắc nghiệm đủ 5 dạng câu hỏi, 1 bài
nghe chép 4 câu, 2 câu luyện nói. Mười đề thi có sẵn từ data pipeline.

---

## 5. Đi qua từng tính năng

### 5.1 Onboarding

Tài khoản mới vào thẳng luồng onboarding. Đi hết 5 bước: mục đích học → chứng
chỉ nhắm tới → trình độ hiện tại → mục tiêu → kỹ năng muốn cải thiện.

Điểm đáng thử: ở bước trình độ, chọn **"Tôi không biết"**. Hệ thống sẽ dẫn sang
đề xếp trình độ thay vì bắt bạn tự khai một con số không có cơ sở.

### 5.2 Thi thử — `/exams`

Mở một đề, bấm bắt đầu. Trong lúc làm:

- **Đồng hồ đếm lùi từ hạn nộp do server cấp**, không phải từ thời lượng đề.
  Thử đóng tab rồi mở lại — thời gian còn lại phải đúng, không reset.
- Xem network tab: đề trả về **không có đáp án đúng**. Đáp án chỉ xuất hiện
  sau khi nộp.
- Nộp bài. Thẻ đề ở danh sách giờ hiện điểm phần trăm cao nhất và trạng thái
  "Đã hoàn thành".

### 5.3 Thẻ từ — `/study/flashcards`

Mở bộ thẻ, bấm học. Chấm từng thẻ bằng 4 nút (Again / Hard / Good / Easy).

Thử: chấm một thẻ **Again**, thoát ra, vào lại — thẻ đó phải quay lại ngay
(lịch SM-2 đẩy nó về sau 10 phút). Chấm **Easy** thì nó biến mất khỏi hàng đợi.

Tab **Thống kê** hiện số thẻ đã học, tỷ lệ nhớ, chuỗi ngày — tất cả từ dữ liệu
thật, nên lần đầu vào sẽ là số 0, không phải số đẹp.

### 5.4 Trắc nghiệm — `/study/quiz`

Bài mẫu có đủ 5 dạng: trắc nghiệm, điền từ, viết lại, sắp xếp, nối vế.

Đáng thử nhất là **nối vế**: các vế bên phải được xáo trộn bằng seed lấy từ
lượt làm bài. Tải lại trang — thứ tự phải **giữ nguyên**, không xáo lại (xáo
lại là phát cho người học một câu đố khác).

Nộp bài xem điểm và giải thích từng câu.

### 5.5 Nghe chép — `/study/dictation`

> **Chưa chạy được**: fixture chỉ ghi khoá file âm thanh, chưa có file thật.
> Xem mục 6.

Vẫn xem được: danh sách bài, tiến độ từng bài, và màn **ôn câu hay sai**
(`.../review`) — màn này liệt kê những câu bạn chấm thấp nhất trên toàn bộ bài
học, kèm câu bạn gõ lần trước.

### 5.6 Luyện nói — `/study/pronunciation`

> **Chấm điểm chưa chạy được**: cần khoá Azure Speech. Xem mục 6.

Phần chạy được không cần khoá:

- Bấm **Nghe câu mẫu** — giọng tổng hợp của trình duyệt đọc câu.
- Bấm **Bắt đầu ghi âm**, nói, rồi **Dừng**. Trình duyệt xin quyền micro.
- Nghe lại bản vừa ghi ngay tại chỗ.
- Bản ghi được đóng gói WAV 16 kHz trong trình duyệt và tải thẳng lên MinIO
  qua URL ký sẵn. Mở console MinIO (`http://localhost:9001`, `minioadmin` /
  `minioadmin`) → bucket `speaking` → thấy file của bạn.

Sau đó màn hình quay chờ rồi báo hết giờ, vì không có worker nào chấm.

### 5.7 Lộ trình hằng ngày — `/study/daily-path`

Màn này tổng hợp mọi thứ bạn vừa làm: chuỗi ngày học, điểm kinh nghiệm, các
việc còn dở.

Đáng thử: làm vài thẻ từ rồi quay lại — **trạm "Ôn thẻ từ" phải cập nhật số
thẻ còn lại**. Nếu bạn học hết thẻ đến hạn, trạm đó chuyển thành "đã xong".

Mục tiêu trong ngày cũng là số thật: "Trả hết thẻ đến hạn" hiện `3/12`, không
phải một con số cố định.

### 5.8 Quản trị đề thi — `/admin/exams`

Đăng nhập bằng tài khoản `STAFF`:

1. Thấy danh sách đầy đủ, kể cả bản nháp.
2. Ở một đề nháp, chỉ có nút **Gửi duyệt** — không có Duyệt, Phát hành, Lưu trữ.
3. Bấm Gửi duyệt → trạng thái chuyển "Chờ duyệt".

Đổi sang tài khoản `ADMIN` (nhớ đăng nhập lại):

4. Đề đó giờ có nút **Duyệt** và **Trả lại**.
5. Bấm **Trả lại** → hộp thoại bắt nhập lý do. Bỏ trống thì nút bị khoá; nếu
   gọi thẳng API mà để trống, backend cũng từ chối.
6. Nhập lý do, xác nhận. Đăng nhập lại bằng `STAFF` — **lý do hiện ngay trên
   dòng của đề đó**, không phải mở trang chi tiết mới thấy.
7. `STAFF` sửa rồi gửi duyệt lại. `ADMIN` bấm **Duyệt** → đề được phát hành
   trong cùng một bước.

### 5.9 Quản trị nội dung học — `/admin/content`

Cùng quy trình, cho 4 loại: bộ thẻ từ, bài trắc nghiệm, bài nghe chép, câu
luyện nói. Chuyển loại bằng thanh tab.

Chú ý cột **Nội dung**: câu luyện nói hiện dấu gạch ngang thay vì một con số —
nó là một câu, không phải một tập hợp, nên "1 mục" sẽ là sự thật vô nghĩa.

### 5.10 Thử vượt quyền

Bằng tài khoản `LEARNER`, mở `http://localhost:3000/admin/exams`. Phải thấy
thông báo không có quyền, không phải danh sách rỗng.

Gọi thẳng API để chắc chắn chặn ở backend chứ không chỉ ẩn nút:

```bash
curl -i http://localhost:8080/api/admin/exams \
  -H "Authorization: Bearer <token của LEARNER>"
# 403 ACCESS_DENIED
```

Lấy token: mở DevTools → Application → Cookies → cookie `...-auth-token`.

---

## 6. Những gì chưa chạy được, và vì sao

**Âm thanh nghe chép.** Fixture chỉ ghi khoá file; chưa có file thật. Để chạy:

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
mc cp cau-1.wav local/learning/dictation/airport/1.wav
# ... lặp cho 2.wav, 3.wav, 4.wav
```

**Chấm điểm phát âm.** Cần khoá Azure Speech. Sau khi có:

```bash
# Englow3_BE/.env
AI_ENABLED=true
AI_SERVICE_INTERNAL_API_KEY=<chuỗi ngẫu nhiên dài>

# ai_service/.env  (mọi biến đều có tiền tố AI_SERVICE_)
AI_SERVICE_SPEECH_ENABLED=true
AI_SERVICE_AZURE_SPEECH_API_KEY=<khoá>
AI_SERVICE_AZURE_SPEECH_BASE_URL=https://<vùng>.cognitiveservices.azure.com
AI_SERVICE_INTERNAL_API_KEY=<đúng chuỗi ở trên>
```

`AI_ENABLED=true` là thứ bật worker; khi tắt, bản ghi vẫn tải lên được nhưng
không có gì chấm. Đường ống đã viết xong và có test cho mọi quyết định nó đưa
ra dựa trên câu trả lời của provider — nhưng **chưa từng gọi provider thật**.

**Phát âm thẻ từ.** Cùng lý do với nghe chép: chưa có file trong bucket
`learning`.

---

## 7. Chạy toàn bộ kiểm thử

```bash
cd Englow3_BE && mvn clean test          # 240 test
cd Englow3_FE && pnpm test               # 75 (bff) + 47 (web)
```

`mvn clean test`, không phải `mvn test` — bản build tăng dần từng báo xanh
trong khi build sạch thì đỏ.

## 8. Kiểm tra security header

```bash
cd Englow3_FE/apps/web && pnpm build && pnpm start
curl -s -I http://localhost:3000/ | grep -iE "content-security|x-frame|x-content|referrer|permissions|strict-transport"
```

Phải thấy đủ 6 dòng. Header cũng áp cho file tĩnh — thử với
`/favicon.ico`.
