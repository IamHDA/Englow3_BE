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

    /** Declared read-only user-role join: historical rows are inactive while either account is not a learner. */
    String ACTIVE_LEARNERS = """
            exists (select u.id from User u where u.id = f.requesterId and u.role = com.englow3.user.entity.Role.LEARNER)
            and exists (select u.id from User u where u.id = f.addresseeId and u.role = com.englow3.user.entity.Role.LEARNER)
            """;

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

    @Query("select f from Friendship f where " + ACTIVE_LEARNERS + " and " + """
            f.status = com.englow3.friendship.entity.FriendshipStatus.ACCEPTED
              and (f.requesterId = :me or f.addresseeId = :me)
            """)
    Page<Friendship> findFriendships(@Param("me") UUID me, Pageable page);

    /** Requests waiting for {@code addresseeId} to answer. */
    @Query("select f from Friendship f where " + ACTIVE_LEARNERS + " and f.addresseeId = :id and f.status = :status")
    Page<Friendship> findByAddresseeIdAndStatus(@Param("id") UUID addresseeId, @Param("status") FriendshipStatus status,
            Pageable page);

    /** Requests {@code requesterId} has sent and nobody has answered yet. */
    @Query("select f from Friendship f where " + ACTIVE_LEARNERS + " and f.requesterId = :id and f.status = :status")
    Page<Friendship> findByRequesterIdAndStatus(@Param("id") UUID requesterId, @Param("status") FriendshipStatus status,
            Pageable page);

    @Query("select count(f) from Friendship f where " + ACTIVE_LEARNERS
            + " and f.addresseeId = :id and f.status = :status")
    long countByAddresseeIdAndStatus(@Param("id") UUID addresseeId, @Param("status") FriendshipStatus status);

    /** Whatever connects {@code me} to each of {@code others}: one row at most per person. */
    @Query("""
            select f from Friendship f
            where (f.requesterId = :me and f.addresseeId in :others)
               or (f.addresseeId = :me and f.requesterId in :others)
            """)
    List<Friendship> findPairsWith(@Param("me") UUID me, @Param("others") Collection<UUID> others);

    @Query("select count(f) > 0 from Friendship f where " + ACTIVE_LEARNERS
            + " and f.status = :status and f.requesterId = :requester and f.addresseeId = :addressee")
    boolean existsByStatusAndRequesterIdAndAddresseeId(@Param("status") FriendshipStatus status,
            @Param("requester") UUID requesterId, @Param("addressee") UUID addresseeId);

    /** The other side of every active accepted friendship of {@code user}. */
    @Query("select case when f.requesterId = :user then f.addresseeId else f.requesterId end from Friendship f where "
            + ACTIVE_LEARNERS + " and " + """
                    f.status = com.englow3.friendship.entity.FriendshipStatus.ACCEPTED
                      and (f.requesterId = :user or f.addresseeId = :user)
                    """)
    List<UUID> findFriendIdsOf(@Param("user") UUID userId);

    /** Accepted friendships on either side; a pending request is not a friend yet. */
    @Query("select count(f) from Friendship f where " + ACTIVE_LEARNERS + " and " + """
            f.status = com.englow3.friendship.entity.FriendshipStatus.ACCEPTED
              and (f.requesterId = :user or f.addresseeId = :user)
            """)
    long countFriendsOf(@Param("user") UUID userId);
}
