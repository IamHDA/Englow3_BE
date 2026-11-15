package com.englow3.friendship.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.friendship.entity.Friendship;
import com.englow3.friendship.entity.FriendshipStatus;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /** Held to the end of the transaction. Every write for one pair takes it first. */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key,0)) as text)", nativeQuery = true)
    String lockPair(@Param("key") String key);

    @Query("""
            select f from Friendship f
            where (f.requesterId = :a and f.addresseeId = :b) or (f.requesterId = :b and f.addresseeId = :a)
            """)
    Optional<Friendship> findPair(@Param("a") UUID a, @Param("b") UUID b);

    /**
     * The two people behind a request id, read without loading the entity, so the lock can be taken before the load.
     */
    interface PairIds {
        UUID getRequesterId();

        UUID getAddresseeId();
    }

    @Query("select f.requesterId as requesterId, f.addresseeId as addresseeId from Friendship f where f.id = :id")
    Optional<PairIds> findPairIds(@Param("id") UUID id);

    /** Blocking removes the pair's friendship or pending request, whichever it is. */
    @Modifying
    @Query("""
            delete from Friendship f
            where (f.requesterId = :a and f.addresseeId = :b) or (f.requesterId = :b and f.addresseeId = :a)
            """)
    int deletePair(@Param("a") UUID a, @Param("b") UUID b);

    @Query("""
            select f from Friendship f
            where f.status = com.englow3.friendship.entity.FriendshipStatus.ACCEPTED
              and (f.requesterId = :me or f.addresseeId = :me)
            """)
    Page<Friendship> findFriendships(@Param("me") UUID me, Pageable page);

    /** Requests waiting for {@code addresseeId} to answer. */
    Page<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status, Pageable page);

    /** Requests {@code requesterId} has sent and nobody has answered yet. */
    Page<Friendship> findByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status, Pageable page);

    long countByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);

    /** Whatever connects {@code me} to each of {@code others}: one row at most per person. */
    @Query("""
            select f from Friendship f
            where (f.requesterId = :me and f.addresseeId in :others)
               or (f.addresseeId = :me and f.requesterId in :others)
            """)
    List<Friendship> findPairsWith(@Param("me") UUID me, @Param("others") Collection<UUID> others);

    boolean existsByStatusAndRequesterIdAndAddresseeId(FriendshipStatus status, UUID requesterId, UUID addresseeId);
}
