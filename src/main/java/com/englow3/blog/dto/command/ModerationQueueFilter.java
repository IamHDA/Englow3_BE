package com.englow3.blog.dto.command;

import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogTargetType;

/** {@code type} may be null for posts and comments together; a null {@code sort} is {@code OLDEST}. */
public record ModerationQueueFilter(BlogTargetType type, BlogQueueSort sort) {
}
