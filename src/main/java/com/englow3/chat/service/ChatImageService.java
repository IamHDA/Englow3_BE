package com.englow3.chat.service;

import com.englow3.chat.dto.command.StartImageUploadCommand;
import com.englow3.chat.dto.result.ChatImageUploadTicket;

/** The first half of sending an image: a place to PUT it. The second half is naming it in a message. */
public interface ChatImageService {

    /** Records the upload and signs a URL for exactly this type and size. Only while the two are friends. */
    ChatImageUploadTicket startUpload(StartImageUploadCommand command);

    /** For the cleanup job: removes uploads nobody sent within a day, and their objects. Returns how many. */
    int removeAbandonedUploads();
}
