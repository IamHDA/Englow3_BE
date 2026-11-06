package com.englow3.exam.service;

import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.dto.result.FullTestResult;

/** Authoring and review of IELTS full tests, on the same workflow as papers. */
public interface FullTestService {

    FullTestResult save(SaveFullTestCommand command);

    FullTestResult detail(UUID id);

    List<FullTestResult> list();

    FullTestResult submitForReview(UUID id);

    FullTestResult approve(UUID id);

    FullTestResult reject(UUID id, String note);

    FullTestResult archive(UUID id);

    /** What learners may choose from: published full tests, newest first. */
    List<FullTestResult> catalogue();
}
