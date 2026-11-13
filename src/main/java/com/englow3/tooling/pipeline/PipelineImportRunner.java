package com.englow3.tooling.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.flashcard.dto.command.AddFlashcardsCommand;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;
import com.englow3.dictation.service.AdminDictationService;
import com.englow3.flashcard.service.AdminFlashcardService;
import com.englow3.flashcard.helper.FlashcardImport;
import com.englow3.shared.error.ConflictException;
import com.englow3.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Loads a pipeline {@code output/} directory into whatever database the application is pointed at, then exits.
 * <p>
 * The same code the admin screens run - the same parsing, the same checks, the same draft status - so nothing arrives
 * by a path the application would not have accepted from a person. Only the transport differs: a file on disk rather
 * than an upload, because the output is 3,000 cards and a person uploading them in batches is not the point.
 * <p>
 * Cards are split into one draft set per CEFR level. Dictation clips become one draft lesson each. Nothing is
 * published; an administrator reviews and approves as for anything else.
 * <p>
 * Safe to run twice. A level whose set already exists is skipped, and so is a clip whose lesson does. Each set is
 * written in one transaction with its cards, so a failure part-way leaves no half-filled set that the next run would
 * then skip.
 *
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=pipeline-import \
 *     -Dspring-boot.run.arguments="--pipeline.output=/path/to/output --pipeline.admin-auth-id=&lt;uuid&gt;"
 * </pre>
 *
 * {@code admin-auth-id} is the Supabase user id of an administrator - the subject their token would carry. The run
 * refuses anyone who is not one: this bypasses the controllers, and with them the role checks they carry.
 */
@Component
@Profile("pipeline-import")
@RequiredArgsConstructor
class PipelineImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineImportRunner.class);

    /** Unlevelled cards are collected rather than dropped, so a reviewer can see them and place them. */
    private static final String NO_LEVEL = "unlevelled";

    private final AdminFlashcardService flashcards;
    private final AdminDictationService dictation;
    private final UserService users;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final ConfigurableApplicationContext context;

    @Value("${pipeline.output}")
    private String output;

    @Value("${pipeline.admin-auth-id}")
    private String adminAuthId;

    @Override
    public void run(ApplicationArguments arguments) {
        int exitCode = 0;
        try {
            signInAs(UUID.fromString(adminAuthId));
            requireAdministrator();

            importFlashcards(Path.of(output));
            importDictation(Path.of(output));
        } catch (RuntimeException failure) {
            log.error("Pipeline import stopped", failure);
            exitCode = 1;
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Schedulers keep the JVM alive after the runner returns; a tool that never ends is one people kill mid-write.
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private void importFlashcards(Path root) {
        Map<String, List<AddFlashcardsCommand.NewCard>> byLevel = new TreeMap<>();
        int rejected = 0;
        for (Path batch : files(root.resolve("flashcards"), "flashcard_batch_")) {
            FlashcardImport.Report report = FlashcardImport.read(objectMapper, read(batch));
            rejected += report.rejectedCount();
            report.rejections().forEach(rejection -> log.warn("{} row {} ({}): {}", batch.getFileName(),
                    rejection.index(), rejection.lemma(), rejection.reason()));
            report.cards().forEach(card -> byLevel
                    .computeIfAbsent(card.cefrLevel() == null ? NO_LEVEL : card.cefrLevel(), level -> new ArrayList<>())
                    .add(card));
        }

        byLevel.forEach(this::importLevel);
        log.info("Flashcards: {} level(s) read, {} row(s) rejected", byLevel.size(), rejected);
    }

    private void importLevel(String level, List<AddFlashcardsCommand.NewCard> cards) {
        String slug = "core-vocabulary-" + level.toLowerCase(Locale.ROOT);
        try {
            transactions.executeWithoutResult(status -> {
                UUID setId = flashcards
                        .createSet(
                                new CreateFlashcardSetCommand(slug, "Core vocabulary " + level,
                                        "Generated vocabulary at %s, imported for review on %s.".formatted(level,
                                                Instant.now()),
                                        "vocabulary", NO_LEVEL.equals(level) ? null : level))
                        .id();
                flashcards.addCards(new AddFlashcardsCommand(setId, cards));
            });
            log.info("Set {}: {} card(s) imported as a draft", slug, cards.size());
        } catch (ConflictException alreadyThere) {
            log.info("Set {}: already imported, skipped", slug);
        }
    }

    private void importDictation(Path root) {
        for (Path batch : files(root.resolve("shadowing"), "shadowing_batch_")) {
            var report = dictation.importLessons(read(batch));
            report.rejections().forEach(rejection -> log.warn("{} clip {} ({}): {}", batch.getFileName(),
                    rejection.index(), rejection.clipId(), rejection.reason()));
            log.info("{}: {} lesson(s), {} sentence(s) imported as drafts, {} skipped", batch.getFileName(),
                    report.acceptedCount(), report.sentenceCount(), report.rejectedCount());
        }
    }

    /** As a request would be: a token whose subject the real user directory then resolves. */
    private static void signInAs(UUID authProviderId) {
        Jwt jwt = Jwt.withTokenValue("pipeline-import").header("alg", "none").subject(authProviderId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3_600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    /** The controllers this bypasses are admin-only; so is this. */
    private void requireAdministrator() {
        var me = users.me();
        if (!"ADMIN".equals(me.role().name())) {
            throw new IllegalStateException("%s is %s, not an administrator".formatted(me.email(), me.role()));
        }
        log.info("Importing as {}", me.email());
    }

    private static List<Path> files(Path folder, String prefix) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> listing = Files.list(folder)) {
            return listing.filter(file -> file.getFileName().toString().startsWith(prefix)).sorted()
                    .collect(Collectors.toList());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
