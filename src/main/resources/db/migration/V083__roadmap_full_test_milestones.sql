-- An IELTS roadmap's mock milestone is an IELTS full test, passed on its overall band (docs/decisions.md, D-IELTS-6):
-- a Listening + Reading paper alone leaves out the two skills that most often hold the overall band down. TOEIC
-- milestones keep naming a paper. full_test_id is the exam module's full test, referenced by id only.

alter table roadmap_milestones
    add column full_test_id                uuid,
    add column result_full_test_sitting_id uuid,
    drop constraint ck_roadmap_milestone_kind,
    add constraint ck_roadmap_milestone_kind check (
        (kind = 'CERTIFICATE_MOCK' and required_score is not null
            and ((exam_id is not null) <> (full_test_id is not null)))
        or (kind = 'LEVEL_CHECK' and required_level is not null));
