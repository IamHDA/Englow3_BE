package com.englow3.exam.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.exam.dto.command.ConfigureToeicScoresCommand;
import com.englow3.exam.dto.result.ScoreConversionResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.repository.ScoreConversionRepository;
import com.englow3.exam.helper.ToeicScoring;
import com.englow3.exam.service.ScoreConfigurationService;
import com.englow3.shared.error.*;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreConfigurationServiceImpl implements ScoreConfigurationService {
    private final ScoreConversionRepository conversionRepo;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<ScoreConversionResult> toeic() {
        return conversionRepo.findByCertificateType(CertificateType.TOEIC).stream()
                .map(r -> new ScoreConversionResult(r.getId(), r.getSectionType(), r.getRawScoreFrom(),
                        r.getRawScoreTo(), r.getConvertedScore(), r.getEffectiveFrom(), r.getSourceReference()))
                .sorted(Comparator.comparing(ScoreConversionResult::effectiveFrom).reversed()
                        .thenComparing(ScoreConversionResult::sectionType)
                        .thenComparingInt(ScoreConversionResult::rawScoreFrom))
                .toList();
    }

    @Transactional
    public List<ScoreConversionResult> configure(ConfigureToeicScoresCommand command) {
        ToeicScoring.requireCompleteTable(command.rows());
        if (command.effectiveFrom() == null || command.effectiveFrom().isBefore(LocalDate.now(clock))
                || command.sourceReference() == null || command.sourceReference().isBlank()
                || command.sourceReference().strip().length() > 1000)
            throw new BadRequestException("SCORE_REFERENCE_REQUIRED",
                    "Supply a reference and a current or future effective date");
        conversionRepo.lockConfiguration();
        if (conversionRepo.existsByCertificateTypeAndEffectiveFrom(CertificateType.TOEIC, command.effectiveFrom()))
            throw new ConflictException("SCORE_TABLE_EXISTS",
                    "This date already has a table; publish a new version on a later date");
        String json;
        try {
            json = mapper.writeValueAsString(command.rows());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode score configuration");
        }
        conversionRepo.insertToeicTable(json, command.effectiveFrom(), command.sourceReference().strip(),
                userDirectory.requireCurrentUserId());
        return toeic();
    }
}
