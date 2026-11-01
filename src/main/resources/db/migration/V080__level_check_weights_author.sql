-- Weights are now changed from the admin screen; record who set each row, so a change in placements can be traced.
alter table level_check_weights
    add column created_by_user_id uuid;
