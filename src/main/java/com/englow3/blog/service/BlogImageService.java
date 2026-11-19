package com.englow3.blog.service;

import com.englow3.blog.dto.command.StartImageUploadCommand;
import com.englow3.blog.dto.result.BlogImageUploadTicket;

/** The first half of putting an image in a post: a place to PUT it. The second half is naming it in the post's text. */
public interface BlogImageService {

    /** Records the upload and signs a URL for exactly this type and size. Any signed-in account. */
    BlogImageUploadTicket startUpload(StartImageUploadCommand command);

    /** For the cleanup job: removes images no post took within a day, and their objects. Returns how many. */
    int removeUnattached();
}
