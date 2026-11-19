package com.englow3.blog.dto.request;

import java.util.List;

import com.englow3.blog.dto.command.SavePostCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new post's fields plus the {@code version} of the post as the editor opened it. If the post has changed since - a
 * moderator hid it, say - the save is refused rather than undoing that.
 */
public record UpdatePostRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 20000) String body,
        @NotNull @Size(max = 8) List<@NotNull @Valid TagRequest> tags, @NotNull Long version) {

    public SavePostCommand toCommand() {
        return new SavePostCommand(title, body, tags.stream().map(TagRequest::toCommand).toList(), version);
    }
}
