alter table shadowing_clips add column embedding_text text;
update shadowing_clips set embedding_text = script where embedding_text is null;
alter table shadowing_clips alter column embedding_text set not null;
alter table shadowing_clips add column embedding vector(1024);

create table ai_embedding_index_state (
    content_type varchar(30) not null check (content_type in
        ('EXAM_ITEM', 'SHADOWING_CLIP', 'FLASHCARD', 'GRAMMAR_POINT')),
    content_id text not null,
    revision integer not null check (revision >= 0),
    content_hash varchar(64) not null check (content_hash ~ '^[0-9a-f]{64}$'),
    status varchar(20) not null check (status in
        ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED', 'STALE')),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    available_at timestamptz not null default now(),
    locked_at timestamptz,
    locked_by varchar(200),
    provider varchar(100),
    model varchar(200),
    dimensions integer check (dimensions is null or dimensions = 1024),
    input_tokens integer check (input_tokens is null or input_tokens >= 0),
    error_code varchar(100),
    error_message varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    indexed_at timestamptz,
    primary key (content_type, content_id, revision, content_hash)
);

create index idx_ai_embedding_index_claim
    on ai_embedding_index_state (available_at, created_at)
    where status in ('PENDING', 'FAILED');

create index idx_ai_embedding_index_content
    on ai_embedding_index_state (content_type, content_id, revision desc);

insert into ai_embedding_index_state (content_type, content_id, revision, content_hash, status)
select 'EXAM_ITEM', i.item_id,
       coalesce((select max(p.revision) from ai_content_publications p
                 where p.entity_type = 'EXAM_ITEM' and p.entity_id = i.item_id), 0),
       encode(sha256(convert_to(i.embedding_text, 'UTF8')), 'hex'), 'PENDING'
from exam_items i where i.review_status = 'human_approved'
on conflict do nothing;

insert into ai_embedding_index_state (content_type, content_id, revision, content_hash, status)
select 'SHADOWING_CLIP', s.clip_id,
       coalesce((select max(p.revision) from ai_content_publications p
                 where p.entity_type = 'SHADOWING_CLIP' and p.entity_id = s.clip_id), 0),
       encode(sha256(convert_to(s.embedding_text, 'UTF8')), 'hex'), 'PENDING'
from shadowing_clips s where s.review_status = 'human_approved'
on conflict do nothing;

insert into ai_embedding_index_state (content_type, content_id, revision, content_hash, status)
select 'FLASHCARD', f.id,
       coalesce((select max(p.revision) from ai_content_publications p
                 where p.entity_type = 'FLASHCARD' and p.entity_id = f.id), 0),
       encode(sha256(convert_to(f.embedding_text, 'UTF8')), 'hex'), 'PENDING'
from flashcards f where f.review_status = 'human_approved'
on conflict do nothing;

insert into ai_embedding_index_state (content_type, content_id, revision, content_hash, status)
select 'GRAMMAR_POINT', g.id,
       coalesce((select max(p.revision) from ai_content_publications p
                 where p.entity_type = 'GRAMMAR_POINT' and p.entity_id = g.id), 0),
       encode(sha256(convert_to(g.embedding_text, 'UTF8')), 'hex'), 'PENDING'
from grammar_points g where g.review_status = 'human_approved'
on conflict do nothing;
