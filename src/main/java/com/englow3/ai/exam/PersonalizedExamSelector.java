package com.englow3.ai.exam;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
class PersonalizedExamSelector {

    List<Candidate> select(List<Candidate> candidates, PersonalizedExamDtos.Skill skill, int count,
            BigDecimal minimumDifficulty, BigDecimal maximumDifficulty, UUID seed) {
        BigDecimal midpoint = minimumDifficulty.add(maximumDifficulty).divide(BigDecimal.valueOf(2));
        List<Candidate> eligible = candidates.stream()
                .filter(candidate -> candidate.difficulty().compareTo(minimumDifficulty) >= 0
                        && candidate.difficulty().compareTo(maximumDifficulty) <= 0)
                .filter(candidate -> skill == PersonalizedExamDtos.Skill.MIXED
                        || candidate.skill().equals(skill.name()))
                .sorted(Comparator.comparing(Candidate::usedBefore).thenComparing(Candidate::mastery)
                        .thenComparing(candidate -> candidate.difficulty().subtract(midpoint).abs())
                        .thenComparing(candidate -> stableOrder(seed, candidate.itemId())))
                .toList();
        if (skill != PersonalizedExamDtos.Skill.MIXED) {
            return eligible.stream().limit(count).toList();
        }
        int listeningTarget = count / 2;
        int readingTarget = count - listeningTarget;
        List<Candidate> selected = new java.util.ArrayList<>();
        selected.addAll(eligible.stream().filter(candidate -> candidate.skill().equals("LISTENING"))
                .limit(listeningTarget).toList());
        selected.addAll(eligible.stream().filter(candidate -> candidate.skill().equals("READING")).limit(readingTarget)
                .toList());
        return selected.stream().sorted(Comparator.comparing(candidate -> stableOrder(seed, candidate.itemId())))
                .toList();
    }

    private String stableOrder(UUID seed, String itemId) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((seed + ":" + itemId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    record Candidate(String itemId, String groupId, int partNumber, String skill, String question, String questionType,
            BigDecimal difficulty, BigDecimal mastery, boolean usedBefore, String explanationEn, String explanationVi,
            String reviewStatus, List<String> conceptIds) {
    }
}
