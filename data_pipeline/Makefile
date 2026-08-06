PY := .venv/bin/python
PIP := .venv/bin/pip

.PHONY: bootstrap venv install taxonomy seed export-db schema validate test clean tree doctor

# --- Bootstrap trên máy mới ---------------------------------------------------
# Chạy `make bootstrap` là đủ: tự cài uv nếu chưa có, tự tải CPython 3.12,
# tạo .venv và cài requirements. Không cần sudo, không đụng Python hệ thống.
bootstrap:
	@command -v uv >/dev/null 2>&1 || curl -LsSf https://astral.sh/uv/install.sh | sh
	@PATH="$$HOME/.local/bin:$$PATH"; uv python install 3.12
	@PATH="$$HOME/.local/bin:$$PATH"; uv venv --python 3.12 --seed .venv
	$(PIP) install -r requirements.txt
	@echo
	@$(MAKE) --no-print-directory doctor

# Dùng khi đã có sẵn python3.12 trong PATH
venv:
	python3.12 -m venv .venv

install: venv
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements.txt

doctor:
	@test -x $(PY) || (echo "FAIL: chưa có .venv — chạy 'make bootstrap'" && exit 1)
	@$(PY) -c "import sys; v=sys.version_info; \
	assert v >= (3,11), f'FAIL: cần Python 3.11+, đang là {v.major}.{v.minor}'; \
	print(f'python      {v.major}.{v.minor}.{v.micro}')"
	@$(PY) -c "import pydantic, jsonschema, yaml, psycopg, rapidfuzz, orjson, sqlalchemy; \
	print('pydantic   ', pydantic.VERSION); print('OK — môi trường sẵn sàng')"

# Phase 1
taxonomy:
	$(PY) validators/check_taxonomy.py --report reports/taxonomy_summary.md

# Phase 4 — cần seeds/raw/ (chạy seeds/raw/fetch_wordlists.sh trước)
seed:
	$(PY) generators/build_vocab_seed.py

# Tang staging: output/<module>/ -> output/_db/*.jsonl (1 file = 1 bang)
export-db:
	$(PY) generators/export_db_staging.py

# Phase 2
schema:
	$(PY) schemas/export_json_schema.py
	@echo
	$(PY) schemas/export_ddl.py

# Phase 3 — usage: make validate BATCH=output/flashcards/xxx.json
validate:
	@test -n "$(BATCH)" || (echo "usage: make validate BATCH=<path>" && exit 2)
	$(PY) validators/validate_batch.py $(BATCH)

test:
	$(PY) -m pytest tests/ -v

tree:
	find . -type d -not -path './.venv/*' -not -path '*/__pycache__*' | sort

clean:
	find . -name '__pycache__' -type d -prune -exec rm -rf {} +
	rm -rf .pytest_cache
