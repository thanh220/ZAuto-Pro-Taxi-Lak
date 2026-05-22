package org.zauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "ZAuto::Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (context == null || intent == null || intent.getAction() == null) return;

            String action = intent.getAction();
            Log.d(TAG, "BOOT ACTION: " + action);

            // Bắt mọi sự kiện khởi động thiết bị hoặc cập nhật App
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                
                Log.d(TAG, "Starting ZAuto services...");
                startAllServices(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "BootReceiver Error: " + e.getMessage());
        }
    }

    private void startAllServices(Context context) {
        try {
            // 1. Kích hoạt dịch vụ ngầm lần 1
            Intent serviceIntent = new Intent(context, ZaloForegroundService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Foreground Service Started");

        } catch (Exception e) {
            Log.e(TAG, "Foreground start error: " + e.getMessage());
        }

        // 2. Bọc lót: Đợi 5s kích hoạt bồi thêm lần 2 chống bị hệ điều hành kill
        try {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    Intent serviceIntent = new Intent(context, ZaloForegroundService.class);
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "Delayed restart success");
                } catch (Exception e) {
                    Log.e(TAG, "Delayed restart error: " + e.getMessage());
                }
            }).start();
        } catch (Exception ignored) {}
    }
}
