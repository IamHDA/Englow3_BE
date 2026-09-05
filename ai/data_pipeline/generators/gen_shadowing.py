#!/usr/bin/env python3
"""Build 30 business-English shadowing/dictation clips."""

from __future__ import annotations

import datetime as dt
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    BatchMetadata, ModuleType, ShadowingBatch, ShadowingClip, ShadowingSegment,
)

OUT = ROOT / "output" / "shadowing" / "shadowing_batch_001.json"

# Four accents: 15 US, 5 UK, 5 AU, 5 CA. Scripts are intentionally written as
# short workplace monologues rather than fragments copied from the exam bank.
SCRIPTS = [
    ("B1", "US", "The weekly sales meeting has moved to Thursday morning. Please send your figures to Maria by Wednesday at noon. She will combine the regional results and prepare one short presentation. If your numbers are not final, mark them as estimates and explain when an update will be available."),
    ("B1", "US", "Passengers for flight two eighteen should proceed to gate fourteen. Boarding will begin with travelers who need extra assistance, followed by rows twenty through thirty. Please keep your passport and boarding pass ready. Large carry-on bags may need to be checked at the gate."),
    ("B1", "US", "Our office kitchen will be closed tomorrow while the water pipes are repaired. Staff may use the cafeteria on the ground floor between eight and four. Reusable cups will be available near the entrance. The kitchen is expected to reopen on Wednesday morning."),
    ("B1", "US", "Thank you for ordering a standing desk from North Street Furniture. The desk will arrive in two boxes on Friday afternoon. The delivery team can bring them upstairs, but assembly is not included. Please call us today if the building requires a delivery permit."),
    ("B1", "US", "The museum opens at ten, and the first guided tour starts fifteen minutes later. Visitors may leave coats and umbrellas in the free lockers. Photography is allowed in most galleries, but flash equipment is prohibited. The café beside the main entrance closes at five thirty."),
    ("B1", "US", "Before you submit an expense report, attach a clear image of every receipt. Choose the correct project code and enter the amount in the original currency. Your supervisor will review the request within three business days. Incomplete reports will be returned for correction."),
    ("B1", "US", "The customer service workshop begins at nine in Conference Room B. Participants will practice greeting clients, clarifying requests, and handling complaints calmly. Bring a notebook, because laptops will not be needed. A completion certificate will be emailed after the final activity."),
    ("B1", "US", "A maintenance technician will inspect the elevators this Saturday. One elevator will remain available while the other is tested. Please allow extra time if you are visiting an upper floor. Normal service should resume before the building opens on Monday."),
    ("B1", "US", "Your library card can now be renewed through the mobile application. Open your profile, confirm your address, and select a new expiration date. If you have unpaid charges, the application will direct you to the payment page. Assistance is available at the information desk."),
    ("B1", "US", "The hotel shuttle leaves the lobby every thirty minutes from six in the morning until midnight. Seats do not need to be reserved, but space for bicycles is limited. The trip to the airport usually takes twenty minutes. Heavy traffic may cause delays during the evening."),
    ("B2", "US", "We received several comments about the new inventory system, especially the number of steps required to record a return. The software team will simplify that process in next month's release. Until then, supervisors can approve a shortened form for low-value items. Updated instructions are available on the staff portal."),
    ("B2", "US", "The marketing survey will remain open for another week so that we can hear from smaller retailers. Early responses suggest that customers value faster delivery more than additional packaging options. The research team will verify the sample before drawing conclusions. A summary will be circulated after the data has been cleaned."),
    ("B2", "US", "Because the main conference hall is being renovated, the annual awards dinner will take place at the Riverside Hotel. The program and start time have not changed. Guests who requested parking will receive a separate confirmation. Any dietary changes must reach the events team by Monday."),
    ("B2", "US", "The finance department has introduced a monthly review of recurring subscriptions. Department heads should confirm that each service is still in use and assigned to an active employee. Duplicate tools should be consolidated where practical. The first review must be completed before the end of this quarter."),
    ("B2", "US", "Applicants will complete a short technical exercise before the second interview. The exercise is designed to show how candidates organize information and explain their decisions. It does not require knowledge of our internal software. Detailed instructions and sample input will be provided at the start."),
    ("B2", "UK", "The council is consulting local businesses about proposed changes to loading zones in the town centre. Deliveries would be permitted earlier in the morning, while several spaces would become short-stay parking after ten. Traders can comment through the online form. The consultation closes on the final Friday of this month."),
    ("B2", "UK", "Our supplier has confirmed that the replacement components meet the revised safety specification. A small trial shipment will arrive next Tuesday for inspection. If the quality team approves the samples, full production can restart immediately. This approach should prevent further disruption to customer orders."),
    ("B2", "UK", "The training platform now saves progress automatically at the end of each section. Learners can switch devices without repeating completed activities. Managers will still receive a weekly summary, but the report will show time spent as well as assessment results. Please report any missing records to technical support."),
    ("B2", "UK", "Several colleagues have asked whether flexible hours will continue after the office move. The policy itself is unchanged, although team coverage must be agreed in advance. Managers should discuss individual arrangements before publishing the monthly schedule. Human resources will answer questions that cannot be resolved within a team."),
    ("B2", "UK", "The exhibition catalogue went to print before the final group of photographs was licensed. Those images will instead appear in a digital supplement, together with an interview with the curator. Ticket holders can access the supplement using the code on their receipt. Printed copies will not be amended."),
    ("B2", "AU", "The coastal rail service will use replacement buses between Milton and Bayview this weekend. Buses will leave from the station forecourts and accept valid rail tickets. Travellers with large luggage should allow additional boarding time. The regular train timetable will resume with the first service on Monday."),
    ("B2", "AU", "We are trialling a booking system for shared meeting rooms across all three offices. Staff can reserve a room up to four weeks ahead and release it from the calendar if plans change. Unused bookings will be monitored during the trial. Feedback should focus on access, reliability, and ease of use."),
    ("C1", "AU", "The board has endorsed the expansion in principle, subject to a more detailed assessment of operating costs. Management will compare two leasing arrangements and stress-test the revenue forecast under weaker demand. No contract will be signed during this review. A final recommendation is expected at the September meeting."),
    ("C1", "AU", "Although the pilot reduced response times, it also shifted a disproportionate workload to the evening team. The next phase will therefore include staggered staffing and clearer escalation criteria. Performance will be measured against service quality, not speed alone. Employee feedback will be reviewed alongside customer outcomes."),
    ("C1", "AU", "The procurement panel found that all shortlisted vendors satisfied the mandatory technical requirements. The remaining evaluation will consider implementation risk, long-term support, and total cost of ownership. Panel members must declare any potential conflict before scoring begins. The recommendation and supporting rationale will be documented for audit."),
    ("C1", "CA", "The university intends to combine several small grants into a single interdisciplinary fund. Proposals will need to demonstrate shared methods rather than simply listing collaborators from different departments. Reviewers will assess feasibility, research value, and plans for public engagement. Detailed eligibility guidance will be published next week."),
    ("C1", "CA", "Our privacy review identified a gap between the information collected by the application and the wording of the customer notice. Development has paused the affected feature while legal counsel revises the disclosure. Existing records remain securely stored and access is restricted. Testing will resume only after the revised notice is approved."),
    ("C1", "CA", "The manufacturer is redesigning the package to reduce material use without compromising protection during transit. Engineers are comparing recycled fibre with a lighter composite insert. Each option will undergo vibration, moisture, and drop testing. The preferred design must also work on the current packing line without extensive modification."),
    ("C1", "CA", "The regional forecast assumes a gradual recovery in business investment, but acknowledges substantial uncertainty around energy prices. Analysts have prepared alternative scenarios rather than relying on a single projection. Departments should use the conservative case when planning discretionary expenditure. Essential services will be protected if revenue falls below expectations."),
    ("C1", "CA", "The mediation process is intended to clarify the parties' interests before they commit to a formal dispute. An independent facilitator will structure the discussion but will not impose a settlement. Participants may request a private session at any point. Any agreement must be recorded in writing and reviewed by both legal teams."),
]


def split_sentences(script: str) -> list[str]:
    return [x.strip() for x in re.findall(r"[^.!?]+[.!?]", script) if x.strip()]


def main() -> int:
    previous = {}
    if OUT.exists():
        old = json.loads(OUT.read_text(encoding="utf-8"))
        previous = {x["clip_id"]: x for x in old.get("clips", [])}
    clips = []
    for index, (level, accent, script) in enumerate(SCRIPTS, start=1):
        clip_id = f"shadow_{index:03d}"
        sentences = split_sentences(script)
        old = previous.get(clip_id, {})
        old_segments = old.get("segments", [])
        carry_timing = len(old_segments) == len(sentences) and old.get("script") == script
        segments = [ShadowingSegment(
            order=i, text=text,
            start_ms=(old_segments[i - 1].get("start_ms") if carry_timing else None),
            end_ms=(old_segments[i - 1].get("end_ms") if carry_timing else None),
        ) for i, text in enumerate(sentences, start=1)]
        clips.append(ShadowingClip(
            clip_id=clip_id, cefr_level=level, accent=accent, script=script,
            audio_url=old.get("audio_url") if carry_timing else None,
            duration_ms=old.get("duration_ms") if carry_timing else None,
            segments=segments,
            concept_ids=["sp_pronunciation", "sp_fluency", "lc_detail"],
            practice_modes=["shadowing", "dictation"], review_status="draft",
        ))
    if len(clips) != 30 or any(len(x.segments) != 4 for x in clips):
        raise RuntimeError("Shadowing blueprint must contain 30 four-segment clips")
    batch = ShadowingBatch(
        batch_metadata=BatchMetadata(
            batch_id="shadowing_batch_001", module_type=ModuleType.SHADOWING,
            generated_by="gen_shadowing.py/editorial-v1", generated_at=dt.datetime.now(dt.UTC),
            review_status="draft", total_records=len(clips)),
        clips=clips,
    )
    guarded_write_batch(batch, OUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
