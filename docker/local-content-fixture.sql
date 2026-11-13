-- Sample learning content for a local database.
--
-- Not a migration, and deliberately so: CONTRIBUTING.md keeps demo data out of
-- versioned schema. It is also not in docker/postgres-init, which runs before
-- Flyway and so before these tables exist. Run it by hand once the application
-- has started at least once:
--
--   psql "$DATABASE_URL" -f docker/local-content-fixture.sql
--
-- Inserting straight into module tables is what a fixture does; application
-- code still goes through the owning module's service. Re-running is safe -
-- every insert keys on the slug and does nothing if it is already there.
--
-- What this makes usable without any further setup:
--   - flashcards     yes, minus pronunciation audio
--   - quizzes        yes, completely
--   - speaking       yes - the reference is read by the browser's own voice,
--                    and the learner's recording is made in the browser
--   - dictation      no. Dictation is listening, and the clips are not here.
--                    Upload four files to the learning bucket under the keys
--                    below and it works:
--                      mc alias set local http://localhost:9000 <key> <secret>
--                      mc cp line-1.wav local/learning/dictation/airport/1.wav
--
-- Exams are not seeded here; the ten mock papers come from the data pipeline.

-- The application's tables live in the englow3 schema, not public - psql
-- connects with the default search_path and would not find one of them.
set search_path to englow3;

do $$
declare
    author_id uuid;
    set_id uuid;
    quiz_id uuid;
    lesson_id uuid;
    question_id uuid;
begin
    perform set_config('search_path', 'englow3', true);

    -- Every content row records who wrote it. Any account will do for a
    -- fixture, but one has to exist: signing in through Supabase once creates
    -- it. Failing loudly beats inserting a user row that Supabase does not know
    -- about, which would then collide on the next sign-in.
    select id into author_id from users order by created_at limit 1;
    if author_id is null then
        raise exception 'No user rows yet. Sign in through the app once, then re-run this fixture.';
    end if;

    ------------------------------------------------------------------ flashcards
    if not exists (select 1 from flashcard_sets where slug = 'toeic-core-office') then
        set_id := gen_random_uuid();
        insert into flashcard_sets (id, slug, name, description, topic, target_level, status,
                                    created_by_user_id, published_at)
        values (set_id, 'toeic-core-office', 'TOEIC Core: Office & Meetings',
                'Từ vựng hay gặp trong email công việc và các cuộc họp.', 'Work', 'B1', 'PUBLISHED',
                author_id, now());

        insert into flashcards (id, flashcard_set_id, order_no, lemma, part_of_speech, sense_label,
                                ipa_us, ipa_uk, definition_en, definition_vi, example_sentence,
                                example_translation_vi, mnemonic_tip_vi, cefr_level)
        values
            (gen_random_uuid(), set_id, 1, 'agenda', 'noun', 'agenda (meeting)', '/əˈdʒendə/', '/əˈdʒendə/',
             'A list of items to be discussed at a meeting.', 'Chương trình nghị sự, danh sách việc cần bàn.',
             'Please send the agenda before Friday.', 'Hãy gửi chương trình họp trước thứ Sáu.',
             'Nhớ "a-gen-da" - ghi ra "da" những việc cần bàn.', 'B1'),
            (gen_random_uuid(), set_id, 2, 'postpone', 'verb', 'postpone (delay)', '/poʊstˈpoʊn/', '/pəʊstˈpəʊn/',
             'To move an event to a later time.', 'Hoãn lại sang lúc khác.',
             'We had to postpone the launch by two weeks.', 'Chúng tôi phải hoãn buổi ra mắt hai tuần.',
             '"post" = sau, "pone" = đặt - đặt lại về sau.', 'B1'),
            (gen_random_uuid(), set_id, 3, 'invoice', 'noun', 'invoice (bill)', '/ˈɪnvɔɪs/', '/ˈɪnvɔɪs/',
             'A document asking for payment for goods or services.', 'Hoá đơn yêu cầu thanh toán.',
             'The invoice is due within thirty days.', 'Hoá đơn phải thanh toán trong ba mươi ngày.',
             null, 'B1'),
            (gen_random_uuid(), set_id, 4, 'attend', 'verb', 'attend (be present)', '/əˈtend/', '/əˈtend/',
             'To be present at an event.', 'Tham dự, có mặt.',
             'All managers must attend the quarterly review.', 'Tất cả quản lý phải dự buổi tổng kết quý.',
             null, 'A2'),
            (gen_random_uuid(), set_id, 5, 'deadline', 'noun', 'deadline (time limit)', '/ˈdedlaɪn/', '/ˈdedlaɪn/',
             'The latest time by which something must be finished.', 'Hạn chót.',
             'The deadline for the report is Monday morning.', 'Hạn chót nộp báo cáo là sáng thứ Hai.',
             null, 'A2'),
            (gen_random_uuid(), set_id, 6, 'reimburse', 'verb', 'reimburse (pay back)', '/ˌriːɪmˈbɜːrs/', '/ˌriːɪmˈbɜːs/',
             'To pay someone back money they have spent.', 'Hoàn tiền cho ai đó đã chi.',
             'The company will reimburse your travel costs.', 'Công ty sẽ hoàn tiền chi phí đi lại cho bạn.',
             'Nghĩ "re-im-purse" - trả lại vào ví.', 'B2'),
            (gen_random_uuid(), set_id, 7, 'vendor', 'noun', 'vendor (supplier)', '/ˈvendər/', '/ˈvendə/',
             'A company that sells goods or services to another company.', 'Nhà cung cấp.',
             'We are comparing quotes from three vendors.', 'Chúng tôi đang so báo giá từ ba nhà cung cấp.',
             null, 'B1'),
            (gen_random_uuid(), set_id, 8, 'draft', 'noun', 'draft (early version)', '/dræft/', '/drɑːft/',
             'An early version of a document.', 'Bản nháp.',
             'Send me a first draft by Wednesday.', 'Gửi tôi bản nháp đầu tiên vào thứ Tư.',
             null, 'B1');
    end if;

    --------------------------------------------------------------------- quiz
    -- One question of each of the five types, so every sitting screen has
    -- something to render.
    if not exists (select 1 from quizzes where slug = 'grammar-mixed-b1') then
        quiz_id := gen_random_uuid();
        insert into quizzes (id, slug, title, description, category, target_level, time_limit_seconds,
                             passing_score_percent, status, created_by_user_id, published_at)
        values (quiz_id, 'grammar-mixed-b1', 'Ngữ pháp tổng hợp B1',
                'Năm dạng câu hỏi: trắc nghiệm, điền từ, viết lại, sắp xếp và nối vế.', 'Grammar', 'B1',
                900, 60, 'PUBLISHED', author_id, now());

        question_id := gen_random_uuid();
        insert into quiz_questions (id, quiz_id, order_no, question_type, title, prompt, points, explanation)
        values (question_id, quiz_id, 1, 'MULTIPLE_CHOICE', 'Thì hiện tại hoàn thành',
                'She ____ in Hanoi since 2019.', 2,
                '"Since 2019" nối tới hiện tại nên dùng hiện tại hoàn thành.');
        insert into quiz_question_options (id, quiz_question_id, order_no, label, content, correct)
        values
            (gen_random_uuid(), question_id, 1, 'A', 'lives', false),
            (gen_random_uuid(), question_id, 2, 'B', 'has lived', true),
            (gen_random_uuid(), question_id, 3, 'C', 'lived', false),
            (gen_random_uuid(), question_id, 4, 'D', 'is living', false);

        question_id := gen_random_uuid();
        insert into quiz_questions (id, quiz_id, order_no, question_type, title, prompt, points, explanation,
                                    before_text, after_text)
        values (question_id, quiz_id, 2, 'FILL_BLANK', 'Giới từ chỉ thời gian',
                'Điền giới từ đúng.', 2, '"On" đi với thứ trong tuần và ngày cụ thể.',
                'The meeting is', 'Monday morning.');
        insert into quiz_question_tokens (id, quiz_question_id, role, order_no, value)
        values
            (gen_random_uuid(), question_id, 'ACCEPTED_ANSWER', 1, 'on'),
            (gen_random_uuid(), question_id, 'ACCEPTED_ANSWER', 2, 'On');

        question_id := gen_random_uuid();
        insert into quiz_questions (id, quiz_id, order_no, question_type, title, prompt, points, explanation,
                                    original_sentence, rewrite_keyword)
        values (question_id, quiz_id, 3, 'REWRITE', 'Viết lại câu bị động',
                'Viết lại câu dùng từ cho sẵn.', 3, 'Bị động: be + quá khứ phân từ.',
                'Someone stole my bike last night.', 'was');
        insert into quiz_question_tokens (id, quiz_question_id, role, order_no, value)
        values
            (gen_random_uuid(), question_id, 'WORD_BANK', 1, 'My'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 2, 'bike'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 3, 'was'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 4, 'stolen'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 5, 'last'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 6, 'night'),
            (gen_random_uuid(), question_id, 'WORD_BANK', 7, 'yesterday'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 1, 'My'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 2, 'bike'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 3, 'was'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 4, 'stolen'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 5, 'last'),
            (gen_random_uuid(), question_id, 'CORRECT_WORD', 6, 'night');

        question_id := gen_random_uuid();
        insert into quiz_questions (id, quiz_id, order_no, question_type, title, prompt, points, explanation)
        values (question_id, quiz_id, 4, 'REORDER', 'Sắp xếp câu hỏi gián tiếp',
                'Sắp xếp các từ thành câu đúng.', 3,
                'Câu hỏi gián tiếp giữ trật tự chủ ngữ - động từ.');
        insert into quiz_question_tokens (id, quiz_question_id, role, order_no, value)
        values
            (gen_random_uuid(), question_id, 'SCRAMBLED', 1, 'where'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 2, 'is'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 3, 'me'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 4, 'the'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 5, 'Could'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 6, 'you'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 7, 'station'),
            (gen_random_uuid(), question_id, 'SCRAMBLED', 8, 'tell'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 1, 'Could'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 2, 'you'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 3, 'tell'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 4, 'me'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 5, 'where'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 6, 'the'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 7, 'station'),
            (gen_random_uuid(), question_id, 'CORRECT_ORDER', 8, 'is');

        question_id := gen_random_uuid();
        insert into quiz_questions (id, quiz_id, order_no, question_type, title, prompt, points, explanation)
        values (question_id, quiz_id, 5, 'MATCHING', 'Nối vế câu điều kiện',
                'Nối nửa đầu với nửa sau cho đúng.', 4,
                'Mỗi loại câu điều kiện có một cặp thì riêng.');
        insert into quiz_question_pairs (id, quiz_question_id, order_no, left_text, right_text)
        values
            (gen_random_uuid(), question_id, 1, 'If it rains tomorrow,', 'we will stay at home.'),
            (gen_random_uuid(), question_id, 2, 'If I had more time,', 'I would learn the piano.'),
            (gen_random_uuid(), question_id, 3, 'If you heat water to 100°C,', 'it boils.'),
            (gen_random_uuid(), question_id, 4, 'If she had studied harder,', 'she would have passed.');
    end if;

    ---------------------------------------------------------------- dictation
    -- Object keys only. Nothing plays until audio exists at these keys; see the
    -- header for the two commands that put it there.
    if not exists (select 1 from dictation_lessons where slug = 'at-the-airport') then
        lesson_id := gen_random_uuid();
        insert into dictation_lessons (id, slug, title, topic, target_level, status,
                                       created_by_user_id, published_at)
        values (lesson_id, 'at-the-airport', 'At the Airport', 'Travel', 'A2', 'PUBLISHED', author_id, now());

        insert into dictation_sentences (id, dictation_lesson_id, order_no, text, translation_vi,
                                         audio_object_key, audio_duration_seconds, hint_word_count,
                                         hint_first_letters, hint_reveal_word, hint_partial_transcript)
        values
            (gen_random_uuid(), lesson_id, 1, 'Could you tell me where the check-in desk is?',
             'Bạn chỉ giúp tôi quầy làm thủ tục ở đâu được không?',
             'dictation/airport/1.wav', 4, 9, 'C y t m w t c-i d i?', 'check-in', 'Could you tell me ___ ___ ___ ___ ___'),
            (gen_random_uuid(), lesson_id, 2, 'My flight has been delayed by two hours.',
             'Chuyến bay của tôi bị hoãn hai tiếng.',
             'dictation/airport/2.wav', 3, 8, 'M f h b d b t h.', 'delayed', 'My flight has been ___ ___ ___ ___'),
            (gen_random_uuid(), lesson_id, 3, 'I would like a seat by the window, please.',
             'Cho tôi một chỗ cạnh cửa sổ.',
             'dictation/airport/3.wav', 4, 9, 'I w l a s b t w, p.', 'window', 'I would like a ___ ___ ___ ___, ___'),
            (gen_random_uuid(), lesson_id, 4, 'Do I need to collect my luggage here?',
             'Tôi có cần lấy hành lý ở đây không?',
             'dictation/airport/4.wav', 3, 8, 'D I n t c m l h?', 'luggage', 'Do I need to ___ ___ ___ ___');
    end if;

    ----------------------------------------------------------------- speaking
    if not exists (select 1 from speaking_prompts where slug = 'minimal-pair-seat-sit') then
        insert into speaking_prompts (id, slug, title, category, target_level, reference_text, ipa_transcript,
                                      translation_vi, phoneme_target, tips, status, created_by_user_id,
                                      published_at)
        values
            (gen_random_uuid(), 'minimal-pair-seat-sit', 'Cặp âm /iː/ và /ɪ/', 'Minimal Pairs', 'A2',
             'Please sit on this seat and leave your sheet on the ship.',
             '/pliːz sɪt ɒn ðɪs siːt ænd liːv jɔː ʃiːt ɒn ðə ʃɪp/',
             'Xin ngồi vào chiếc ghế này và để tờ giấy của bạn trên con tàu.',
             '/iː/ vs /ɪ/',
             '["Âm /iː/: kéo khoé miệng sang hai bên như đang cười nhẹ, giữ hơi dài.", "Âm /ɪ/: thả lỏng miệng, phát âm ngắn và dứt khoát."]'::jsonb,
             'PUBLISHED', author_id, now()),
            (gen_random_uuid(), 'th-voiced-unvoiced', 'Âm kẹp lưỡi /θ/ và /ð/', 'Minimal Pairs', 'B1',
             'I think that these three brothers are thirty.',
             '/aɪ θɪŋk ðæt ðiːz θriː ˈbrʌðərz ɑːr ˈθɜːrti/',
             'Tôi nghĩ rằng ba anh em này ba mươi tuổi.',
             '/θ/ vs /ð/',
             '["Đặt đầu lưỡi chạm nhẹ giữa hai hàm răng, đừng đẩy thành âm /t/ hay /s/.", "/ð/ có rung thanh quản, /θ/ thì không - đặt tay lên cổ để kiểm tra."]'::jsonb,
             'PUBLISHED', author_id, now());
    end if;

    raise notice 'Local content fixture applied.';
end $$;
