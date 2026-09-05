#!/usr/bin/env python3
"""Author nine disjoint 200-question TOEIC-format practice-test banks.

This generator creates sets 002–010 only.  It intentionally leaves audio
alignment pending and Part 1 image files to the media phases; no record is
promoted beyond draft by automation.
"""

from __future__ import annotations

import hashlib
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import find_span, place_options, report_bias  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    Accent, AudioAsset, BatchMetadata, Definition, ExamBatch, ExamGroup,
    ExamItem, ModuleType, Option, Passage, QuestionType,
)
from schemas.enums import PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "expansion"
MEDIA_IMAGES = ROOT / "output" / "media" / "images" / "toeic" / "listening"
PUBLIC = "http://localhost:9000/images/toeic/listening"
Q = QuestionType
ACCENTS = [Accent.US, Accent.UK, Accent.US, Accent.AU, Accent.US, Accent.CA]
ACTIVE_COMPANY = ""

PROFILES = [
    dict(company="Northstar Mobility", city="Portland", person="Elena Ruiz", product="electric shuttle", project="Riverside route", venue="Harbor Hall", day="Tuesday", code="NM"),
    dict(company="Cedar & Finch Foods", city="Toronto", person="Marcus Lee", product="meal kit", project="cold-storage upgrade", venue="Maple Centre", day="Wednesday", code="CF"),
    dict(company="Bluehaven Analytics", city="Bristol", person="Priya Nair", product="forecasting dashboard", project="Orion rollout", venue="Clifton House", day="Thursday", code="BA"),
    dict(company="Solace Medical Supply", city="Melbourne", person="Noah Bennett", product="clinic cart", project="warehouse relocation", venue="Yarra Pavilion", day="Friday", code="SM"),
    dict(company="Aster Field Design", city="Vancouver", person="Sofia Chen", product="modular desk", project="studio renovation", venue="Granville Forum", day="Monday", code="AF"),
    dict(company="Redwood Maritime", city="Seattle", person="Owen Patel", product="tracking sensor", project="Pier Seven trial", venue="Sound Conference Center", day="Tuesday", code="RM"),
    dict(company="Lumen Public Media", city="Manchester", person="Amelia Brooks", product="audio guide", project="archive digitization", venue="Canal Arts Hub", day="Wednesday", code="LP"),
    dict(company="Meadowline Hotels", city="Sydney", person="Lucas Martin", product="guest-service tablet", project="lobby refurbishment", venue="Darling Exchange", day="Thursday", code="MH"),
    dict(company="Kiteworks Engineering", city="Calgary", person="Aisha Grant", product="inspection drone", project="Foothill survey", venue="Bow Valley Centre", day="Friday", code="KE"),
]

PHOTO_SCENES = [
    ("loading_dock", "A worker is securing a crate on a loading cart.", ["Several crates have been opened on the pavement.", "A delivery van is being washed near a gate.", "Two workers are painting a warehouse door."], Q.LC_PHOTO_ACTION),
    ("hotel_lobby", "A receptionist is handing a key card to a guest.", ["A guest is carrying chairs into an elevator.", "The reception counter has been covered with boxes.", "Several people are waiting outside a restaurant."], Q.LC_PHOTO_ACTION),
    ("park_bench", "A woman is reading a document beside a fountain.", ["A gardener is trimming a hedge with shears.", "Some children are feeding birds from a bridge.", "The benches are being moved into a building."], Q.LC_PHOTO_ACTION),
    ("workshop_shelves", "Several tools are arranged on shelves above a workbench.", ["A mechanic is lifting an engine from a car.", "The shelves are being taken down from a wall.", "Some boxes have been stacked in a doorway."], Q.LC_PHOTO_STATE),
    ("market_stall", "A vendor is weighing produce at an outdoor stall.", ["Customers are folding cloth on a table.", "A street has been blocked by construction equipment.", "Some baskets are being loaded into a bus."], Q.LC_PHOTO_ACTION),
    ("conference_room", "Notepads have been placed at each seat around a table.", ["A speaker is writing on a glass wall.", "The chairs are stacked against the windows.", "A meal is being served to a large audience."], Q.LC_PHOTO_STATE),
]
PHOTO_COLORS = ["blue", "red", "yellow", "green", "orange", "purple", "silver", "teal", "white"]
PHOTO_MARKERS = ["safety cone", "suitcase", "umbrella", "toolbox", "canopy", "folder"]
PHOTO_SET_SCENES = {
3: [
 ("projector_cart", "A technician is adjusting a projector on a cart.", ["A screen is being folded into a case.", "Several chairs are being carried through a doorway.", "A technician is painting the ceiling."], Q.LC_PHOTO_ACTION),
 ("courier_scanner", "A courier is scanning a parcel beside a hand truck.", ["A parcel is being opened on a counter.", "The hand truck is leaning against a vehicle.", "A customer is signing a restaurant bill."], Q.LC_PHOTO_ACTION),
 ("bakery_rack", "A baker is placing trays of bread on a cooling rack.", ["A customer is selecting fruit from a basket.", "The oven doors are being cleaned.", "Some plates have been stacked beside a sink."], Q.LC_PHOTO_ACTION),
 ("kayak_storage", "Several kayaks are stored on racks beside life jackets.", ["People are paddling across a lake.", "A boat is being lifted onto a trailer.", "Some jackets are hanging in a clothing shop."], Q.LC_PHOTO_STATE),
 ("garden_planters", "A gardener is watering raised planters with a hose.", ["A worker is sweeping leaves from a greenhouse.", "Several pots are being loaded into a car.", "A fountain is being repaired with a wrench."], Q.LC_PHOTO_ACTION),
 ("locker_room", "Several coats are hanging above a bench in a locker room.", ["Athletes are tying their shoes on a field.", "The lockers are being painted outdoors.", "A bench has been placed beside a bus stop."], Q.LC_PHOTO_STATE)],
4: [
 ("aircraft_cones", "Ground crew members are positioning cones near an aircraft.", ["Passengers are boarding through a glass bridge.", "A pilot is carrying luggage across a terminal.", "The aircraft is being painted inside a hangar."], Q.LC_PHOTO_ACTION),
 ("library_shelves", "A librarian is arranging books on a low shelf.", ["A reader is returning a laptop at a desk.", "The shelves are being moved into a hallway.", "Some books have been packed in suitcases."], Q.LC_PHOTO_ACTION),
 ("laboratory_samples", "A scientist is examining sample tubes under a lamp.", ["A patient is filling out a medical form.", "The laboratory windows are being washed.", "Some bottles are being delivered to a café."], Q.LC_PHOTO_ACTION),
 ("cafe_umbrellas", "Outdoor café tables are shaded by several umbrellas.", ["Diners are lining up inside a kitchen.", "The tables have been stacked on a truck.", "A server is closing all the umbrellas."], Q.LC_PHOTO_STATE),
 ("copier_paper", "An employee is loading paper into a copying machine.", ["A document is being pinned to a notice board.", "The machine is being carried downstairs.", "Several envelopes are being weighed."], Q.LC_PHOTO_ACTION),
 ("train_platform", "Passengers are standing beneath a platform canopy.", ["Rail workers are repairing a ticket machine.", "A train is being washed in a tunnel.", "The passengers are seated inside a bus."], Q.LC_PHOTO_STATE)],
5: [
 ("bicycle_repair", "A mechanic is tightening the wheel of a bicycle.", ["A cyclist is crossing a busy intersection.", "Several tires are being loaded into a van.", "A bicycle frame is hanging in a gallery."], Q.LC_PHOTO_ACTION),
 ("flower_bouquet", "A florist is wrapping a bouquet at a worktable.", ["A customer is watering plants on a balcony.", "Some flowers are being removed from a window box.", "A table is being covered with newspapers."], Q.LC_PHOTO_ACTION),
 ("parked_forklift", "A forklift is parked between rows of warehouse shelves.", ["A driver is lifting a pallet near the ceiling.", "The shelves have been emptied for painting.", "Several workers are pushing carts outdoors."], Q.LC_PHOTO_STATE),
 ("hotel_bed", "A housekeeper is smoothing a sheet across a hotel bed.", ["A guest is opening curtains beside a balcony.", "Several towels are being packed into a suitcase.", "The bed has been moved into a corridor."], Q.LC_PHOTO_ACTION),
 ("measuring_board", "A construction worker is measuring a wooden board.", ["A worker is drilling into a concrete ceiling.", "Some boards are being unloaded from a boat.", "The measuring tools have been locked in a cabinet."], Q.LC_PHOTO_ACTION),
 ("conference_audience", "Several attendees are seated facing a presentation screen.", ["The audience is standing around a buffet table.", "A screen is being removed from a wall.", "The chairs have been stacked behind a stage."], Q.LC_PHOTO_STATE)],
6: [
 ("coffee_pour", "A barista is pouring milk into a cup of coffee.", ["A customer is washing cups behind the counter.", "Coffee beans are being swept from the floor.", "A menu board is being carried outside."], Q.LC_PHOTO_ACTION),
 ("dock_rope", "A dock worker is coiling a rope beside a vessel.", ["A passenger is climbing a ladder onto the roof.", "Several ropes are being sold at a market stall.", "The vessel is being painted in an office."], Q.LC_PHOTO_ACTION),
 ("grocery_shelves", "An employee is stocking canned goods on supermarket shelves.", ["A shopper is weighing vegetables at checkout.", "The shelves are being dismantled near the entrance.", "Some cans have been placed in a refrigerator."], Q.LC_PHOTO_ACTION),
 ("museum_map", "A visitor is studying a map in a museum gallery.", ["An artist is hanging a painting in a studio.", "Several maps are being printed at a counter.", "The visitor is photographing an outdoor fountain."], Q.LC_PHOTO_ACTION),
 ("chart_meeting", "A woman is pointing to a chart during a meeting.", ["A chart is being rolled up for shipping.", "The participants are looking through restaurant menus.", "A woman is cleaning a glass partition."], Q.LC_PHOTO_ACTION),
 ("bus_luggage", "Suitcases are arranged in an open luggage compartment of a bus.", ["Passengers are collecting bags from an airport carousel.", "A driver is repairing the bus engine.", "The suitcases have been placed on hotel beds."], Q.LC_PHOTO_STATE)],
7: [
 ("camera_tripod", "A photographer is mounting a camera on a tripod.", ["A tourist is drawing a building in a notebook.", "Several cameras are displayed behind glass.", "A tripod is being folded inside a vehicle."], Q.LC_PHOTO_ACTION),
 ("bread_display", "Loaves of bread are arranged on tiered display racks.", ["A baker is slicing vegetables beside an oven.", "The racks are being moved into a warehouse.", "Customers are seated at outdoor tables."], Q.LC_PHOTO_STATE),
 ("sink_repair", "A plumber is working beneath a kitchen sink.", ["A chef is storing pans in an overhead cabinet.", "The floor is being polished by a hotel guest.", "A sink has been loaded onto a delivery truck."], Q.LC_PHOTO_ACTION),
 ("solar_panels", "Technicians are inspecting solar panels on a flat roof.", ["Workers are washing windows from the street.", "Several panels are stacked inside a classroom.", "The roof is being covered with garden soil."], Q.LC_PHOTO_ACTION),
 ("parcel_doorway", "A delivery worker is setting parcels beside an office doorway.", ["An employee is opening parcels at a reception desk.", "Several boxes are being carried onto a train.", "The doorway has been blocked with furniture."], Q.LC_PHOTO_ACTION),
 ("training_room", "Chairs are arranged in rows facing a whiteboard.", ["Students are moving desks into a corridor.", "The whiteboard is being covered with fabric.", "Several chairs have been turned toward the windows."], Q.LC_PHOTO_STATE)],
8: [
 ("vehicle_wash", "A worker is spraying water across the side of a van.", ["A mechanic is changing a tire inside a garage.", "The van is being loaded with office chairs.", "A driver is painting a line on the road."], Q.LC_PHOTO_ACTION),
 ("fabric_cutting", "A tailor is cutting fabric on a large table.", ["A customer is trying on a coat beside a mirror.", "Several rolls of fabric are being loaded outdoors.", "The table is being assembled with a drill."], Q.LC_PHOTO_ACTION),
 ("baggage_carousel", "Travelers are collecting suitcases from a baggage carousel.", ["An attendant is weighing bags at a check-in counter.", "The carousel is being repaired with a ladder.", "Passengers are boarding a ferry with bicycles."], Q.LC_PHOTO_ACTION),
 ("glassware_table", "A waiter is placing glasses on a dining table.", ["Diners are reading newspapers at a café.", "The glasses are being packed in a cardboard box.", "A tablecloth is hanging from a balcony."], Q.LC_PHOTO_ACTION),
 ("bottle_inspection", "A factory worker is inspecting bottles on a conveyor.", ["A cashier is scanning bottles in a grocery store.", "The conveyor has been covered with wooden boards.", "Several bottles are being washed in a restaurant sink."], Q.LC_PHOTO_ACTION),
 ("computer_desks", "Computer monitors have been placed on adjacent office desks.", ["Employees are carrying monitors into a lift.", "The desks are being painted beside a window.", "Several computers are displayed in a shop doorway."], Q.LC_PHOTO_STATE)],
9: [
 ("medical_supplies", "A nurse is arranging supplies on a treatment cart.", ["A patient is paying at a pharmacy counter.", "The cart is being pushed across a parking lot.", "Several boxes are being opened in a kitchen."], Q.LC_PHOTO_ACTION),
 ("ceiling_light", "An electrician is replacing a ceiling light from a ladder.", ["A painter is covering a ladder with cloth.", "The lights have been packed into a suitcase.", "An employee is cleaning the floor beneath a desk."], Q.LC_PHOTO_ACTION),
 ("farm_crates", "Farm workers are stacking vegetable crates beside a field.", ["Customers are selecting produce in a supermarket.", "The field is being watered by hand.", "Several crates are floating beside a dock."], Q.LC_PHOTO_ACTION),
 ("luggage_cart", "A hotel luggage cart is loaded with several suitcases.", ["A guest is unpacking clothes in a lobby.", "The cart is being repaired outside a station.", "Several suitcases are lined up on a runway."], Q.LC_PHOTO_STATE),
 ("document_scanner", "A woman is feeding a document into a desktop scanner.", ["An employee is shredding paper beside a printer.", "The scanner is being wrapped for delivery.", "Several documents are pinned above a sink."], Q.LC_PHOTO_ACTION),
 ("marina_boats", "Small boats are moored along a marina walkway.", ["Passengers are boarding a large cruise ship.", "A walkway is being lifted by a crane.", "Several boats are displayed inside a warehouse."], Q.LC_PHOTO_STATE)],
10: [
 ("vegetable_prep", "A chef is chopping vegetables on a cutting board.", ["A server is carrying plates into a dining room.", "Vegetables are being weighed at a market stall.", "The cutting board is being washed outdoors."], Q.LC_PHOTO_ACTION),
 ("wood_sanding", "A carpenter is sanding the edge of a wooden cabinet.", ["A customer is opening drawers in a furniture shop.", "The cabinet is being loaded onto a bicycle.", "Several boards are stacked across a doorway."], Q.LC_PHOTO_ACTION),
 ("waiting_area", "Armchairs are lined up beside the windows of a waiting area.", ["Visitors are moving chairs onto a stage.", "The windows are being covered with posters.", "Several armchairs have been stacked in a storeroom."], Q.LC_PHOTO_STATE),
 ("helmet_rack", "Cycling helmets are displayed on a wall-mounted rack.", ["Cyclists are riding through a city intersection.", "The rack is being carried into a van.", "Several hats are hanging above a restaurant counter."], Q.LC_PHOTO_STATE),
 ("window_display", "A shop employee is adjusting clothing on a mannequin.", ["A customer is folding clothes at a checkout counter.", "The mannequin is being loaded onto a bus.", "Several windows are being washed from a ladder."], Q.LC_PHOTO_ACTION),
 ("snowy_walkway", "A worker is clearing snow from a building walkway.", ["A gardener is watering flowers beside the entrance.", "The walkway is being covered with carpet.", "Several people are carrying skis into an office."], Q.LC_PHOTO_ACTION)],
}

P2_PATTERNS = [
    ("Where should I leave the {product} samples?", "On the shelf beside the mailroom.", "About twelve kilograms.", "No, the samples arrived early.", Q.LC_WH_QUESTION),
    ("Who approved the budget for the {project}?", "{person} signed it yesterday.", "At the finance counter.", "The figures were quite detailed.", Q.LC_WH_QUESTION),
    ("When will the {venue} reopen?", "Early next {day} morning.", "Near the central station.", "A local contractor did.", Q.LC_WH_QUESTION),
    ("Why was the client presentation postponed?", "Two of the reviewers are still travelling.", "In the smaller meeting room.", "It lasted nearly an hour.", Q.LC_WH_QUESTION),
    ("How often is the inventory report updated?", "Every other business day.", "By the inventory supervisor.", "Yes, the figures are accurate.", Q.LC_WH_QUESTION),
    ("Which entrance leads to the exhibition hall?", "The one facing Oak Street.", "Admission is free before noon.", "A guide will meet the group.", Q.LC_WH_QUESTION),
    ("Could you reserve a table for the supplier luncheon?", "Certainly—how many guests are coming?", "The supplier sent a revised invoice.", "It is beside the window display.", Q.LC_INDIRECT_RESPONSE),
    ("Has the courier collected parcel {code}-47?", "Not yet, but she is due at three.", "The collection contains six pieces.", "At the rear loading bay.", Q.LC_YES_NO),
    ("Didn't {person} attend the safety briefing?", "No, a site visit kept {first} away.", "The briefing room seats thirty.", "Please attach the safety label.", Q.LC_INDIRECT_RESPONSE),
    ("Would you mind checking these expense totals?", "I can do that after lunch.", "The total was printed in blue.", "No, the café is downstairs.", Q.LC_INDIRECT_RESPONSE),
    ("Where can visitors charge their phones?", "There are outlets beside the lounge seats.", "They visited during the morning.", "The new phones are lighter.", Q.LC_WH_QUESTION),
    ("Who is taking minutes at the planning session?", "Darius offered to record them.", "The session begins at ten.", "Only the last twenty minutes.", Q.LC_WH_QUESTION),
    ("When do applications for the internship close?", "At five o'clock on the final Friday.", "The applicants have varied experience.", "Through the careers portal.", Q.LC_WH_QUESTION),
    ("Why don't we move the display closer to the entrance?", "That would make it easier to notice.", "The entrance closes automatically.", "A display of local photographs.", Q.LC_INDIRECT_RESPONSE),
    ("How much did the replacement projector cost?", "Just under nine hundred dollars.", "It projects a very sharp image.", "The technician replaced it.", Q.LC_WH_QUESTION),
    ("Which train stops nearest the {venue}?", "Take the green-line service.", "The venue holds six hundred people.", "The stop was recently renovated.", Q.LC_WH_QUESTION),
    ("Is the revised brochure ready for printing?", "The designer is making one final correction.", "Beside the colour printer.", "It describes the spring programme.", Q.LC_INDIRECT_RESPONSE),
    ("Can the maintenance crew work after closing time?", "Yes, security has been notified.", "The shop closes at seven.", "They repaired the lift last month.", Q.LC_YES_NO),
    ("Where did you find this conference badge?", "It was underneath a seat in Hall C.", "The conference attracted many guests.", "I prefer the blue badge.", Q.LC_WH_QUESTION),
    ("Who should receive the signed rental agreement?", "Send it directly to the property manager.", "The rent is due each month.", "We agreed to extend the lease.", Q.LC_WH_QUESTION),
    ("When are the laboratory results expected?", "They should arrive by midday tomorrow.", "The laboratory is across town.", "A specialist reviewed the sample.", Q.LC_WH_QUESTION),
    ("Why is the north car park closed?", "The lighting system is being replaced.", "Parking permits are sold online.", "It is north of the main office.", Q.LC_WH_QUESTION),
    ("How did the customer hear about our {product}?", "She saw it demonstrated at a trade fair.", "The product comes with a warranty.", "We could hear her clearly.", Q.LC_WH_QUESTION),
    ("Would Thursday afternoon suit the inspection team?", "They asked for a morning appointment instead.", "The inspection covered every floor.", "A suit is not required.", Q.LC_INDIRECT_RESPONSE),
    ("Haven't the new signs been installed yet?", "The mounting brackets arrived late.", "Yes, the instructions are clear.", "They signed the form together.", Q.LC_INDIRECT_RESPONSE),
]


def opts(part: int, index: int, seed: str, correct: str, distractors: list[str]):
    distractors = list(distractors)
    # Prevent a test-taking shortcut in which the longest choice is usually
    # correct.  Extend a plausible distractor on alternating comprehension
    # items; grammar choices are left morphologically clean.
    if part in (1, 2, 3, 4, 7) and index % 2 == 0 \
            and len(correct) >= max(map(len, distractors)):
        distractors[0] = distractors[0].rstrip(".") + ", according to an earlier notice."
    rationale_correct = "Đáp án này phù hợp trực tiếp với thông tin và ngữ cảnh của câu hỏi."
    raw = [(correct, True, rationale_correct)] + [
        (text, False, "Phương án này là bẫy hợp ngữ pháp nhưng không khớp thông tin cần hỏi.")
        for text in distractors
    ]
    placed = place_options(index, seed, raw)
    labels = "ABC" if part == 2 else "ABCD"
    return [Option(label=label, text=text, is_correct=right, rationale_vi=why)
            for label, (text, right, why) in zip(labels, placed)]


def item(part, index, seed, text, qtype, concept, difficulty, correct, distractors,
         evidence=None):
    if ACTIVE_COMPANY.casefold() not in text.casefold():
        if part in (3, 4):
            text = f"In the {ACTIVE_COMPANY} recording, {text[0].lower() + text[1:]}"
        elif part == 5:
            text = f"At {ACTIVE_COMPANY}, {text[0].lower() + text[1:]}"
        elif part == 6:
            text = f"In the {ACTIVE_COMPANY} message, {text[0].lower() + text[1:]}"
        elif part == 7:
            text = f"According to the {ACTIVE_COMPANY} document, {text[0].lower() + text[1:]}"
    jitter_bucket = int(hashlib.sha256(seed.encode("utf-8")).hexdigest()[:2], 16) % 9 - 4
    difficulty = min(0.84, max(0.22, difficulty + jitter_bucket * 0.018))
    return ExamItem(
        part_number=part, question_text=text, question_type=qtype,
        options=opts(part, index, seed, correct, distractors),
        concept_ids=[concept], difficulty_prior=difficulty,
        evidence_span=evidence,
        explanation=Definition(
            en=f'The correct answer is "{correct}".',
            vi="Đáp án đúng được xác định từ cấu trúc hoặc bằng chứng nêu trong ngữ cảnh."),
    )


def add_part1(groups, profile, set_no):
    scenes = PHOTO_SET_SCENES.get(set_no, PHOTO_SCENES)
    for index, (slug, correct, distractors, qtype) in enumerate(scenes):
        filename = f"set{set_no:03d}_{slug}.jpg"
        if set_no == 2:
            color = PHOTO_COLORS[set_no - 2]
            correct = correct.rstrip(".") + f" beside a {color} {PHOTO_MARKERS[index]}."
        descriptions = opts(1, index, filename, correct, distractors)
        script = " ".join(f"({option.label}) {option.text}" for option in descriptions)
        groups.append(ExamGroup(
            part_number=1,
            image_url=f"{PUBLIC}/part1/{filename}",
            audio=AudioAsset(script=script, accent=ACCENTS[index], speaker_count=1),
            questions=[ExamItem(
                part_number=1, question_text=None, question_type=qtype,
                options=descriptions,
                concept_ids=[qtype.value], difficulty_prior=0.28 + index * 0.025,
                explanation=Definition(
                    en=f'The photograph is best described by: "{correct}".',
                    vi="Mô tả đúng khớp cả hành động, đồ vật và vị trí trong ảnh."),
            )],
        ))


def add_part2(groups, profile, set_no):
    values = dict(profile, first=profile["person"].split()[0])
    for index, (prompt, correct, wrong1, wrong2, qtype) in enumerate(P2_PATTERNS):
        prompt, correct, wrong1, wrong2 = [text.format(**values)
                                           for text in (prompt, correct, wrong1, wrong2)]
        if profile["company"].casefold() not in prompt.casefold():
            prompt = f"At {profile['company']}, {prompt[0].lower() + prompt[1:]}"
        options = opts(2, index, f"{set_no}-{prompt}", correct, [wrong1, wrong2])
        script = prompt + "\n" + "\n".join(
            f"({option.label}) {option.text}" for option in options)
        groups.append(ExamGroup(
            part_number=2,
            audio=AudioAsset(script=script, accent=ACCENTS[(index + 6) % 6],
                             speaker_count=2),
            questions=[ExamItem(
                part_number=2, question_text=prompt, question_type=qtype,
                options=options, concept_ids=[qtype.value],
                difficulty_prior=0.31 + (index % 9) * 0.045,
                explanation=Definition(
                    en=f'The best response is "{correct}".',
                    vi="Câu trả lời đúng đáp ứng đúng loại câu hỏi và ngữ cảnh giao tiếp."),
            )],
        ))


def graphic_url(set_no: int, kind: str) -> str:
    return f"{PUBLIC}/graphics/set{set_no:03d}_{kind}.svg"


def add_part3(groups, profile, set_no):
    topics = [
        "a delayed component delivery", "a revised workshop schedule",
        "an incorrect catering invoice", "a visitor parking request",
        "a software access problem", "a damaged exhibition panel",
        "a customer training appointment", "a change to a shipping route",
        "an office furniture order", "a community event permit",
        "a product demonstration", "a staff travel booking", "a printing deadline",
    ]
    for index, topic in enumerate(topics):
        ref = f"{profile['code']}-{set_no}{index + 21}"
        script = (
            f"W: I have an update about {topic} for {profile['company']}. The reference is {ref}.\n"
            f"M: Does it affect the work planned for {profile['day']}?\n"
            f"W: Yes. We need to move the appointment to {(index % 5) + 1}:30 P.M. and notify {profile['person']}.\n"
            f"M: I will revise the shared calendar and send the notice before lunch."
        )
        questions = [
            item(3, index * 3, script, f"What issue involving {topic} are the speakers discussing?", Q.LC_GIST,
                 "lc_gist", 0.38, topic.capitalize(),
                 ["A recruitment campaign", "A building purchase", "A payroll discrepancy"]),
            item(3, index * 3 + 1, script, f"What time is the appointment about {topic} being moved to?", Q.LC_DETAIL,
                 "lc_detail", 0.44, f"{(index % 5) + 1}:30 P.M.",
                 ["8:00 A.M.", "10:15 A.M.", "4:45 P.M."]),
        ]
        is_graphic = index in (4, 10)
        third_type = Q.LC_GRAPHIC_REFERENCE if is_graphic else Q.LC_NEXT_ACTION
        third_text = (f"Look at the reference list. Which entry is associated with {topic}?"
                      if is_graphic else f"What will the man most likely do next concerning {topic}?")
        third_correct = ref if is_graphic else "Update the calendar and send a notice"
        third_wrong = ([f"{profile['code']}-{set_no}{index + 20}", f"{profile['code']}-{set_no}{index + 22}", f"{profile['code']}-{set_no}{index + 23}"]
                       if is_graphic else ["Cancel the entire project", "Prepare a payroll report", "Visit a property agent"])
        questions.append(item(3, index * 3 + 2, script, third_text, third_type,
                              third_type.value, 0.52, third_correct, third_wrong))
        groups.append(ExamGroup(
            part_number=3,
            image_url=graphic_url(set_no, f"dialogue_{index + 1:02d}") if is_graphic else None,
            audio=AudioAsset(script=script, accent=ACCENTS[(index + 31) % 6], speaker_count=2),
            questions=questions,
        ))


def add_part4(groups, profile, set_no):
    formats = ["museum announcement", "voicemail", "radio advertisement", "staff briefing",
               "tour introduction", "traffic report", "award presentation", "store announcement",
               "conference introduction", "recorded service update"]
    for index, fmt in enumerate(formats):
        code = f"{profile['code']}{set_no}{index + 61}"
        topic = f"the {profile['project']}"
        time = f"{(index % 4) + 8}:20 A.M."
        facts = dict(topic=topic, city=profile["city"], venue=profile["venue"], time=time,
                     code=code, person=profile["person"], day=profile["day"])
        talk_templates = [
            "Welcome. Today's programme covers {topic} in {city}. Meet the guide at {venue} by {time} and show confirmation {code}. Renovation has closed the east doors, so use the garden entrance. Send accessibility questions to {person} before {day}.",
            "Hello, this is a message about {topic}. Your appointment at {venue} starts after check-in at {time}. Quote {code} at the desk. Please approach through the garden entrance while repairs continue on the east side. {person} can answer questions until {day}.",
            "Looking for information about {topic} in {city}? A briefing begins at {venue} at {time}; confirmation {code} secures admission. The garden entrance is open, but the east entrance is not. Contact {person} before {day} for assistance.",
            "Before work begins on {topic}, the team will assemble at {venue}. Check-in closes at {time}, and the register lists code {code}. Contractors must enter through the garden entrance because the east corridor is closed. Raise questions with {person} before {day}.",
            "Our visit concerning {topic} starts from {venue} at {time}. Keep confirmation {code} available for the guide. The garden entrance is the temporary meeting point during east-door repairs. If anything is unclear, write to {person} before {day}.",
            "Traffic near {venue} will be heavier because of {topic}. Attendees due at {time} should allow extra travel time and retain code {code}. On arrival, use the garden entrance rather than the east entrance. Route enquiries go to {person} before {day}.",
            "We are pleased to recognise the team behind {topic}. Guests should be seated at {venue} by {time} and present confirmation {code}. Access is through the garden entrance while the east foyer is renovated. Direct advance questions to {person} before {day}.",
            "Attention shoppers: today's information session on {topic} begins at {time} inside {venue}. Staff will check confirmation {code} at the garden entrance; the east entrance is closed for repairs. {person} will accept questions until {day}.",
            "Our next presenter will explain {topic} and its impact in {city}. Delegates must reach {venue} by {time} with confirmation {code}. Please enter through the garden entrance, not the construction area to the east. Submit questions to {person} before {day}.",
            "This recorded update concerns support for {topic}. Service begins at {venue} at {time}, and callers should note confirmation {code}. The garden entrance remains available during east-side maintenance. {person} will respond to messages received before {day}.",
        ]
        script = talk_templates[index].format(**facts)
        questions = [
            item(4, index * 3, script, f"Why is the speaker giving this {fmt}?", Q.LC_GIST,
                 "lc_gist", 0.37, f"To provide instructions about {topic}",
                 ["To announce a company merger", "To advertise a residential property", "To request a tax refund"]),
            item(4, index * 3 + 1, script, f"Which entrance should listeners to this {fmt} use?", Q.LC_DETAIL,
                 "lc_detail", 0.43, "The garden entrance",
                 ["The east entrance", "The loading entrance", "The theatre entrance"]),
        ]
        is_graphic = index in (2, 7)
        qtype = Q.LC_GRAPHIC_REFERENCE if is_graphic else Q.LC_DETAIL
        qtext = (f"Look at the confirmation chart. Which code applies to this {fmt}?"
                 if is_graphic else f"By when should questions about this {fmt} be submitted?")
        correct = code if is_graphic else f"Before {profile['day']}"
        wrong = ([f"{profile['code']}{set_no}{index + 60}", f"{profile['code']}{set_no}{index + 62}", f"{profile['code']}{set_no}{index + 63}"]
                 if is_graphic else ["After the event", "At the end of the month", "During the next quarter"])
        questions.append(item(4, index * 3 + 2, script, qtext, qtype, qtype.value,
                              0.50, correct, wrong))
        groups.append(ExamGroup(
            part_number=4,
            image_url=graphic_url(set_no, f"talk_{index + 1:02d}") if is_graphic else None,
            audio=AudioAsset(script=script, accent=ACCENTS[(index + 44) % 6], speaker_count=1),
            questions=questions,
        ))


P5_FORMS = [
    ("All {company} supervisors must submit travel requests ____ noon on {day}.", "by", ["among", "during", "upon"], Q.GR_PREPOSITION, "gram_preposition_time_advanced"),
    ("The new {product} is considerably ____ than the earlier model.", "lighter", ["light", "lightest", "lightly"], Q.GR_COMPARISON, "gram_comparative_adj"),
    ("{person} ____ the supplier before the revised contract was issued.", "had contacted", ["contacts", "will contact", "is contacting"], Q.GR_TENSE, "gram_past_perfect"),
    ("Visitors to {venue} are asked to keep their badges visible ____ they remain on site.", "while", ["despite", "unless", "because of"], Q.GR_CONJUNCTION, "gram_conjunction_subordinating"),
    ("The {company} design was approved after a ____ review of the safety notes.", "thorough", ["thoroughly", "thoroughness", "thoroughing"], Q.GR_WORD_FORM, "gram_word_form_adj"),
    ("Neither the {company} consultant nor the engineers ____ available this afternoon.", "are", ["is", "be", "being"], Q.GR_TENSE, "gram_subject_verb_agreement"),
    ("The shipment will be inspected ____ it reaches the {city} depot.", "when", ["although", "whereas", "nevertheless"], Q.GR_CONJUNCTION, "gram_time_clause_future"),
    ("{company} customers may return the device in ____ original packaging within fourteen days.", "its", ["it", "itself", "they"], Q.GR_PRONOUN, "gram_pronoun_possessive"),
    ("The {company} committee recommended that the estimate ____ before publication.", "be revised", ["is revised", "revises", "revising"], Q.GR_VOICE, "gram_subjunctive_mandative"),
    ("Demand for the service rose ____ after the demonstration at {venue}.", "sharply", ["sharp", "sharpen", "sharpness"], Q.GR_WORD_FORM, "gram_word_form_adverb"),
]


def add_part5(groups, profile, set_no):
    for index in range(30):
        template, correct, wrong, qtype, concept = P5_FORMS[index % len(P5_FORMS)]
        cycle = index // len(P5_FORMS)
        local = dict(profile)
        if cycle == 1:
            local.update(company=f"{profile['company']} regional office",
                         product=f"compact {profile['product']}",
                         project=f"second phase of the {profile['project']}",
                         venue=f"the annex at {profile['venue']}")
        elif cycle == 2:
            local.update(company=f"{profile['company']} operations unit",
                         product=f"upgraded {profile['product']}",
                         project=f"final phase of the {profile['project']}",
                         venue=f"the east wing of {profile['venue']}")
        stem = template.format(**local)
        if cycle == 1:
            stem = "For the regional review, " + stem[0].lower() + stem[1:]
        elif cycle == 2:
            stem = "As part of the annual audit, " + stem[0].lower() + stem[1:]
        stem = stem.replace("fourteen", str(12 + set_no + index)) if index >= 10 else stem
        stem = stem.replace("this afternoon", f"on {profile['day']} afternoon") if index >= 20 else stem
        groups.append(ExamGroup(
            part_number=5,
            passages=[Passage(order=1, passage_type=PassageType.NOTICE, text=stem)],
            questions=[item(5, index, f"{set_no}-{stem}", stem, qtype, concept,
                            0.32 + (index % 10) * 0.045, correct, wrong)],
        ))


def add_part6(groups, profile, set_no):
    subjects = ["supplier orientation", "facility inspection", "customer survey", "professional workshop"]
    for group_no, subject in enumerate(subjects):
        ref = f"{profile['code']}-P{set_no}{group_no + 1}"
        passage = (
            f"Subject: {subject.title()} ({ref})\n\n"
            f"{profile['company']} will conduct its {subject} at {profile['venue']} next {profile['day']}. "
            "All participants should arrive ____ (1) so that registration can be completed before the opening session. "
            "The coordinator has ____ (2) a checklist to every department. "
            "Please review it carefully, ____ (3) several requirements have changed. "
            "____ (4) Contact {person} if you need an accessible entrance or reserved seating."
        ).format(person=profile["person"])
        specs = [
            (f"Which word best completes blank (1) in the {subject} message?", Q.GR_WORD_FORM, "gram_word_form_adverb", "promptly", ["prompt", "promptness", "prompting"]),
            (f"Which word best completes blank (2) in the {subject} message?", Q.GR_TENSE, "gram_present_perfect", "sent", ["send", "sending", "sends"]),
            (f"Which word best completes blank (3) in the {subject} message?", Q.DS_COHESION, "gram_linking_cause_result", "because", ["unless", "meanwhile", "otherwise"]),
            (f"Which sentence best completes blank (4) in the {subject} message?", Q.DS_SENTENCE_INSERTION, "rc_sentence_insertion", "A detailed map is attached to this message.", ["The cafeteria sold out of soup yesterday.", "Several employees commute by bicycle.", "The quarterly profit figures were confidential."]),
        ]
        questions = [item(6, group_no * 4 + i, f"{set_no}-{passage}-{i}", text, qtype,
                          concept, 0.38 + i * 0.08, correct, wrong)
                     for i, (text, qtype, concept, correct, wrong) in enumerate(specs)]
        groups.append(ExamGroup(
            part_number=6,
            passages=[Passage(order=1, passage_type=[PassageType.EMAIL, PassageType.NOTICE,
                                                     PassageType.MEMO, PassageType.LETTER][group_no],
                              text=passage)],
            questions=questions,
        ))


def p7_item(index, seed, passage, text, qtype, difficulty, correct, wrong, quote,
            order=1):
    return item(7, index, seed, text, qtype, qtype.value, difficulty, correct, wrong,
                find_span(passage, quote, order))


def add_part7(groups, profile, set_no):
    single_kinds = ["email", "notice", "advertisement", "article", "memo",
                    "schedule", "web page", "letter", "invoice", "chat message"]
    for index, kind in enumerate(single_kinds):
        ref = f"{profile['code']}-R{set_no}{index + 10}"
        detail = f"Room {(index % 5) + 2}B at {profile['venue']}"
        facts = dict(kind=kind.title(), ref=ref, company=profile["company"],
                     project=profile["project"], city=profile["city"], detail=detail,
                     day=profile["day"], time=f"{(index % 4) + 9}:15 A.M.",
                     person=profile["person"])
        single_templates = [
            "{kind} — Reference {ref}\n{company} is preparing {project} in {city}. The next session will take place in {detail} on {day} at {time}. Attendees must confirm their places with {person} two days beforehand. The new procedure will streamline check-in and preserve time for questions.",
            "{kind} [{ref}]\nTo support {company} in preparing {project}, a briefing has been arranged in {detail}. It begins at {time} on {day}. Please confirm their places with {person} no later than two days before the event. This timetable should streamline check-in for everyone.",
            "{kind}: {project}\n{company} is preparing {project} for its {city} team. Join us at {time} on {day} in {detail}. Attendees should confirm their places through {person} at least forty-eight hours early. Organisers expect the change to streamline check-in.",
            "{kind} / {ref}\nPreparations for {project} are under way at {company}. {person} will host the {day} session at {time}; the assigned location is {detail}. Attendees must confirm their places in advance. The update is designed to streamline check-in.",
            "{kind} — {company}\nWhile preparing {project} in {city}, the team scheduled a question session for {day}. It starts at {time} in {detail}. Attendees must confirm their places with {person}. Earlier responses will streamline check-in at the venue.",
            "{kind} {ref}\n{day}: {time} — {detail}. This appointment forms part of {company} preparing {project} in {city}. Attendees are required to confirm their places with {person} two days in advance so staff can streamline check-in.",
            "{kind}\nThe {city} office of {company} is preparing {project}. A working session has therefore been set for {time} on {day} in {detail}. Attendees must confirm their places with {person}; doing so will streamline check-in.",
            "{kind} (Ref. {ref})\nThank you for helping {company} while it is preparing {project}. Please come to {detail} at {time} on {day}. Attendees must confirm their places with {person} forty-eight hours beforehand. The revised plan will streamline check-in.",
            "{kind}: Registration information\n{company} is preparing {project} and will meet participants in {detail}. The doors open on {day} for a {time} start. Attendees must confirm their places with {person}. Advance confirmation should streamline check-in.",
            "{kind} thread — {ref}\n{person}: We are preparing {project} for {company}.\nCoordinator: I reserved {detail} for {day} at {time}.\n{person}: Good. Attendees must confirm their places two days early; that will streamline check-in.",
        ]
        passage = single_templates[index].format(**facts)
        questions = [
            p7_item(index * 3, passage, passage, f"Why was this {kind} written?", Q.RC_MAIN_IDEA,
                    0.38, f"To give arrangements for {profile['project']}",
                    ["To advertise a vacant apartment", "To report a factory accident", "To request a bank loan"],
                    profile["project"]),
            p7_item(index * 3 + 1, passage, passage, f"Where will the session described in the {kind} be held?", Q.RC_DETAIL,
                    0.43, detail, ["At the central station", "In the city library", "At a riverside warehouse"], detail),
        ]
        if index < 9:
            if index == 0:
                qtype, qtext, correct, wrong, quote = (Q.RC_VOCAB_IN_CONTEXT,
                    'The word "streamline" is closest in meaning to', "make more efficient",
                    ["postpone indefinitely", "increase in price", "divide into teams"], "streamline")
            elif index == 1:
                qtype, qtext, correct, wrong, quote = (Q.RC_PARAPHRASE,
                    "What is indicated about registration?", "It must be completed before the event.",
                    ["It is limited to managers.", "It requires a cash payment.", "It opens after the session."],
                    "confirm their places")
            elif index == 2:
                qtype, qtext, correct, wrong, quote = (Q.RC_SENTENCE_INSERTION,
                    "Which sentence would best follow the third sentence?", "A reply with the reference number is sufficient.",
                    ["The building was sold ten years ago.", "Local trains run every half hour.", "The product weighs less than a kilogram."],
                    "confirm their places")
            else:
                qtype, qtext, correct, wrong, quote = (Q.RC_INFERENCE,
                    f"What can be inferred about the revised arrangement in the {kind}?", "It should reduce waiting time.",
                    ["It requires a larger budget.", "It was requested by a landlord.", "It applies only to new employees."],
                    "streamline check-in")
            questions.append(p7_item(index * 3 + 2, passage, passage, qtext, qtype, 0.55,
                                     correct, wrong, quote))
        groups.append(ExamGroup(
            part_number=7,
            passages=[Passage(order=1, passage_type=PassageType.EMAIL, text=passage)],
            questions=questions,
        ))

    for index in range(5):
        ref = f"{profile['code']}-M{set_no}{index + 1}"
        extras = [
            ("The technician will test the equipment after arrival.", "The driver has already received a parking map."),
            ("Each case bears a numbered security seal.", "Venue staff will provide a trolley at the door."),
            ("The demonstration begins the next evening.", "A receptionist will record the delivery time."),
            ("The operations team has cleared space beside the stage.", "The carrier confirmed that the cables are undamaged."),
            ("A presenter will collect the materials after check-in.", "The west desk is staffed until five o'clock."),
        ][index]
        first = (
            f"Email — {ref}\n{profile['person']} asks the operations team to deliver the "
            f"{profile['product']} demonstration materials to {profile['venue']} by {profile['day']} at 3:00 P.M. "
            f"{extras[0]} The blue cases contain display units; the grey cases contain cables."
        )
        second = (
            f"Delivery update — {ref}\n{extras[1]} The carrier will reach {profile['venue']} at 2:20 P.M. "
            f"A loading permit is waiting at the west desk. Because one blue case is delayed in {profile['city']}, "
            "the driver will bring the remaining display unit on the following morning."
        )
        questions = [
            p7_item(index * 5, first, first, f"What does the {ref} email request?", Q.RC_MAIN_IDEA, 0.40,
                    "Delivery of demonstration materials", ["Payment of a membership fee", "Repair of an office lift", "Publication of a job advertisement"], "deliver the", 1),
            p7_item(index * 5 + 1, first, first, f"According to {ref}, what is stored in the grey cases?", Q.RC_DETAIL, 0.43,
                    "Cables", ["Display units", "Printed permits", "Protective uniforms"], "grey cases contain cables", 1),
            p7_item(index * 5 + 2, first + second, first, f"What can be concluded from both {ref} documents?", Q.RC_CROSS_REFERENCE, 0.60,
                    "Most materials will arrive before the requested deadline.", ["The event has been cancelled.", "The venue has no loading area.", "All blue cases will arrive together."], "by {day} at 3:00 P.M.".format(**profile), 1),
            p7_item(index * 5 + 3, first + second, second, f"Which {ref} item will arrive separately?", Q.RC_CROSS_REFERENCE, 0.62,
                    "One display unit", ["All of the cables", "The loading permit", "The grey cases"], "one blue case is delayed", 2),
            p7_item(index * 5 + 4, second, second, f"Where can the {ref} driver collect the permit?", Q.RC_DETAIL, 0.48,
                    "At the west desk", ["At the east entrance", "In the conference room", "At the carrier's depot"], "west desk", 2),
        ]
        groups.append(ExamGroup(
            part_number=7,
            passages=[Passage(order=1, passage_type=PassageType.EMAIL, text=first),
                      Passage(order=2, passage_type=PassageType.NOTICE, text=second)],
            questions=questions,
        ))


def write_graphics(profile, set_no):
    graphic_dir = MEDIA_IMAGES / "graphics"
    graphic_dir.mkdir(parents=True, exist_ok=True)
    entries = [(4, "dialogue_05"), (10, "dialogue_11"), (2, "talk_03"), (7, "talk_08")]
    for value, name in entries:
        codes = [f"{profile['code']}-{set_no}{value + offset + 20}" for offset in range(4)]
        if name.startswith("talk"):
            codes = [f"{profile['code']}{set_no}{value + offset + 61}" for offset in range(4)]
        rows = "".join(f'<text x="45" y="{85 + i * 45}" font-size="22">{code}</text>'
                       for i, code in enumerate(codes))
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="700" height="320">'
               f'<rect width="100%" height="100%" fill="#f7f5ef"/>'
               f'<text x="35" y="42" font-size="25" font-weight="bold">Reference list — {profile["company"]}</text>'
               f'{rows}</svg>')
        (graphic_dir / f"set{set_no:03d}_{name}.svg").write_text(svg, encoding="utf-8")


def main() -> int:
    global ACTIVE_COMPANY
    OUT.mkdir(parents=True, exist_ok=True)
    for offset, profile in enumerate(PROFILES, start=2):
        ACTIVE_COMPANY = profile["company"]
        groups = []
        add_part1(groups, profile, offset)
        add_part2(groups, profile, offset)
        add_part3(groups, profile, offset)
        add_part4(groups, profile, offset)
        add_part5(groups, profile, offset)
        add_part6(groups, profile, offset)
        add_part7(groups, profile, offset)
        out = OUT / f"exam_bank_set_{offset:03d}.json"
        if out.exists():
            previous = ExamBatch.model_validate_json(out.read_text(encoding="utf-8"))
            prior_audio = {group.group_id: group.audio for group in previous.groups if group.audio}
            for group in groups:
                if group.group_id in prior_audio:
                    group.audio = prior_audio[group.group_id]
        batch = ExamBatch(
            batch_metadata=BatchMetadata(
                batch_id=f"exam_bank_set_{offset:03d}", module_type=ModuleType.EXAM,
                generated_by="codex-gpt-5", generated_at=datetime.now(UTC),
                total_records=len(groups)),
            groups=groups,
        )
        guarded_write_batch(batch, out)
        write_graphics(profile, offset)
        print(f"set_{offset:03d}: {len(groups)} groups / {sum(len(g.questions) for g in groups)} items")
        for warning in report_bias(groups):
            print(f"  WARNING: {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
