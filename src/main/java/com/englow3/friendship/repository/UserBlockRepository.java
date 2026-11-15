package com.englow3.friendship.repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.friendship.entity.UserBlock;
import com.englow3.friendship.entity.UserBlockId;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {

    /** Blocking someone already blocked is not an error. */
    @Modifying
    @Query(value = """
            insert into user_blocks (blocker_id, blocked_id, created_at) values (:blocker, :blocked, :now)
            on conflict (blocker_id, blocked_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("blocker") UUID blocker, @Param("blocked") UUID blocked, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserBlock b where b.id.blockerId = :blocker and b.id.blockedId = :blocked")
    int deleteBlock(@Param("blocker") UUID blocker, @Param("blocked") UUID blocked);

    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.id.blockerId = :a and b.id.blockedId = :b) or (b.id.blockerId = :b and b.id.blockedId = :a)
            """)
    boolean existsEitherWay(@Param("a") UUID a, @Param("b") UUID b);

    /** One direction only: whether {@code blocker} has blocked {@code blocked}. */
    boolean existsByIdBlockerIdAndIdBlockedId(UUID blocker, UUID blocked);

    /** Everyone {@code me} has blocked. */
    @Query("select b.id.blockedId from UserBlock b where b.id.blockerId = :me")
    Set<UUID> blockedBy(@Param("me") UUID me);

    /** Everyone who has blocked {@code me}. */
    @Query("select b.id.blockerId from UserBlock b where b.id.blockedId = :me")
    Set<UUID> blockersOf(@Param("me") UUID me);

    Page<UserBlock> findByIdBlockerId(UUID blockerId, Pageable page);
}
