-- Where a sentence sits inside its audio file.
--
-- Until now every sentence carried its own file, which is what an author
-- uploading one clip per line produces. The generated shadowing batches are the
-- other shape: one recording of a whole passage, with the timings of each
-- sentence inside it. Without somewhere to put those timings, importing a clip
-- would mean either cutting the audio into 120 files or playing the whole
-- passage back for every line.
--
-- Both nullable, and null keeps its existing meaning: play the file from the
-- start. Every row written so far means exactly that, so there is nothing to
-- backfill and nothing about existing lessons changes.

alter table dictation_sentences
    add column audio_start_ms integer,
    add column audio_end_ms integer;

-- A window that ends before it starts is not a window. Checked here rather than
-- in the importer alone: the admin authoring API writes these columns too, and
-- a rule the database does not hold is a rule that holds only where someone
-- remembered it.
alter table dictation_sentences
    add constraint chk_dictation_sentences_audio_window
    check (
        (audio_start_ms is null and audio_end_ms is null)
        or (audio_start_ms >= 0 and audio_end_ms > audio_start_ms)
    );
