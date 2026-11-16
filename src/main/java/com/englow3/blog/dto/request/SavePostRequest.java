package com.englow3.blog.dto.request;

import java.util.List;

import com.englow3.blog.dto.command.SavePostCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body is Markdown; an image is written {@code blog-image:<id>} with the id from {@code POST /api/blog/images}. The
 * limits here are the outer layer: the same ones are enforced again where a post is made.
 */
public record SavePostRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 20000) String body,
        @NotNull @Size(max = 8) List<@NotNull @Valid TagRequest> tags) {

    public SavePostCommand toCommand() {
        return new SavePostCommand(title, body, tags.stream().map(TagRequest::toCommand).toList(), null);
    }
}
