# Fixing Supabase Signup 504 Timeout Error

## Problem Analysis

The logs show:
```
"pathname": "/auth/v1/signup",
"status": "504",
```

A **504 Gateway Timeout** means Supabase's edge network timed out waiting for the auth service to respond. This typically happens when:

1. **Email provider is misconfigured** - Supabase tries to send confirmation email but the SMTP connection hangs
2. **Email confirmation is enabled but no valid SMTP is configured** - Supabase uses default email service which may be slow or rate-limited

## Solution Steps

### Option 1: Configure Custom SMTP (Recommended for Production)

1. Go to your Supabase Dashboard: https://app.supabase.com
2. Navigate to **Authentication** → **Email Templates**
3. Click on **SMTP Settings**
4. Configure your SMTP provider:
   - **Host**: e.g., `smtp.gmail.com` or `smtp.sendgrid.net`
   - **Port**: 587 (TLS) or 465 (SSL)
   - **Sender Email**: your verified email
   - **Sender Name**: Your App Name
   - **Username/Password**: Your SMTP credentials

For Gmail:
- Enable "App Passwords" in Google Account settings
- Use the app password (not your regular password)

### Option 2: Disable Email Confirmation (Development Only)

For testing purposes, you can temporarily disable email confirmation:

1. Go to Supabase Dashboard → **Authentication** → **Settings**
2. Scroll to **Email Auth**
3. Uncheck **"Enable email confirmations"**
4. Save changes

⚠️ **Warning**: Only do this in development. Production apps should require email verification.

### Option 3: Use Supabase's Default Email (May Be Slow)

If using Supabase's built-in email service:
- Emails may take 1-5 minutes to arrive
- Rate limits apply (4 emails per hour per IP)
- Check spam folder

### Verify Configuration

After configuring SMTP:

1. Test signup with a real email address
2. Check email arrives within 30 seconds
3. Click confirmation link
4. Login should work immediately

### Android App Changes Made

I've already updated the Android app to handle slow responses:

```java
// SupabaseClient.java - Increased timeouts
.callTimeout(120, TimeUnit.SECONDS)  // Total call timeout
.connectTimeout(60, TimeUnit.SECONDS)
.readTimeout(60, TimeUnit.SECONDS)
.writeTimeout(60, TimeUnit.SECONDS)
```

And improved error messages in `SupabaseAuthRepository.java`:
```java
if (lower.contains("timeout") || lower.contains("timed out")) {
    return "Request timed out. This can happen if the server is slow to respond...";
}
```

## Quick Test

After applying SMTP settings, test with:

```bash
curl -X POST 'https://bcbhcrdqqihdgtvwtevc.supabase.co/auth/v1/signup' \
  -H 'apikey: YOUR_ANON_KEY' \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "test@example.com",
    "password": "testpass123",
    "data": {"username": "testuser"}
  }'
```

Should return 200 status within 10 seconds if SMTP is configured correctly.

## Common SMTP Providers

| Provider | Host | Port | Notes |
|----------|------|------|-------|
| Gmail | smtp.gmail.com | 587 | Need App Password |
| SendGrid | smtp.sendgrid.net | 587 | Free tier available |
| Mailgun | smtp.mailgun.org | 587 | Free tier available |
| Amazon SES | email-smtp.region.amazonaws.com | 587 | AWS account required |
