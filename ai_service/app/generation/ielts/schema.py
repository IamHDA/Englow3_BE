"""Validate model output shapes before semantic rules inspect nested values."""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator


class Shape(BaseModel):
    model_config = ConfigDict(strict=True, extra="allow", allow_inf_nan=False)

    @field_validator("*")
    @classmethod
    def bounded_content(cls, value):
        if isinstance(value, str) and (not value.strip() or len(value) > 30_000):
            raise ValueError("provide nonblank text of at most 30000 characters")
        if isinstance(value, list) and (
            len(value) > 200 or any(isinstance(x, str) and not x.strip() for x in value)
        ):
            raise ValueError("provide at most 200 nonblank items")
        return value


class Evidence(Shape):
    evidence: str | list[str] | None = None
    trap: str | None = None


class GapQuestion(Evidence):
    prompt: str
    answers: list[str]


class SingleQuestion(Evidence):
    prompt: str
    options: list[str]
    answer: int


class MultiQuestion(Evidence):
    prompt: str
    options: list[str]
    answers: list[int]


class MatchingQuestion(Evidence):
    prompt: str
    answer: str


class JudgementQuestion(Evidence):
    statement: str
    answer: str


class SetBase(Shape):
    instruction: str


class GapSet(SetBase):
    kind: Literal["gap"]
    content: str
    word_limit: int
    questions: list[GapQuestion]


class SingleSet(SetBase):
    kind: Literal["single"]
    questions: list[SingleQuestion]


class MultiSet(SetBase):
    kind: Literal["multi"]
    questions: list[MultiQuestion]


class Entry(Shape):
    label: str
    text: str


class MatchingSet(SetBase):
    kind: Literal["matching"]
    list: list[Entry]
    questions: list[MatchingQuestion]


class JudgementSet(SetBase):
    kind: Literal["tfng", "ynng"]
    questions: list[JudgementQuestion]


QuestionSet = Annotated[
    GapSet | SingleSet | MultiSet | MatchingSet | JudgementSet, Field(discriminator="kind")
]


class Plan(Shape):
    title: str
    listening: list[str]
    reading: list[str]
    writing_task1: str
    writing_task2: str
    speaking: str


class Speaker(Shape):
    name: str
    accent: str
    gender: str


class Line(Shape):
    speaker: str
    text: str


class Listening(Shape):
    speakers: list[Speaker]
    transcript: list[Line]
    sets: list[QuestionSet]


class Reading(Shape):
    title: str
    paragraphs: list[str]
    sets: list[QuestionSet]
    label_paragraphs: bool = False


class Series(Shape):
    name: str
    values: list[float]


class Chart(Shape):
    type: str
    title: str
    unit: str
    categories: list[str]
    series: list[Series]


class Task1(Shape):
    title: str
    description: str
    chart: Chart
    key_features: list[str]


class Task2(Shape):
    title: str
    prompt: str


class Cue(Shape):
    cue: str
    points: list[str]
    explain: str


class Speaking(Shape):
    part1: list[str]
    part2: Cue
    part3: list[str]


def shape_problems(step: str, reply: dict) -> list[str]:
    model = (
        Plan
        if step == "plan"
        else Listening
        if step.startswith("listening-")
        else Reading
        if step.startswith("reading-")
        else Task1
        if step == "writing-1"
        else Task2
        if step == "writing-2"
        else Speaking
    )
    try:
        model.model_validate(reply)
    except ValidationError as error:
        return [
            f"{step}: {'.'.join(map(str, item['loc']))}: {item['msg']}"
            for item in error.errors(include_input=False, include_url=False)[:50]
        ]
    return []
