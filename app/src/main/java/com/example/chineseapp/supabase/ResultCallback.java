package com.example.chineseapp.supabase;

public interface ResultCallback<T> {
    void onSuccess(T value);
    void onError(String message);
}
