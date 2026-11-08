package com.englow3.generation.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.GeneratedTaskWriter;
import com.englow3.exam.api.GeneratedPaperWriter;
import com.englow3.generation.repository.GenerationRequestRepository;
import com.englow3.generation.service.GenerationArtifacts;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** Database work only: provider calls and storage uploads stay in the handler, outside this transaction. */
@Service
@RequiredArgsConstructor
public class GenerationArtifactsImpl implements GenerationArtifacts {
    private final GenerationRequestRepository requestRepo;
    private final GenerationSteps steps;
    private final GeneratedPaperWriter papers;
    private final GeneratedTaskWriter tasks;
    private final ObjectMapper mapper;

    @Override
    @Transactional
    public void assemble(UUID requestId, String builtJson) {
        var request = requestRepo.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.isAt("assemble")) {
            return;
        }
        var work = steps.work(requestId, "assemble");
        JsonNode built = read(builtJson);
        UUID examId = papers.createDraft(work.requestedByUserId(), built.path("paper").toString());
        List<UUID> taskIds = new ArrayList<>();
        for (JsonNode task : built.path("tasks")) {
            ObjectNode body = task.deepCopy();
            body.remove("chart");
            taskIds.add(tasks.createDraft(work.requestedByUserId(), body.toString()));
        }
        ObjectNode result = mapper.createObjectNode().put("examId", examId.toString());
        result.set("taskIds", mapper.valueToTree(taskIds));
        result.set("chart_png_base64", built.path("chart_png_base64"));
        result.set("listening", built.path("listening"));
        steps.apply(requestId, "assemble", work.attempt(), result.toString());
    }

    @Override
    @Transactional
    public void finish(UUID requestId) {
        var request = requestRepo.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !request.isAt("finish")) {
            return;
        }
        var work = steps.work(requestId, "finish");
        papers.submitForReview(work.examId());
        work.taskIds().forEach(tasks::submitForReview);
        String title = "IELTS Academic AI Set: " + work.components().path("plan").path("title").asText("Untitled");
        UUID fullTestId = papers.createFullTestDraft(work.requestedByUserId(),
                title.substring(0, Math.min(title.length(), 100)),
                "Written with AI. Review the paper and every task before approving.", work.examId(), work.taskIds());
        steps.apply(requestId, "finish", work.attempt(),
                mapper.createObjectNode().put("fullTestId", fullTestId.toString()).toString());
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid generated assembly", e);
        }
    }
}
