package com.englow3.user.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.service.AccountService;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/user/me")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** A download, so the browser offers to save it rather than showing a wall of JSON. */
    @GetMapping("/export")
    ResponseEntity<java.util.Map<String, JsonNode>> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("englow3-my-data.json").build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(accountService.exportData());
    }

    /**
     * POST rather than DELETE: a body travels reliably through every proxy and the BFF, and an erasure that needs the
     * learner to type something should not depend on a verb some intermediaries strip the body from.
     */
    @PostMapping("/erase")
    ResponseEntity<Void> erase(@Valid @RequestBody EraseRequest request) {
        accountService.erase(request.confirmEmail());
        return ResponseEntity.noContent().build();
    }

    record EraseRequest(@NotBlank String confirmEmail) {
    }
}
