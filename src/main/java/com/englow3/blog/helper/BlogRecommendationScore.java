package com.englow3.blog.helper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTagKind;

/**
 * How well a post fits what a reader declared about their learning, and which posts fit best. Pure.
 * <p>
 * The profile arrives as the words the {@code user} module writes - a level, a certificate and skills as strings - and
 * is turned into blog's own enums here, because the blog never borrows another module's enum: a value blog has no tag
 * for ({@code PRONUNCIATION}) or does not know at all is dropped, not an error.
 * <p>
 * Points: the certificate the reader is aiming for {@value #CERTIFICATE_POINTS}, any of the skills they want to work on
 * {@value #SKILL_POINTS} (once, however many of them the post carries), their own level {@value #SAME_LEVEL_POINTS} or
 * the level next to it {@value #NEXT_LEVEL_POINTS}. A post without a level tag gains and loses nothing for level. A
 * post needs {@value #THRESHOLD} points to be worth recommending. Of equal points the one with more likes comes first,
 * then the newer.
 */
public final class BlogRecommendationScore {

    public static final int CERTIFICATE_POINTS = 3;
    public static final int SKILL_POINTS = 2;
    public static final int SAME_LEVEL_POINTS = 2;
    public static final int NEXT_LEVEL_POINTS = 1;
    public static final int THRESHOLD = 2;

    private BlogRecommendationScore() {
    }

    /** What the reader declared, in blog's terms. Any part may be missing. */
    public record Profile(BlogLevel level, BlogCertificate certificate, Set<BlogSkill> skills) {

        /** Nothing to match a post against. */
        public boolean isEmpty() {
            return level == null && certificate == null && skills.isEmpty();
        }
    }

    /** A post to weigh: what the ranking needs of it, and its tags. */
    public record Candidate(UUID postId, Instant createdAt, long likeCount, Collection<BlogTagResult> tags) {
    }

    /**
     * A post chosen for the reader. {@code matchedTags} are the tags that are what the reader declared - their
     * certificate, their skills, their own level - and never the neighbouring level, which scores but is not a match
     * the reader would recognise as theirs. Skills first, then the level, then the certificate.
     */
    public record Pick(UUID postId, int points, List<BlogTagResult> matchedTags) {
    }

    /** The profile as blog's enums; a value that is not one of blog's is left out. */
    public static Profile profileOf(String currentLevel, String targetCertificate, Collection<String> targetSkills) {
        Set<BlogSkill> skills = targetSkills == null ? Set.of()
                : targetSkills.stream().map(name -> parse(BlogSkill.class, name)).flatMap(Optional::stream)
                        .collect(Collectors.toUnmodifiableSet());
        return new Profile(parse(BlogLevel.class, currentLevel).orElse(null),
                parse(BlogCertificate.class, targetCertificate).orElse(null), skills);
    }

    /**
     * The candidates that reach the threshold, best first, at most {@code limit}. Candidates the reader would not be
     * recommended at all (below the threshold) are not in the answer, however few that leaves.
     */
    public static List<Pick> rank(Profile profile, Collection<Candidate> candidates, int limit) {
        Comparator<Scored> best = Comparator.comparingInt((Scored scored) -> scored.pick().points()).reversed()
                .thenComparing(Comparator.comparingLong((Scored scored) -> scored.candidate().likeCount()).reversed())
                .thenComparing(Comparator.comparing((Scored scored) -> scored.candidate().createdAt()).reversed())
                .thenComparing(scored -> scored.candidate().postId());
        return candidates.stream().map(candidate -> new Scored(candidate, score(profile, candidate)))
                .filter(scored -> scored.pick().points() >= THRESHOLD).sorted(best).limit(limit).map(Scored::pick)
                .toList();
    }

    private record Scored(Candidate candidate, Pick pick) {
    }

    /**
     * The points a post earns for this profile and the tags that earned them, whether or not it reaches the threshold.
     */
    public static Pick score(Profile profile, Candidate candidate) {
        int points = 0;
        List<BlogTagResult> matched = new ArrayList<>();

        if (profile.certificate() != null) {
            BlogTagResult certificate = new BlogTagResult(BlogTagKind.CERTIFICATE, profile.certificate().name());
            if (candidate.tags().contains(certificate)) {
                points += CERTIFICATE_POINTS;
                matched.add(certificate);
            }
        }

        List<BlogTagResult> skills = candidate.tags().stream().filter(tag -> tag.kind() == BlogTagKind.SKILL
                && profile.skills().stream().anyMatch(skill -> skill.name().equals(tag.value()))).toList();
        if (!skills.isEmpty()) {
            points += SKILL_POINTS;
            matched.addAll(skills);
        }

        if (profile.level() != null) {
            int bestLevelPoints = 0;
            BlogTagResult sameLevel = null;
            for (BlogTagResult tag : candidate.tags()) {
                if (tag.kind() != BlogTagKind.LEVEL) {
                    continue;
                }
                Optional<BlogLevel> level = parse(BlogLevel.class, tag.value());
                if (level.isEmpty()) {
                    continue;
                }
                int distance = Math.abs(level.get().ordinal() - profile.level().ordinal());
                if (distance == 0) {
                    bestLevelPoints = SAME_LEVEL_POINTS;
                    sameLevel = tag;
                } else if (distance == 1) {
                    bestLevelPoints = Math.max(bestLevelPoints, NEXT_LEVEL_POINTS);
                }
            }
            points += bestLevelPoints;
            if (sameLevel != null) {
                matched.add(sameLevel);
            }
        }

        return new Pick(candidate.postId(), points, matched.stream().sorted(BlogTagResult.DISPLAY_ORDER).toList());
    }

    private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.name().equals(name)).findFirst();
    }
}
