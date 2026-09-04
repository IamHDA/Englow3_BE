# Phạm vi chức năng ứng dụng học tiếng Anh

## 1. Người dùng

### 1.1. Đăng ký và đăng nhập

- Đăng ký bằng email hoặc tài khoản Google/Facebook.
- Đăng nhập, đăng xuất và duy trì phiên đăng nhập.
- Xác minh email, quên mật khẩu và đặt lại mật khẩu.
- Clerk quản lý tài khoản, session và phát token; backend xác minh token trước khi xử lý request.
- Hệ thống tạo hồ sơ người học nội bộ liên kết với `clerkUserId`.

### 1.2. Onboarding và hồ sơ học tập

- Người dùng chọn mục đích học:
  - Giao tiếp hằng ngày.
  - Công việc.
  - Du lịch.
  - Học tập.
  - Luyện thi chứng chỉ.
  - Cải thiện phát âm.
- Người dùng chọn trình độ hiện tại hoặc chọn làm bài kiểm tra đầu vào.
- Nếu mục tiêu là luyện thi, người dùng chọn:
  - Loại chứng chỉ: IELTS, TOEIC.
  - Điểm hiện tại và điểm mục tiêu.
  - Thời hạn dự kiến đạt mục tiêu.
- Người dùng chọn kỹ năng ưu tiên: Listening, Speaking, Reading, Writing, Vocabulary hoặc Grammar.
- Người dùng có thể cập nhật mục tiêu và hồ sơ học tập sau onboarding.

### 1.3. Kiểm tra đầu vào

- Người dùng có thể làm bài kiểm tra đầu vào khi chưa biết trình độ của mình.
- Đề được chọn hoặc sinh phù hợp với mục tiêu/chứng chỉ đã chọn.
- Hệ thống chấm điểm, giải thích đáp án và xác định trình độ ban đầu.
- Kết quả được lưu vào lịch sử và dùng để tạo lộ trình học cá nhân hóa.
- Cần có bộ tiêu chí kiểm định đề AI trước khi đưa cho người dùng; không mặc định coi nội dung AI là chính xác tuyệt đối.

### 1.4. Lộ trình học cá nhân hóa

- AI đề xuất lộ trình dựa trên:
  - Mục tiêu học.
  - Trình độ hiện tại.
  - Điểm mạnh và điểm yếu.
  - Kết quả các lần học và làm bài trước.
- Đề xuất bài học hoặc bài luyện tiếp theo.
- Tự điều chỉnh độ khó theo kết quả học tập.
- Người dùng có thể xem, thay đổi hoặc tạo lại lộ trình.

### 1.5. Học tập

- Học bằng Flashcard theo chủ đề và trình độ.
- Làm Quiz về từ vựng, ngữ pháp và đọc hiểu.
- Luyện Dictation bằng audio và nhập nội dung nghe được.
- Học ngữ pháp theo bài học và ví dụ.
- Lưu từ vựng hoặc câu chưa thuộc vào danh sách ôn tập.
- Đánh dấu hoàn thành bài học.
- Ôn tập ngắt quãng (spaced repetition) cho Flashcard.

### 1.6. Luyện nói và phân tích giọng nói

- Người dùng chọn đoạn văn, câu mẫu hoặc chủ đề hội thoại.
- Ghi âm trực tiếp trên web và tải audio lên Object Storage.
- Chuyển giọng nói thành văn bản.
- AI phân tích:
  - Độ chính xác phát âm.
  - Độ trôi chảy.
  - Trọng âm và ngữ điệu.
  - Ngữ pháp và từ vựng trong câu nói.
- Hiển thị điểm tổng và lỗi theo từng từ/câu.
- Cho phép nghe lại bản ghi của người dùng và audio mẫu.
- Đề xuất bài luyện tiếp theo dựa trên lỗi phát âm thường gặp.
- Lưu lịch sử luyện nói để so sánh tiến bộ.

### 1.7. Luyện đề

- Người dùng chọn loại chứng chỉ, kỹ năng, trình độ và độ khó.
- Có hai nguồn đề:
  - Đề đã được nhân viên/admin kiểm duyệt.
  - Đề cá nhân hóa do AI tạo.
- Hỗ trợ làm từng phần hoặc mô phỏng bài thi đầy đủ.
- Có đồng hồ đếm thời gian, lưu tạm đáp án và nộp bài.
- Chấm điểm, hiển thị đáp án và giải thích.
- Lưu lịch sử làm bài, thời gian làm và kết quả theo từng kỹ năng.
- Cho phép làm lại đề và xem lại các câu sai.
- Không bắt buộc người dùng chỉ luyện đúng chứng chỉ đã chọn trong onboarding.

### 1.8. AI Chat/Tutor

- Hỏi đáp kiến thức tiếng Anh.
- Giải thích từ vựng, ngữ pháp và đáp án.
- Luyện hội thoại theo vai hoặc tình huống.
- Sửa câu, bài viết và gợi ý cách diễn đạt tự nhiên hơn.
- AI sử dụng hồ sơ học tập khi được phép để cá nhân hóa phản hồi.
- Lưu và quản lý lịch sử cuộc trò chuyện.
- Cho phép người dùng báo cáo phản hồi AI không chính xác hoặc không phù hợp.

### 1.9. Tiến độ và thống kê cá nhân

- Dashboard hiển thị:
  - Thời gian học.
  - Số bài đã hoàn thành.
  - Điểm số theo kỹ năng.
  - Chuỗi ngày học liên tục.
  - Các lỗi thường gặp.
- So sánh kết quả theo tuần/tháng.
- Hiển thị tiến độ đối với mục tiêu hoặc điểm chứng chỉ.
- Đề xuất nội dung cần ôn tập.

### 1.10. Blog/Diễn đàn

- Xem danh sách và chi tiết bài đăng.
- Tìm kiếm, lọc và phân trang bài đăng.
- Tạo, sửa và xóa bài đăng của bản thân.
- Bình luận, sửa và xóa bình luận của bản thân.
- Thích/lưu bài đăng.
- Báo cáo bài đăng, bình luận hoặc người dùng vi phạm.
- Nhân viên duyệt bài trước hoặc kiểm duyệt sau khi đăng tùy chính sách hệ thống.

### 1.11. Tin nhắn

- Nhắn tin riêng giữa các người dùng.
- Nhắn tin nhóm.
- Xem trạng thái đã gửi/đã đọc.
- Chặn và bỏ chặn người dùng.
- Báo cáo tin nhắn hoặc tài khoản vi phạm.
- Nhận tin nhắn hỗ trợ từ nhân viên hệ thống.

> Tin nhắn và diễn đàn có thể để sau MVP vì thời gian phát triển và trình độ có hạn.

### 1.12. Thông báo

- Thông báo kết quả xử lý bài luyện nói hoặc đề AI.
- Nhắc lịch học và ôn tập.
- Thông báo phản hồi mới trên blog/tin nhắn.

### 1.13. Tài khoản và quyền riêng tư

- Xem và cập nhật hồ sơ cá nhân.
- Quản lý mục tiêu, trình độ và cài đặt học tập.
- Xóa tài khoản và dữ liệu cá nhân theo chính sách.
- Xem điều khoản sử dụng và chính sách quyền riêng tư đối với dữ liệu giọng nói/AI.

## 2. Nhân viên hệ thống

### 2.1. Quản lý nội dung học tập

- Tạo, sửa, xóa và xuất bản Flashcard, Quiz, Dictation và bài học ngữ pháp.
- Gắn category, chứng chỉ, kỹ năng, level và độ khó.
- Import Flashcard/Quiz từ XLSX hoặc CSV và hiển thị kết quả kiểm tra dữ liệu trước khi lưu.
- Tạo Dictation hoặc câu hỏi bằng AI dưới dạng bản nháp.
- Nhân viên phải kiểm tra nội dung AI trước khi xuất bản chính thức.
- Quản lý audio, transcript, đáp án và lời giải.

### 2.2. Quản lý đề thi

- Tạo, sửa, xóa và xem trước đề.
- Gửi đề để admin/người có quyền duyệt.
- Quản lý phiên bản của đề và trạng thái `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `ARCHIVED`.
- Quản lý ngân hàng câu hỏi để tránh tạo trùng hoặc lộ đề.

### 2.3. Quản lý blog và báo cáo vi phạm

- Xem danh sách bài đăng và bình luận.
- Duyệt, từ chối, ẩn hoặc xóa nội dung vi phạm.
- Xử lý báo cáo từ người dùng.
- Ghi lại lý do và lịch sử kiểm duyệt.

### 2.4. Hỗ trợ người dùng

- Tiếp nhận yêu cầu hỗ trợ.
- Trả lời người dùng qua hệ thống.
- Xem thông tin cần thiết để xử lý sự cố nhưng không được truy cập dữ liệu vượt quá quyền hạn.

## 3. Admin

### 3.1. Quản lý tài khoản và phân quyền

- Xem, tìm kiếm, khóa/mở khóa tài khoản.
- Quản lý role và permission cho admin/nhân viên.
- Không tự sửa password của người dùng; các thao tác authentication thực hiện qua Clerk.
- Xem audit log đối với các thao tác quản trị quan trọng.

### 3.2. Quản lý nội dung toàn hệ thống

- Duyệt đề và nội dung học tập trước khi xuất bản.
- Quản lý blog, bình luận và báo cáo vi phạm.
- Quản lý category, chứng chỉ, level, kỹ năng và cấu hình chấm điểm.

### 3.3. Quản lý AI

- Quản lý prompt template và phiên bản prompt.
- Quản lý model AI đang sử dụng theo từng chức năng.
- Duyệt nội dung AI quan trọng trước khi xuất bản.
- Theo dõi và xử lý phản hồi AI bị người dùng báo cáo.

### 3.4. Thống kê và vận hành

- Thống kê người dùng hoạt động, tỷ lệ hoàn thành onboarding và retention.
- Thống kê kết quả học tập, loại bài được sử dụng và tỷ lệ hoàn thành.
- Thống kê số lần gọi AI, chi phí AI và tỷ lệ lỗi.
- Theo dõi dung lượng audio, dữ liệu lưu trữ và queue xử lý.
- Cấu hình thông báo hệ thống và thời gian bảo trì.

## 4. Chức năng nền của hệ thống

### 4.1. Xử lý tác vụ AI bất đồng bộ

- Đưa tác vụ tạo đề, phân tích audio, Speech-to-Text và tạo feedback vào queue.
- Trạng thái nghiệp vụ: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- Hỗ trợ retry, timeout và chống tạo job trùng.
- Frontend nhận kết quả bằng polling, SSE hoặc WebSocket.

### 4.2. Quản lý file

- Audio, ảnh và tài liệu được lưu trong Object Storage, không lưu trực tiếp trong PostgreSQL.
- PostgreSQL chỉ lưu metadata, bucket và object key.
- Upload/download qua presigned URL có thời hạn.
- Kiểm tra loại file, kích thước và quyền truy cập.
- Có lifecycle tự động xóa audio cũ và cơ chế backup phù hợp.

### 4.3. Bảo mật và giám sát

- Backend xác minh Clerk token cho mọi API cần đăng nhập.
- Authorization được kiểm tra tại backend theo chủ sở hữu, role và permission.
- Audit log cho thao tác admin/nhân viên.
- Logging, monitoring, cảnh báo lỗi và theo dõi hiệu năng.
- Không ghi token, password, nội dung nhạy cảm hoặc audio vào log.

## 5. Phạm vi MVP đề xuất

Để tránh làm hệ thống quá rộng ngay từ đầu, MVP nên tập trung vào:

1. Clerk authentication và hồ sơ người học.
2. Onboarding: mục tiêu, level và chứng chỉ nếu cần.
3. Kiểm tra đầu vào.
4. Flashcard, Quiz và Dictation cơ bản.
5. Luyện nói, lưu audio và AI phân tích giọng nói.
6. Luyện đề và lưu lịch sử kết quả.
7. AI Tutor cơ bản.
8. Dashboard tiến độ.
9. Nhân viên quản lý nội dung và admin duyệt nội dung.

Blog, tin nhắn nên triển khai ở giai đoạn sau nếu chưa phải yêu cầu bắt buộc.
