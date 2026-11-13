package com.englow3.roadmap.dto.command;

public record SetLevelCheckWeightCommand(String purposeCode, int vocabulary, int listening, int speaking,
        int priority) {
}
