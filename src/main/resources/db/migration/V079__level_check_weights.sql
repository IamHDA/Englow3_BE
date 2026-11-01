-- How much each part of the level check counts, by learning purpose. Kept as data rather than code so the weights can
-- be tuned from real results without a release: insert new rows with a later effective_from, and every check completed
-- after that uses them. A completed check keeps the weights it was combined with (level_checks.weights), so changing
-- this table never rewrites a level already recorded.
--
-- A learner with several purposes gets the row with the highest priority among them; a learner whose purposes have no
-- row gets DEFAULT.

create table level_check_weights (
    id             uuid primary key,
    purpose_code   varchar(30) not null,
    vocabulary     integer     not null check (vocabulary between 0 and 100),
    listening      integer     not null check (listening between 0 and 100),
    speaking       integer     not null check (speaking between 0 and 100),
    priority       integer     not null default 0,
    effective_from timestamptz not null default now(),
    constraint ck_level_check_weights_total check (vocabulary + listening + speaking = 100),
    -- Without a written part there is nothing to scale the weights up from when speaking is skipped.
    constraint ck_level_check_weights_written check (vocabulary + listening > 0),
    unique (purpose_code, effective_from)
);

insert into level_check_weights (id, purpose_code, vocabulary, listening, speaking, priority, effective_from)
values (gen_random_uuid(), 'COMMUNICATION', 35, 30, 35, 10, '2026-01-01T00:00:00Z'),
       (gen_random_uuid(), 'TRAVEL', 35, 30, 35, 10, '2026-01-01T00:00:00Z'),
       (gen_random_uuid(), 'WORK', 40, 30, 30, 5, '2026-01-01T00:00:00Z'),
       (gen_random_uuid(), 'DEFAULT', 40, 30, 30, 0, '2026-01-01T00:00:00Z');
