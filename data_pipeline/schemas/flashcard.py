"""Flashcard — §2.3 của work order."""

from __future__ import annotations

from pydantic import Field, model_validator

from .common import Definition, Example, StrictModel
from .enums import CEFRLevel, CEFRSource, CollocationPattern, PartOfSpeech, ReviewStatus
from .ids import flashcard_id

__all__ = ["Collocation", "Flashcard"]

# §2.3: B2/C1 bắt buộc ≥3 collocation
COLLOCATION_REQUIRED_LEVELS = {CEFRLevel.B2, CEFRLevel.C1}
MIN_COLLOCATIONS = 3


class Collocation(StrictModel):
    """Lỗi P1-9: object có `pattern`, không phải mảng chuỗi phẳng."""
    pattern: CollocationPattern
    text: str = Field(min_length=3)
    cefr: CEFRLevel


class Flashcard(StrictModel):
    id: str = Field(default="")           # tự tính, xem below
    lemma: str = Field(min_length=1)
    pos: PartOfSpeech

    # Lỗi P1-8: khoá là (lemma, pos, sense_index) — address(n) khác address(v)
    sense_index: int = Field(default=1, ge=1)
    sense_label_en: str = Field(min_length=3)   # nhãn ngắn phân biệt nghĩa

    ipa_us: str = Field(min_length=2)           # BẮT BUỘC — TOEIC là American English
    ipa_uk: str | None = None
    ipa_verified: bool = False                  # Phase 5 đối chiếu CMUdict rồi bật

    definition: Definition
    examples: list[Example] = Field(min_length=2, max_length=4)
    collocations: list[Collocation] = Field(default_factory=list)
    mnemonic_tip_vi: str | None = None

    cefr_level: CEFRLevel
    cefr_source: CEFRSource                     # §0.4 — phải truy vết được
    frequency_rank: int | None = Field(default=None, ge=1)

    topics: list[str] = Field(min_length=1)
    concept_ids: list[str] = Field(min_length=1)   # lỗi P1-1
    difficulty_prior: float = Field(ge=0.0, le=1.0)  # lỗi P1-3

    embedding_text: str = Field(default="")     # tự tính theo §2.7
    review_status: ReviewStatus = ReviewStatus.DRAFT

    @model_validator(mode="after")
    def collocations_required_at_b2_c1(self):
        if self.cefr_level in COLLOCATION_REQUIRED_LEVELS and len(self.collocations) < MIN_COLLOCATIONS:
            raise ValueError(
                f"{self.cefr_level} bắt buộc ≥{MIN_COLLOCATIONS} collocation, "
                f"đang có {len(self.collocations)}"
            )
        return self

    @model_validator(mode="after")
    def compute_derived_fields(self):
        """ID và embedding_text do CODE tính, không nhận từ LLM.

        §2.7 nói công thức embedding_text là 'chốt cứng, không được ad-hoc'. Cách
        duy nhất bảo đảm điều đó là tính ở đây — nếu để LLM điền, mỗi batch sẽ có
        một biến thể và vector search sẽ so những thứ không cùng dạng.
        """
        object.__setattr__(self, "id", flashcard_id(self.lemma, self.pos, self.sense_index))
        examples = "; ".join(e.sentence for e in self.examples)
        object.__setattr__(
            self, "embedding_text",
            f"{self.lemma} ({self.pos}, {self.sense_label_en}). "
            f"{self.definition.en} Examples: {examples}"
        )
        return self
