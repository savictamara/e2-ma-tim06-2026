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

    private Task<Void> createOfflineNotifications(String senderId, String senderName, String region, String text) {
        return db.collection("users")
                .whereEqualTo("region", region)
                .whereEqualTo("online", false)
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
                        DocumentReference notification = db.collection("users")
                                .document(user.getId())
                                .collection("notifications")
                                .document();
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", notification.getId());
                        data.put("type", "CHAT");
                        data.put("title", "Nova poruka u cetu");
                        data.put("message", senderName + ": " + text);
                        data.put("read", false);
                        data.put("createdAt", FieldValue.serverTimestamp());
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
