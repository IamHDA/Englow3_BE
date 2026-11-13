package com.englow3.tutor.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Durable acknowledgement of an action; retrying a delivery must not buy a second answer. */
@Entity
@Table(name = "tutor_requests")
@Getter
public class TutorRequest {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private UUID clientMessageId;
    @Column(nullable = false)
    private String fingerprint;
    @Column(nullable = false)
    private UUID conversationId;
    @Column(nullable = false)
    private UUID questionId;
    @Column(nullable = false)
    private UUID answerId;

    protected TutorRequest() {
    }

    public static TutorRequest accepted(UUID user, UUID client, String fingerprint, TutorConversation conversation,
            TutorMessage question, TutorMessage answer) {
        TutorRequest request = new TutorRequest();
        request.id = UUID.randomUUID();
        request.userId = user;
        request.clientMessageId = client;
        request.fingerprint = fingerprint;
        request.conversationId = conversation.getId();
        request.questionId = question.getId();
        request.answerId = answer.getId();
        return request;
    }
}
