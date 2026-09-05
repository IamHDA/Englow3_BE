# FINAL QA — English/TOEIC data

Generated from the current source-of-truth output by `generators/build_qa_reports.py`.

## Outcome

- Exam sets currently present: **10/10**. Structural readiness: **READY**.
- Bank: **2020 exam questions** (1000 Listening, 1020 Reading).
- Supporting content: **3000 flashcards**, **90 grammar points / 450 exercises**, **11 Speaking tasks**, **8 Writing tasks**.
- Practice/evaluation: **30 shadowing+dictation clips**, **2 versioned assessment prompts**, **10 offline calibration fixtures**.
- Media: **6726 files**, 153.3 MiB, each recorded with SHA-256.
- Collection QA requires all 10 sets to pass blueprint and cross-set no-reuse checks.
- Exact test totals must be taken from the current CI run; this report does not hard-code them.

## Exam blueprint

| Part | Bank questions | Required in set |
|---:|---:|---:|
| 1 | 60 | 6 |
| 2 | 250 | 25 |
| 3 | 390 | 39 |
| 4 | 300 | 30 |
| 5 | 300 | 30 |
| 6 | 160 | 16 |
| 7 | 560 | 54 |

Part 7 in every set is locked to 10 single texts / 29 questions plus 5
double-or-triple sets / 25 questions. The collection includes vocabulary-in-context,
paraphrase, sentence-insertion, intent, inference, detail, main-idea,
not-true, and cross-reference items.

## Media integrity

- Listening: 540/540 groups have real MP3 URLs,
  measured durations, and measured utterance cues.
- Exam/Speaking/Writing images: 105/105 referenced files exist locally.
- Speaking recorded prompts: 3/3 files exist with duration and cue data.
- Flashcard pronunciation: 3000/3000 cards have both local US and UK MP3 files.
- Shadowing/dictation: 30/30 clips have local audio metadata and measured sentence timestamps in the 30–60 second target.
- Public object-store upload is reproducible with
  `powershell -File generators/upload_media_to_minio.ps1`.

## Human and empirical gates (not fabricated)

- Review status: {'draft': 5161}. No item was marked approved by automation.
- IRT calibrated exam items: 0/2020. Calibration requires real learner responses.
- Assessment provider runs: 0. The 10 cases are schema/regression fixtures, not empirical gold labels; three-run variance still requires a configured model and human adjudication.
- Leaf concepts below five linked records: 19/156.
  This does not block a fixed TOEIC-format test, but it is insufficient for stable
  per-concept BKT estimates on every leaf.
- This offline report does not assert MinIO upload state. Local files and upload
  automation are complete; deployment verification must check the public URLs.

## Remaining release actions

1. A human reviewer completes `reports/human_review_packet.md` and changes only
   accepted records from `draft` to `approved`.
2. Run the MinIO upload script; verify image/listening URLs under
   `http://localhost:9000/images/` and flashcard/shadowing URLs under
   `http://localhost:9000/audio/`.
3. Collect real responses before estimating IRT parameters or claiming calibrated difficulty.
