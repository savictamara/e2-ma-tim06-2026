package rs.ac.uns.ftn.slagalica.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import rs.ac.uns.ftn.slagalica.NotificationDetailActivity;
import rs.ac.uns.ftn.slagalica.R;
import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;

public final class NotificationHelper {
    public static final String CHANNEL_CHAT = "chat_notifications";
    public static final String CHANNEL_RANKING = "ranking_notifications";
    public static final String CHANNEL_REWARD = "reward_notifications";
    public static final String CHANNEL_OTHER = "other_notifications";
    private static final java.util.Set<String> DISPLAYED_NOTIFICATION_IDS = new java.util.HashSet<>();

    private NotificationHelper() {
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_CHAT, "Obaveštenja u četu", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_RANKING, "Obaveštenja o rangiranju", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_REWARD, "Obaveštenja o nagradama", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_OTHER, "Ostala obaveštenja", NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static void showNotification(Context context, AppNotification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || notification == null) {
            return;
        }
        createNotificationChannels(context);
        Intent intent = new Intent(context, NotificationDetailActivity.class);
        intent.putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION_ID, notification.id);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notification.id == null ? 0 : notification.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(context, channelForType(notification.type))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setStyle(new Notification.BigTextStyle().bigText(notification.message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify(notification.id == null ? (int) System.currentTimeMillis() : notification.id.hashCode(), builder.build());
    }

    public static synchronized boolean markDisplayedIfNew(String notificationId) {
        if (notificationId == null || notificationId.isEmpty() || DISPLAYED_NOTIFICATION_IDS.contains(notificationId)) {
            return false;
        }
        DISPLAYED_NOTIFICATION_IDS.add(notificationId);
        return true;
    }

    public static synchronized void rememberDisplayed(String notificationId) {
        if (notificationId != null && !notificationId.isEmpty()) {
            DISPLAYED_NOTIFICATION_IDS.add(notificationId);
        }
    }

    private static String channelForType(String type) {
        if ("CHAT".equals(type)) {
            return CHANNEL_CHAT;
        }
        if ("RANKING".equals(type)) {
            return CHANNEL_RANKING;
        }
        if ("REWARD".equals(type)) {
            return CHANNEL_REWARD;
        }
        return CHANNEL_OTHER;
    }
}
