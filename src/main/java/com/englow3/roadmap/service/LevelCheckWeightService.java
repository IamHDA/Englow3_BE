package com.englow3.roadmap.service;

import com.englow3.roadmap.dto.command.SetLevelCheckWeightCommand;
import com.englow3.roadmap.dto.result.LevelCheckWeightsResult;

/** The administrator's view of the level-check weights, and the one way to change them. */
public interface LevelCheckWeightService {

    LevelCheckWeightsResult current();

    /**
     * New weights for one purpose, in force from now. Earlier rows stay as history, and checks already completed keep
     * the weights they were combined with.
     */
    LevelCheckWeightsResult set(SetLevelCheckWeightCommand command);
}
