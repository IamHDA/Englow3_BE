# AI data pipeline

Data pipeline cho hệ thống học tiếng Anh A1–C1 + luyện thi định dạng TOEIC.
Nguồn chân lý: [`AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md`](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md).
Mã nguồn nằm ở [`ai/data_pipeline/`](../ai/data_pipeline/) trong không gian AI riêng của repo.

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `taxonomy/` | `concepts.yaml` — xương sống BKT (Phase 1) |
| `schemas/` | Pydantic models — **nguồn định nghĩa duy nhất** (Phase 2) |
| `schemas/json/` | JSON Schema **sinh tự động** từ Pydantic, không sửa tay |
| `seeds/` | Wordlist, grammar syllabus, topic taxonomy (Phase 4) |
| `generators/` | Script gọi LLM sinh nội dung (Phase 5–10) |
| `validators/` | Validation harness — reject-only, không auto-repair (Phase 3) |
| `output/` | Batch JSON đã pass validator |
| `rejects/` | Bản ghi bị từ chối + lý do. Không bao giờ tự sửa rồi cho qua |
| `reports/` | Báo cáo QA, coverage, phân bố |
| `tests/` | pytest — gồm fixture cố ý sai để chứng minh validator thật sự từ chối |

## Setup trên máy mới

```bash
git clone https://github.com/IamHDA/Englow3_BE.git
cd Englow3_BE/ai/data_pipeline
make bootstrap
cp .env.example .env
```

`make bootstrap` tự lo hết: cài [uv](https://docs.astral.sh/uv/) vào `~/.local/bin`
nếu chưa có, tải CPython 3.12 standalone, tạo `.venv`, cài `requirements.txt`,
rồi chạy `make doctor` để xác nhận.

Không cần `sudo`, không đụng Python hệ thống, không cần Homebrew. Gỡ sạch bằng
`rm -rf .venv ~/.local/share/uv ~/.local/bin/uv`.

Nếu máy đã sẵn `python3.12` trong `PATH` thì `make install` cũng được.

**Cần Python 3.11+.** macOS mặc định là 3.9 — không chạy được. `make doctor`
sẽ báo lỗi nếu phiên bản không đạt.

## Lệnh

```bash
make doctor                        # kiểm tra môi trường
make taxonomy                      # Phase 1 — check DAG, p_* ranges, sinh summary
make schema                        # Phase 2 — Pydantic → JSON Schema + DDL
make validate BATCH=<path>         # Phase 3 — validate 1 batch
make test                          # pytest
```

## Trạng thái

Xem [TODO.md](TODO.md) — checklist từng phase và danh sách blocker đang chờ quyết.

## Ràng buộc bất di bất dịch

- Model dữ liệu định nghĩa **một lần** trong Pydantic. JSON Schema sinh ra từ đó.
- Validation là **reject-only**. Không auto-repair.
- Mọi JSON: UTF-8, `ensure_ascii=false`.
- ID sinh bằng hash tất định → pipeline idempotent.
- `is_ai_generated = true` bắt buộc trên mọi batch.
- TOEIC® là nhãn hiệu ETS — dùng "TOEIC-format practice" / "Đề luyện theo định dạng TOEIC".
- Không secret trong repo. Key đọc từ env.
