package org.zauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class ZaloForegroundService extends Service {

    private static final String TAG = "ZAuto::Foreground";
    private static final String CHANNEL_ID = "ZAuto_Core";
    private static final int NOTIFICATION_ID = 199;
    private PowerManager.WakeLock wakeLock = null;

    // =====================================================
    // CREATE
    // =====================================================
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Foreground Service Create");
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZAuto::ProdWakeLock");
                wakeLock.setReferenceCounted(false); // Fix memory leak
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock init error: " + e.getMessage());
        }
    }

    // =====================================================
    // START COMMAND
    // =====================================================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        try {
            createNotificationChannel();
            Notification notification = buildNotification();
            startForegroundSafe(notification);
            acquireWakeLock();
        } catch (Exception e) {
            Log.e(TAG, "Service Start Error: " + e.getMessage());
        }
        // QUAN TRỌNG: Tự động hồi sinh nếu bị hệ điều hành kill
        return START_STICKY;
    }

    // =====================================================
    // CREATE CHANNEL
    // =====================================================
    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "ZAuto Production Engine",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("ZAuto Background Engine");
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setShowBadge(false);

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Create channel error: " + e.getMessage());
        }
    }

    // =====================================================
    // BUILD NOTIFICATION
    // =====================================================
    private Notification buildNotification() {
        Intent notificationIntent = new Intent();
        try {
            notificationIntent.setClassName(getPackageName(), "org.kivy.android.PythonActivity");
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } catch (Exception e) {
            Log.e(TAG, "PythonActivity error: " + e.getMessage());
        }

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, pendingFlags
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder.setContentTitle("ZAuto VIP")
                .setContentText("Hệ thống tự động đang chạy")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .build();
    }

    // =====================================================
    // START FOREGROUND SAFE
    // =====================================================
    private void startForegroundSafe(Notification notification) {
        try {
            // ANDROID 14+
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                Log.d(TAG, "Foreground Android 14+");
                return;
            }
            // ANDROID 10+
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                Log.d(TAG, "Foreground Android 10+");
                return;
            }
            // LEGACY
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground Legacy");

        } catch (Exception e) {
            Log.e(TAG, "startForeground error: " + e.getMessage());
            try {
                // Fallback
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {}
        }
    }

    // =====================================================
    // WAKE LOCK
    // =====================================================
    private void acquireWakeLock() {
        try {
            if (wakeLock == null) return;
            if (wakeLock.isHeld()) wakeLock.release();
            // Khóa CPU tỉnh táo trong 24H
            wakeLock.acquire(24L * 60L * 60L * 1000L);
            Log.d(TAG, "WakeLock acquired");
        } catch (Exception e) {
            Log.e(TAG, "WakeLock error: " + e.getMessage());
        }
    }

    // =====================================================
    // DESTROY & BIND
    // =====================================================
    @Override
    public void onDestroy() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // =====================================================
    // STATIC START (Hàm gọi nhanh từ Python/Java khác)
    // =====================================================
    public static void startService(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, ZaloForegroundService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start service error: " + e.getMessage());
        }
    }
}
