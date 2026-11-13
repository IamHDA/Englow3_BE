package com.englow3.subscription.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.subscription.dto.PlanView;
import com.englow3.subscription.dto.SubscriptionView;
import com.englow3.subscription.service.SubscriptionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
class SubscriptionController {

    private final SubscriptionService subscriptions;

    SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Open to anyone: it is the pricing page's data, and shows no one's account. */
    @GetMapping("/api/plans")
    ResponseEntity<List<PlanView>> plans() {
        return ResponseEntity.ok(subscriptions.plans());
    }

    @GetMapping("/api/user/me/subscription")
    ResponseEntity<SubscriptionView> mine() {
        return ResponseEntity.ok(subscriptions.mine());
    }

    /** Until a payment provider is connected, an administrator is who puts a learner on the paid plan. */
    @PutMapping("/api/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> grant(@Valid @RequestBody GrantRequest request) {
        subscriptions.grantPaid(request.email(), request.billingPeriod(), request.days());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> revoke(@RequestParam String email) {
        subscriptions.revoke(email);
        return ResponseEntity.noContent().build();
    }

    record GrantRequest(@NotBlank @Email String email, @NotBlank String billingPeriod, int days) {
    }
}
