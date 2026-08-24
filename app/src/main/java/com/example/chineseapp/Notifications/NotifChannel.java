package com.example.chineseapp.Notifications;

import android.app.Application;

public class NotifChannel extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationScheduler.createChannel(this);
    }
}
