ALTER TABLE tutor_messages ADD COLUMN is_summary boolean NOT NULL DEFAULT false;

CREATE TABLE tutor_memories (
    conversation_id uuid PRIMARY KEY REFERENCES tutor_conversations(id) ON DELETE CASCADE,
    content text,
    through_order integer NOT NULL DEFAULT 0 CHECK (through_order >= 0),
    epoch bigint NOT NULL DEFAULT 0 CHECK (epoch >= 0),
    pending_message_id uuid,
    source_message_id uuid,
    pending_through_order integer NOT NULL DEFAULT 0 CHECK (pending_through_order >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE tutor_memories IS 'Unverified, learner-requested rolling AI notes. A pending UUID is a fenced intent, not a foreign key to a mutable job.';
