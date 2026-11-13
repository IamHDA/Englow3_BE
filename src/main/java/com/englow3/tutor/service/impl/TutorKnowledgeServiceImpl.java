package com.englow3.tutor.service.impl;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.englow3.tutor.dto.result.TutorSourceResult;
import com.englow3.tutor.repository.TutorResourceRepository;
import com.englow3.tutor.service.TutorKnowledgeService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorKnowledgeServiceImpl implements TutorKnowledgeService {
    private final TutorResourceRepository resourceRepo;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Set<String> STOP = Set.of("what", "which", "when", "where", "how", "why", "the", "and", "this",
            "that", "with", "for", "can", "you", "please", "explain", "help", "english", "giúp", "mình", "bạn", "giải",
            "thích", "tiếng", "anh", "cách", "dùng");

    @Transactional(readOnly = true)
    public List<TutorSourceResult> retrieve(String question) {
        var matcher = WORD.matcher(question.toLowerCase(Locale.ROOT));
        Set<String> words = new LinkedHashSet<>();
        while (matcher.find() && words.size() < 12)
            if (!STOP.contains(matcher.group()))
                words.add(matcher.group());
        if (words.isEmpty())
            return List.of();
        var resources = resourceRepo.retrieve(String.join(" OR ", words), PageRequest.of(0, 3));
        List<TutorSourceResult> sources = new ArrayList<>();
        for (var resource : resources) {
            String body = resource.getBody();
            sources.add(new TutorSourceResult("S" + (sources.size() + 1), resource.getId(), resource.getTitle(),
                    resource.getVersionNumber(), body.substring(0, Math.min(1200, body.length()))));
        }
        return sources;
    }
}
