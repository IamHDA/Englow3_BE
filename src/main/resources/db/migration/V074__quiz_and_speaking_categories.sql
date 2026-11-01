-- Quiz and speaking-prompt categories become a fixed list. As free text, "Grammar", "grammar" and "Ngữ pháp" were three
-- filters on the catalogue, and the learning roadmap could not tell which skill a quiz or prompt trains.
--
-- Existing rows are mapped by keyword, in English and Vietnamese, before the constraint goes on. Anything that matches
-- no keyword lands on the broadest value (MIXED for quizzes, SOUNDS for prompts) rather than failing the migration; an
-- author can correct it in the editor, and nothing is lost because the old text was only ever a label.

update quizzes
   set category = case
       when lower(category) ~ '(grammar|ngữ pháp|ngu phap|tense|thì)' then 'GRAMMAR'
       when lower(category) ~ '(vocab|từ vựng|tu vung|word|collocation|idiom)' then 'VOCABULARY'
       when lower(category) ~ '(read|đọc|doc hieu)' then 'READING'
       else 'MIXED'
   end
 where category not in ('GRAMMAR', 'VOCABULARY', 'READING', 'MIXED');

alter table quizzes
    add constraint ck_quizzes_category check (category in ('GRAMMAR', 'VOCABULARY', 'READING', 'MIXED'));

update speaking_prompts
   set category = case
       when lower(category) ~ '(stress|trọng âm|trong am)' then 'WORD_STRESS'
       when lower(category) ~ '(inton|ngữ điệu|ngu dieu|rhythm)' then 'INTONATION'
       when lower(category) ~ '(connect|linking|nối âm|noi am|elision|weak form)' then 'CONNECTED_SPEECH'
       else 'SOUNDS'
   end
 where category not in ('SOUNDS', 'WORD_STRESS', 'INTONATION', 'CONNECTED_SPEECH');

alter table speaking_prompts
    add constraint ck_speaking_prompts_category
        check (category in ('SOUNDS', 'WORD_STRESS', 'INTONATION', 'CONNECTED_SPEECH'));
