"""Đề thi — §2.4 và §2.5 của work order."""

from __future__ import annotations

from pydantic import Field, HttpUrl, model_validator

from .common import Definition, StrictModel
from .enums import (
    Accent, AlignmentStatus, CalibrationStatus, OptionLabel, PassageType,
    QuestionType, ReviewStatus,
)
from .ids import exam_group_id, exam_item_id

__all__ = [
    "Option", "IRTParams", "EvidenceSpan", "ExamItem", "Passage",
    "AudioAsset", "ExamGroup", "ExamSet", "SetItemRef",
]


class Option(StrictModel):
    """Lỗi P0-2: mảng option, không phải object hard-code {A,B,C,D} —
    Part 2 chỉ có 3 lựa chọn."""
    label: OptionLabel
    text: str = Field(min_length=1)
    is_correct: bool
    # Lỗi P1-5: mỗi option có lý do riêng, kể cả đáp án đúng lẫn distractor
    rationale_vi: str = Field(min_length=5)


class IRTParams(StrictModel):
    """Lỗi P1-2. Khởi tạo `uncalibrated`; chỉ lượt trả lời thật mới nâng cấp được."""
    a: float | None = None   # discrimination
    b: float | None = None   # difficulty
    c: float | None = None   # guessing
    calibration_status: CalibrationStatus = CalibrationStatus.UNCALIBRATED
    n_responses: int = Field(default=0, ge=0)

    @model_validator(mode="after")
    def status_must_match_evidence(self):
        """Không được khai đã hiệu chuẩn khi chưa có dữ liệu — xem exam-quality-bar.md §7."""
        if self.calibration_status is CalibrationStatus.CALIBRATED and self.n_responses < 200:
            raise ValueError(f"calibrated cần ≥200 lượt trả lời, đang có {self.n_responses}")
        if self.calibration_status is CalibrationStatus.PROVISIONAL and self.n_responses < 30:
            raise ValueError(f"provisional cần ≥30 lượt trả lời, đang có {self.n_responses}")
        if self.calibration_status is CalibrationStatus.UNCALIBRATED and (
            self.a is not None or self.b is not None or self.c is not None
        ):
            raise ValueError("uncalibrated thì không được có tham số a/b/c")
        return self


class EvidenceSpan(StrictModel):
    """Vị trí bằng chứng. Reading dùng char offset, Listening dùng ms."""
    passage_order: int | None = Field(default=None, ge=1)
    char_start: int | None = Field(default=None, ge=0)
    char_end: int | None = Field(default=None, ge=0)
    audio_start_ms: int | None = Field(default=None, ge=0)
    audio_end_ms: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def ranges_must_be_ordered(self):
        if self.char_start is not None and self.char_end is not None and self.char_end <= self.char_start:
            raise ValueError(f"char_end ({self.char_end}) phải lớn hơn char_start ({self.char_start})")
        if self.audio_start_ms is not None and self.audio_end_ms is not None and self.audio_end_ms <= self.audio_start_ms:
            raise ValueError("audio_end_ms phải lớn hơn audio_start_ms")
        return self


class ExamItem(StrictModel):
    item_id: str = Field(default="")          # tự tính
    # Lỗi P1-12: position thuộc về MỘT đề cụ thể, không phải khoá
    position: int | None = Field(default=None, ge=1)
    part_number: int = Field(ge=1, le=7)

    question_text: str | None = None          # Part 1 có thể null
    question_type: QuestionType               # lỗi P1-4
    options: list[Option] = Field(min_length=3, max_length=4)

    concept_ids: list[str] = Field(min_length=1)     # lỗi P1-1
    difficulty_prior: float = Field(ge=0.0, le=1.0)  # lỗi P1-3
    irt_params: IRTParams = Field(default_factory=IRTParams)
    evidence_span: EvidenceSpan | None = None

    explanation: Definition
    embedding_text: str = Field(default="")   # tự tính theo §2.7
    review_status: ReviewStatus = ReviewStatus.DRAFT

    @model_validator(mode="after")
    def exactly_one_correct(self):
        n = sum(o.is_correct for o in self.options)
        if n != 1:
            raise ValueError(f"Phải có đúng 1 đáp án đúng, có {n}")
        return self

    @model_validator(mode="after")
    def part2_has_three_options(self):
        """§2.5 — Part 2 đúng 3 lựa chọn, các part còn lại đúng 4."""
        n = len(self.options)
        if self.part_number == 2 and n != 3:
            raise ValueError("Part 2 bắt buộc 3 lựa chọn")
        if self.part_number != 2 and n != 4:
            raise ValueError(f"Part {self.part_number} bắt buộc 4 lựa chọn, có {n}")
        return self

    @model_validator(mode="after")
    def labels_unique_and_contiguous(self):
        """A,B,C[,D] — không nhảy cóc, không lặp. Bắt lỗi đánh nhãn của LLM."""
        # str() chứ không .value — nhãn có thể là str thuần nếu bị gán sau khi khởi tạo
        labels = [str(o.label) for o in self.options]
        if len(set(labels)) != len(labels):
            raise ValueError(f"nhãn option trùng: {labels}")
        expected = ["A", "B", "C", "D"][: len(labels)]
        if sorted(labels) != expected:
            raise ValueError(f"nhãn option phải là {expected}, đang là {sorted(labels)}")
        return self

    @model_validator(mode="after")
    def compute_derived_fields(self):
        correct = next(o for o in self.options if o.is_correct)
        object.__setattr__(
            self, "item_id",
            exam_item_id(self.part_number, self.question_text, correct.text))
        object.__setattr__(
            self, "embedding_text",
            f"[Part {self.part_number}][{self.question_type}] "
            f"{self.question_text or ''} Correct: {correct.text}")
        return self

    @property
    def correct_option(self) -> Option:
        return next(o for o in self.options if o.is_correct)


class Passage(StrictModel):
    """Lỗi P0-3: passages là ARRAY trên group — Part 7 có double/triple."""
    order: int = Field(ge=1)
    passage_type: PassageType
    text: str = Field(min_length=20)
    graphic_url: HttpUrl | None = None
    speaker: str | None = None      # cho chuỗi chat
    timestamp: str | None = None


class AudioAsset(StrictModel):
    # null cho tới khi Phase 8 TTS xong. §Phase 8 cấm nhét URL giả.
    audio_url: HttpUrl | None = None
    script: str = Field(min_length=10)
    accent: Accent                              # lỗi P1-6
    speaker_count: int = Field(default=1, ge=1, le=4)
    duration_ms: int | None = Field(default=None, ge=0)
    alignment_status: AlignmentStatus = AlignmentStatus.PENDING

    @model_validator(mode="after")
    def aligned_needs_audio(self):
        if self.alignment_status is AlignmentStatus.ALIGNED and self.audio_url is None:
            raise ValueError("alignment_status=aligned nhưng chưa có audio_url")
        return self


class ExamGroup(StrictModel):
    group_id: str = Field(default="")           # tự tính
    part_number: int = Field(ge=1, le=7)
    passages: list[Passage] = Field(default_factory=list)
    image_url: HttpUrl | None = None
    audio: AudioAsset | None = None
    questions: list[ExamItem] = Field(min_length=1)

    @model_validator(mode="after")
    def questions_match_part(self):
        for q in self.questions:
            if q.part_number != self.part_number:
                raise ValueError(
                    f"câu {q.item_id} có part_number={q.part_number}, "
                    f"group là part {self.part_number}")
        return self

    @model_validator(mode="after")
    def passage_order_contiguous(self):
        orders = [p.order for p in self.passages]
        if orders and orders != list(range(1, len(orders) + 1)):
            raise ValueError(f"passage.order phải liên tục từ 1, đang là {orders}")
        return self

    @model_validator(mode="after")
    def compute_group_id(self):
        texts = [p.text for p in self.passages] or [
            (self.audio.script if self.audio else "") or self.questions[0].embedding_text
        ]
        object.__setattr__(self, "group_id", exam_group_id(self.part_number, *texts))
        return self


class SetItemRef(StrictModel):
    """Bộ đề chỉ THAM CHIẾU câu hỏi, không sao chép nội dung — xem
    docs/exam-set-structure.md."""
    group_id: str = Field(min_length=1)
    item_id: str = Field(min_length=1)
    position: int = Field(ge=1)


class ExamSet(StrictModel):
    set_id: str = Field(min_length=1)
    # §0.7 — TOEIC® là nhãn hiệu ETS, không dùng "TOEIC Practice Test" trần
    title: str = Field(min_length=1)
    listening: list[SetItemRef] = Field(default_factory=list)
    reading: list[SetItemRef] = Field(default_factory=list)
    total_questions: int = Field(ge=0)

    @model_validator(mode="after")
    def counts_and_positions(self):
        counted = len(self.listening) + len(self.reading)
        if counted != self.total_questions:
            raise ValueError(
                f"total_questions={self.total_questions} nhưng đếm được {counted} (lỗi P0-5)")
        for name, refs in (("listening", self.listening), ("reading", self.reading)):
            pos = [r.position for r in refs]
            if pos and pos != list(range(1, len(pos) + 1)):
                raise ValueError(f"position của {name} phải liên tục từ 1")
            ids = [r.item_id for r in refs]
            if len(set(ids)) != len(ids):
                raise ValueError(f"{name} có item_id lặp trong cùng một bộ đề")
        return self

    @model_validator(mode="after")
    def title_respects_trademark(self):
        low = self.title.lower()
        if "toeic" in low and "format" not in low and "định dạng" not in low:
            raise ValueError(
                'title dùng "TOEIC" trần — §0.7 yêu cầu "TOEIC-format practice" '
                'hoặc "Đề luyện theo định dạng TOEIC"')
        return self
