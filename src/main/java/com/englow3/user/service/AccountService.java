package com.englow3.user.service;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What a person may ask of their own data: a copy of it, and its removal.
 * <p>
 * Separate from {@link UserService}, which edits a profile. These two reach every module's tables, so they are kept
 * apart from the code that most changes.
 */
public interface AccountService {

    /** Everything held about the signed-in learner, one JSON array per kind of record. */
    Map<String, JsonNode> exportData();

    /**
     * Removes the signed-in learner and everything they produced, then their sign-in.
     *
     * @param confirmEmail
     *            the account's own email, typed by the learner. Not authentication - the token already did that - but a
     *            deliberate step, so a stray click or a script cannot erase an account.
     */
    void erase(String confirmEmail);
}
