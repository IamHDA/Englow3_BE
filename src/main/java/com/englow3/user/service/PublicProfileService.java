package com.englow3.user.service;

import java.util.UUID;

import com.englow3.user.dto.result.PublicProfileResult;

public interface PublicProfileService {

    /**
     * The public side of one account, for its wall. Knows nothing about blocks: that is the friendship module's, and
     * this module does not depend on it, so the page that shows the wall applies the block rule.
     */
    PublicProfileResult wall(UUID userId);
}
