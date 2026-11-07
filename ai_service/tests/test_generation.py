import base64
import json

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from tests.generation_fixtures import PLAN, components, listening

HEADERS = {"X-Internal-API-Key": "test-internal-key"}


def settings(**overrides) -> Settings:
    values = {
        "internal_api_key": SecretStr("test-internal-key"),
        "generation_enabled": True,
        "generation_api_key": SecretStr("generation-key"),
        "generation_model": "test-writer",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


class FakeWriter:
    """Replies with the queued texts in turn and keeps the messages it was sent."""

    def __init__(self, *replies: str):
        self.replies = list(replies)
        self.sent: list[list[dict]] = []

    @property
    def model(self) -> str:
        return "test-writer"

    async def complete(self, messages):
        self.sent.append(messages)
        return self.replies.pop(0), {"input_tokens": 100, "output_tokens": 50}


class FakeVoice:
    def __init__(self):
        self.segments = None

    async def synthesize(self, segments, rate="-5%"):
        self.segments = segments
        return b"\xff\xf3" + b"\x00" * 100


def client_with(writer=None, voice=None, **overrides):
    app = create_app(settings(**overrides))
    client = TestClient(app)
    client.__enter__()
    if writer:
        app.state.generation_provider = writer
    if voice:
        app.state.speech_synthesizer = voice
    return client


def test_a_plan_that_passes_hands_back_the_topics_it_used():
    client = client_with(FakeWriter(json.dumps(PLAN)))

    body = client.post(
        "/internal/v1/generation/ielts/step",
        headers=HEADERS,
        json={"step": "plan", "used_topics": ["household waste recycling"]},
    ).json()

    assert body["problems"] == []
    assert "renting a kayak" in body["topics"] and len(body["topics"]) == 10
    assert body["model"] == "test-writer" and body["input_tokens"] == 100


def test_the_authors_brief_and_the_last_problems_reach_the_model():
    writer = FakeWriter(json.dumps(listening(1)))
    client = client_with(writer)

    client.post(
        "/internal/v1/generation/ielts/step",
        headers=HEADERS,
        json={
            "step": "listening-1",
            "plan": PLAN,
            "brief": "Make Part 1 about a dentist appointment.",
            "previous": {
                "reply": {"topic": "x"},
                "problems": ["Listening Part 1: give 2 speakers."],
            },
        },
    )

    sent = writer.sent[0]
    assert "dentist appointment" in sent[1]["content"]
    assert sent[-2]["role"] == "assistant" and sent[-1]["role"] == "user"
    assert "give 2 speakers" in sent[-1]["content"]


def test_problems_with_a_component_come_back_for_the_next_attempt():
    wrong = listening(1)
    wrong["sets"][0]["questions"][0]["answers"] = ["elephant"]
    client = client_with(FakeWriter(json.dumps(wrong)))

    body = client.post(
        "/internal/v1/generation/ielts/step",
        headers=HEADERS,
        json={"step": "listening-1", "plan": PLAN},
    ).json()

    assert any("'elephant' does not appear" in p for p in body["problems"])
    assert body["reply"]["topic"] == "listening topic 1"


def test_a_reply_that_is_not_json_is_kept_raw_with_the_reason():
    client = client_with(FakeWriter("Sorry, here is your test: ..."))

    body = client.post(
        "/internal/v1/generation/ielts/step", headers=HEADERS, json={"step": "plan"}
    ).json()

    assert body["reply"] is None
    assert body["raw"].startswith("Sorry")
    assert "could not be read" in body["problems"][0]


def test_assembly_builds_the_paper_tasks_chart_and_what_to_record():
    client = client_with()

    body = client.post(
        "/internal/v1/generation/ielts/assemble", headers=HEADERS, json={"components": components()}
    ).json()

    sections = body["paper"]["content"]["sections"]
    assert [s["maxRawScore"] for s in sections] == [40, 40]
    assert [t["taskType"] for t in body["tasks"]] == [
        "TASK_1",
        "TASK_2",
        "PART_1",
        "PART_2",
        "PART_3",
    ]
    assert base64.b64decode(body["chart_png_base64"])[:8] == b"\x89PNG\r\n\x1a\n"
    assert sorted(body["listening"]) == ["1", "2", "3", "4"]
    voices = {s["speaker"]: s["voice"] for s in body["listening"]["1"]}
    assert voices["Anna"] != voices["Ben"]
    assert body["problems"] == []


def test_assembly_refuses_an_incomplete_set():
    parts = components()
    del parts["reading-3"]
    client = client_with()

    response = client.post(
        "/internal/v1/generation/ielts/assemble", headers=HEADERS, json={"components": parts}
    )

    assert response.status_code == 422
    assert response.json()["code"] == "GENERATION_COMPONENTS_MISSING"


def test_a_part_is_recorded_with_each_speakers_voice():
    voice = FakeVoice()
    client = client_with(voice=voice)

    body = client.post(
        "/internal/v1/generation/ielts/audio",
        headers=HEADERS,
        json={
            "segments": [
                {"speaker": "Anna", "text": "Hello.", "voice": "en-GB-SoniaNeural"},
                {"speaker": "Ben", "text": "Hi there.", "voice": "en-AU-WilliamNeural"},
            ]
        },
    ).json()

    assert base64.b64decode(body["mp3_base64"])[:2] == b"\xff\xf3"
    assert voice.segments == [("Hello.", "en-GB-SoniaNeural"), ("Hi there.", "en-AU-WilliamNeural")]


def test_generation_is_refused_until_it_is_configured():
    client = client_with(generation_enabled=False)

    response = client.post(
        "/internal/v1/generation/ielts/step", headers=HEADERS, json={"step": "plan"}
    )

    assert response.status_code == 503
    assert response.json()["code"] == "AI_GENERATION_DISABLED"


def test_readiness_fails_when_generation_is_on_without_a_model():
    client = client_with(generation_model="")

    assert client.get("/health/ready").json()["checks"]["generation"] is False


def test_the_endpoints_need_the_internal_key():
    client = client_with()

    assert (
        client.post("/internal/v1/generation/ielts/step", json={"step": "plan"}).status_code == 401
    )
