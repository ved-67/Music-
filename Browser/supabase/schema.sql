-- Browser: private two-person messaging schema.
-- Run in Supabase SQL Editor. Add the two account UUIDs to browser_members after creating accounts.
create table if not exists public.browser_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default 'Member',
  created_at timestamptz not null default now()
);
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('text','photo','voice')),
  body text not null,
  created_at timestamptz not null default now()
);
alter table public.browser_members enable row level security;
alter table public.messages enable row level security;
create policy "members can view pair" on public.browser_members for select to authenticated
using (exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
create policy "members can read messages" on public.messages for select to authenticated
using (exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
create policy "members can send messages" on public.messages for insert to authenticated
with check (sender_id = auth.uid() and exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
create policy "sender can delete own messages" on public.messages for delete to authenticated
using (sender_id = auth.uid());
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('voice-notes','voice-notes',false,52428800,array['audio/mp4','audio/m4a'])
on conflict (id) do nothing;
create policy "members upload voice notes" on storage.objects for insert to authenticated
with check (bucket_id = 'voice-notes' and exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
create policy "members read voice notes" on storage.objects for select to authenticated
using (bucket_id = 'voice-notes' and exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
create policy "members delete voice notes" on storage.objects for delete to authenticated
using (bucket_id = 'voice-notes' and exists(select 1 from public.browser_members m where m.user_id = auth.uid()));
-- For photos, create a private bucket named chat-photos and apply equivalent member-only storage policies.
