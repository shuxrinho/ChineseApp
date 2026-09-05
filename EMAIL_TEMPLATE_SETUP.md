# Supabase Email Confirmation Template Setup

## HTML Template Location
The email confirmation HTML template is located at:
`/workspace/email_templates/confirmation.html`

## How to Configure in Supabase Dashboard

1. **Go to Supabase Dashboard** → Authentication → Email Templates

2. **Select "Confirmation" template** from the dropdown

3. **Copy the content** from `/workspace/email_templates/confirmation.html`

4. **Paste into Supabase's template editor**

5. **Important Settings:**
   - Make sure "Confirm email" is ENABLED in Authentication → Providers → Email
   - The template uses these Supabase variables:
     - `{{ .Email }}` - User's email address
     - `{{ .ConfirmationURL }}` - The confirmation link (required)

6. **Save the template**

## What the Template Does

- Sends a beautiful dark-themed email matching your app's style
- Shows the user's email address
- Provides a prominent "Confirm Email Address" button
- Includes a fallback copy/paste link
- Uses your app's color scheme (#00C853 green, dark gradients)
- Mobile-responsive design

## Testing

After setup:
1. Create a new test account in your app
2. Check the email inbox for the confirmation email
3. Click the confirmation link
4. Log in to the app - the "Profile not authenticated" message should disappear
