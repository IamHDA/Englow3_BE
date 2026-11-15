package com.englow3.friendship.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.friendship.dto.result.BlockedUserResult;
import com.englow3.friendship.entity.Friendship;
import com.englow3.friendship.entity.UserBlock;
import com.englow3.friendship.repository.FriendshipRepository;
import com.englow3.friendship.repository.UserBlockRepository;
import com.englow3.friendship.service.BlockService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * A block is a row in one table and the end of a friendship in another, and that rule spans both, so it is here rather
 * than in either entity. It takes the pair's lock so that it cannot interleave with an accept of the same pair.
 */
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private final UserDirectory userDirectory;
    private final PublicProfileReader publicProfileReader;
    private final FriendshipRepository friendshipRepo;
    private final UserBlockRepository userBlockRepo;
    private final Clock clock;

    @Override
    @Transactional
    public void block(UUID blockedId) {
        UUID me = userDirectory.requireCurrentUserId();
        if (blockedId.equals(me)) {
            throw new BadRequestException("BLOCK_SELF", "You cannot block yourself");
        }
        if (!publicProfileReader.isLearner(me)) {
            throw new ForbiddenException("FRIENDSHIP_LEARNERS_ONLY", "Only learners can use friends");
        }
        if (!publicProfileReader.isLearner(blockedId)) {
            throw new NotFoundException("LEARNER_NOT_FOUND", "No such learner");
        }

        friendshipRepo.lockPair(Friendship.pairKey(me, blockedId));
        userBlockRepo.insertIfAbsent(me, blockedId, Instant.now(clock));
        friendshipRepo.deletePair(me, blockedId);
    }

    @Override
    @Transactional
    public void unblock(UUID blockedId) {
        userBlockRepo.deleteBlock(userDirectory.requireCurrentUserId(), blockedId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlockedUserResult> blocked(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        // The key is composite, so the tie-break names its column rather than going through StablePage's "id".
        Pageable ordered = PageRequest.of(page.getPageNumber(), page.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id.blockedId")));
        Page<UserBlock> rows = userBlockRepo.findByIdBlockerId(me, ordered);

        Set<UUID> blockedIds = rows.getContent().stream().map(block -> block.getId().getBlockedId())
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = publicProfileReader.profiles(blockedIds);
        return rows.map(block -> {
            UUID blockedId = block.getId().getBlockedId();
            PublicProfile profile = profiles.get(blockedId);
            return new BlockedUserResult(blockedId, profile == null ? null : profile.displayName(),
                    profile == null ? null : profile.avatarUrl(), block.getCreatedAt());
        });
    }
}
