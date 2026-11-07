"""AI-written IELTS Academic full test sets, offline.

The rules, prompts, builder, chart and step catalogue live once, in the AI service
(ai_service/app/generation/ielts), which the admin "generate" button drives. This package
re-exports them so the command-line generator checks and builds exactly the same way; only the
topic registry file (`topics`) and the direct model client (`llm`) are the pipeline's own.
"""

import sys
from pathlib import Path

_AI_SERVICE = Path(__file__).resolve().parents[2] / "ai_service"
if str(_AI_SERVICE) not in sys.path:
    sys.path.insert(0, str(_AI_SERVICE))

from app.generation.ielts import build, chart, prompts, rules, steps  # noqa: E402

__all__ = ["build", "chart", "prompts", "rules", "steps"]
