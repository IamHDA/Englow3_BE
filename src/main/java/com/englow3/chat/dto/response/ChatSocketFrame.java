package com.englow3.chat.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the server pushes down the chat socket; the {@code type} field says which. This is the contract the chat screen
 * reads - field names are not to change.
 * <ul>
 * <li>{@code message}: a new message, in the same shape as the REST response.</li>
 * <li>{@code recalled}: a message was taken back; replace it with a "recalled" placeholder.</li>
 * <li>{@code read}: {@code userId} has read the conversation up to {@code upToSeq}.</li>
 * <li>{@code typing}: {@code userId} started or stopped typing. Never stored; the viewer expires it on a timer.</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ChatSocketFrame.Message.class, name = "message"),
        @JsonSubTypes.Type(value = ChatSocketFrame.Recalled.class, name = "recalled"),
        @JsonSubTypes.Type(value = ChatSocketFrame.Read.class, name = "read"),
        @JsonSubTypes.Type(value = ChatSocketFrame.Typing.class, name = "typing") })
public sealed interface ChatSocketFrame {

    record Message(ChatMessageResponse message) implements ChatSocketFrame {
    }

    record Recalled(UUID conversationId, UUID messageId, long seq) implements ChatSocketFrame {
    }

    record Read(UUID conversationId, UUID userId, long upToSeq) implements ChatSocketFrame {
    }

    record Typing(UUID conversationId, UUID userId, boolean active) implements ChatSocketFrame {
    }
}
