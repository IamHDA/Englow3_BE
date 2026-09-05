# Data completion summary

Generated and verified on 2026-08-24.

## Completed repository data

| Area | Verified total |
|---|---:|
| TOEIC-format practice sets | 10 sets × 200 questions |
| Unique selected exam questions | 2,000 |
| Exam question bank | 2,020 |
| Listening audio | 540/540 MP3 with measured cues |
| Exam/Speaking/Writing images | 105/105 local files |
| Flashcards | 3,000 unique cards |
| Flashcard pronunciation | 6,000/6,000 MP3 (US + UK) |
| B2/C1 collocations | 4,200 (3 per card) |
| Grammar | 90 points / 450 exercises |
| Speaking tasks | 11 |
| Writing tasks | 8 |
| Shadowing + dictation | 30 clips / 120 measured segments |
| Assessment prompts | 2 versioned prompts |
| Assessment regression fixtures | 10 schema-valid cases |
| Taxonomy leaf coverage | 156/156 leaves non-empty |
| Staging | 26/26 non-empty tables / 35,742 rows |
| Media manifest | 6,726 files with SHA-256 |

## Verification

- Data audit: 0 errors, 0 warnings.
- Python: 85 tests passed.
- FastAPI: 30 tests passed.
- Java: 65 tests passed, 0 failures/errors/skips; all 40 Flyway migrations validated.
- Delivery ZIPs: 10/10 valid; 2,000 unique selected questions; 638 packaged
  exam media files; all package checksums valid.
- MinIO upload remains an environment/deployment check. The upload script now
  handles both `images` and `audio` buckets and fails on Docker command errors.

## Gates that require real people or production observations

- Every generated record remains `draft`; automation did not mark any content
  `human_approved`.
- IRT parameters remain uncalibrated until real learner-response data exists.
- The ten assessment cases are offline regression fixtures. Live three-run
  variance and agreement against human gold labels are still pending a configured
  assessment provider and qualified adjudicators.
- Open dictionary-derived flashcard data is CC BY-SA 4.0; Tatoeba examples are
  CC BY 2.0 France. See `output/flashcards/ATTRIBUTION.md`.
