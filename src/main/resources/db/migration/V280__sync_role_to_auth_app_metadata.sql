-- englow3.users.role is the source of truth. Supabase copies app_metadata.role
-- into access tokens, so keep auth.users.raw_app_meta_data synchronized without
-- modifying any previously applied migration.

create or replace function englow3.sync_role_to_auth()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update auth.users
       set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb)
                               || jsonb_build_object('role', new.role)
     where id = new.auth_provider_id;
    return new;
end;
$$;

drop trigger if exists trg_users_sync_role_to_auth on englow3.users;

create trigger trg_users_sync_role_to_auth
after update of role on englow3.users
for each row execute function englow3.sync_role_to_auth();

-- Fire the trigger once for existing users while preserving their role values.
update englow3.users set role = role;
