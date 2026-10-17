-- englow3.users.role stays the one place a role is edited; auth.users.raw_app_meta_data is a derived copy that
-- Supabase mints into the access token as the app_metadata.role claim, which is what @PreAuthorize("hasRole(...)")
-- reads. app_metadata and not user_metadata: only service_role may write the former, so a learner cannot promote
-- themselves.
--
-- Mirror of V025, which syncs auth.users -> englow3.users at signup; this is the reverse edge, for the role only.

create function englow3.sync_role_to_auth()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    -- jsonb concatenation, not jsonb_set on a fresh object: it must preserve the keys GoTrue already keeps there
    -- (provider, providers), which a wholesale replace would drop.
    update auth.users
       set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb)
                               || jsonb_build_object('role', new.role)
     where id = new.auth_provider_id;
    return new;
end;
$$;

-- UPDATE only, deliberately. An INSERT into englow3.users happens inside V025's own AFTER INSERT trigger on
-- auth.users, so syncing there would write the very row that insert is still creating. New users are LEARNER, and no
-- endpoint asks for ROLE_LEARNER, so they lose nothing by having no claim until someone promotes them.
--
-- No WHEN clause on purpose: the backfill below fires this by re-assigning role to itself.
create trigger trg_users_sync_role_to_auth
after update of role on englow3.users
for each row execute function englow3.sync_role_to_auth();

-- Backfill: the trigger only fires on future writes, so push the current roles across once.
update englow3.users set role = role;
