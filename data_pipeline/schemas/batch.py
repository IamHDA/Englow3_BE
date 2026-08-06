"""Batch bao ngoài — 8 loại, mỗi loại một file JSON trong output/<module>/.

`total_records` do pipeline đếm lại chứ không tin LLM khai (lỗi P0-5): validator
`records_match_total` dưới đây là chỗ cưỡng chế.
"""

from __future__ import annotations

from pydantic import Field, model_validator

from .assessment import AssessmentPrompt, ShadowingClip
from .common import BatchMetadata, StrictModel
from .enums import ModuleType
from .exam import ExamGroup, ExamSet
from .flashcard import Collocation, Flashcard
from .grammar import GrammarPoint
from .speaking_writing import Rubric, SpeakingTask, WritingTask

__all__ = [
    "FlashcardBatch", "GrammarBatch", "CollocationBatch", "ExamBatch",
    "SpeakingBatch", "WritingBatch", "ShadowingBatch", "AssessmentPromptBatch",
    "BATCH_MODELS",
]


class _Batch(StrictModel):
    batch_metadata: BatchMetadata

    @property
    def _records(self) -> list:
        raise NotImplementedError

    @model_validator(mode="after")
    def records_match_total(self):
        n = len(self._records)
        if self.batch_metadata.total_records != n:
            raise ValueError(
                f"total_records={self.batch_metadata.total_records} nhưng đếm được "
                f"{n} bản ghi (lỗi P0-5)")
        return self

    @model_validator(mode="after")
    def module_type_matches(self):
        want = self.__class__.__module_type__
        if self.batch_metadata.module_type is not want:
            raise ValueError(
                f"module_type phải là {want.value} cho {self.__class__.__name__}, "
                f"đang là {self.batch_metadata.module_type.value}")
        return self


class FlashcardBatch(_Batch):
    __module_type__ = ModuleType.FLASHCARD
    flashcards: list[Flashcard] = Field(default_factory=list)

    @property
    def _records(self): return self.flashcards


class GrammarBatch(_Batch):
    __module_type__ = ModuleType.GRAMMAR
    grammar_points: list[GrammarPoint] = Field(default_factory=list)

    @property
    def _records(self): return self.grammar_points


class CollocationBatch(_Batch):
    __module_type__ = ModuleType.COLLOCATION
    collocations: list[Collocation] = Field(default_factory=list)

    @property
    def _records(self): return self.collocations


class ExamBatch(_Batch):
    __module_type__ = ModuleType.EXAM
    groups: list[ExamGroup] = Field(default_factory=list)
    sets: list[ExamSet] = Field(default_factory=list)

    @property
    def _records(self): return self.groups


class SpeakingBatch(_Batch):
    __module_type__ = ModuleType.SPEAKING
    tasks: list[SpeakingTask] = Field(default_factory=list)
    rubrics: list[Rubric] = Field(default_factory=list)

    @property
    def _records(self): return self.tasks


class WritingBatch(_Batch):
    __module_type__ = ModuleType.WRITING
    tasks: list[WritingTask] = Field(default_factory=list)
    rubrics: list[Rubric] = Field(default_factory=list)

    @property
    def _records(self): return self.tasks


class ShadowingBatch(_Batch):
    __module_type__ = ModuleType.SHADOWING
    clips: list[ShadowingClip] = Field(default_factory=list)

    @property
    def _records(self): return self.clips


class AssessmentPromptBatch(_Batch):
    __module_type__ = ModuleType.ASSESSMENT_PROMPT
    prompts: list[AssessmentPrompt] = Field(default_factory=list)

    @property
    def _records(self): return self.prompts


BATCH_MODELS: dict[ModuleType, type[_Batch]] = {
    ModuleType.FLASHCARD: FlashcardBatch,
    ModuleType.GRAMMAR: GrammarBatch,
    ModuleType.COLLOCATION: CollocationBatch,
    ModuleType.EXAM: ExamBatch,
    ModuleType.SPEAKING: SpeakingBatch,
    ModuleType.WRITING: WritingBatch,
    ModuleType.SHADOWING: ShadowingBatch,
    ModuleType.ASSESSMENT_PROMPT: AssessmentPromptBatch,
}
