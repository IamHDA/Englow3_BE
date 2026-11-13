package com.englow3.exam.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.api.CompletedFullTestReader;
import com.englow3.exam.entity.FullTestSittingStatus;
import com.englow3.exam.repository.FullTestSittingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompletedFullTestReaderImpl implements CompletedFullTestReader {

    private final FullTestSittingRepository sittingRepo;

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletedSitting> latestCompleted(UUID fullTestId, UUID userId, Instant since) {
        return sittingRepo
                .findFirstByUserIdAndFullTestIdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(userId,
                        fullTestId, FullTestSittingStatus.COMPLETED, since)
                .map(sitting -> new CompletedSitting(sitting.getId(), sitting.getFullTestId(), sitting.getCompletedAt(),
                        sitting.getOverallBand()));
    }
}
