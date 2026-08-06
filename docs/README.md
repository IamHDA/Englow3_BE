# Tài liệu — Englow3 BE

Toàn bộ tài liệu viết tay của dự án nằm trong thư mục này.

## Data pipeline (A1–C1 + luyện thi định dạng TOEIC)

| Tài liệu | Nội dung |
|---|---|
| [AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) | **Nguồn chân lý.** Nguyên tắc vận hành, 13 phase, spec schema, danh sách lỗi P0/P1 phải sửa |
| [decisions.md](decisions.md) | **Quyết định đã chốt** — dữ liệu local, embedding 1024 chiều, cấu trúc bộ đề, sản lượng item |
| [TODO.md](TODO.md) | Checklist tiến độ từng phase + bảng blocker cần Owner quyết |
| [data-pipeline.md](data-pipeline.md) | Layout thư mục `data_pipeline/`, lệnh chạy, ràng buộc bất di bất dịch |
| [storage-layout.md](storage-layout.md) | Bố cục trên đĩa: tầng batch vs tầng staging `_db/`, 21 bảng và thứ tự nạp |
| [exam-set-structure.md](exam-set-structure.md) | Cấu trúc bộ đề: ngân hàng câu tách khỏi manifest, thành phần 200 câu, cần bao nhiêu bộ |
| [phase0-recon.md](phase0-recon.md) | Báo cáo Phase 0: hiện trạng repo, migration tool, toolchain, blocker |

## Trạng thái hiện tại

**STOP GATE 1** — Phase 0 và 1 xong, chờ Owner gõ `APPROVE PHASE 1`.

Mỗi phase kết thúc bằng một STOP GATE. Không gộp phase, không chạy trước.
Chi tiết luật: §0 của work order.

## Chưa có nhưng sẽ cần

- `module-map.md` — module nào sở hữu bảng nào. Cần trước Phase 11 (ingest),
  dùng skill `design-backend-module` để quyết.

## Không nằm ở đây

- `data_pipeline/reports/` — báo cáo **do script sinh ra** (validation, QA, coverage).
  Work order tham chiếu đường dẫn đó trực tiếp nên giữ nguyên vị trí.
- `.claude/skills/` — luật kiến trúc cho agent, không phải tài liệu người đọc.
