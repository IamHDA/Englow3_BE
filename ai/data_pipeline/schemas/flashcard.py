"""Flashcard — §2.3 của work order."""

from __future__ import annotations

from pydantic import Field, model_validator

from .common import Definition, Example, StrictModel
from .enums import CEFRLevel, CEFRSource, CollocationPattern, PartOfSpeech, ReviewStatus
from .ids import flashcard_id

__all__ = ["Collocation", "Flashcard"]

COLLOCATION_REQUIRED_LEVELS = {CEFRLevel.B2, CEFRLevel.C1}
MIN_COLLOCATIONS = 3


class Collocation(StrictModel):
    pattern: CollocationPattern
    text: str = Field(min_length=3)
    cefr: CEFRLevel


class Flashcard(StrictModel):
    id: str = Field(default="")           # tự tính
    lemma: str = Field(min_length=1)
    pos: PartOfSpeech

    sense_index: int = Field(default=1, ge=1)
    sense_label_en: str = Field(min_length=3)

    ipa_us: str = Field(min_length=2)           # BẮT BUỘC — TOEIC là American English
    ipa_uk: str | None = None
    ipa_verified: bool = False

    audio_url_us: str | None = None             # URL file mp3 phát âm US
    audio_url_uk: str | None = None             # URL file mp3 phát âm UK

    definition: Definition
    examples: list[Example] = Field(min_length=2, max_length=4)
    collocations: list[Collocation] = Field(default_factory=list)
    mnemonic_tip_vi: str | None = None

    cefr_level: CEFRLevel
    cefr_source: CEFRSource
    frequency_rank: int | None = Field(default=None, ge=1)

    topics: list[str] = Field(min_length=1)
    concept_ids: list[str] = Field(min_length=1)
    difficulty_prior: float = Field(ge=0.0, le=1.0)

    embedding_text: str = Field(default="")
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
        object.__setattr__(self, "id", flashcard_id(self.lemma, self.pos, self.sense_index))
        examples = "; ".join(e.sentence for e in self.examples)
        object.__setattr__(
            self, "embedding_text",
            f"{self.lemma} ({self.pos}, {self.sense_label_en}). "
            f"{self.definition.en} Examples: {examples}"
        )
        return self
