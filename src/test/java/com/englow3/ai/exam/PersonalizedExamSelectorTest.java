package com.englow3.ai.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.ai.exam.PersonalizedExamSelector.Candidate;

class PersonalizedExamSelectorTest {
    private final PersonalizedExamSelector selector = new PersonalizedExamSelector();

    private final UUID seed = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Candidate candidate(String id, String skill, String difficulty, String mastery, boolean used) {
        return new Candidate(id, "group-" + id, skill.equals("LISTENING") ? 2 : 5, skill, "Question " + id,
                "MULTIPLE_CHOICE", new BigDecimal(difficulty), new BigDecimal(mastery), used, "Explanation",
                "Giải thích", "human_approved", List.of("concept-" + id));
    }

    @Nested
    class Success {

        @Test
        void prefersUnusedContentBeforePreviouslyDeliveredContent() {
            Candidate usedWeakItem = candidate("used", "READING", "0.1", "0.1", true);
            Candidate unusedStrongItem = candidate("unused", "READING", "0.5", "0.8", false);

            List<Candidate> selected = selector.select(List.of(usedWeakItem, unusedStrongItem),
                    PersonalizedExamDtos.Skill.READING, 1, BigDecimal.ZERO, BigDecimal.ONE, seed);

            assertThat(selected).extracting(Candidate::itemId).containsExactly("unused");
        }

        @Test
        void prioritizesWeakConceptMasteryWithinUnusedPool() {
            Candidate strong = candidate("strong", "READING", "0.5", "0.9", false);
            Candidate weak = candidate("weak", "READING", "0.5", "0.2", false);

            List<Candidate> selected = selector.select(List.of(strong, weak), PersonalizedExamDtos.Skill.READING, 1,
                    BigDecimal.ZERO, BigDecimal.ONE, seed);

            assertThat(selected).extracting(Candidate::itemId).containsExactly("weak");
        }

        @Test
        void excludesItemsOutsideRequestedDifficultyWindow() {
            Candidate easy = candidate("easy", "READING", "0.2", "0.2", false);
            Candidate medium = candidate("medium", "READING", "0.5", "0.3", false);
            Candidate hard = candidate("hard", "READING", "0.9", "0.1", false);

            List<Candidate> selected = selector.select(List.of(easy, medium, hard), PersonalizedExamDtos.Skill.READING,
                    10, new BigDecimal("0.4"), new BigDecimal("0.6"), seed);

            assertThat(selected).extracting(Candidate::itemId).containsExactly("medium");
        }

        @Test
        void mixedBlueprintUsesDeterministicListeningReadingSplit() {
            List<Candidate> candidates = List.of(candidate("l1", "LISTENING", "0.5", "0.1", false),
                    candidate("l2", "LISTENING", "0.5", "0.2", false), candidate("r1", "READING", "0.5", "0.1", false),
                    candidate("r2", "READING", "0.5", "0.2", false), candidate("r3", "READING", "0.5", "0.3", false));

            List<Candidate> selected = selector.select(candidates, PersonalizedExamDtos.Skill.MIXED, 5, BigDecimal.ZERO,
                    BigDecimal.ONE, seed);

            assertThat(selected).hasSize(5);
            assertThat(selected).filteredOn(candidate -> candidate.skill().equals("LISTENING")).hasSize(2);
            assertThat(selected).filteredOn(candidate -> candidate.skill().equals("READING")).hasSize(3);
            assertThat(selector.select(candidates, PersonalizedExamDtos.Skill.MIXED, 5, BigDecimal.ZERO, BigDecimal.ONE,
                    seed)).extracting(Candidate::itemId)
                            .containsExactlyElementsOf(selected.stream().map(Candidate::itemId).toList());
        }

    }

}
