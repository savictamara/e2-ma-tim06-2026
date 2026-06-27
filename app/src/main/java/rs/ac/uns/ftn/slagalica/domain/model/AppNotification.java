package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class AppNotification {
    public String id;
    public String notificationId;
    public String type;
    public String title;
    public String message;
    public boolean read;
    public Timestamp createdAt;
    public String targetScreen;
    public String actionType;
    public String actionTargetId;
    public String senderUid;
    public String senderName;
    public Map<String, Object> payload;

    public static AppNotification fromSnapshot(DocumentSnapshot snapshot) {
        AppNotification notification = new AppNotification();
        notification.notificationId = value(snapshot.getString("notificationId"), value(snapshot.getString("id"), snapshot.getId()));
        notification.id = notification.notificationId;
        notification.type = value(snapshot.getString("type"), "OTHER");
        notification.title = value(snapshot.getString("title"), "");
        notification.message = value(snapshot.getString("message"), "");
        notification.read = Boolean.TRUE.equals(snapshot.getBoolean("read"));
        notification.createdAt = snapshot.getTimestamp("createdAt");
        notification.targetScreen = value(snapshot.getString("targetScreen"), "");
        notification.actionType = value(snapshot.getString("actionType"), notification.targetScreen);
        notification.actionTargetId = value(snapshot.getString("actionTargetId"), "");
        notification.senderUid = value(snapshot.getString("senderUid"), "");
        notification.senderName = value(snapshot.getString("senderName"), "");
        Object rawPayload = snapshot.get("payload");
        notification.payload = rawPayload instanceof Map ? new HashMap<>((Map<String, Object>) rawPayload) : new HashMap<>();
        return notification;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
