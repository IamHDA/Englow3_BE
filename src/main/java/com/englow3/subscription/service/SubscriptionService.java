package com.englow3.subscription.service;

import java.util.List;

import com.englow3.subscription.dto.PlanView;
import com.englow3.subscription.dto.SubscriptionView;

public interface SubscriptionService {

    List<PlanView> plans();

    SubscriptionView mine();

    /**
     * Puts a learner on a paid plan for a number of days from now, or from the end of the current period if that is
     * later - granting a month to someone with three weeks left gives them seven weeks, not one.
     */
    void grantPaid(String email, String billingPeriod, int days);

    /** Back to the free plan immediately. */
    void revoke(String email);
}
