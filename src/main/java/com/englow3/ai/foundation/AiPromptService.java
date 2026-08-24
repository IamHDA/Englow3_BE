package com.englow3.ai.foundation;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.ConflictException;

@Service
public class AiPromptService {

    private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("\\{\\{[a-zA-Z0-9_.-]+}}", Pattern.MULTILINE);

    private final AiPromptVersionRepository repository;

    AiPromptService(AiPromptVersionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RenderedPrompt render(String templateKey, Map<String, ?> variables) {
        AiPromptVersion prompt = repository.findFirstByTemplateTemplateKeyAndActiveTrueOrderByVersionDesc(templateKey)
                .orElseThrow(() -> new ConflictException("AI_PROMPT_NOT_ACTIVE",
                        "No active prompt exists for " + templateKey));
        return new RenderedPrompt(prompt.getId(), Integer.toString(prompt.getVersion()),
                renderText(prompt.getSystemTemplate(), variables), renderText(prompt.getUserTemplate(), variables),
                prompt.getResponseSchema());
    }

    static String renderText(String template, Map<String, ?> variables) {
        String rendered = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        if (UNRESOLVED_VARIABLE.matcher(rendered).find()) {
            throw new ConflictException("AI_PROMPT_VARIABLE_MISSING", "The prompt is missing a required variable");
        }
        return rendered;
    }
}
