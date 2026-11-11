package com.englow3.translation.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.translation.dto.command.SaveTranslationSetCommand;
import com.englow3.translation.dto.result.TranslationAuthoringResult;
import com.englow3.translation.dto.result.TranslationSetReviewResult;
import com.englow3.translation.entity.TranslationItem;
import com.englow3.translation.entity.TranslationSet;
import com.englow3.translation.entity.TranslationSetStatus;
import com.englow3.translation.repository.TranslationItemRepository;
import com.englow3.translation.repository.TranslationSetRepository;
import com.englow3.translation.service.AdminTranslationService;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Authoring translation sets, and the review workflow every other content type already runs. */
@Service
@RequiredArgsConstructor
public class AdminTranslationServiceImpl implements AdminTranslationService {

    private final TranslationSetRepository setRepo;
    private final TranslationItemRepository itemRepo;
    private final UserDirectory userDirectory;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<TranslationSetReviewResult> searchForAuthoring(TranslationSetStatus status, String title,
            Pageable pageable) {
        UUID scope = userDirectory.authoringScope();
        Page<TranslationSet> page = scope == null ? setRepo.searchForAuthoring(status, title, pageable)
                : setRepo.searchVisibleForAuthoring(status, title, scope, pageable);
        Map<UUID, Long> counts = itemCounts(page);
        return page.map(set -> TranslationSetReviewResult.of(set, counts.getOrDefault(set.getId(), 0L)));
    }

    @Override
    @Transactional
    public TranslationSetReviewResult submitForReview(UUID setId) {
        TranslationSet set = requireSet(setId);
        long items = itemRepo.countByTranslationSetId(setId);
        set.submitForReview(items, clock.instant());
        return TranslationSetReviewResult.of(set, items);
    }

    /** The reviewer's id comes from the token, not the request - nobody credits an approval to someone else. */
    @Override
    @Transactional
    public TranslationSetReviewResult approve(UUID setId) {
        TranslationSet set = requireSet(setId);
        set.approve(userDirectory.requireCurrentUserId(), clock.instant());
        return reviewOf(set);
    }

    @Override
    @Transactional
    public TranslationSetReviewResult reject(UUID setId, String note) {
        TranslationSet set = requireSet(setId);
        set.reject(userDirectory.requireCurrentUserId(), note, clock.instant());
        return reviewOf(set);
    }

    @Override
    @Transactional
    public TranslationSetReviewResult publish(UUID setId) {
        TranslationSet set = requireSet(setId);
        long items = itemRepo.countByTranslationSetId(setId);
        set.publish(items, clock.instant());
        return TranslationSetReviewResult.of(set, items);
    }

    @Override
    @Transactional
    public TranslationSetReviewResult restore(UUID setId) {
        TranslationSet set = requireSet(setId);
        set.restore();
        return reviewOf(set);
    }

    @Override
    @Transactional
    public TranslationSetReviewResult archive(UUID setId) {
        TranslationSet set = requireSet(setId);
        set.archive();
        return reviewOf(set);
    }

    @Override
    @Transactional(readOnly = true)
    public TranslationAuthoringResult authoringDetail(UUID setId) {
        TranslationSet set = requireSet(setId);
        return new TranslationAuthoringResult(setId, set.getVersion(), set.getStatus().name(), set.getReviewNote(),
                new SaveTranslationSetCommand.Metadata(set.getSlug(), set.getTitle(), set.getTopic(),
                        set.getTargetLevel(), set.getDescription()),
                itemRepo.findByTranslationSetIdOrderByOrderNo(setId).stream()
                        .map(item -> new SaveTranslationSetCommand.Item(item.getSourceVi(), item.getReferenceEn(),
                                item.getNotes()))
                        .toList());
    }

    /**
     * Saves the details and replaces every sentence. Replacing rather than diffing is safe because only an unpublished
     * set can be edited, and nobody can have attempted a sentence of a set that was never published.
     */
    @Override
    @Transactional
    public TranslationAuthoringResult saveAuthoring(SaveTranslationSetCommand command) {
        var meta = command.metadata();
        UUID id = command.id();
        if (id == null) {
            if (setRepo.existsBySlug(meta.slug())) {
                throw new ConflictException("CONTENT_SLUG_TAKEN", "Another item uses this slug");
            }
            id = setRepo.save(TranslationSet.draft(meta.slug(), meta.title(), meta.topic(), meta.targetLevel(),
                    meta.description(), userDirectory.requireCurrentUserId())).getId();
        } else {
            if (command.version() == null) {
                throw new BadRequestException("CONTENT_VERSION_REQUIRED", "A version is required when editing");
            }
            TranslationSet set = requireSet(id);
            if (setRepo.existsBySlugAndIdNot(meta.slug(), id)) {
                throw new ConflictException("CONTENT_SLUG_TAKEN", "Another item uses this slug");
            }
            set.updateDraft(meta.slug(), meta.title(), meta.topic(), meta.targetLevel(), meta.description(),
                    command.version(), clock.instant());
            setRepo.flush();
        }
        itemRepo.deleteAllInBatch(itemRepo.findByTranslationSetIdOrderByOrderNo(id));
        var rows = new ArrayList<TranslationItem>();
        int order = 1;
        for (var item : command.items()) {
            rows.add(TranslationItem.of(id, order++, item.sourceVi(), item.referenceEn(), item.notes()));
        }
        itemRepo.saveAll(rows);
        setRepo.flush();
        return authoringDetail(id);
    }

    private TranslationSetReviewResult reviewOf(TranslationSet set) {
        return TranslationSetReviewResult.of(set, itemRepo.countByTranslationSetId(set.getId()));
    }

    private Map<UUID, Long> itemCounts(Page<TranslationSet> page) {
        var ids = page.map(TranslationSet::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return itemRepo.countBySetIds(ids).stream().collect(Collectors
                .toMap(TranslationItemRepository.SetCount::getSetId, TranslationItemRepository.SetCount::getTotal));
    }

    private TranslationSet requireSet(UUID setId) {
        TranslationSet set = setRepo.findById(setId)
                .orElseThrow(() -> new NotFoundException("TRANSLATION_SET_NOT_FOUND",
                        "No translation set with id %s".formatted(setId)));
        if (set.getStatus() != TranslationSetStatus.PUBLISHED)
            userDirectory.requireAuthorAccess(set.getCreatedByUserId());
        return set;
    }
}
