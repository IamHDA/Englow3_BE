package com.englow3.blog.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.helper.BlogRecommendationScore.Candidate;
import com.englow3.blog.helper.BlogRecommendationScore.Pick;
import com.englow3.blog.helper.BlogRecommendationScore.Profile;

class BlogRecommendationScoreTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private static BlogTagResult skill(BlogSkill skill) {
        return new BlogTagResult(BlogTagKind.SKILL, skill.name());
    }

    private static BlogTagResult level(BlogLevel level) {
        return new BlogTagResult(BlogTagKind.LEVEL, level.name());
    }

    private static BlogTagResult certificate(BlogCertificate certificate) {
        return new BlogTagResult(BlogTagKind.CERTIFICATE, certificate.name());
    }

    private static Candidate post(BlogTagResult... tags) {
        return new Candidate(UUID.randomUUID(), NOW, 0, Arrays.asList(tags));
    }

    private static Candidate post(long likes, Instant createdAt, BlogTagResult... tags) {
        return new Candidate(UUID.randomUUID(), createdAt, likes, Arrays.asList(tags));
    }

    private static Profile profile(BlogLevel level, BlogCertificate certificate, BlogSkill... skills) {
        return new Profile(level, certificate, Set.of(skills));
    }

    @Nested
    class Points {

        @Test
        void theCertificateTheReaderIsAimingForIsWorthThree() {
            Pick pick = BlogRecommendationScore.score(profile(null, BlogCertificate.IELTS, BlogSkill.SPEAKING),
                    post(certificate(BlogCertificate.IELTS)));

            assertThat(pick.points()).isEqualTo(3);
            assertThat(pick.matchedTags()).containsExactly(certificate(BlogCertificate.IELTS));
        }

        @Test
        void anotherCertificateIsWorthNothing() {
            Pick pick = BlogRecommendationScore.score(profile(null, BlogCertificate.IELTS),
                    post(certificate(BlogCertificate.TOEIC)));

            assertThat(pick.points()).isZero();
            assertThat(pick.matchedTags()).isEmpty();
        }

        @Test
        void aSkillTheReaderWantsToWorkOnIsWorthTwo() {
            Pick pick = BlogRecommendationScore.score(profile(null, null, BlogSkill.SPEAKING, BlogSkill.WRITING),
                    post(skill(BlogSkill.SPEAKING)));

            assertThat(pick.points()).isEqualTo(2);
            assertThat(pick.matchedTags()).containsExactly(skill(BlogSkill.SPEAKING));
        }

        @Test
        void severalOfTheirSkillsOnOnePostAreWorthTwoOnceAndAllAreNamed() {
            Pick pick = BlogRecommendationScore.score(profile(null, null, BlogSkill.SPEAKING, BlogSkill.WRITING),
                    post(skill(BlogSkill.WRITING), skill(BlogSkill.SPEAKING), skill(BlogSkill.READING)));

            assertThat(pick.points()).isEqualTo(2);
            assertThat(pick.matchedTags()).containsExactly(skill(BlogSkill.SPEAKING), skill(BlogSkill.WRITING));
        }

        @Test
        void aSkillTheReaderDidNotChooseIsWorthNothing() {
            Pick pick = BlogRecommendationScore.score(profile(null, null, BlogSkill.SPEAKING),
                    post(skill(BlogSkill.GRAMMAR)));

            assertThat(pick.points()).isZero();
        }

        @Test
        void theReadersOwnLevelIsWorthTwo() {
            Pick pick = BlogRecommendationScore.score(profile(BlogLevel.B1, null), post(level(BlogLevel.B1)));

            assertThat(pick.points()).isEqualTo(2);
            assertThat(pick.matchedTags()).containsExactly(level(BlogLevel.B1));
        }

        @Test
        void theLevelNextToTheirsIsWorthOneInEitherDirectionAndIsNotNamedAsAMatch() {
            Profile b1 = profile(BlogLevel.B1, null);

            Pick above = BlogRecommendationScore.score(b1, post(level(BlogLevel.B2)));
            Pick below = BlogRecommendationScore.score(b1, post(level(BlogLevel.A2)));

            assertThat(above.points()).isEqualTo(1);
            assertThat(below.points()).isEqualTo(1);
            assertThat(above.matchedTags()).isEmpty();
        }

        @Test
        void aLevelTwoStepsAwayIsWorthNothing() {
            Pick pick = BlogRecommendationScore.score(profile(BlogLevel.B1, null), post(level(BlogLevel.C1)));

            assertThat(pick.points()).isZero();
        }

        @Test
        void aPostWithTwoLevelTagsEarnsTheBetterOfThemOnce() {
            Profile b1 = profile(BlogLevel.B1, null);

            Pick pick = BlogRecommendationScore.score(b1,
                    post(level(BlogLevel.A2), level(BlogLevel.B1), level(BlogLevel.B2)));

            assertThat(pick.points()).isEqualTo(2);
            assertThat(pick.matchedTags()).containsExactly(level(BlogLevel.B1));
        }

        @Test
        void aPostWithoutALevelTagGainsNothingAndLosesNothingForLevel() {
            Profile learner = profile(BlogLevel.B1, null, BlogSkill.SPEAKING);

            Pick withoutLevel = BlogRecommendationScore.score(learner, post(skill(BlogSkill.SPEAKING)));
            Pick withOtherLevel = BlogRecommendationScore.score(learner,
                    post(skill(BlogSkill.SPEAKING), level(BlogLevel.C2)));

            assertThat(withoutLevel.points()).isEqualTo(2);
            assertThat(withOtherLevel.points()).isEqualTo(2);
        }

        @Test
        void everythingAddsUpAndTheMatchedTagsComeSkillsFirstThenLevelThenCertificate() {
            Profile learner = profile(BlogLevel.B2, BlogCertificate.IELTS, BlogSkill.WRITING);

            Pick pick = BlogRecommendationScore.score(learner,
                    post(certificate(BlogCertificate.IELTS), level(BlogLevel.B2), skill(BlogSkill.WRITING)));

            assertThat(pick.points()).isEqualTo(7);
            assertThat(pick.matchedTags()).containsExactly(skill(BlogSkill.WRITING), level(BlogLevel.B2),
                    certificate(BlogCertificate.IELTS));
        }
    }

    @Nested
    class Ranking {

        private final Profile speaker = profile(BlogLevel.B1, null, BlogSkill.SPEAKING);

        @Test
        void aPostWithTwoPointsIsRecommendedAndOneWithOnePointIsNot() {
            Candidate twoPoints = post(skill(BlogSkill.SPEAKING));
            Candidate onePoint = post(level(BlogLevel.B2));

            List<Pick> picks = BlogRecommendationScore.rank(speaker, List.of(twoPoints, onePoint), 3);

            assertThat(picks).extracting(Pick::postId).containsExactly(twoPoints.postId());
        }

        @Test
        void aPostWithOnePointAndSomethingElseIsRecommended() {
            Candidate adjacentAndSkill = post(level(BlogLevel.B2), skill(BlogSkill.SPEAKING));

            List<Pick> picks = BlogRecommendationScore.rank(speaker, List.of(adjacentAndSkill), 3);

            assertThat(picks).extracting(Pick::points).containsExactly(3);
        }

        @Test
        void morePointsComeFirstThenMoreLikesThenTheNewer() {
            Candidate fewerPoints = post(50, NOW, skill(BlogSkill.SPEAKING));
            Candidate morePoints = post(0, NOW.minusSeconds(86_400 * 80L), skill(BlogSkill.SPEAKING),
                    level(BlogLevel.B1));
            Candidate liked = post(9, NOW.minusSeconds(86_400 * 30L), skill(BlogSkill.SPEAKING));
            Candidate olderSameLikes = post(5, NOW.minusSeconds(86_400 * 20L), skill(BlogSkill.SPEAKING));
            Candidate newerSameLikes = post(5, NOW.minusSeconds(86_400 * 2L), skill(BlogSkill.SPEAKING));

            List<Pick> picks = BlogRecommendationScore.rank(speaker,
                    List.of(olderSameLikes, newerSameLikes, liked, fewerPoints, morePoints), 10);

            assertThat(picks).extracting(Pick::postId).containsExactly(morePoints.postId(), fewerPoints.postId(),
                    liked.postId(), newerSameLikes.postId(), olderSameLikes.postId());
        }

        @Test
        void onlyTheFirstLimitAreKept() {
            List<Candidate> five = List.of(post(5, NOW, skill(BlogSkill.SPEAKING)),
                    post(4, NOW, skill(BlogSkill.SPEAKING)), post(3, NOW, skill(BlogSkill.SPEAKING)),
                    post(2, NOW, skill(BlogSkill.SPEAKING)), post(1, NOW, skill(BlogSkill.SPEAKING)));

            List<Pick> picks = BlogRecommendationScore.rank(speaker, five, 3);

            assertThat(picks).extracting(Pick::postId).containsExactly(five.get(0).postId(), five.get(1).postId(),
                    five.get(2).postId());
        }

        @Test
        void nothingIsRecommendedWhenNothingFits() {
            assertThat(BlogRecommendationScore.rank(speaker, List.of(post(), post(skill(BlogSkill.GRAMMAR))), 3))
                    .isEmpty();
        }
    }

    @Nested
    class ProfileOf {

        @Test
        void theWordsOfTheUserModuleBecomeBlogsOwnValues() {
            Profile profile = BlogRecommendationScore.profileOf("B2", "TOEIC", List.of("LISTENING", "GRAMMAR"));

            assertThat(profile.level()).isEqualTo(BlogLevel.B2);
            assertThat(profile.certificate()).isEqualTo(BlogCertificate.TOEIC);
            assertThat(profile.skills()).containsExactlyInAnyOrder(BlogSkill.LISTENING, BlogSkill.GRAMMAR);
        }

        @Test
        void pronunciationHasNoBlogTagAndIsLeftOut() {
            Profile profile = BlogRecommendationScore.profileOf(null, null, List.of("PRONUNCIATION", "SPEAKING"));

            assertThat(profile.skills()).containsExactly(BlogSkill.SPEAKING);
        }

        @Test
        void aValueBlogDoesNotKnowIsLeftOutNotRefused() {
            Profile profile = BlogRecommendationScore.profileOf("D9", "CAMBRIDGE", List.of("juggling", "speaking"));

            assertThat(profile.level()).isNull();
            assertThat(profile.certificate()).isNull();
            assertThat(profile.skills()).isEmpty();
            assertThat(profile.isEmpty()).isTrue();
        }

        @Test
        void aProfileWithNothingDeclaredIsEmptyAndAnyOnePartMakesItNot() {
            assertThat(BlogRecommendationScore.profileOf(null, null, null).isEmpty()).isTrue();
            assertThat(BlogRecommendationScore.profileOf("A1", null, List.of()).isEmpty()).isFalse();
            assertThat(BlogRecommendationScore.profileOf(null, "IELTS", List.of()).isEmpty()).isFalse();
            assertThat(BlogRecommendationScore.profileOf(null, null, List.of("WRITING")).isEmpty()).isFalse();
        }
    }
}
