package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class ChatRepository {
    private static final String TAG = "ChatRepository";
    private final FirebaseFirestore db;

    public ChatRepository(Context context) {
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firestore nije dostupan", e);
        }
        db = instance;
    }

    public boolean isReady() {
        return db != null;
    }

    public ListenerRegistration listenMessages(String region, EventListener<QuerySnapshot> listener) {
        if (db == null || isBlank(region)) {
            return null;
        }
        return messages(region)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    public Task<Void> sendMessage(String senderId, String senderName, String region, String text) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(senderId) || isBlank(region) || isBlank(text)) {
            return Tasks.forException(new IllegalArgumentException("Nedostaju podaci za slanje poruke"));
        }
        String cleanText = text.trim();
        String cleanName = isBlank(senderName) ? "Igrac" : senderName.trim();
        DocumentReference messageRef = messages(region).document();
        Map<String, Object> data = new HashMap<>();
        data.put("senderId", senderId);
        data.put("senderName", cleanName);
        data.put("region", region);
        data.put("text", cleanText);
        data.put("timestamp", FieldValue.serverTimestamp());
        return messageRef.set(data).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return createOfflineNotifications(senderId, cleanName, region, cleanText);
        });
    }

    public Task<Void> setActiveConversation(String uid, String region) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("activeChatRegion", isBlank(region) ? "" : regionId(region));
        data.put("online", !isBlank(region));
        data.put("lastActiveAt", FieldValue.serverTimestamp());
        return db.collection("users").document(uid).set(data, SetOptions.merge());
    }

    public Task<Void> clearActiveConversation(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("activeChatRegion", "");
        data.put("online", false);
        data.put("lastActiveAt", FieldValue.serverTimestamp());
        return db.collection("users").document(uid).set(data, SetOptions.merge());
    }

    private Task<Void> createOfflineNotifications(String senderId, String senderName, String region, String text) {
        String currentRegionId = regionId(region);
        return db.collection("users")
                .whereEqualTo("region", region)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    WriteBatch batch = db.batch();
                    boolean hasWrites = false;
                    for (com.google.firebase.firestore.DocumentSnapshot user : task.getResult().getDocuments()) {
                        if (senderId.equals(user.getId())) {
                            continue;
                        }
                        String activeChatRegion = user.getString("activeChatRegion");
                        if (currentRegionId.equals(activeChatRegion)) {
                            continue;
                        }
                        DocumentReference notification = db.collection("users")
                                .document(user.getId())
                                .collection("notifications")
                                .document();
                        Map<String, Object> data = new java.util.HashMap<>();
                        data.put("notificationId", notification.getId());
                        data.put("id", notification.getId());
                        data.put("type", "CHAT");
                        data.put("title", "Nova poruka");
                        data.put("message", senderName + ": " + preview(text));
                        data.put("read", false);
                        data.put("createdAt", FieldValue.serverTimestamp());
                        data.put("actionType", "CHAT");
                        data.put("actionTargetId", currentRegionId);
                        data.put("senderUid", senderId);
                        data.put("senderName", senderName);
                        data.put("targetScreen", "CHAT");
                        batch.set(notification, data, SetOptions.merge());
                        hasWrites = true;
                    }
                    return hasWrites ? batch.commit() : Tasks.forResult(null);
                });
    }

    private com.google.firebase.firestore.CollectionReference messages(String region) {
        return db.collection("chats").document(regionId(region)).collection("messages");
    }

    private String regionId(String region) {
        return region == null ? "" : region.trim().replace("/", "_");
    }

    private String preview(String text) {
        String clean = text == null ? "" : text.trim();
        return clean.length() <= 80 ? clean : clean.substring(0, 77) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
