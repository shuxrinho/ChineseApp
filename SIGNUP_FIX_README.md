# Sign Up Issue Fix - Supabase Email Confirmation Timeout

## Problem Description

When users tried to sign up, they experienced:
1. **No confirmation email received** from Supabase
2. **App timeout error** after ~20 seconds (log shows "Signup error callback: timeout")
3. **App doesn't navigate** to main page or login page after signup

### Root Causes Identified

1. **HTTP Timeout Too Short**: The OkHttp client had 20-second timeouts for connect/read/write operations. When Supabase processes signup with email confirmation enabled, it can take longer than 20 seconds, especially if:
   - The email provider is slow to accept the message
   - Supabase's email service is under load
   - Network latency is high

2. **Missing User Guidance**: After successful signup with email confirmation enabled, users weren't being properly directed to check their email and then log in.

## Changes Made

### 1. Increased HTTP Timeouts (`SupabaseClient.java`)

Changed timeout values from 20 seconds to 60 seconds:

```java
// Before
.connectTimeout(20, TimeUnit.SECONDS)
.readTimeout(20, TimeUnit.SECONDS)
.writeTimeout(20, TimeUnit.SECONDS)

// After
.connectTimeout(60, TimeUnit.SECONDS)
.readTimeout(60, TimeUnit.SECONDS)
.writeTimeout(60, TimeUnit.SECONDS)
```

This gives Supabase enough time to process the signup request and send the confirmation email.

### 2. Improved Signup Flow (`SignupActivity.java`)

- Fixed anonymous type inference issue with `ResultCallback<SupabaseUser>()`
- Changed navigation flow when email confirmation is required:
  - Instead of `finishAffinity()` which closes all activities, now uses `finish()` to return to login
  - Passes the email to LoginActivity via intent extra for pre-filling
  - Shows clear instructions to check email inbox/spam folder

### 3. Enhanced Login Experience (`LoginActivity.java`)

Added support for pre-filling email when coming from signup:
- Checks for `CONFIRMATION_EMAIL` intent extra
- Pre-fills the email field
- Shows helpful toast message: "Please log in with your email to continue"

### 4. Better Error Messages (`SupabaseAuthRepository.java`)

Added specific handling for timeout errors:
```java
if (lower.contains("timeout") || lower.contains("timed out")) {
    return "Request timed out. This can happen if the server is slow to respond. Please check your internet connection and try again. If you just signed up, wait a moment and try logging in.";
}
```

## Required Supabase Configuration

For this fix to work properly, ensure your Supabase project has:

### 1. Email Provider Enabled
Go to: **Authentication → Providers → Email**
- Enable Email provider
- Decide on "Confirm email" setting:
  - **If ENABLED** (recommended for production): Users must confirm email before logging in
  - **If DISABLED**: Users can log in immediately after signup (useful for development/testing)

### 2. Email Template Configured
Go to: **Authentication → Email Templates → Confirmation**
- Use the template from `/workspace/email_templates/confirmation.html`
- Ensure these variables are used:
  - `{{ .Email }}` - User's email address
  - `{{ .ConfirmationURL }}` - The confirmation link

### 3. SMTP Settings (Important!)
By default, Supabase uses their shared email service which can be slow or unreliable. For production:

Go to: **Authentication → Email Templates → SMTP Settings**
- Configure your own SMTP server (e.g., SendGrid, Mailgun, Postmark, AWS SES)
- This ensures reliable and faster email delivery

## Testing Steps

1. **Build and install** the updated app
2. **Create a new test account** with a valid email
3. **Wait for the success message** about checking email
4. **Check email inbox** (and spam folder) for confirmation email
5. **Click the confirmation link** in the email
6. **Log in** with the confirmed credentials
7. **Verify** you're taken to the main page

## Troubleshooting

### Still Getting Timeout Errors?

1. **Check Supabase keys** in `local.properties`:
   ```properties
   SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
   SUPABASE_ANON_KEY=YOUR_ANON_KEY
   ```

2. **Verify network connectivity** - test on WiFi and mobile data

3. **Check Supabase dashboard logs** - go to Authentication → Logs to see server-side errors

4. **Temporarily disable email confirmation** for testing:
   - Go to Authentication → Providers → Email
   - Disable "Confirm email"
   - Test signup (should work immediately)
   - Re-enable for production

### Not Receiving Confirmation Emails?

1. **Check spam/junk folder**
2. **Verify email address** is correct
3. **Check Supabase email logs** in dashboard
4. **Configure custom SMTP** (see above)
5. **Check rate limits** - Supabase limits emails on free tier

### "Email Not Confirmed" Error on Login?

This is expected behavior when email confirmation is enabled. User must:
1. Check their email inbox
2. Click the confirmation link
3. Then log in

## Files Modified

1. `/workspace/app/src/main/java/com/example/chineseapp/supabase/SupabaseClient.java`
2. `/workspace/app/src/main/java/com/example/chineseapp/SignupActivity.java`
3. `/workspace/app/src/main/java/com/example/chineseapp/LoginActivity.java`
4. `/workspace/app/src/main/java/com/example/chineseapp/supabase/SupabaseAuthRepository.java`

## Additional Notes

- The 60-second timeout should be sufficient for most signup scenarios
- If you keep experiencing issues, consider implementing retry logic
- For production apps, always use a custom SMTP provider for better deliverability
- Monitor Supabase dashboard logs during testing to identify any server-side issues
