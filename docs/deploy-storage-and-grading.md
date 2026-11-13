# Đưa kho media và chấm tự động lên bản deploy

Trên máy dev, audio bài nghe, audio flashcard, media đề thi và bản ghi Speaking nằm trong MinIO local; chấm
Writing/Speaking tự động cần `ai_service`. Bản deploy (Render + Vercel) chưa có hai thứ này nên người học
thấy bài nhưng không nghe được và không nhận kết quả chấm. Tài liệu này là các bước để có chúng.

## 1. Kho lưu trữ thật

Backend nói chuyện với kho qua chuẩn S3 (path-style), nên dùng được Supabase Storage, Cloudflare R2 hay S3.
Khoá object trong DB là đường dẫn tương đối theo từng bucket, vì vậy **không phải sửa DB**: chép file rồi đổi
biến môi trường.

Khối lượng hiện tại (đã đo): `exams` 686 file / 87 MB, `learning` 6.075 file / 84 MB (6.000 audio flashcard +
75 audio bài nghe). Tổng 173 MB, vừa gói miễn phí của cả Supabase (1 GB) lẫn R2 (10 GB).

Bucket `speaking` chứa bản ghi riêng tư của người học; bản mới bắt đầu rỗng, script không chép nó trừ khi được
chỉ định.

### Cách ít việc nhất: Supabase Storage (dự án đã dùng Supabase)

1. Dashboard Supabase, **Storage**, tạo 4 bucket **private**: `exams`, `learning`, `speaking`, `avatars`
   (`avatars` đặt **public**). Có thể thêm tiền tố nếu muốn, ví dụ `englow3-exams`.
2. **Storage, S3 Connection**: bật S3 protocol, tạo **Access key** (lấy Access key ID và Secret).
   Endpoint có dạng `https://<project-ref>.supabase.co/storage/v1/s3`, region là region của dự án.
3. Chép file (chạy trên máy có MinIO local; khoá nhập qua biến môi trường, không dán vào chat hay commit):

   ```bash
   export SOURCE_S3_ENDPOINT=http://localhost:9000 SOURCE_S3_REGION=us-east-1 \
          SOURCE_S3_ACCESS_KEY=... SOURCE_S3_SECRET_KEY=...
   export TARGET_S3_ENDPOINT=https://<ref>.supabase.co/storage/v1/s3 TARGET_S3_REGION=<region> \
          TARGET_S3_ACCESS_KEY=... TARGET_S3_SECRET_KEY=...

   python scripts/migrate_object_storage.py --dry-run     # chỉ đếm
   python scripts/migrate_object_storage.py               # exams + learning
   # nếu bucket đích có tiền tố: --target-prefix englow3-
   ```

   Chạy lại bao nhiêu lần cũng được: file đã có cùng kích thước sẽ bị bỏ qua. Đo trên máy: 6.761 file trong
   khoảng 30 giây (đích là MinIO local).
4. Đặt biến trên **Render** (service backend của từng môi trường), rồi redeploy:

   | Biến | Giá trị |
   |---|---|
   | `S3_ENDPOINT` | endpoint ở bước 2 |
   | `S3_REGION` | region của dự án |
   | `S3_ACCESS_KEY`, `S3_SECRET_KEY` | khoá ở bước 2 (đặt là *secret*) |
   | `STORAGE_EXAM_BUCKET`, `STORAGE_LEARNING_BUCKET`, `STORAGE_SPEAKING_BUCKET`, `STORAGE_AVATAR_BUCKET` | tên bucket (bỏ qua nếu dùng đúng tên mặc định) |
   | `STORAGE_PUBLIC_BASE_URL` | địa chỉ công khai của bucket `avatars`, dạng `https://<ref>.supabase.co/storage/v1/object/public/avatars` |

5. **CORS cho bucket**: trình duyệt PUT bản ghi Speaking thẳng lên địa chỉ presigned và phát audio từ đó, nên
   bucket phải cho phép origin của web (`https://englow3-web.vercel.app` và hai alias dev/test) với phương thức
   `GET, PUT, HEAD` và header `*`. Với R2 đây là mục *CORS policy* của bucket; Supabase Storage không chặn theo
   origin cho địa chỉ presigned, nhưng vẫn nên thử một lần ghi âm thật.
6. Kiểm: đăng nhập learner, mở một bài nghe và bấm phát; mở một đề TOEIC có audio; ghi âm một câu Speaking.

### Lựa chọn khác: Cloudflare R2

Cùng các bước trên; endpoint là `https://<account-id>.r2.cloudflarestorage.com`, region `auto`, token cần quyền
*Object Read & Write*. Không tính phí băng thông ra. Tạo bucket trên dashboard hoặc để script tự tạo
(token cần quyền admin).

## 2. Chấm Writing/Speaking tự động

Cần trên **Render** (backend) và một service **ai_service** chạy kèm (workflow CD đã có hook cho nó,
`RENDER_AI_DEPLOY_HOOK_URL`).

**Backend:**

| Biến | Giá trị |
|---|---|
| `AI_ENABLED` | `true` |
| `ASSESSMENT_AUTOMATIC_WRITING`, `ASSESSMENT_AUTOMATIC_SPEAKING` | `true` |
| `AI_SERVICE_BASE_URL` | địa chỉ ai_service |
| `AI_SERVICE_INTERNAL_API_KEY` | khoá nội bộ, **cùng giá trị** ở ai_service (đặt là *secret*) |

**ai_service** (mọi biến có tiền tố `AI_SERVICE_`, xem `ai_service/app/config.py`):

| Biến | Giá trị |
|---|---|
| `AI_SERVICE_ENVIRONMENT` | `production` (ẩn `/docs` và `/openapi.json`) |
| `AI_SERVICE_INTERNAL_API_KEY` | cùng giá trị với backend |
| `AI_SERVICE_LLM_ENABLED` | `true` |
| `AI_SERVICE_LLM_BASE_URL`, `AI_SERVICE_LLM_API_KEY` | nhà cung cấp LLM (mặc định Groq); khoá đặt là *secret* |
| `AI_SERVICE_SPEECH_ENABLED` | `true` |
| `AI_SERVICE_SPEECH_PROVIDER` | `whisper` (dùng chung khoá LLM nếu cùng nhà cung cấp) |
| `AI_SERVICE_WHISPER_MODEL` | `whisper-large-v3-turbo` (mặc định) |

Kiểm: `GET /api/assessments/capabilities` (đăng nhập) trả `automaticWriting` và `automaticSpeaking` đều `true`;
nộp một bài Writing, vài giây sau có kết quả.

## 3. Các thay đổi cấu hình đã có hiệu lực từ lần deploy gần nhất

- Swagger/OpenAPI **tắt** theo mặc định. Muốn xem trên dev/testing: đặt `API_DOCS_ENABLED=true`.
  README còn ghi đường `/swagger-ui/index.html`; đường đó trả 404 khi biến này không bật.
- `/actuator/health` chỉ hiện chi tiết cho ADMIN; kiểm sức khoẻ công khai vẫn dùng được.
- Hook deploy của Render chỉ nên nằm trong GitHub **Secrets**, không để trong *Variables* (URL kèm khoá).
