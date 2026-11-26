-- The daily limit counts a request on the day it last started running, not the day it was made. A failed request of an
-- earlier day that is resumed today runs again today, so it counts today; counting by created_at let it count on no day
-- at all. Existing rows started when they were made.

alter table generation_requests add column started_at timestamptz;
update generation_requests set started_at = created_at;
alter table generation_requests alter column started_at set not null;

-- The daily limit reads a user's requests started today. generation_requests_user_created_idx stays: an author's list is
-- still ordered by created_at.
create index generation_requests_user_started_idx on generation_requests (requested_by_user_id, started_at desc);
