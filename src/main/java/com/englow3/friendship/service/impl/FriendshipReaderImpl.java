package com.englow3.friendship.service.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.friendship.api.FriendshipReader;
import com.englow3.friendship.entity.FriendshipStatus;
import com.englow3.friendship.repository.FriendshipRepository;
import com.englow3.friendship.repository.UserBlockRepository;

import lombok.RequiredArgsConstructor;

/** Reads only, and caches nothing: a block must take effect on the very next message or post. */
@Service
@RequiredArgsConstructor
public class FriendshipReaderImpl implements FriendshipReader {

    private final FriendshipRepository friendshipRepo;
    private final UserBlockRepository blockRepo;

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(UUID a, UUID b) {
        return friendshipRepo.existsByStatusAndRequesterIdAndAddresseeId(FriendshipStatus.ACCEPTED, a, b)
                || friendshipRepo.existsByStatusAndRequesterIdAndAddresseeId(FriendshipStatus.ACCEPTED, b, a);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return blockRepo.existsEitherWay(a, b);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> blockedEitherWay(UUID userId) {
        Set<UUID> either = new HashSet<>(blockRepo.blockedBy(userId));
        either.addAll(blockRepo.blockersOf(userId));
        return either;
    }
}
