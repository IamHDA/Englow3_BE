-- Attempts whose recording never arrived are swept once they are old. The sweep asks for the oldest of those; partial,
-- so it holds only the few rows still waiting and costs nothing for the assessed ones that make up the table.
create index idx_speaking_attempts_awaiting_upload on speaking_attempts (created_at)
    where status = 'AWAITING_UPLOAD';
