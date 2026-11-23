# Browser — private Android chat

A small two-person Android messaging app: permanent text messages, voice notes and photo sharing. No feed, stories, calls or public profiles.

## Android build
Open `Browser/android` in Android Studio, or use the repository GitHub Actions workflow. The workflow produces a debug APK artifact.

## Backend setup (required)
1. Create a Supabase project.
2. Run `Browser/supabase/schema.sql` in the Supabase SQL Editor.
3. In Authentication, create the two accounts (email/password).
4. Copy each account UUID from Authentication → Users.
5. Insert both UUIDs into `public.browser_members` with display names.
6. Create a private Storage bucket named `chat-photos` (50 MB limit). Add member-only SELECT/INSERT/DELETE policies matching the voice-notes policies in schema.sql.
7. In GitHub repository Settings → Secrets and variables → Actions, add:
   - `SUPABASE_URL`: project URL
   - `SUPABASE_ANON_KEY`: publishable/anon key (never use service_role in the APK)
8. Run the workflow again to build an APK with the project endpoint configured.

## Important implementation notes
- Messages are stored in Supabase Postgres and are not automatically expired.
- Voice recordings are uploaded to the private `voice-notes` bucket.
- The current chat refreshes every 4 seconds; it does not use a persistent Realtime socket yet.
- Photo picker/upload wiring and signed-media playback are the next implementation items; the photo button is currently a placeholder.
- Offline support is limited to the Android app cache; unsent messages are not yet queued for automatic delivery.
- Deleting a message removes the database row only; uploaded media cleanup must be implemented separately.
- Keep the repository and Supabase project access restricted. RLS is mandatory.
