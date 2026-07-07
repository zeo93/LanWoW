package com.marco.lanwow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    public static final String CHANNEL_UPDATES = "aggiornamenti";

    public static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel updates = new NotificationChannel(CHANNEL_UPDATES,
                c.getString(R.string.canale_aggiornamenti), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(updates);
    }

    public static void notifyUpdate(Context c, String version) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) {
            return;
        }
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 999, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(c.getString(R.string.app_name))
                .setContentText(c.getString(R.string.notifica_aggiornamento, version))
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(c).notify(999, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
