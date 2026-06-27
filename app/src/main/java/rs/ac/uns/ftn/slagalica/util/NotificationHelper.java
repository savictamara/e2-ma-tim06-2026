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
import rs.ac.uns.ftn.slagalica.RewardActivity;
import rs.ac.uns.ftn.slagalica.FriendsActivity;
import rs.ac.uns.ftn.slagalica.RegionsActivity;
import rs.ac.uns.ftn.slagalica.ChallengeResultActivity;
import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;

public final class NotificationHelper {
    public static final String CHAT_CHANNEL = "CHAT_CHANNEL";
    public static final String RANKING_CHANNEL = "RANKING_CHANNEL";
    public static final String REWARD_CHANNEL = "REWARD_CHANNEL";
    public static final String OTHER_CHANNEL = "OTHER_CHANNEL";
    public static final String CHANNEL_CHAT = CHAT_CHANNEL;
    public static final String CHANNEL_RANKING = RANKING_CHANNEL;
    public static final String CHANNEL_REWARD = REWARD_CHANNEL;
    public static final String CHANNEL_OTHER = OTHER_CHANNEL;
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
                CHAT_CHANNEL, "Chat notifications", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                RANKING_CHANNEL, "Ranking notifications", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                REWARD_CHANNEL, "Reward notifications", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                OTHER_CHANNEL, "Other notifications", NotificationManager.IMPORTANCE_DEFAULT));
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
        Class<?> target = NotificationDetailActivity.class;
        if ("REWARD".equals(notification.type)) {
            target = RewardActivity.class;
        } else if ("FRIEND_INVITE".equals(notification.type) || "FRIENDLY_MATCH_INVITE".equals(notification.type)) {
            target = FriendsActivity.class;
        } else if ("CHALLENGE".equals(notification.type) || "CHALLENGE_START".equals(notification.type)) {
            target = RegionsActivity.class;
        } else if ("CHALLENGE_RESULT".equals(notification.type)) {
            target = ChallengeResultActivity.class;
        }
        Intent intent = new Intent(context, target);
        intent.putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION_ID, notification.id);
        intent.putExtra("actionTargetId", notification.actionTargetId);
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
            return CHAT_CHANNEL;
        }
        if ("RANKING".equals(type)) {
            return RANKING_CHANNEL;
        }
        if ("REWARD".equals(type)) {
            return REWARD_CHANNEL;
        }
        return OTHER_CHANNEL;
    }
}
