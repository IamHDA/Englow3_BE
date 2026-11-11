package com.englow3.exam.service;

import java.util.List;
import com.englow3.exam.dto.command.ConfigureToeicScoresCommand;
import com.englow3.exam.dto.result.ScoreConversionResult;

public interface ScoreConfigurationService {
    List<ScoreConversionResult> toeic();

    List<ScoreConversionResult> configure(ConfigureToeicScoresCommand command);
}
