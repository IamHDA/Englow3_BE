package com.englow3.subscription.dto;

/** One plan as the pricing page shows it. Prices are whole VND; 0 means free. */
public record PlanView(String code, long priceVndMonthly, long priceVndYearly, int aiDailyRequestLimit) {
}
