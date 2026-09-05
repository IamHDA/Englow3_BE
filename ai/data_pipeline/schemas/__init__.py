"""Schema dữ liệu — nguồn định nghĩa DUY NHẤT.

§0.6: model định nghĩa một lần bằng Pydantic; JSON Schema và DDL đều SINH RA từ
đây. Không viết tay bản thứ hai rồi để hai bản lệch nhau.

    python schemas/export_json_schema.py    -> schemas/json/*.schema.json
    python schemas/export_ddl.py            -> migrations/V1__content_tables.sql
"""

from __future__ import annotations

from .assessment import (
    AssessmentPrompt, AssessmentResult, DimensionScore, ErrorFinding,
    ShadowingClip, ShadowingSegment,
)
from .batch import (
    BATCH_MODELS, AssessmentPromptBatch, CollocationBatch, ExamBatch,
    FlashcardBatch, GrammarBatch, ShadowingBatch, SpeakingBatch, WritingBatch,
)
from .common import SCHEMA_VERSION, BatchMetadata, Definition, Example, StrictModel
from .enums import (
    Accent, AlignmentStatus, CalibrationStatus, CEFRLevel, CEFRSource,
    CollocationPattern, ModuleType, OptionLabel, PartOfSpeech, PassageType,
    QuestionType, ReviewStatus, SourceType, WritingTaskType,
)
from .exam import (
    AudioAsset, EvidenceSpan, ExamGroup, ExamItem, ExamSet, IRTParams, Option,
    Passage, SetItemRef,
)
from .flashcard import Collocation, Flashcard
from .grammar import CommonMistake, GrammarPoint
from .ids import (
    exam_group_id, exam_item_id, flashcard_id, grammar_point_id, passage_hash,
    rubric_id, stable_id, task_id,
)
from .speaking_writing import (
    BandDescriptor, Rubric, RubricDimension, SpeakingTask, WritingTask,
)

__all__ = [
    "SCHEMA_VERSION",
    # ids
    "stable_id", "flashcard_id", "exam_item_id", "exam_group_id",
    "grammar_point_id", "task_id", "rubric_id", "passage_hash",
    # enums
    "CEFRLevel", "ModuleType", "ReviewStatus", "CEFRSource", "PartOfSpeech",
    "CollocationPattern", "PassageType", "Accent", "AlignmentStatus",
    "CalibrationStatus", "QuestionType", "OptionLabel", "WritingTaskType",
    "SourceType",
    # common
    "StrictModel", "Definition", "Example", "BatchMetadata",
    # flashcard
    "Collocation", "Flashcard",
    # exam
    "Option", "IRTParams", "EvidenceSpan", "ExamItem", "Passage", "AudioAsset",
    "ExamGroup", "ExamSet", "SetItemRef",
    # grammar
    "CommonMistake", "GrammarPoint",
    # speaking / writing
    "BandDescriptor", "RubricDimension", "Rubric", "SpeakingTask", "WritingTask",
    # assessment / shadowing
    "DimensionScore", "ErrorFinding", "AssessmentResult", "AssessmentPrompt",
    "ShadowingSegment", "ShadowingClip",
    # batches
    "FlashcardBatch", "GrammarBatch", "CollocationBatch", "ExamBatch",
    "SpeakingBatch", "WritingBatch", "ShadowingBatch", "AssessmentPromptBatch",
    "BATCH_MODELS",
]
