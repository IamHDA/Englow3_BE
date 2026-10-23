# Runbook vận hành production — Englow3

**Cập nhật:** 2026-10-23 · Áp dụng cho BE (Render), FE web + BFF (Vercel), DB/Auth/Storage (Supabase), AI (Groq qua `ai_service`).

Tài liệu này là các việc **vận hành** phải làm trước khi mở cho người dùng thật. Mỗi mục có: hiện trạng (có bằng chứng trong repo), các bước làm, và cách kiểm tra đã xong. Phần nào là việc trên bảng điều khiển của nhà cung cấp thì ghi rõ — code không làm thay được. Giao diện và gói của nhà cung cấp thay đổi theo thời gian: đối chiếu tên menu với trang hiện tại trước khi làm.

> **Quy tắc bất biến:** không đẩy bản sao lưu, dump hay dữ liệu xuất ra GitHub — kể cả dưới dạng artifact của CI. Mọi bản sao lưu đi vào kho lưu trữ riêng tư ngoài GitHub.

Mục lục: [2.1 Backup](#21-backup-và-khôi-phục) · [2.2 Hosting](#22-hosting-luôn-bật) · [2.3 Giám sát](#23-giám-sát-và-cảnh-báo) · [2.4 Tách môi trường](#24-tách-môi-trường) · [2.5 Chi phí AI](#25-kiểm-soát-chi-phí-ai) · [Checklist trước ngày mở cửa](#checklist-trước-ngày-mở-cửa)

---

## 2.1 Backup và khôi phục

**Hiện trạng.** `docs/decisions.md` ghi: dữ liệu không có bản sao trên remote, cần cơ chế backup riêng. Chưa có script, lịch chạy hay buổi thử khôi phục nào trong repo.

**Cần bảo vệ ba thứ khác nhau** — bản sao lưu của Supabase chỉ lo thứ đầu:

| Thứ | Chứa gì | Mất thì sao |
|---|---|---|
| Cơ sở dữ liệu Postgres (schema `englow3`) | tài khoản, kết quả thi, tiến độ, gói dịch vụ | mất lịch sử học của mọi người |
| Đối tượng trong Storage (bucket `avatars`, `exams`, `learning`, `speaking`) | ảnh, audio đề thi, bản ghi âm người học | đề thi nghe không còn tiếng; mất bản ghi âm |
| Tài khoản đăng nhập (`auth.users`) | email, mật khẩu băm | không ai đăng nhập được |

Mục tiêu đề xuất: **RPO ≤ 24 giờ** (mất tối đa một ngày dữ liệu), **RTO ≤ 4 giờ** (dựng lại dịch vụ). Nếu không chấp nhận mất tới 24 giờ kết quả thi, bật PITR (bước 2).

### Bước 1 — Dừng dùng gói không có sao lưu (bảng điều khiển Supabase)
1. Project Settings → Billing: xác nhận project production đang ở gói trả phí. Gói miễn phí không có sao lưu hằng ngày và tạm dừng project khi không hoạt động lâu.
2. Database → Backups: kiểm tra có danh sách bản sao lưu hằng ngày. Ghi lại thời gian lưu giữ.

### Bước 2 — Bật khôi phục tại một thời điểm (PITR) nếu cần RPO nhỏ hơn 24 giờ
Database → Backups → Point in Time Recovery (tính phí thêm). Bật nếu một ngày dữ liệu thi là không chấp nhận được.

### Bước 3 — Sao lưu logic ra ngoài Supabase (phòng khi mất cả project)
Chạy từ máy bạn hoặc một VPS nhỏ, **không** chạy trong GitHub Actions. Cần `pg_dump` bản 16+ và `age` để mã hoá.

```bash
# Chuỗi kết nối chỉ-đọc tới DB production, lấy từ Supabase → Settings → Database.
# Đặt trong biến môi trường của máy chạy, KHÔNG viết vào file trong repo.
export PGURL='postgresql://...'
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
pg_dump "$PGURL" --format=custom --no-owner --schema=englow3 --file="englow3-$STAMP.dump"
age -r "$AGE_PUBLIC_KEY" -o "englow3-$STAMP.dump.age" "englow3-$STAMP.dump"
shred -u "englow3-$STAMP.dump"           # xoá bản chưa mã hoá
# đẩy lên kho riêng (S3-compatible, bật versioning + khoá xoá), ví dụ:
aws s3 cp "englow3-$STAMP.dump.age" "s3://<bucket-sao-luu-rieng>/db/" --endpoint-url <endpoint>
```

- Lịch: mỗi ngày bằng cron (Linux) hoặc Task Scheduler (Windows). Giữ 7 bản ngày, 4 bản tuần, 6 bản tháng.
- Khoá giải mã (`age` private key) cất ở nơi **khác** với bản sao lưu, ví dụ trình quản lý mật khẩu.
- Bucket sao lưu: riêng tư, bật versioning, tài khoản ghi không có quyền xoá.
- `auth.users` nằm ngoài schema `englow3`. Muốn sao lưu cả đăng nhập, thêm `--schema=auth` (bản sao lưu này nhạy cảm hơn nữa: mã hoá bắt buộc).

### Bước 4 — Sao lưu Storage
Sao lưu cơ sở dữ liệu **không** chứa file. Đồng bộ định kỳ bucket sang kho riêng:

```bash
aws s3 sync s3://speaking s3://<bucket-sao-luu-rieng>/storage/speaking/ \
  --endpoint-url <supabase-s3-endpoint>
```
Lặp cho `exams`, `learning`, `avatars`. `exams` và `learning` có thể dựng lại từ `data_pipeline` nên ưu tiên thấp hơn `speaking`.

### Bước 5 — Thử khôi phục (bắt buộc, mỗi quý một lần)
Một bản sao lưu chưa từng khôi phục thử chưa phải là bản sao lưu.
1. Dựng Postgres tạm: `docker run --rm -d -p 55432:5432 -e POSTGRES_PASSWORD=test --name restore-test postgres:16`
2. Giải mã và khôi phục: `age -d -i key.txt englow3-<STAMP>.dump.age > r.dump` rồi `pg_restore --no-owner -d postgresql://postgres:test@localhost:55432/postgres r.dump`
3. So khớp: đếm dòng `users`, `exam_attempts`, `flashcard_reviews` với DB production (truy vấn chỉ-đọc). Lệch nhiều hơn dữ liệu của một ngày là có vấn đề.
4. Chạy BE trỏ vào DB tạm (biến môi trường của **chỉ phiên đó**) để Flyway kiểm `validate` qua mọi migration. **Không** trỏ BE vào production khi thử.
5. Ghi lại thời gian thực hiện: đó là RTO thật.
6. `docker rm -f restore-test`, xoá `r.dump`.

**Xong khi:** có lịch chạy tự động, có ít nhất một bản đã khôi phục thành công, và RTO thật được ghi vào đây.

---

## 2.2 Hosting luôn bật

**Hiện trạng và bằng chứng.**
- BE chạy trên Render. Trước đó đã đo: gói miễn phí ngủ, khởi động lạnh khoảng 2 phút. `Dockerfile` đặt `MaxRAMPercentage=75`, nên kích thước RAM của dịch vụ quyết định bộ nhớ heap.
- Web và BFF chạy trên Vercel. Gói Hobby của Vercel chỉ dành cho sử dụng cá nhân phi thương mại; bán dịch vụ cần gói Pro.
- Code đã sẵn sàng chạy nhiều bản BE: hàng đợi AI dùng `for update skip locked` và khoá tư vấn trong `AiJobQueueImpl`, Flyway tự khoá khi di trú.

### Các bước
1. **Render — BE (`Englow3_BE`)**
   - Settings → Instance Type: chọn gói trả phí **luôn bật** (không ngủ). Chọn RAM tối thiểu 1 GB; Spring Boot trên 512 MB dễ hết bộ nhớ khi tải cao.
   - Health Check Path: `/actuator/health/readiness` (đã mở không cần đăng nhập, xem `SecurityConfig`). Render chỉ chuyển lưu lượng sang bản mới khi đường này trả 200 → deploy không gián đoạn.
   - Auto-Deploy: giữ qua deploy hook như `cd.yml` đang làm.
   - Khi cần chịu tải, đặt số instance = 2. Kiểm tra: gọi `/actuator/info`, `release` phải trùng commit.
2. **Render — `ai_service`:** cùng quy tắc luôn bật. Nó là điểm duy nhất nói chuyện với nhà cung cấp AI; khi nó ngủ, chấm bài sẽ chờ khởi động.
3. **Cùng vùng địa lý:** đặt Render, Supabase và Vercel Functions trong cùng khu vực (hoặc gần nhất có thể). Mỗi lệnh gọi DB qua vùng khác tốn khoảng 100–200 ms, nhân lên theo số truy vấn của mỗi trang.
4. **DB — pooler và kết nối:** dùng chuỗi kết nối pooler (Supavisor, chế độ session) cho BE. Giữ `hikari.maximum-pool-size` × số instance nhỏ hơn giới hạn kết nối của gói Supabase. Thêm `reWriteBatchedInserts=true` vào `DB_URL` (đã bật batch insert trong code; tham số này gom nhiều INSERT thành một lệnh).
5. **Vercel:** nâng lên Pro, đặt `maxDuration` đã có (60 giây) cho các route nặng như nộp bài thi và xuất dữ liệu.
6. **Supabase:** gói trả phí, không tạm dừng, đúng vùng.

**Kiểm tra xong:** sau 30 phút không có lưu lượng, một request tới `/actuator/health/readiness` vẫn trả lời dưới 1 giây (không còn khởi động lạnh). Deploy một commit thử và xác nhận trong lúc deploy các request vẫn thành công.

---

## 2.3 Giám sát và cảnh báo

**Hiện trạng.** Có `/actuator/health`, `/actuator/info`, `/actuator/metrics` (chỉ ADMIN) và log. Chưa có theo dõi lỗi, cảnh báo, hay theo dõi uptime từ bên ngoài. **Đã thêm trong code:** báo lỗi tới Sentry ở BE và BFF, tắt hoàn toàn khi không có `SENTRY_DSN`.

### Bước 1 — Báo lỗi (Sentry)
1. Tạo tài khoản Sentry; tạo 3 project: `englow3-be` (Java/Spring Boot), `englow3-bff` (Node.js), và (tuỳ chọn, chưa có trong code) `englow3-web`.
2. Lấy DSN của từng project.
3. Đặt biến môi trường (không bao giờ dán DSN vào chat hay repo):
   - Render BE: `SENTRY_DSN=<dsn be>`, `SENTRY_ENVIRONMENT=production`
   - Vercel BFF: `SENTRY_DSN=<dsn bff>`, `SENTRY_ENVIRONMENT=production`
4. Deploy lại. Kiểm tra bằng cách gây một lỗi 500 có chủ đích trên **môi trường staging** và xác nhận sự kiện xuất hiện trong Sentry với `release` đúng commit.
5. Cảnh báo (Alerts → Create Alert): (a) issue mới xuất hiện, (b) một issue lặp quá 20 lần trong 1 giờ, (c) tỉ lệ lỗi tăng gấp đôi so với trung bình. Gửi email hoặc Slack tới người trực.

Dữ liệu cá nhân: cấu hình đã đặt `send-default-pii=false` và không gửi nội dung request. Không bật lại. Lỗi 4xx do người dùng gây ra không được báo: ở BE, ngoại lệ đã được `GlobalExceptionHandler` xử lý có chủ đích không tới Sentry, còn lỗi 500 tới Sentry qua dòng log mức `error` mà handler này ghi; ở BFF, `BackendError` có mã 4xx bị lọc.

### Bước 2 — Theo dõi uptime từ bên ngoài
Dùng UptimeRobot hoặc Better Stack (có gói miễn phí), kiểm tra mỗi 1–5 phút:

| Điểm kiểm tra | Mong đợi | Ý nghĩa khi hỏng |
|---|---|---|
| `https://<be>/actuator/health/readiness` | 200 | BE hoặc DB chết |
| `https://<web>/` | 200 | web chết |
| `https://<web>/api/system/status` | 200 | web không nói chuyện được với BFF/BE/AI |
| `https://<web>/pricing` | 200 và có chữ "Pro" | SSR hỏng |

Cảnh báo khi hai lần kiểm tra liên tiếp thất bại. Bật trang trạng thái công khai nếu muốn.

### Bước 3 — Log tập trung
Log của Render và Vercel được giữ ngắn. Tạo log drain tới một dịch vụ log (Better Stack, Datadog…) để tra cứu sau sự cố. BE ghi `traceId` trong mỗi dòng (xem `logging.pattern`) và BFF chuyển nó trong lỗi, nên có thể lần theo một yêu cầu đi qua cả hai.

### Bước 4 — Tài nguyên hạ tầng
- Supabase → Reports: cảnh báo khi CPU > 80%, dung lượng đĩa > 80%, số kết nối > 80% giới hạn.
- Render → Metrics: cảnh báo khi bộ nhớ > 85% hoặc instance khởi động lại.
- Nhà cung cấp AI: bật cảnh báo chi tiêu (xem 2.5).

### Bước 5 — Quy trình khi có sự cố
1. Xác nhận bằng uptime monitor và Sentry. 2. Kiểm tra `/actuator/info` để biết commit đang chạy; nếu lỗi bắt đầu ngay sau deploy, rollback bản trước trên Render. 3. Ghi giờ bắt đầu/kết thúc và nguyên nhân sau khi xong (post-mortem ngắn). 4. Nếu dữ liệu người dùng bị ảnh hưởng, thông báo theo Chính sách bảo mật.

**Xong khi:** có sự kiện thử xuất hiện trong Sentry, có ít nhất 3 monitor chạy, và một cảnh báo thử đã đến được người trực.

---

## 2.4 Tách môi trường

**Hiện trạng và rủi ro.** `testing` đang dùng chung Supabase với dữ liệu thật; hàng đợi AI chỉ tách bằng `AI_QUEUE_SCOPE`. Một migration đẩy lên `testing` chạy ngay trên cùng DB mà production dùng. Với dữ liệu người dùng thật, điều này không chấp nhận được.

### Mô hình đề xuất
| | local | staging (`testing`) | production (`main`) |
|---|---|---|---|
| Supabase | docker/DB riêng | **project riêng** | **project riêng** |
| BE (Render) | máy bạn | service riêng | service riêng |
| Web + BFF (Vercel) | `localhost` | project riêng | project riêng |
| `AI_QUEUE_SCOPE` | `local` | `testing` | `production` |
| Khoá AI / S3 | khoá riêng | khoá riêng | khoá riêng |
| Dữ liệu | giả | giả/đã ẩn danh | thật |

### Các bước
1. **Tạo Supabase project staging** (gói rẻ nhất đủ dùng). Không sao chép dữ liệu người dùng thật vào đó.
2. **Dựng schema:** trỏ một BE staging vào DB mới; Flyway chạy toàn bộ migration (V001…V071). Với `V024`/`V026` cần có `auth.users` của Supabase — project mới đã có sẵn.
3. **Nạp nội dung:** chạy import từ `data_pipeline` vào staging để có đề và học liệu (nội dung không phải dữ liệu người dùng).
4. **Chuyển `testing` sang staging:** cập nhật biến môi trường trên Render/Vercel của nhánh `testing`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SUPABASE_*`, `S3_*`, `STORAGE_*`, `CORS_ALLOWED_ORIGINS`, `AI_QUEUE_SCOPE=testing`, `SENTRY_ENVIRONMENT=staging`.
5. **Production:** tạo service Render và project Vercel riêng cho `main`, với tập biến môi trường **riêng**, khoá **riêng**. Đặt `AI_QUEUE_SCOPE=production` và `AI_WORKER_ENABLED=true` chỉ ở nơi nào cần chạy worker.
6. **Khoá bí mật cho từng môi trường** (không dùng chung, không dán vào chat): khoá Groq, khoá S3, `AI_SERVICE_INTERNAL_API_KEY`, khoá `service_role` của Supabase. Xoay khoá Groq cũ vì từng nằm trong file local (`docs/TODO.md`).
7. **Quy trình lên production:** merge vào `main` → CI → build ảnh → deploy hook Render. Trước khi merge, bản migration phải đã chạy thành công trên staging. Migration đã áp dụng thì **không sửa** (Flyway sẽ báo checksum); sửa bằng migration mới.
8. **CORS:** đặt `CORS_ALLOWED_ORIGINS` đúng tên miền thật của từng môi trường. Bỏ địa chỉ mặc định nội bộ trong `application.yml` khỏi production.

**Kiểm tra xong:** đăng ký một tài khoản trên staging, xác nhận nó **không** xuất hiện trong DB production (truy vấn chỉ-đọc); chạy một migration thử ở staging mà production không đổi.

---

## 2.5 Kiểm soát chi phí AI

**Hiện trạng.** Đã có hạn mức theo từng người học mỗi ngày (`AI_DAILY_REQUEST_LIMIT`, mặc định 100) và hàng đợi giới hạn số việc đang chờ (`AI_MAX_OUTSTANDING_JOBS`, 200). **Đã thêm trong code:**
- Hạn mức theo gói: gói Pro dùng `PLAN_PRO_AI_DAILY_LIMIT` (mặc định 300).
- **Trần toàn nền tảng mỗi ngày** `AI_PLATFORM_DAILY_REQUEST_LIMIT` (mặc định 0 = tắt). Khi tổng số yêu cầu AI trong ngày của môi trường đạt trần, mọi yêu cầu AI mới bị từ chối bằng cùng thông báo "đã hết hạn mức hôm nay" và BE ghi dòng cảnh báo `Platform AI ceiling reached`. Các tính năng không dùng AI vẫn hoạt động bình thường.

### Các bước
1. **Ước tính chi phí một yêu cầu.** Lấy số token trung bình mỗi lần chấm Writing/Speaking/hỏi gia sư (bảng `ai_jobs` có cột đầu vào/đầu ra; xem `Operations.Metrics`) nhân đơn giá của nhà cung cấp.
2. **Đặt trần.** Trần ngày = ngân sách ngày ÷ chi phí mỗi yêu cầu, chừa 20% dự phòng. Ví dụ: ngân sách 5 USD/ngày, 0,01 USD/yêu cầu → trần 400 → đặt `AI_PLATFORM_DAILY_REQUEST_LIMIT=400`.
3. **Kiểm tra hạn mức người dùng tương thích:** `AI_DAILY_REQUEST_LIMIT` × số người dùng hoạt động phải xấp xỉ trần. Nếu 1.000 người dùng mỗi người 100 yêu cầu, trần thấp hơn 100.000 nghĩa là trần sẽ chạm trước.
4. **Cảnh báo chi tiêu ở nhà cung cấp.** Trong bảng điều khiển Groq: đặt giới hạn chi tiêu hàng tháng cứng và cảnh báo ở 50% / 80%. Trần của ứng dụng bảo vệ theo ngày; giới hạn của nhà cung cấp bảo vệ theo tháng. Cần cả hai.
5. **Cảnh báo trong ứng dụng.** Tạo cảnh báo log (Better Stack/Datadog) khi xuất hiện `Platform AI ceiling reached`: đó là tín hiệu để quyết định tăng trần hay điều tra lạm dụng.
6. **Dự phòng khi nhà cung cấp lỗi.** Hiện chỉ có một nhà cung cấp. `app.ai.provider` đã là một điểm cắm; thêm nhà cung cấp thứ hai là việc code trong `ai_service` (chưa làm). Trong lúc chưa có, chấp nhận chấm bài trễ: hàng đợi tự thử lại 3 lần với độ trễ tăng dần.
7. **Dùng gói để điều tiết:** giảm `AI_DAILY_REQUEST_LIMIT` (gói Miễn phí) khi mở công khai để chi phí tỉ lệ với số người trả tiền.

**Kiểm tra xong:** đặt tạm trần thấp trên staging, gửi vài yêu cầu chấm và xác nhận (a) yêu cầu thứ N+1 bị từ chối với thông báo hết hạn mức, (b) dòng log cảnh báo xuất hiện, (c) làm bài thi và flashcard vẫn dùng được.

---

## Checklist trước ngày mở cửa

- [ ] Supabase production ở gói trả phí; sao lưu hằng ngày hiển thị trong Backups
- [ ] Sao lưu logic ra ngoài chạy tự động; mã hoá; **một lần khôi phục thử thành công**; RTO đã ghi
- [ ] Sao lưu Storage (ít nhất bucket `speaking`)
- [ ] Render BE và `ai_service` luôn bật, health check đúng đường, RAM ≥ 1 GB
- [ ] Vercel gói Pro; Render/Supabase/Vercel cùng vùng
- [ ] Sentry nhận lỗi từ BE và BFF; có cảnh báo gửi tới người trực
- [ ] ≥ 3 monitor uptime; cảnh báo đã thử
- [ ] Log drain tới dịch vụ lưu log
- [ ] Staging và production dùng **Supabase project khác nhau** và khoá khác nhau
- [ ] Khoá Groq cũ đã xoay; `Englow3_BE.zip` đã xoá và mọi khoá trong đó đã xoay
- [ ] `CORS_ALLOWED_ORIGINS` đúng tên miền thật
- [ ] `AI_PLATFORM_DAILY_REQUEST_LIMIT` và giới hạn chi tiêu hàng tháng ở nhà cung cấp đã đặt
- [ ] `NEXT_PUBLIC_CONTACT_EMAIL` và `NEXT_PUBLIC_OPERATOR_NAME` đã đặt (trang pháp lý)
- [ ] Trang Điều khoản/Bảo mật đã được người có chuyên môn pháp lý rà soát
