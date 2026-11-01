package com.englow3.roadmap.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.englow3.roadmap.entity.CefrLevel;

class LevelCombinerTest {

    @Test
    void weighsSpeakingMoreForThoseLearningToTalk() {
        assertThat(LevelCombiner.forPurposes(List.of("TRAVEL"))).isEqualTo(new LevelCombiner.Weights(35, 30, 35));
        assertThat(LevelCombiner.forPurposes(List.of("COMMUNICATION", "WORK")))
                .isEqualTo(new LevelCombiner.Weights(35, 30, 35));
        assertThat(LevelCombiner.forPurposes(List.of("WORK"))).isEqualTo(new LevelCombiner.Weights(40, 30, 30));
        assertThat(LevelCombiner.forPurposes(null)).isEqualTo(new LevelCombiner.Weights(40, 30, 30));
    }

    @Test
    void roundsDownSoNobodyIsPlacedAboveWhatTheyShowed() {
        // 4*40 + 4*30 + 3*30 = 370 -> 3.7, B1 rather than B2.
        var result = LevelCombiner.combine(CefrLevel.B2, CefrLevel.B2, CefrLevel.B1,
                new LevelCombiner.Weights(40, 30, 30));
        assertThat(result.level()).isEqualTo(CefrLevel.B1);
        assertThat(result.appliedWeights()).isEqualTo("{\"vocabulary\":40,\"listening\":30,\"speaking\":30}");
    }

    @Test
    void equalPartsGiveThatBand() {
        assertThat(LevelCombiner.combine(CefrLevel.C1, CefrLevel.C1, CefrLevel.C1, LevelCombiner.DEFAULT).level())
                .isEqualTo(CefrLevel.C1);
    }

    @Test
    void withoutSpeakingTheWrittenWeightsMakeTheWhole() {
        // (4*40 + 3*30) / 70 = 3.57 -> B1, and the record says speaking counted for nothing.
        var result = LevelCombiner.combine(CefrLevel.B2, CefrLevel.B1, null, LevelCombiner.DEFAULT);
        assertThat(result.level()).isEqualTo(CefrLevel.B1);
        assertThat(result.appliedWeights()).contains("\"speaking\":0");
    }
}
