package com.englow3.blog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.blog.entity.BlogModerationAction;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogTargetType;

public interface BlogModerationActionRepository extends JpaRepository<BlogModerationAction, UUID> {

    /** Newest first; two actions in the same instant are ordered by id, so the order never changes between calls. */
    List<BlogModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(BlogTargetType type,
            UUID targetId);

    /** The last thing done to the target, of any of these kinds. */
    Optional<BlogModerationAction> findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
            BlogTargetType type, UUID targetId, Collection<BlogModerationActionType> actions);

    /** Newest first, ties by id: a page never repeats or skips an action that was recorded in the same instant. */
    Page<BlogModerationAction> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}
