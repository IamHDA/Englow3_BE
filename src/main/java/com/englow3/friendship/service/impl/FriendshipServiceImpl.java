package com.englow3.friendship.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.friendship.dto.command.SendFriendRequestCommand;
import com.englow3.friendship.dto.result.FriendRequestOutcome;
import com.englow3.friendship.dto.result.FriendRequestResult;
import com.englow3.friendship.dto.result.FriendResult;
import com.englow3.friendship.dto.result.LearnerSearchResult;
import com.englow3.friendship.dto.result.RelationshipResult;
import com.englow3.friendship.entity.Friendship;
import com.englow3.friendship.entity.FriendshipStatus;
import com.englow3.friendship.entity.RelationshipStatus;
import com.englow3.friendship.repository.FriendshipRepository;
import com.englow3.friendship.repository.FriendshipRepository.PairIds;
import com.englow3.friendship.repository.UserBlockRepository;
import com.englow3.friendship.service.FriendshipService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.page.StablePage;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates requests, friendships and the friend search. Every write on a pair takes the pair's advisory lock first
 * and reads afterwards, so a block cannot race an accept and two crossed requests cannot both create a row. The rules
 * about one row live in {@link Friendship}; what spans a block, a lock and a second row is here.
 */
@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    /** Below this a search would list almost everyone, which is what the minimum is there to prevent. */
    private static final int MIN_SEARCH_TERM_LENGTH = 2;

    private final UserDirectory userDirectory;
    private final PublicProfileReader publicProfileReader;
    private final FriendshipRepository friendshipRepo;
    private final UserBlockRepository userBlockRepo;
    private final Clock clock;

    @Override
    @Transactional
    public FriendRequestOutcome sendRequest(SendFriendRequestCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        UUID other = command.addresseeId();
        requireLearnerCaller(me);
        requireLearnerTarget(other);

        friendshipRepo.lockPair(Friendship.pairKey(me, other));
        if (userBlockRepo.existsEitherWay(me, other)) {
            // Deliberately silent on who blocked whom.
            throw new ForbiddenException("FRIEND_REQUEST_NOT_ALLOWED", "This learner cannot be invited");
        }

        Instant now = Instant.now(clock);
        Optional<Friendship> existing = friendshipRepo.findPair(me, other);
        if (existing.isEmpty()) {
            Friendship created = friendshipRepo.save(Friendship.request(me, other, now));
            return new FriendRequestOutcome(requestResult(created, other), true);
        }
        Friendship friendship = existing.get();
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new ConflictException("ALREADY_FRIENDS", "You are already friends");
        }
        if (!friendship.isRequestedBy(me)) {
            // The other learner asked first: both want it, so this is the answer to their request.
            friendship.accept(me, now);
        }
        return new FriendRequestOutcome(requestResult(friendship, other), false);
    }

    @Override
    @Transactional
    public FriendRequestOutcome accept(UUID requestId) {
        UUID me = userDirectory.requireCurrentUserId();
        requireLearnerCaller(me);

        Friendship friendship = lockAndLoad(requestId, me);
        friendship.accept(me, Instant.now(clock));
        return new FriendRequestOutcome(requestResult(friendship, friendship.otherParty(me)), false);
    }

    @Override
    @Transactional
    public void withdraw(UUID requestId) {
        UUID me = userDirectory.requireCurrentUserId();

        Friendship friendship = lockAndLoad(requestId, me);
        friendship.ensureWithdrawableBy(me);
        friendshipRepo.delete(friendship);
    }

    @Override
    @Transactional
    public void unfriend(UUID otherUserId) {
        UUID me = userDirectory.requireCurrentUserId();

        friendshipRepo.lockPair(Friendship.pairKey(me, otherUserId));
        Friendship friendship = friendshipRepo.findPair(me, otherUserId).orElseThrow(
                () -> new NotFoundException("FRIENDSHIP_NOT_FOUND", "You are not friends with this learner"));
        friendship.ensureUnfriendableBy(me);
        friendshipRepo.delete(friendship);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendResult> friends(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        Page<Friendship> rows = friendshipRepo.findFriendships(me, ordered(page, "acceptedAt"));
        Map<UUID, PublicProfile> profiles = profilesOfOthers(rows.getContent(), me);
        return rows.map(friendship -> {
            UUID other = friendship.otherParty(me);
            PublicProfile profile = profiles.get(other);
            return new FriendResult(other, displayName(profile), avatarUrl(profile), friendship.getAcceptedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendRequestResult> incomingRequests(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        Page<Friendship> rows = friendshipRepo.findByAddresseeIdAndStatus(me, FriendshipStatus.PENDING,
                ordered(page, "requestedAt"));
        return requestPage(rows, me);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendRequestResult> outgoingRequests(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        Page<Friendship> rows = friendshipRepo.findByRequesterIdAndStatus(me, FriendshipStatus.PENDING,
                ordered(page, "requestedAt"));
        return requestPage(rows, me);
    }

    @Override
    @Transactional(readOnly = true)
    public long incomingRequestCount() {
        return friendshipRepo.countByAddresseeIdAndStatus(userDirectory.requireCurrentUserId(),
                FriendshipStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LearnerSearchResult> searchLearners(String term, Pageable page) {
        String trimmed = term == null ? "" : term.trim();
        if (trimmed.length() < MIN_SEARCH_TERM_LENGTH) {
            throw new BadRequestException("SEARCH_TERM_TOO_SHORT",
                    "Type at least %d characters to search".formatted(MIN_SEARCH_TERM_LENGTH));
        }
        UUID me = userDirectory.requireCurrentUserId();
        requireLearnerCaller(me);

        // Always holds the caller, so the "not in" the search builds from it is never given an empty list.
        Set<UUID> excluded = new HashSet<>();
        excluded.add(me);
        excluded.addAll(userBlockRepo.blockedBy(me));
        excluded.addAll(userBlockRepo.blockersOf(me));

        Page<PublicProfile> found = publicProfileReader.searchLearners(trimmed, excluded, ordered(page, "displayName"));
        if (found.isEmpty()) {
            return new PageImpl<>(List.of(), found.getPageable(), found.getTotalElements());
        }
        List<UUID> ids = found.getContent().stream().map(PublicProfile::id).toList();
        Map<UUID, Friendship> withMe = friendshipRepo.findPairsWith(me, ids).stream()
                .collect(Collectors.toMap(friendship -> friendship.otherParty(me), Function.identity()));
        return found.map(profile -> {
            Friendship friendship = withMe.get(profile.id());
            RelationshipStatus relationship = relationshipOf(friendship, me);
            UUID requestId = relationship == RelationshipStatus.REQUEST_RECEIVED ? friendship.getId() : null;
            return new LearnerSearchResult(profile.id(), profile.displayName(), profile.avatarUrl(), relationship,
                    requestId);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public RelationshipResult relationshipWith(UUID otherUserId) {
        UUID me = userDirectory.requireCurrentUserId();
        if (otherUserId.equals(me)) {
            return new RelationshipResult(otherUserId, RelationshipStatus.SELF, null);
        }
        // A wall can be opened by anyone and for anyone, so this answers with a state rather than an error: staff, and
        // an id that names no account, simply have no friend actions.
        if (!publicProfileReader.isLearner(me) || !publicProfileReader.isLearner(otherUserId)) {
            return new RelationshipResult(otherUserId, RelationshipStatus.NOT_APPLICABLE, null);
        }
        if (userBlockRepo.existsByIdBlockerIdAndIdBlockedId(me, otherUserId)) {
            return new RelationshipResult(otherUserId, RelationshipStatus.BLOCKED_BY_ME, null);
        }
        if (userBlockRepo.existsByIdBlockerIdAndIdBlockedId(otherUserId, me)) {
            return new RelationshipResult(otherUserId, RelationshipStatus.UNAVAILABLE, null);
        }
        Friendship friendship = friendshipRepo.findPair(me, otherUserId).orElse(null);
        RelationshipStatus relationship = relationshipOf(friendship, me);
        UUID requestId = relationship == RelationshipStatus.REQUEST_RECEIVED ? friendship.getId() : null;
        return new RelationshipResult(otherUserId, relationship, requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public long friendCountOf(UUID userId) {
        UUID me = userDirectory.requireCurrentUserId();
        // Only the number is public. Two learners who blocked each other do not see each other's page at all, and the
        // answer says the learner is missing rather than that a block exists.
        if (!userId.equals(me) && userBlockRepo.existsEitherWay(me, userId)) {
            throw new NotFoundException("LEARNER_NOT_FOUND", "No such learner");
        }
        return friendshipRepo.countFriendsOf(userId);
    }

    /**
     * The request behind {@code requestId}, read after the pair's lock is held. The pair is found first without loading
     * the entity, so the lock is taken before the first load and the load sees the latest committed state. Someone
     * else's request is reported as missing, so a request id says nothing about other people's.
     */
    private Friendship lockAndLoad(UUID requestId, UUID me) {
        PairIds pair = friendshipRepo.findPairIds(requestId).orElseThrow(FriendshipServiceImpl::requestNotFound);
        if (!me.equals(pair.getRequesterId()) && !me.equals(pair.getAddresseeId())) {
            throw requestNotFound();
        }
        friendshipRepo.lockPair(Friendship.pairKey(pair.getRequesterId(), pair.getAddresseeId()));
        return friendshipRepo.findById(requestId).orElseThrow(FriendshipServiceImpl::requestNotFound);
    }

    private static NotFoundException requestNotFound() {
        return new NotFoundException("FRIEND_REQUEST_NOT_FOUND", "No such friend request");
    }

    private void requireLearnerCaller(UUID me) {
        if (!publicProfileReader.isLearner(me)) {
            throw new ForbiddenException("FRIENDSHIP_LEARNERS_ONLY", "Only learners can use friends");
        }
    }

    private void requireLearnerTarget(UUID other) {
        if (!publicProfileReader.isLearner(other)) {
            throw new NotFoundException("LEARNER_NOT_FOUND", "No such learner");
        }
    }

    /** Only the page number and size come from the caller; the order is the service's, so it is always stable. */
    private static Pageable ordered(Pageable page, String sortProperty) {
        return StablePage.of(
                PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by(Sort.Direction.DESC, sortProperty)));
    }

    private Page<FriendRequestResult> requestPage(Page<Friendship> rows, UUID me) {
        Map<UUID, PublicProfile> profiles = profilesOfOthers(rows.getContent(), me);
        return rows.map(friendship -> {
            UUID other = friendship.otherParty(me);
            PublicProfile profile = profiles.get(other);
            return toRequestResult(friendship, other, profile);
        });
    }

    /** One call for the whole page, never one per row. */
    private Map<UUID, PublicProfile> profilesOfOthers(List<Friendship> rows, UUID me) {
        Set<UUID> others = rows.stream().map(friendship -> friendship.otherParty(me)).collect(Collectors.toSet());
        return publicProfileReader.profiles(others);
    }

    private FriendRequestResult requestResult(Friendship friendship, UUID other) {
        return toRequestResult(friendship, other, publicProfileReader.profiles(Set.of(other)).get(other));
    }

    private static FriendRequestResult toRequestResult(Friendship friendship, UUID other, PublicProfile profile) {
        return new FriendRequestResult(friendship.getId(), other, displayName(profile), avatarUrl(profile),
                friendship.getStatus(), friendship.getRequestedAt(), friendship.getAcceptedAt());
    }

    private static RelationshipStatus relationshipOf(Friendship friendship, UUID me) {
        if (friendship == null) {
            return RelationshipStatus.NONE;
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return RelationshipStatus.FRIENDS;
        }
        return friendship.isRequestedBy(me) ? RelationshipStatus.REQUEST_SENT : RelationshipStatus.REQUEST_RECEIVED;
    }

    private static String displayName(PublicProfile profile) {
        return profile == null ? null : profile.displayName();
    }

    private static String avatarUrl(PublicProfile profile) {
        return profile == null ? null : profile.avatarUrl();
    }
}
