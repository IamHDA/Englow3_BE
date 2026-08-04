# Tài liệu — Englow3 BE

Toàn bộ tài liệu viết tay của dự án nằm trong thư mục này.

## Data pipeline (A1–C1 + luyện thi định dạng TOEIC)

| Tài liệu | Nội dung |
|---|---|
| [AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) | **Nguồn chân lý.** Nguyên tắc vận hành, 13 phase, spec schema, danh sách lỗi P0/P1 phải sửa |
| [TODO.md](TODO.md) | Checklist tiến độ từng phase + bảng blocker cần Owner quyết |
| [data-pipeline.md](data-pipeline.md) | Layout thư mục `data_pipeline/`, lệnh chạy, ràng buộc bất di bất dịch |
| [phase0-recon.md](phase0-recon.md) | Báo cáo Phase 0: hiện trạng repo, migration tool, toolchain, blocker |

## Trạng thái hiện tại

**STOP GATE 0** — Phase 0 xong, chờ Owner gõ `APPROVE PHASE 0`.

Mỗi phase kết thúc bằng một STOP GATE. Không gộp phase, không chạy trước.
Chi tiết luật: §0 của work order.

## Chưa có nhưng sẽ cần

- `module-map.md` — module nào sở hữu bảng nào. Cần trước Phase 11 (ingest),
  dùng skill `design-backend-module` để quyết.

## Không nằm ở đây

- `data_pipeline/reports/` — báo cáo **do script sinh ra** (validation, QA, coverage).
  Work order tham chiếu đường dẫn đó trực tiếp nên giữ nguyên vị trí.
- `.claude/skills/` — luật kiến trúc cho agent, không phải tài liệu người đọc.
