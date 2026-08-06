create function englow3.sync_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into englow3.users (id, auth_provider_id, email, full_name, display_name)
    values (
        gen_random_uuid(),
        new.id,
        new.email,
        coalesce(new.raw_user_meta_data ->> 'full_name', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1))
    );
    return new;
end;
$$;

create trigger trg_users_sync_from_auth
after insert on auth.users
for each row execute function englow3.sync_auth_user();
