# data_pipeline

Data pipeline cho hệ thống học tiếng Anh A1–C1 + luyện thi định dạng TOEIC.
Nguồn chân lý: [`AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md`](../AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md).

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

## Setup

```bash
cd data_pipeline
make install        # tạo .venv (python3.12) + cài requirements
cp .env.example .env
```

## Lệnh

```bash
make taxonomy                      # Phase 1 — check DAG, p_* ranges
make schema                        # Phase 2 — Pydantic → JSON Schema + DDL
make validate BATCH=<path>         # Phase 3 — validate 1 batch
make test                          # pytest
```

## Ràng buộc bất di bất dịch

- Model dữ liệu định nghĩa **một lần** trong Pydantic. JSON Schema sinh ra từ đó.
- Validation là **reject-only**. Không auto-repair.
- Mọi JSON: UTF-8, `ensure_ascii=false`.
- ID sinh bằng hash tất định → pipeline idempotent.
- `is_ai_generated = true` bắt buộc trên mọi batch.
- TOEIC® là nhãn hiệu ETS — dùng "TOEIC-format practice" / "Đề luyện theo định dạng TOEIC".
- Không secret trong repo. Key đọc từ env.
