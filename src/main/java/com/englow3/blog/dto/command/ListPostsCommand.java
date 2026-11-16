package com.englow3.blog.dto.command;

import java.util.UUID;

import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogSkill;

/** Every field but {@code sort} may be null: a filter that is not given is not applied. */
public record ListPostsCommand(String q, BlogSkill skill, BlogLevel level, BlogCertificate certificate, UUID authorId,
        BlogPostSort sort) {
}
