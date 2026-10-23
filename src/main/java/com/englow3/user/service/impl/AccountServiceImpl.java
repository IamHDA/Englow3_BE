package com.englow3.user.service.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.AccountService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    /** A cap on any one kind of record, so one very active learner cannot make an export that exhausts memory. */
    private static final int MAX_ROWS_PER_KIND = 20_000;

    private final UserRepository userRepo;
    private final CurrentUser currentUser;
    private final UserDirectory directory;
    private final JdbcClient jdbc;
    private final ObjectStorageClient storage;
    private final ObjectMapper json;
    private final UserRateLimiter rateLimiter;

    @Value("${app.storage.avatar-bucket}")
    private String avatarBucket;

    @Value("${app.storage.speaking-bucket}")
    private String speakingBucket;

    @Value("${app.tutor.audio-bucket:speaking}")
    private String tutorAudioBucket;

    @Override
    @Transactional(readOnly = true)
    public Map<String, JsonNode> exportData() {
        UUID userId = requireUser().getId();
        // Reads a dozen tables; a person needs one copy, not one a second.
        rateLimiter.check(userId, "account-export", 3, Duration.ofMinutes(10));
        Map<String, JsonNode> out = new LinkedHashMap<>();
        out.put("account", rows("users", "id = :u", userId));
        out.put("learnerProfile", rows("learner_profiles", "user_id = :u", userId));
        out.put("learningPurposes", rows("user_learning_purposes", "user_id = :u", userId));
        out.put("targetSkills", rows("user_target_skills", "user_id = :u", userId));
        out.put("subscription", rows("user_subscriptions", "user_id = :u", userId));
        out.put("examAttempts", rows("exam_attempts", "user_id = :u", userId));
        out.put("examAnswers", rows("attempt_answers",
                "exam_attempt_id in (select id from exam_attempts where user_id = :u)", userId));
        out.put("assessmentAttempts", rows("assessment_attempts", "user_id = :u", userId));
        out.put("speakingAttempts", rows("speaking_attempts", "user_id = :u", userId));
        out.put("dictationAttempts", rows("dictation_attempts", "user_id = :u", userId));
        out.put("quizAttempts", rows("quiz_attempts", "user_id = :u", userId));
        out.put("flashcardReviews", rows("flashcard_reviews", "user_id = :u", userId));
        out.put("flashcardReviewLogs", rows("flashcard_review_logs", "user_id = :u", userId));
        out.put("tutorConversations", rows("tutor_conversations", "user_id = :u", userId));
        out.put("tutorMessages", rows("tutor_messages",
                "tutor_conversation_id in (select id from tutor_conversations where user_id = :u)", userId));
        return out;
    }

    /**
     * Table and condition are literals written in this class, never request data, so assembling the statement from
     * them is not an injection point; only the user id travels as a bound parameter.
     */
    private JsonNode rows(String table, String condition, UUID userId) {
        String text = jdbc.sql("select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb)::text from (select * from "
                + table + " where " + condition + " limit " + MAX_ROWS_PER_KIND + ") t").param("u", userId)
                .query(String.class).single();
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Postgres returned JSON that does not parse for " + table, e);
        }
    }

    @Override
    @Transactional
    public void erase(String confirmEmail) {
        User user = requireUser();
        if (user.getRole() != Role.LEARNER) {
            // Staff own published content through foreign keys; deleting them would either fail or take lessons and
            // exams with it. Removing a back-office account is an administrator's decision, not a self-service one.
            throw new ConflictException("ACCOUNT_ERASURE_NOT_SELF_SERVICE",
                    "Staff and administrator accounts are removed by an administrator");
        }
        if (confirmEmail == null || !user.getEmail().equalsIgnoreCase(confirmEmail.trim())) {
            throw new BadRequestException("ACCOUNT_CONFIRMATION_MISMATCH",
                    "Type the email address of this account to confirm");
        }
        UUID id = user.getId();
        // Five wrong confirmations an hour is a slip; more is a script guessing.
        rateLimiter.check(id, "account-erase", 5, Duration.ofHours(1));
        // A job still running reads and writes the rows below; deleting under it would fail the job half-way and could
        // leave a result nobody owns. Waiting a few minutes is cheaper than that.
        if (jdbc.sql("select count(*) from ai_jobs where requested_by_user_id = :u and status in ('PENDING','RUNNING')")
                .param("u", id).query(Long.class).single() > 0) {
            throw new ConflictException("ACCOUNT_AI_WORK_PENDING",
                    "An AI job for this account is still running. Try again in a few minutes");
        }

        // Read before deleting, or the keys are gone with the rows that hold them.
        List<String> speakingKeys = jdbc.sql("""
                select audio_object_key from assessment_attempts where user_id = :u and audio_object_key is not null
                union select audio_object_key from speaking_attempts where user_id = :u and audio_object_key is not null
                union select a.audio_object_key from attempt_answers a join exam_attempts e on e.id = a.exam_attempt_id
                      where e.user_id = :u and a.audio_object_key is not null
                """).param("u", id).query(String.class).list();

        List<String> tutorKeys = jdbc.sql("select object_key from tutor_recordings where user_id = :u").param("u", id)
                .query(String.class).list();

        // Foreign keys without a cascade, children before parents. Everything else hangs off users with ON DELETE
        // CASCADE and goes with the last statement.
        delete("delete from assessment_reviews where attempt_id in (select id from assessment_attempts where user_id = :u)", id);
        delete("delete from assessment_attempts where user_id = :u", id);
        delete("""
                delete from attempt_answer_options where attempt_answer_id in
                  (select a.id from attempt_answers a join exam_attempts e on e.id = a.exam_attempt_id where e.user_id = :u)
                """, id);
        delete("""
                delete from attempt_answer_criterion_scores where attempt_answer_id in
                  (select a.id from attempt_answers a join exam_attempts e on e.id = a.exam_attempt_id where e.user_id = :u)
                """, id);
        delete("delete from attempt_answers where exam_attempt_id in (select id from exam_attempts where user_id = :u)", id);
        delete("delete from attempt_section_results where exam_attempt_id in (select id from exam_attempts where user_id = :u)", id);
        delete("delete from exam_attempt_drafts where attempt_id in (select id from exam_attempts where user_id = :u)", id);
        delete("delete from exam_attempts where user_id = :u", id);
        delete("delete from ai_jobs where requested_by_user_id = :u", id);
        delete("delete from user_learning_purposes where user_id = :u", id);
        delete("delete from user_target_skills where user_id = :u", id);
        delete("delete from learner_profiles where user_id = :u", id);
        delete("delete from users where id = :u", id);

        // The sign-in itself. The application's database role owns this table on Supabase; GoTrue's own tables
        // (identities, sessions, refresh tokens) cascade from it, so every device is signed out at once.
        jdbc.sql("delete from auth.users where id = :a").param("a", user.getAuthProviderId()).update();
        directory.forget(user.getAuthProviderId());

        String avatar = user.getAvatarObjectKey();
        String banner = user.getBannerObjectKey();
        afterCommit(() -> {
            for (String key : new String[] { avatar, banner }) {
                removeObject(avatarBucket, key);
            }
            speakingKeys.forEach(key -> removeObject(speakingBucket, key));
            tutorKeys.forEach(key -> removeObject(tutorAudioBucket, key));
            log.info("Erased account {}", id);
        });
    }

    private void delete(String sql, UUID userId) {
        jdbc.sql(sql).param("u", userId).update();
    }

    private User requireUser() {
        return userRepo.findByAuthProviderId(currentUser.authProviderId()).orElseThrow(
                () -> new NotFoundException("USER_NOT_FOUND", "No user is linked to this sign-in"));
    }

    /**
     * Files go after the database commit. Before it, a failed commit would leave rows pointing at files that were
     * already deleted; after it, the worst case is an orphan file, which a lifecycle rule can sweep and no learner
     * ever sees.
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void removeObject(String bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storage.delete(bucket, key);
        } catch (RuntimeException e) {
            log.warn("Could not remove stored file {} from {} after an account erasure", key, bucket, e);
        }
    }
}
