PY := .venv/bin/python
PIP := .venv/bin/pip

.PHONY: venv install taxonomy schema validate test clean tree

venv:
	python3.12 -m venv .venv

install: venv
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements.txt

# Phase 1
taxonomy:
	$(PY) validators/check_taxonomy.py

# Phase 2
schema:
	$(PY) schemas/export_json_schema.py
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
