-- Which plan a learner is on. A learner with no row is on the free plan, so signing up writes nothing here and the
-- table stays as small as the number of paying learners.
--
-- Nothing here knows about a payment provider. `source` records how a row came to be: MANUAL is an admin granting the
-- plan (a trial, a prize, a refund made good); GATEWAY is reserved for the payment integration, which will write the
-- same columns plus `external_reference`. Adding it later changes who writes the row, not the row.
create table user_subscriptions (
    user_id             uuid primary key references users (id) on delete cascade,
    plan                varchar(20) not null check (plan in ('FREE', 'PRO')),
    billing_period      varchar(10) check (billing_period in ('MONTHLY', 'YEARLY')),
    -- the plan is in force until this moment; after it the learner is back on the free plan without anyone writing
    -- anything, so an expired subscription can never keep granting access
    current_period_end  timestamptz,
    source              varchar(20) not null default 'MANUAL' check (source in ('MANUAL', 'GATEWAY')),
    external_reference  varchar(100),
    granted_by_user_id  uuid references users (id) on delete set null,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    -- a paid plan always has an end; only FREE rows may have none
    constraint user_subscriptions_pro_has_end check (plan = 'FREE' or current_period_end is not null)
);

create index user_subscriptions_period_end_idx on user_subscriptions (current_period_end);
