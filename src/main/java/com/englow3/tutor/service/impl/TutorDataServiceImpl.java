package com.englow3.tutor.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.BadRequestException;
import com.englow3.tutor.dto.result.TutorDataPageResult;
import com.englow3.tutor.repository.TutorDataRepository;
import com.englow3.tutor.service.TutorDataService;
import com.englow3.user.api.UserDirectory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorDataServiceImpl implements TutorDataService {
    private final UserDirectory users;
    private final AiJobQueue jobs;
    private final TutorDataRepository dataRepo;

    @Override
    @Transactional(readOnly = true)
    public TutorDataPageResult export(String kind, int page) {
        if (page < 0 || page > 100000)
            throw new BadRequestException("TUTOR_EXPORT_PAGE", "Invalid export page");
        var items = dataRepo.export(users.requireCurrentUserId(), kind, page);
        return new TutorDataPageResult(items.stream().limit(2).toList(), page, items.size() > 2);
    }

    @Override
    @Transactional
    public void purge() {
        var user = users.requireCurrentUserId();
        jobs.lockBudget(user);
        jobs.purgeTutorData(user);
        dataRepo.purge(user);
    }
}
