# Remote Username Change Implementation

## Overview
Implemented full remote username change functionality with real-time availability checking against Supabase.

## Changes Made

### 1. SQL Schema Updates (`docs/supabase_schema.sql`)

Added new RPC function to check username availability:

```sql
create or replace function public.check_username_availability(check_username text)
returns table (available boolean)
language plpgsql
security definer
set search_path = public
as $$
begin
    if check_username is null or btrim(check_username) = '' then
        return query select false;
        return;
    end if;

    if length(btrim(check_username)) < 3
        or length(btrim(check_username)) > 24
        or btrim(check_username) !~ '^[A-Za-z0-9_]+$' then
        return query select false;
        return;
    end if;

    return query select not exists (
        select 1
        from public.profiles p
        where lower(p.username) = lower(btrim(check_username))
    );
end;
$$;

grant execute on function public.check_username_availability(text) to authenticated;
```

**Action Required**: Run this SQL in your Supabase SQL Editor to add the new function.

### 2. SupabaseProfileRepository.java

Added `isUsernameAvailable()` method:
- Calls the new `check_username_availability` RPC function
- Returns boolean via callback indicating if username is available
- Handles validation and errors properly

### 3. ChangeUsernameActivity.java

Updated `checkUsernameAvailability()` method:
- Removed placeholder simulation code
- Now calls `profileRepository.isUsernameAvailable()` for real Supabase check
- Properly handles success/error callbacks
- Shows toast messages for errors

## How It Works

### Username Change Flow:

1. **User types username** → Real-time validation (lowercase, length, special chars)
2. **Validation passes** → After 500ms delay, calls Supabase to check availability
3. **Supabase checks** → Queries `profiles` table for existing username (case-insensitive)
4. **Result returned** → Updates UI with checkmark/X icon
5. **User clicks Save** → Calls existing `update_profile_username` RPC
6. **Username updated** → SessionManager saves updated user data

### Validation Rules:
- Length: 3-24 characters
- Characters: lowercase letters, numbers, underscores only
- Must not start with a number (enforced in UI)
- Must be unique (case-insensitive comparison)

## Testing

1. **Deploy SQL**: Run the updated schema in Supabase SQL Editor
2. **Build app**: `./gradlew assembleDebug`
3. **Test flow**:
   - Login to app
   - Go to Profile → Change Username
   - Try taken username (e.g., "admin") → Should show as unavailable
   - Try valid new username → Should show as available
   - Click Save → Should update remotely and navigate back

## Files Modified

- `/workspace/docs/supabase_schema.sql` - Added `check_username_availability` function
- `/workspace/app/src/main/java/com/example/chineseapp/supabase/SupabaseProfileRepository.java` - Added `isUsernameAvailable()` method
- `/workspace/app/src/main/java/com/example/chineseapp/ChangeUsernameActivity.java` - Integrated real Supabase availability check

## Error Handling

The implementation handles:
- Network errors → Shows error toast
- Username taken → Shows unavailable state immediately
- Invalid format → Prevents availability check until fixed
- Not logged in → Throws appropriate error message

## Security

- Both RPC functions use `security definer` to run with elevated privileges
- RLS policies ensure users can only update their own profile
- Case-insensitive uniqueness prevents similar usernames
- Input sanitization via `btrim()` and regex validation
