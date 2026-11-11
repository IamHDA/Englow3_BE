"""Internal endpoints the backend's generation module calls, one step at a time.

/step writes and checks one component with one model call; /assemble builds the paper, the five
tasks and the Task 1 chart from accepted components; /audio records one Listening part. None of them
keeps state: the backend stores everything between calls.
"""

from __future__ import annotations

import asyncio
import base64
import json
from typing import Any

from fastapi import APIRouter, Request
from pydantic import BaseModel, Field

from app.errors import ServiceError
from app.generation.ielts import build, chart, prompts, rules, steps
from app.providers.generation import parse_object

router = APIRouter(prefix="/internal/v1/generation/ielts")

MAX_RAW = 20_000


class Previous(BaseModel):
    """The last attempt at this step, so the model can see what to fix."""

    reply: dict[str, Any] | None = None
    raw: str | None = Field(default=None, max_length=MAX_RAW)
    problems: list[str] = Field(default_factory=list, max_length=200)


class StepRequest(BaseModel):
    step: str = Field(pattern=r"^(plan|listening-[1-4]|reading-[1-3]|writing-[12]|speaking)$")
    plan: dict[str, Any] | None = None
    brief: str | None = Field(default=None, max_length=1_000)
    used_topics: list[str] = Field(default_factory=list, max_length=2_000)
    previous: Previous | None = None


class StepResponse(BaseModel):
    reply: dict[str, Any] | None
    raw: str | None = None
    problems: list[str]
    topics: list[str] = Field(default_factory=list)
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None


class AssembleRequest(BaseModel):
    components: dict[str, dict[str, Any]]


class Segment(BaseModel):
    speaker: str | None = None
    text: str = Field(min_length=1, max_length=5_000)
    voice: str = Field(pattern=r"^[a-z]{2}-[A-Z]{2}-[A-Za-z]+Neural$")


class AssembleResponse(BaseModel):
    paper: dict[str, Any]
    tasks: list[dict[str, Any]]
    chart_png_base64: str
    listening: dict[str, list[Segment]]
    problems: list[str]


class AudioRequest(BaseModel):
    segments: list[Segment] = Field(min_length=1, max_length=200)


class AudioResponse(BaseModel):
    mp3_base64: str
    size_bytes: int


@router.post("/step", response_model=StepResponse)
async def step(request: Request, body: StepRequest) -> StepResponse:
    try:
        messages = steps.messages(body.step, body.plan, body.brief, body.used_topics)
    except (KeyError, IndexError, ValueError) as exc:
        raise ServiceError("GENERATION_STEP_INVALID", str(exc), 422) from exc
    if body.previous and body.previous.problems:
        if body.previous.reply is not None:
            messages.append(prompts.as_message(body.previous.reply))
        elif body.previous.raw:
            messages.append({"role": "assistant", "content": body.previous.raw})
        messages.append(prompts.fix(body.previous.problems))

    provider = request.app.state.generation_provider
    text, usage = await provider.complete(messages)
    try:
        reply = parse_object(text)
    except (ValueError, json.JSONDecodeError) as exc:
        return StepResponse(
            reply=None,
            raw=text[:MAX_RAW],
            model=provider.model,
            problems=[f"The reply could not be read as JSON: {exc}"],
            **usage,
        )
    problems = steps.check(body.step, reply, body.used_topics)
    try:
        json.dumps(reply, allow_nan=False)
    except ValueError:
        return StepResponse(
            reply=None,
            raw=text[:MAX_RAW],
            problems=problems or ["Use finite JSON numbers"],
            model=provider.model,
            **usage,
        )
    topics = steps.topics_of(reply) if body.step == steps.PLAN and not problems else []
    return StepResponse(
        reply=reply, problems=problems, topics=topics, model=provider.model, **usage
    )


@router.post("/assemble", response_model=AssembleResponse)
async def assemble(body: AssembleRequest) -> AssembleResponse:
    parts = body.components
    missing = [s for s in steps.WRITING_STEPS if s not in parts]
    if missing:
        raise ServiceError("GENERATION_COMPONENTS_MISSING", f"missing {', '.join(missing)}", 422)
    plan = parts[steps.PLAN]
    invalid = [
        problem for name in steps.WRITING_STEPS for problem in steps.check(name, parts[name], [])
    ]
    if invalid:
        raise ServiceError("GENERATION_COMPONENTS_INVALID", "; ".join(invalid[:10]), 422)
    listening = {int(s[-1]): parts[s] for s in steps.LISTENING}
    reading = {int(s[-1]): parts[s] for s in steps.READING}
    problems = rules.check_paper_mix(listening, reading)
    title = f"IELTS Academic AI Set: {plan.get('title', 'Untitled')}"
    paper = build.build_paper(f"{title} - Listening and Reading", listening, reading)
    tasks = build.build_tasks(
        plan.get("title", "AI set"),
        parts[steps.WRITING_1],
        parts[steps.WRITING_2],
        parts[steps.SPEAKING],
        "task1-chart.png",
    )
    voices = build.voices(listening)
    recordings = {
        str(n): [
            Segment(
                speaker=line["speaker"],
                text=line["text"].strip(),
                voice=voices[str(n)][line["speaker"]],
            )
            for line in spec["transcript"]
            if str(line.get("text", "")).strip()
        ]
        for n, spec in listening.items()
    }
    png = await asyncio.to_thread(chart.png, parts[steps.WRITING_1]["chart"])
    return AssembleResponse(
        paper=paper,
        tasks=tasks,
        chart_png_base64=base64.b64encode(png).decode(),
        listening=recordings,
        problems=problems,
    )


@router.post("/audio", response_model=AudioResponse)
async def audio(request: Request, body: AudioRequest) -> AudioResponse:
    mp3 = await request.app.state.speech_synthesizer.synthesize(
        [(s.text, s.voice) for s in body.segments]
    )
    return AudioResponse(mp3_base64=base64.b64encode(mp3).decode(), size_bytes=len(mp3))
