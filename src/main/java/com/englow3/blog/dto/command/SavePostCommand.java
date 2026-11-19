package com.englow3.blog.dto.command;

import java.util.List;

import com.englow3.blog.entity.BlogTagKind;

/**
 * A post to create or to update. {@code expectedVersion} is the version the editor opened and is null when a post is
 * created; an update without one is refused as stale.
 */
public record SavePostCommand(String title, String body, List<Tag> tags, Long expectedVersion) {

    public record Tag(BlogTagKind kind, String value) {
    }
}
