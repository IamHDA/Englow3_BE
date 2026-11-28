-- Learner-authored ("personal") flashcard sets live in the same tables as the
-- catalogue so studying, rating, scheduling and statistics work on them unchanged:
-- all of those key off flashcard ids, not off who wrote the set.
--
-- kind separates the two. A CATALOGUE set is authored by staff and goes through
-- review; a PERSONAL set belongs to the learner who made it, is never reviewed, and
-- is live from the moment it is created - which is why it is always PUBLISHED.
-- visibility only means something for a personal set; the catalogue is always
-- visible to every learner once published.
alter table flashcard_sets
    add column kind varchar(20) not null default 'CATALOGUE',
    add column visibility varchar(20) not null default 'PUBLIC';

alter table flashcard_sets
    add constraint ck_flashcard_sets_kind check (kind in ('CATALOGUE', 'PERSONAL')),
    add constraint ck_flashcard_sets_visibility check (visibility in ('PRIVATE', 'PUBLIC', 'SELECTED_FRIENDS')),
    add constraint ck_flashcard_sets_catalogue_public check (kind = 'PERSONAL' or visibility = 'PUBLIC'),
    add constraint ck_flashcard_sets_personal_published check (kind = 'CATALOGUE' or status = 'PUBLISHED');

create index idx_flashcard_sets_personal_owner on flashcard_sets (created_by_user_id) where kind = 'PERSONAL';
create index idx_flashcard_sets_personal_visibility on flashcard_sets (visibility) where kind = 'PERSONAL';

-- Who a SELECTED_FRIENDS set is shown to. A row grants nothing on its own: the
-- reader must also still be the owner's friend, so unfriending (or a block, which
-- deletes the friendship) withdraws access without touching this table, and
-- befriending again restores it. Rows are cleared when the set leaves
-- SELECTED_FRIENDS.
create table flashcard_set_shares (
    flashcard_set_id uuid not null references flashcard_sets (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (flashcard_set_id, user_id)
);

create index idx_flashcard_set_shares_user_id on flashcard_set_shares (user_id);

-- A learner's own card needs a term and its meaning, nothing more. The catalogue
-- still fills every one of these: FlashcardImport and the admin request records
-- require them, so dropping the database backstop changes nothing for staff content.
alter table flashcards
    alter column part_of_speech drop not null,
    alter column sense_label drop not null,
    alter column ipa_us drop not null,
    alter column definition_en drop not null,
    alter column example_sentence drop not null;
