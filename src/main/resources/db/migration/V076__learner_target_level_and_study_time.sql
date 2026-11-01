-- What the learning roadmap needs from a learner's goal beyond a certificate score.
--
-- target_level: the CEFR band a learner who is not preparing for a certificate wants to reach.
-- daily_study_minutes: how much time a day they have, which is what lets a plan be spread over the weeks to the date.
-- level_check_id: the roadmap level check a measured level came from, the counterpart of placement_attempt_id for
--   learners measured by the vocabulary + listening + speaking test rather than the placement paper. A plain id: the
--   level check belongs to the roadmap module.
alter table learner_profiles
    add column target_level varchar(2) check (target_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    add column daily_study_minutes integer check (daily_study_minutes between 10 and 240),
    add column level_check_id uuid;
