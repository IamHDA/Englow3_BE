package com.englow3.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "learning_purposes")
@Getter
public class LearningPurpose {

    /** The purpose that sends a learner down the placement-test branch. */
    public static final String CERTIFICATE_CODE = "CERTIFICATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected LearningPurpose() {
    }
}
