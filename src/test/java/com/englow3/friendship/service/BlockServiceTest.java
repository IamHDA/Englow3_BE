package com.englow3.friendship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.englow3.friendship.dto.result.BlockedUserResult;
import com.englow3.friendship.entity.Friendship;
import com.englow3.friendship.entity.UserBlock;
import com.englow3.friendship.repository.FriendshipRepository;
import com.englow3.friendship.repository.UserBlockRepository;
import com.englow3.friendship.service.impl.BlockServiceImpl;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

class BlockServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final FriendshipRepository friendshipRepo = mock(FriendshipRepository.class);
    private final UserBlockRepository userBlockRepo = mock(UserBlockRepository.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final BlockService service = new BlockServiceImpl(userDirectory, publicProfileReader, friendshipRepo,
            userBlockRepo, Clock.fixed(now, ZoneOffset.UTC));

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(publicProfileReader.isLearner(me)).thenReturn(true);
        when(publicProfileReader.isLearner(other)).thenReturn(true);
    }

    @Test
    void blockingTakesThePairLockThenRecordsTheBlockThenEndsTheFriendship() {
        service.block(other);

        InOrder order = inOrder(friendshipRepo, userBlockRepo);
        order.verify(friendshipRepo).lockPair(Friendship.pairKey(me, other));
        order.verify(userBlockRepo).insertIfAbsent(me, other, now);
        order.verify(friendshipRepo).deletePair(me, other);
    }

    @Test
    void blockingYourselfIsRefusedWithoutTouchingARepository() {
        assertThatThrownBy(() -> service.block(me)).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOCK_SELF"));
        verifyNoInteractions(friendshipRepo, userBlockRepo);
    }

    @Test
    void staffCannotBlock() {
        when(publicProfileReader.isLearner(me)).thenReturn(false);

        assertThatThrownBy(() -> service.block(other)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
        verifyNoInteractions(friendshipRepo, userBlockRepo);
    }

    @Test
    void aTargetThatIsNotALearnerIsNotFound() {
        when(publicProfileReader.isLearner(other)).thenReturn(false);

        assertThatThrownBy(() -> service.block(other)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
        verifyNoInteractions(friendshipRepo, userBlockRepo);
    }

    @Test
    void unblockingOnlyRemovesTheBlockAndNeverRestoresAFriendship() {
        service.unblock(other);

        verify(userBlockRepo).deleteBlock(me, other);
        verifyNoInteractions(friendshipRepo);
    }

    @Test
    void theBlockedListAsksTheProfileReaderOnceAndOrdersNewestFirst() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant blockedAt = Instant.parse("2026-11-10T00:00:00Z");
        when(userBlockRepo.findByIdBlockerId(org.mockito.ArgumentMatchers.eq(me), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(UserBlock.of(me, first, blockedAt), UserBlock.of(me, second, now))));
        when(publicProfileReader.profiles(Set.of(first, second))).thenReturn(Map.of(first,
                new PublicProfile(first, "An", null), second, new PublicProfile(second, "Binh", "/avatars/fox.svg")));

        Page<BlockedUserResult> page = service.blocked(PageRequest.of(1, 5, Sort.by("displayName")));

        verify(publicProfileReader, times(1)).profiles(anyCollection());
        assertThat(page.getContent())
                .extracting(BlockedUserResult::userId, BlockedUserResult::displayName, BlockedUserResult::blockedAt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(first, "An", blockedAt),
                        org.assertj.core.groups.Tuple.tuple(second, "Binh", now));
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(userBlockRepo).findByIdBlockerId(org.mockito.ArgumentMatchers.eq(me), asked.capture());
        assertThat(asked.getValue().getPageNumber()).isEqualTo(1);
        assertThat(asked.getValue().getPageSize()).isEqualTo(5);
        assertThat(asked.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"),
                Sort.Order.asc("id.blockedId"));
        verify(friendshipRepo, never()).lockPair(anyString());
    }
}
