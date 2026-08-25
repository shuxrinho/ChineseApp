-- Run this in Supabase SQL Editor for an existing project.
-- It synchronizes public.profiles.email after Supabase Auth confirms an email-change OTP.

create or replace function public.sync_profile_email_from_auth()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.email is distinct from old.email then
        update public.profiles
        set email = new.email
        where id = new.id;
    end if;
    return new;
end;
$$;

drop trigger if exists on_auth_user_email_changed on auth.users;
create trigger on_auth_user_email_changed
after update of email on auth.users
for each row
when (old.email is distinct from new.email)
execute function public.sync_profile_email_from_auth();
