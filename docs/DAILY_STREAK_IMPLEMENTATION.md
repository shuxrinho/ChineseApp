# Daily Streak Mechanism Implementation

## Overview
The app now tracks user daily practice streaks with Supabase backend integration. When a user writes at least one character in handwriting practice, their daily goal is marked as completed and their streak is updated.

## Database Schema (Supabase)

### New Tables

#### `user_streaks`
Tracks current and longest streaks for each user.
```sql
create table user_streaks (
  id uuid default uuid_generate_v4() primary key,
  user_id uuid references profiles(id) not null,
  current_streak int default 0,
  longest_streak int default 0,
  last_activity_date date default CURRENT_DATE,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  updated_at timestamp with time zone default timezone('utc'::text, now()) not null,
  unique(user_id)
);
```

#### `daily_goals`
Records which days a user completed their practice goal.
```sql
create table daily_goals (
  id uuid default uuid_generate_v4() primary key,
  user_id uuid references profiles(id) not null,
  date date not null,
  is_completed boolean default false,
  practice_count int default 0,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  unique(user_id, date)
);
```

### New Functions

#### `initialize_user_streak()`
Trigger function that creates a streak record when a new user signs up.

#### `complete_daily_goal()`
RPC function that:
1. Marks today's daily goal as completed
2. Updates the user's streak:
   - First time ever: sets streak to 1
   - Consecutive day: increments streak
   - After a gap: resets streak to 1
3. Tracks longest streak achieved

## Setup Instructions

### 1. Deploy Database Schema
Run the SQL from `/workspace/docs/supabase_schema.sql` in your Supabase SQL Editor:
- Go to Supabase Dashboard → SQL Editor
- Copy the entire schema file content
- Execute it to create tables, functions, and triggers

### 2. Android Integration

#### New Repository Class
`SupabaseStreakRepository.java` handles all streak-related operations:
- `getStreakData()` - Fetches current streak, longest streak, and completed dates
- `markGoalCompleted()` - Calls Supabase RPC to complete today's goal
- `isTodayCompletedLocally()` - Checks local cache
- `markGoalCompletedLocally()` - Marks locally for instant feedback

#### Updated Activities

**StreakActivity.java**
- Displays real-time streak data from Supabase
- Shows calendar with completed days marked in green
- Highlights today in gold if not yet completed
- Shows current month name and year dynamically
- Displays "X/Y Days" completion count for current month

**WordListPracticeActivity.java**
- Automatically marks daily goal on first character submission
- Syncs with Supabase in background
- Falls back to local storage if network fails
- Prevents duplicate marking on same day

## How It Works

### User Flow
1. User opens handwriting practice
2. Writes first character of any word
3. App immediately:
   - Marks goal as completed locally (instant UI feedback)
   - Calls Supabase `complete_daily_goal()` RPC (background sync)
4. Streak is updated:
   - If consecutive day: streak increases
   - If gap: streak resets to 1
   - Longest streak tracked separately
5. User can view progress in Streak screen

### Calendar Display
- **Green circle**: Day completed
- **Gold circle with white border**: Today (not yet completed)
- **No background**: Future or past uncompleted days
- **Month selector**: Shows current month/year automatically
- **Counter**: Shows completed days this month (e.g., "12/31 Days")

### Streak Logic
```
Last Activity → Today = Result
null          → Any   = Streak = 1 (first time)
Yesterday     → Today = Streak++ (consecutive)
Older         → Today = Streak = 1 (reset after gap)
Today         → Today = No change (already counted)
```

## Files Modified/Created

### Created
- `app/src/main/java/com/example/chineseapp/supabase/SupabaseStreakRepository.java`
- `app/src/main/res/drawable/bg_streak_today_day.xml`
- `docs/supabase_schema.sql` (updated with streak tables)

### Modified
- `app/src/main/java/com/example/chineseapp/StreakActivity.java`
- `app/src/main/java/com/example/chineseapp/WordListPracticeActivity.java`
- `app/src/main/res/layout/activity_streak.xml`

## Testing

### Manual Testing Steps
1. Sign up / log in to the app
2. Navigate to Handwriting Practice
3. Write at least one character
4. Go to Streak screen
5. Verify:
   - Current streak shows "1 Days"
   - Today is highlighted in gold (or green if already synced)
   - Calendar shows today's date marked
   - Month title shows current month/year

### Edge Cases Handled
- No internet: Local storage ensures streak is counted
- Multiple practices same day: Only counts once
- Timezone issues: Uses UTC dates from server
- First-time user: Initializes streak record automatically

## Future Enhancements
- [ ] Add monthly navigation in calendar
- [ ] Show streak history graph
- [ ] Add shared streaks with friends
- [ ] Configurable daily goal (currently 1 character)
- [ ] Push notifications reminder integration
- [ ] Streak freeze power-ups

## Troubleshooting

### Streak not updating
1. Check Supabase logs for RPC errors
2. Verify `complete_daily_goal` function exists in database
3. Ensure RLS policies allow user to update their own records

### Calendar not showing data
1. Check that `user_streaks` and `daily_goals` tables have data
2. Verify user is logged in
3. Check Logcat for `CHAPP_SB_STREAK` tag errors

### Duplicate entries
- The database uses `unique(user_id, date)` constraint
- RPC function uses `ON CONFLICT ... DO UPDATE`
- Local storage prevents multiple calls per day
