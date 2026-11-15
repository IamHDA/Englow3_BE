package com.englow3.friendship.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.friendship.dto.result.BlockedUserResult;

public interface BlockService {

    /** Blocks {@code blockedId} and ends whatever friendship or pending request the two had. Blocking twice is fine. */
    void block(UUID blockedId);

    /** Lifts the block. It does not bring the friendship back, and lifting one that is not there is fine. */
    void unblock(UUID blockedId);

    Page<BlockedUserResult> blocked(Pageable page);
}
