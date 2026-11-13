package com.englow3.tutor.entity;

import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "tutor_audit")
@Getter
public class TutorAudit {
    @Id
    private UUID id;
    private UUID actorId;
    private UUID targetId;
    private String action;
    private String note;

    protected TutorAudit() {
    }

    public static TutorAudit record(UUID actor, UUID target, String action, String note) {
        TutorAudit audit = new TutorAudit();
        audit.id = UUID.randomUUID();
        audit.actorId = actor;
        audit.targetId = target;
        audit.action = action;
        audit.note = note;
        return audit;
    }
}
