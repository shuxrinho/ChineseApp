# Supabase Setup For ChineseApp

This app now expects Supabase for:
- auth/profile
- offline preload source data (`words`, `sentences`, `popular_word_lists`, `popular_list_words`)

## 1. Create a Supabase project
1. Open your Supabase dashboard.
2. Create a new project.
3. In `Settings -> API`, copy:
   - `Project URL`
   - `anon public key`

## 2. Add keys to Android project
Add these lines to `local.properties` in project root:

```properties
SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
SUPABASE_ANON_KEY=YOUR_ANON_KEY
```

## 3. Create tables + RPC functions
1. Open `SQL Editor` in Supabase.
2. Paste and run [`docs/supabase_schema.sql`](./supabase_schema.sql).

This creates:
- auth-linked profile table
- public `avatars` storage bucket for profile photos
- case-insensitive unique usernames with an `update_profile_username` RPC
- dictionary words table
- sentences table
- popular lists + mapping
- RLS policies
- RPC functions used by Android app

## 4. Import your data
Populate at least these tables:
- `words`
- `sentences`
- `popular_list_words`

Recommended order:
1. Import all dictionary rows into `words`.
2. Import sentence rows into `sentences`.
3. Insert list mappings into `popular_list_words` using existing word ids.

The app uses popular list ids:
- `102` HSK 1 Essentials
- `103` Greetings & Introductions
- `104` Food & Restaurant
- `105` Travel & Transport
- `106` Numbers & Time
- `107` Family & Daily Life
- `108` Radicals

## 5. Auth settings
In `Authentication -> Providers -> Email`:
- Enable Email provider.
- If you want immediate signup login, disable "Confirm email" (or keep enabled and verify users before login).

## 6. Run app
- `SplashScreen` sends unauthenticated users to login/signup.
- After login, the app downloads required content files on first run and after each app update.
- Word/sentence/list browsing runs from offline JSON cache files after preload.
- Profile photos upload to `avatars/{user_id}/avatar.jpg`; the public URL is saved in `profiles.avatar_url`.
- Usernames are changed through `public.update_profile_username(new_username)`, which enforces 3-24 letters/numbers/underscores and case-insensitive uniqueness.

## 7. Configure six-digit email-change codes
The Change Email screen verifies the current password, requests an Auth email change, then verifies a six-digit OTP from the new inbox. Configure this once in the Supabase Dashboard:

1. Go to **Authentication → Providers → Email** and keep the Email provider enabled.
2. Under **Email change**, turn **Secure email change** **off**. The app has already verified the user's password and is intentionally designed to require one code delivered to the *new* email address. Leaving it enabled requires confirmations from both the old and new addresses, which this flow does not collect.
3. Go to **Authentication → Email Templates → Change Email**. Replace the confirmation-link content with a code that includes `{{ .Token }}`, for example:

   ```html
   <h2>Confirm your new ChineseApp email</h2>
   <p>Enter this code in the app:</p>
   <p style="font-size: 28px; font-weight: bold; letter-spacing: 6px;">{{ .Token }}</p>
   <p>This code expires soon. If you did not request this change, you can ignore this email.</p>
   ```

4. In **Authentication → Settings**, set an appropriate OTP expiry and email-send rate limit for your application. Do not expose a service-role key in the Android app.
5. Open **SQL Editor**, paste and run the full current [`docs/supabase_schema.sql`](./supabase_schema.sql). Its `on_auth_user_email_changed` trigger keeps `public.profiles.email` consistent with the Supabase Auth email after verification.

The Android client uses only the anon key, password grant, authenticated `PATCH /auth/v1/user`, and `POST /auth/v1/verify` with `type: email_change`; no passwords or OTPs are stored in the app database.

## Notes
- If you keep email confirmation ON, signup can succeed without returning an access token immediately; user must verify email and then log in.
- Old local Room/assets initialization in splash is removed from startup flow.
