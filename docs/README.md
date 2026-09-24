# Tài liệu Englow3 BE

## Phát triển backend

| Tài liệu | Nội dung |
|---|---|
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | Nguồn duy nhất về cấu trúc code, coding convention, migration, kiểm thử và quy trình hoàn thành thay đổi |
| [`module-map.md`](module-map.md) | Module hiện tại, bảng do từng module sở hữu và ranh giới AI/data pipeline |
| [`product-scope.md`](product-scope.md) | Phạm vi sản phẩm mục tiêu; không phải báo cáo trạng thái triển khai |
| [`../ai_service/README.md`](../ai_service/README.md) | Contract và cách chạy FastAPI AI service hiện tại |

## Data pipeline

| Tài liệu | Nội dung |
|---|---|
| [`data-pipeline.md`](data-pipeline.md) | Cấu trúc, thiết lập và lệnh vận hành pipeline |
| [`decisions.md`](decisions.md) | Các quyết định hiện hành về dữ liệu, nguồn và quy trình sinh |
| [`TODO.md`](TODO.md) | Việc vận hành hoặc quyết định của owner còn mở |
| [`storage-layout.md`](storage-layout.md) | Bố cục batch/staging và thứ tự dữ liệu |
| [`exam-quality-bar.md`](exam-quality-bar.md) | Chuẩn chất lượng nội dung và kiểm tra thiên lệch |
| [`exam-set-structure.md`](exam-set-structure.md) | Cấu trúc ngân hàng câu hỏi và bộ đề |
| [`AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md`](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) | Đặc tả gốc để tra cứu; các phase gate lịch sử không còn điều khiển công việc hiện tại |

Các báo cáo sinh tự động nằm trong `data_pipeline/reports/`. Không đưa cache, môi
trường ảo, nguồn dữ liệu lớn hoặc output sinh tự động vào `docs/`.
