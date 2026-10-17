create function englow3.sync_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into englow3.users (id, auth_provider_id, email, full_name, display_name, gender, birth_date)
    values (
        gen_random_uuid(),
        new.id,
        new.email,
        coalesce(new.raw_user_meta_data ->> 'full_name', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)),
        -- an unrecognised value is dropped rather than stored: it would break the Gender
        -- enum on every later read of this user
        case upper(new.raw_user_meta_data ->> 'gender')
            when 'MALE' then 'MALE'
            when 'FEMALE' then 'FEMALE'
            when 'OTHER' then 'OTHER'
        end,
        -- a malformed date must not raise here: this runs inside the auth.users insert,
        -- so a bad string would fail the whole signup
        case
            when new.raw_user_meta_data ->> 'birth_date' ~ '^\d{4}-\d{2}-\d{2}$'
            then (new.raw_user_meta_data ->> 'birth_date')::date
        end
    );
    return new;
end;
$$;

create trigger trg_users_sync_from_auth
after insert on auth.users
for each row execute function englow3.sync_auth_user();
