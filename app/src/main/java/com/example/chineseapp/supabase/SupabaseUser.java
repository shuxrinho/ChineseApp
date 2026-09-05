package com.example.chineseapp.supabase;

public class SupabaseUser {
    public final String id;
    public final String email;
    public final String username;
    public String avatarUrl;
    public boolean emailConfirmed;

    public SupabaseUser(String id, String email, String username) {
        this(id, email, username, "");
    }

    public SupabaseUser(String id, String email, String username, String avatarUrl) {
        this(id, email, username, avatarUrl, false);
    }

    public SupabaseUser(String id, String email, String username, String avatarUrl, boolean emailConfirmed) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.emailConfirmed = emailConfirmed;
    }
}
