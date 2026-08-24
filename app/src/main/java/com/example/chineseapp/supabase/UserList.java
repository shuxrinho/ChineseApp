package com.example.chineseapp.supabase;

public class UserList {
    public final int position;
    public final String title;
    public final String description;

    public UserList(int position, String title, String description) {
        this.position = position;
        this.title = title;
        this.description = description;
    }
}
