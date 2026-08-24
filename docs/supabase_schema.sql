-- Supabase schema + RPC for ChineseApp
-- Run this entire script in Supabase SQL Editor.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text,
    email text,
    avatar_url text,
    created_at timestamptz not null default now()
);

alter table public.profiles
add column if not exists avatar_url text;

create unique index if not exists profiles_username_unique_idx
on public.profiles (lower(username))
where username is not null and btrim(username) <> '';

create table if not exists public.words (
    id bigint generated always as identity primary key,
    traditional text,
    hanzi text not null,
    pinyin text not null,
    english text not null,
    hsk int,
    created_at timestamptz not null default now()
);

create index if not exists idx_words_hanzi on public.words(hanzi);
create index if not exists idx_words_pinyin on public.words(pinyin);
create index if not exists idx_words_english on public.words(english);

create table if not exists public.sentences (
    id bigint generated always as identity primary key,
    mandarin text not null,
    pinyin text not null,
    english text not null,
    hsk int not null default 1,
    created_at timestamptz not null default now()
);

create index if not exists idx_sentences_mandarin on public.sentences(mandarin);
create index if not exists idx_sentences_pinyin on public.sentences(pinyin);
create index if not exists idx_sentences_english on public.sentences(english);

create table if not exists public.popular_word_lists (
    id int primary key,
    name text not null,
    description text,
    created_at timestamptz not null default now()
);

insert into public.popular_word_lists (id, name, description)
values
    (102, 'HSK 1 Essentials', 'Starter words for HSK1'),
    (103, 'Greetings & Introductions', 'Basic conversational starters'),
    (104, 'Food & Restaurant', 'Food-related words'),
    (105, 'Travel & Transport', 'Travel vocabulary'),
    (106, 'Numbers & Time', 'Numbers, dates and time'),
    (107, 'Family & Daily Life', 'Family and daily routine'),
    (108, 'Radicals', 'Chinese radicals')
on conflict (id) do nothing;

create table if not exists public.popular_list_words (
    list_id int not null references public.popular_word_lists(id) on delete cascade,
    word_id bigint not null references public.words(id) on delete cascade,
    primary key (list_id, word_id)
);

create table if not exists public.user_word_lists (
    id bigint generated always as identity primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    position int not null check (position between 1 and 6),
    title text not null,
    description text not null default '',
    created_at timestamptz not null default now(),
    unique(user_id, position)
);

create table if not exists public.user_list_words (
    list_id bigint not null references public.user_word_lists(id) on delete cascade,
    word_id bigint not null references public.words(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (list_id, word_id)
);

create table if not exists public.sentence_favorites (
    user_id uuid not null references auth.users(id) on delete cascade,
    sentence_id bigint not null references public.sentences(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, sentence_id)
);

alter table public.profiles enable row level security;
alter table public.words enable row level security;
alter table public.sentences enable row level security;
alter table public.popular_word_lists enable row level security;
alter table public.popular_list_words enable row level security;
alter table public.user_word_lists enable row level security;
alter table public.user_list_words enable row level security;
alter table public.sentence_favorites enable row level security;

-- Public read access for dictionary content
create policy if not exists "words read all" on public.words
for select
using (true);

create policy if not exists "sentences read all" on public.sentences
for select
using (true);

create policy if not exists "popular lists read all" on public.popular_word_lists
for select
using (true);

create policy if not exists "popular list words read all" on public.popular_list_words
for select
using (true);

-- Profile policies
create policy if not exists "profiles self read" on public.profiles
for select using (auth.uid() = id);

create policy if not exists "profiles self write" on public.profiles
for insert with check (auth.uid() = id);

create policy if not exists "profiles self update" on public.profiles
for update using (auth.uid() = id) with check (auth.uid() = id);

create or replace function public.update_profile_username(new_username text)
returns table (id uuid, email text, username text, avatar_url text)
language plpgsql
security definer
set search_path = public
as $$
declare
    current_uid uuid := auth.uid();
    clean_username text := btrim(coalesce(new_username, ''));
begin
    if current_uid is null then
        raise exception 'AUTH_REQUIRED';
    end if;

    if length(clean_username) < 3
        or length(clean_username) > 24
        or clean_username !~ '^[A-Za-z0-9_]+$' then
        raise exception 'USERNAME_INVALID';
    end if;

    if exists (
        select 1
        from public.profiles p
        where lower(p.username) = lower(clean_username)
          and p.id <> current_uid
    ) then
        raise exception 'USERNAME_TAKEN';
    end if;

    insert into public.profiles (id, username)
    values (current_uid, clean_username)
    on conflict on constraint profiles_pkey do update
    set username = excluded.username;

    return query
    select p.id, p.email, p.username, p.avatar_url
    from public.profiles p
    where p.id = current_uid;
exception
    when unique_violation then
        raise exception 'USERNAME_TAKEN';
end;
$$;

grant execute on function public.update_profile_username(text) to authenticated;

-- Public avatar storage. Files are written under avatars/{auth.uid()}/avatar.jpg.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'avatars',
    'avatars',
    true,
    5242880,
    array['image/jpeg', 'image/png', 'image/webp']::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy if not exists "avatars read all" on storage.objects
for select
using (bucket_id = 'avatars');

create policy if not exists "avatars insert own folder" on storage.objects
for insert
with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy if not exists "avatars update own folder" on storage.objects
for update
using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy if not exists "avatars delete own folder" on storage.objects
for delete
using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

-- User lists policies
create policy if not exists "user lists self all" on public.user_word_lists
for all
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy if not exists "user list words self all" on public.user_list_words
for all
using (
    exists (
        select 1
        from public.user_word_lists ul
        where ul.id = user_list_words.list_id
          and ul.user_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.user_word_lists ul
        where ul.id = user_list_words.list_id
          and ul.user_id = auth.uid()
    )
);

-- Favorite policies
create policy if not exists "favorites self all" on public.sentence_favorites
for all
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create or replace function public.search_words(query_text text)
returns table (id bigint, hanzi text, pinyin text, english text)
language sql
stable
as $$
    select w.id, w.hanzi, w.pinyin, w.english
    from public.words w
    where coalesce(trim(query_text), '') = ''
       or w.hanzi ilike '%' || trim(query_text) || '%'
       or w.pinyin ilike '%' || trim(query_text) || '%'
       or w.english ilike '%' || trim(query_text) || '%'
    order by char_length(w.hanzi), w.hanzi
    limit 150;
$$;

create or replace function public.search_sentences(query_text text)
returns table (id bigint, mandarin text, pinyin text, english text, hsk int)
language sql
stable
as $$
    select s.id, s.mandarin, s.pinyin, s.english, s.hsk
    from public.sentences s
    where s.hsk between 1 and 4
      and (
          coalesce(trim(query_text), '') = ''
          or s.mandarin ilike '%' || trim(query_text) || '%'
          or s.pinyin ilike '%' || trim(query_text) || '%'
          or s.english ilike '%' || trim(query_text) || '%'
      )
    order by s.hsk asc, char_length(s.mandarin) asc
    limit 120;
$$;

create or replace function public.get_popular_list_words(target_list_id int)
returns table (id bigint, hanzi text, pinyin text, english text)
language sql
stable
as $$
    select w.id, w.hanzi, w.pinyin, w.english
    from public.popular_list_words plw
    join public.words w on w.id = plw.word_id
    where plw.list_id = target_list_id
    order by w.hanzi;
$$;

create or replace function public.get_user_lists()
returns table (position int, title text, description text)
language sql
stable
as $$
    select position, title, description
    from public.user_word_lists
    where user_id = auth.uid()
    order by position;
$$;

create or replace function public.create_user_list(list_title text, list_description text)
returns table (position int)
language plpgsql
security invoker
as $$
declare
    next_position int;
begin
    select min(n) into next_position
    from generate_series(1, 6) as n
    where not exists (
        select 1
        from public.user_word_lists ul
        where ul.user_id = auth.uid() and ul.position = n
    );

    if next_position is null then
        return query select -1;
        return;
    end if;

    insert into public.user_word_lists (user_id, position, title, description)
    values (auth.uid(), next_position, coalesce(trim(list_title), ''), coalesce(trim(list_description), ''));

    return query select next_position;
end;
$$;

create or replace function public.rename_user_list(target_position int, list_title text, list_description text)
returns void
language sql
security invoker
as $$
    update public.user_word_lists
    set title = coalesce(trim(list_title), ''),
        description = coalesce(trim(list_description), '')
    where user_id = auth.uid() and position = target_position;
$$;

create or replace function public.remove_user_list(target_position int)
returns void
language plpgsql
security invoker
as $$
begin
    delete from public.user_word_lists
    where user_id = auth.uid() and position = target_position;

    update public.user_word_lists
    set position = position - 1
    where user_id = auth.uid() and position > target_position;
end;
$$;

create or replace function public.get_user_list_words(target_position int)
returns table (id bigint, hanzi text, pinyin text, english text)
language sql
stable
as $$
    select w.id, w.hanzi, w.pinyin, w.english
    from public.user_word_lists ul
    join public.user_list_words ulw on ulw.list_id = ul.id
    join public.words w on w.id = ulw.word_id
    where ul.user_id = auth.uid() and ul.position = target_position
    order by w.hanzi;
$$;

create or replace function public.is_word_in_user_list(target_position int, target_word_id bigint)
returns table (contains boolean)
language sql
stable
as $$
    select exists (
        select 1
        from public.user_word_lists ul
        join public.user_list_words ulw on ulw.list_id = ul.id
        where ul.user_id = auth.uid()
          and ul.position = target_position
          and ulw.word_id = target_word_id
    );
$$;

create or replace function public.set_user_list_word(target_position int, target_word_id bigint, should_exist boolean)
returns void
language plpgsql
security invoker
as $$
declare
    resolved_list_id bigint;
begin
    select id into resolved_list_id
    from public.user_word_lists
    where user_id = auth.uid() and position = target_position;

    if resolved_list_id is null then
        return;
    end if;

    if should_exist then
        insert into public.user_list_words (list_id, word_id)
        values (resolved_list_id, target_word_id)
        on conflict do nothing;
    else
        delete from public.user_list_words
        where list_id = resolved_list_id and word_id = target_word_id;
    end if;
end;
$$;

create or replace function public.get_user_favorite_sentence_ids()
returns table (sentence_id bigint)
language sql
stable
as $$
    select sf.sentence_id
    from public.sentence_favorites sf
    where sf.user_id = auth.uid();
$$;

create or replace function public.get_sentences_by_ids(sentence_ids bigint[])
returns table (id bigint, mandarin text, pinyin text, english text, hsk int)
language sql
stable
as $$
    select s.id, s.mandarin, s.pinyin, s.english, s.hsk
    from public.sentences s
    where s.id = any(sentence_ids)
    order by s.hsk asc, char_length(s.mandarin) asc;
$$;

create or replace function public.set_sentence_favorite(target_sentence_id bigint, should_exist boolean)
returns void
language plpgsql
security invoker
as $$
begin
    if should_exist then
        insert into public.sentence_favorites (user_id, sentence_id)
        values (auth.uid(), target_sentence_id)
        on conflict do nothing;
    else
        delete from public.sentence_favorites
        where user_id = auth.uid() and sentence_id = target_sentence_id;
    end if;
end;
$$;
