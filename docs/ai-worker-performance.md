# Worker AI: năng lực xử lý và thời hạn

Worker có hai slot độc lập: Tutor và chấm bài (Writing/Speaking/phát âm). Chỉ claim một job khi slot thực sự rảnh; không để batch đã đánh dấu RUNNING nằm chờ trong executor. Tổng mặc định là hai job trên một instance; tăng số instance sẽ tăng tổng concurrency và phải xét quota provider/DB.

V055 thêm `lease_until` và index pending theo loại. Heartbeat riêng mỗi 10 giây, giữ nguyên `started_at` của claim để chặn kết quả cũ. Reclaim dùng khóa skip-locked; complete khóa job, kiểm tra claim và ghi kết quả nghiệp vụ cùng transaction ngắn. Handler không ghi kết quả trong lúc gọi provider nữa. Writer vẫn thuộc module sở hữu bảng.

Liveness adapter được kiểm tra trước inference; khi adapter chưa chạy, job được trì hoãn 10 giây mà không mất lượt retry. Job cũ hơn 15 phút khi nhận lại sẽ kết thúc với lỗi có thể xem được, không chờ vô hạn. Liveness không kiểm tra key/provider; lỗi cấu hình do adapter trả về được phân loại permanent và không thử đi thử lại.

Ngân sách mặc định: FastAPI LLM/embedding tổng 45 giây; speech tổng 60 giây; BE LLM 55 giây; speech 70 giây; storage API tổng 15 giây; worker một attempt 140 giây; lease 3 phút. Khi vượt ngân sách worker không công bố kết quả đến muộn và không gia hạn heartbeat mãi. Slot được giữ tới khi I/O thực sự thoát, tránh tạo concurrency ảo.

Retry có giới hạn theo job hiện có, backoff có jitter; adapter chuyển Retry-After (giới hạn 10 phút) từ provider và BE giữ thời điểm đó trong queue. Kết quả HTTP 502/503 không mặc định là retryable nếu adapter đã xác định lỗi permanent. Không bảo đảm exactly-once với provider khi tiến trình chết sau khi upstream đã nhận request.

Env vận hành: `AI_WORKER_ENABLED`, `AI_WORKER_FIXED_DELAY`, `AI_WORKER_LOCK_TIMEOUT`, `AI_WORKER_MAX_RUN`, `AI_WORKER_RECONCILE_DELAY`, `AI_READ_TIMEOUT`, `AI_SPEECH_READ_TIMEOUT`; FastAPI có `AI_SERVICE_READ_TIMEOUT_SECONDS`, `AI_SERVICE_SPEECH_TIMEOUT_SECONDS`. Giữ deadline trong nhỏ hơn deadline ngoài và xét lease khi override.

Deploy BE và AI testing bằng hook riêng; cấu hình `RENDER_AI_TESTING_DEPLOY_HOOK_URL` và `AI_TESTING_HEALTH_URL` trên GitHub, `AI_SERVICE_BASE_URL`/key trên Render. CI kiểm tra readiness và release SHA cả hai service trước khi báo thành công. Flyway phải áp dụng V055; không dùng launcher cũ tắt migration.

Các test PostgreSQL kiểm tra claim theo loại, hai worker claim khác job, deferral không tăng retry, giữ Retry-After, rollback và việc worker cũ không công bố kết quả sau reclaim. Test worker giữ Speaking để kiểm tra Tutor vẫn được chạy. Pytest kiểm tra deadline tổng hủy provider bị treo.

## AI testing trên Render

URL đã được xác nhận: `https://englow3-ai-service-testing.onrender.com`. Readiness ở `/health/ready`; BE dùng URL gốc làm `AI_SERVICE_BASE_URL`, BFF dùng URL readiness làm `AI_HEALTH_URL`.

Kiểm tra ngày 16/10/2026: key nội bộ đã đồng bộ, readiness trả 200, body rỗng tới `/internal/v1/llm/generate` trả 422 sau xác thực; yêu cầu tạo câu trả lời thử trả 200 với nội dung hợp lệ. BE/BFF đã khởi động lại và FE/BFF đều báo backend cùng AI sẵn sàng. Không ghi key vào tài liệu hoặc commit. Endpoint `/version` hiện trả 404; cần deploy bản source có endpoint này trước khi CI kiểm tra release SHA.

Các bước cấu hình/deploy:

1. Trên Render Dashboard kiểm tra service có root/build context `ai_service` hoặc image kết thúc bằng `-ai-service:testing`. Nếu có, lấy External URL và deploy hook của chính service đó.
2. Nếu chưa có, tạo Web Service từ repo BE, branch `testing`, chọn Docker; Dockerfile `ai_service/Dockerfile`, Docker context `ai_service`. Chọn compute/region phù hợp với BE testing sau khi kiểm tra; chưa mặc định đổi hạ tầng hoặc mua gói.
3. Đặt env qua Dashboard: `AI_SERVICE_ENVIRONMENT=production`, `AI_SERVICE_LLM_ENABLED=true`, `AI_SERVICE_SPEECH_ENABLED=true`, `AI_SERVICE_SPEECH_PROVIDER=whisper`, key LLM và internal API key. Internal key phải trùng BE; không dán key vào chat hoặc YAML.
4. Đặt health check `/health/live`. Kiểm tra `/health/ready` và inference smoke riêng; `/health/live` không chứng minh key/provider đã hoạt động. Nếu dùng build Git trực tiếp thay vì image CI, phải truyền `RELEASE_SHA` của commit build cho Docker build/runtime để bước kiểm chứng release không nhận `unknown`.
5. Cấu hình BE `AI_SERVICE_BASE_URL` bằng URL gốc ở trên; thêm `/health/ready` cho `AI_TESTING_HEALTH_URL` ở GitHub Variables cả hai repo. Đặt hook vào GitHub Secret `RENDER_AI_TESTING_DEPLOY_HOOK_URL`.
6. Chạy release testing đã qua CI, kiểm tra log version/readiness và từ một tab mới mở Vercel URL. Nếu service dùng private image, cấu hình registry credential trên Render; không commit registry token.

Tham chiếu cấu hình Docker, health check và branch từ [Render Blueprint reference](https://render.com/docs/blueprint-spec). Không có quyền đăng nhập/API Render trong workspace hiện tại để xác nhận hoặc tạo service hộ người dùng.
