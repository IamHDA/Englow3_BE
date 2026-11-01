package com.englow3.roadmap.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.roadmap.dto.command.SetLevelCheckWeightCommand;
import com.englow3.roadmap.dto.result.LevelCheckWeightsResult;
import com.englow3.roadmap.entity.LevelCheckWeight;
import com.englow3.roadmap.query.LevelCheckWeightQuery;
import com.englow3.roadmap.query.LevelCheckWeightQuery.PurposeName;
import com.englow3.roadmap.repository.LevelCheckWeightRepository;
import com.englow3.roadmap.service.LevelCheckWeightService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LevelCheckWeightServiceImpl implements LevelCheckWeightService {

    private final LevelCheckWeightRepository weightRepo;
    private final LevelCheckWeightQuery weightQuery;
    private final UserDirectory userDirectory;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public LevelCheckWeightsResult current() {
        Map<String, UUID> inForce = weightQuery.rowsInForce(clock.instant());
        List<LevelCheckWeight> history = weightRepo.findTop30ByOrderByEffectiveFromDesc();
        Map<UUID, LevelCheckWeight> rows = weightRepo.findAllById(inForce.values()).stream()
                .collect(Collectors.toMap(LevelCheckWeight::getId, Function.identity()));
        List<UUID> authors = new ArrayList<>();
        history.forEach(row -> authors.add(row.getCreatedByUserId()));
        rows.values().forEach(row -> authors.add(row.getCreatedByUserId()));
        Map<UUID, String> names = userDirectory.displayNames(authors.stream().filter(Objects::nonNull).toList());

        List<LevelCheckWeightsResult.Purpose> purposes = new ArrayList<>();
        purposes.add(new LevelCheckWeightsResult.Purpose(LevelCheckWeight.DEFAULT_PURPOSE, null,
                change(rows.get(inForce.get(LevelCheckWeight.DEFAULT_PURPOSE)), names)));
        for (PurposeName purpose : weightQuery.learningPurposes()) {
            purposes.add(new LevelCheckWeightsResult.Purpose(purpose.code(), purpose.displayName(),
                    change(rows.get(inForce.get(purpose.code())), names)));
        }
        return new LevelCheckWeightsResult(purposes, history.stream().map(row -> change(row, names)).toList());
    }

    @Override
    @Transactional
    public LevelCheckWeightsResult set(SetLevelCheckWeightCommand command) {
        boolean known = LevelCheckWeight.DEFAULT_PURPOSE.equals(command.purposeCode()) || weightQuery.learningPurposes()
                .stream().anyMatch(purpose -> purpose.code().equals(command.purposeCode()));
        if (!known) {
            throw new BadRequestException("LEVEL_CHECK_PURPOSE_UNKNOWN",
                    "No learning purpose %s".formatted(command.purposeCode()));
        }
        weightRepo.saveAndFlush(LevelCheckWeight.from(UUID.randomUUID(), command.purposeCode(), command.vocabulary(),
                command.listening(), command.speaking(), command.priority(), clock.instant(),
                userDirectory.requireCurrentUserId()));
        return current();
    }

    private static LevelCheckWeightsResult.Change change(LevelCheckWeight row, Map<UUID, String> names) {
        if (row == null) {
            return null;
        }
        return new LevelCheckWeightsResult.Change(row.getId(), row.getPurposeCode(), row.getVocabulary(),
                row.getListening(), row.getSpeaking(), row.getPriority(), row.getEffectiveFrom(),
                row.getCreatedByUserId() == null ? null : names.get(row.getCreatedByUserId()));
    }
}
