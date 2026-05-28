package org.zauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class ZaloForegroundService extends Service {
    private static final String CHANNEL_ID = "ZAutoApiChannel";

    public static void startService(Context context) {
        Intent intent = new Intent(context, ZaloForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = null;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("ZAuto VIP đang chạy ngầm")
                    .setContentText("Hệ thống API Zalo đang hoạt động")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("ZAuto VIP đang chạy ngầm")
                    .setContentText("Hệ thống API Zalo đang hoạt động")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }
        
        startForeground(1998, notification); // Số ID bất kỳ
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // START_STICKY giúp service tự sống lại nếu bị hệ điều hành giết
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ZAuto API Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
