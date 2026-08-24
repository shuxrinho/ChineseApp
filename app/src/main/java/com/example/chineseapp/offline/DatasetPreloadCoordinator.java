package com.example.chineseapp.offline;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatasetPreloadCoordinator {

    public interface Listener {
        void onDatasetStatus(boolean running, String message);
    }

    private static final Object LOCK = new Object();
    private static final Set<Listener> listeners = new HashSet<>();
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static boolean started;
    private static boolean running;
    private static String status = "";

    private DatasetPreloadCoordinator() {
    }

    public static void startIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (started) return;
            started = true;
            running = true;
            status = "Checking datasets...";
        }
        notifyListeners();

        io.execute(() -> {
            OfflinePreloadManager manager = new OfflinePreloadManager(appContext);
            OfflinePreloadManager.PreloadResult result = manager.ensureOfflineData(message -> {
                synchronized (LOCK) {
                    status = message;
                    running = true;
                }
                notifyListeners();
            });

            synchronized (LOCK) {
                running = false;
                if (result.errorMessage != null && result.usedCachedCopy) {
                    status = "Using saved datasets.";
                } else if (result.errorMessage != null) {
                    status = "Dataset download failed: " + result.errorMessage;
                } else if (result.refreshed) {
                    status = "Datasets downloaded.";
                } else {
                    status = "Datasets ready.";
                }
            }
            notifyListeners();
        });
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        synchronized (LOCK) {
            listeners.add(listener);
        }
        notifyOne(listener);
    }

    public static void removeListener(Listener listener) {
        synchronized (LOCK) {
            listeners.remove(listener);
        }
    }

    private static void notifyListeners() {
        Set<Listener> snapshot;
        synchronized (LOCK) {
            snapshot = new HashSet<>(listeners);
        }
        for (Listener listener : snapshot) {
            notifyOne(listener);
        }
    }

    private static void notifyOne(Listener listener) {
        boolean runningSnapshot;
        String statusSnapshot;
        synchronized (LOCK) {
            runningSnapshot = running;
            statusSnapshot = status;
        }
        main.post(() -> listener.onDatasetStatus(runningSnapshot, statusSnapshot));
    }
}
