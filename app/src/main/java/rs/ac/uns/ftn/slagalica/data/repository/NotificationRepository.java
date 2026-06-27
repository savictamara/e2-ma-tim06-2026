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

public class NotificationRepository {
    private static final String TAG = "NotificationRepository";
    private final FirebaseFirestore db;

    public NotificationRepository(Context context) {
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase Firestore nije dostupan", e);
        }
        db = instance;
    }

    public boolean isReady() {
        return db != null;
    }

    public ListenerRegistration listenNotifications(String uid, EventListener<QuerySnapshot> listener) {
        if (db == null || isBlank(uid)) {
            return null;
        }
        return notifications(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }

    public Task<QuerySnapshot> getNotifications(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        return notifications(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
    }

    public Task<QuerySnapshot> getUnreadNotifications(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        return notifications(uid)
                .whereEqualTo("read", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
    }

    public Task<DocumentReference> createNotification(String receiverUid, String type, String title,
                                                      String message, String actionType,
                                                      String actionTargetId, String senderUid,
                                                      String senderName) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(receiverUid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        DocumentReference doc = notifications(receiverUid).document();
        Map<String, Object> data = notificationData(doc.getId(), type, title, message, actionType,
                actionTargetId, senderUid, senderName);
        return doc.set(data, SetOptions.merge()).continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return doc;
        });
    }

    public Task<Void> markAsRead(String uid, String notificationId) {
        return markRead(uid, notificationId);
    }

    public Task<Void> markRead(String uid, String notificationId) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid) || isBlank(notificationId)) {
            return Tasks.forException(new IllegalArgumentException("Nedostaje identifikator notifikacije"));
        }
        return notification(uid, notificationId).update("read", true);
    }

    public Task<Void> markAllRead(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        return notifications(uid).whereEqualTo("read", false).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult().getDocuments()) {
                batch.update(doc.getReference(), "read", true);
            }
            return batch.commit();
        });
    }

    public Task<com.google.firebase.firestore.DocumentSnapshot> getNotification(String uid, String notificationId) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid) || isBlank(notificationId)) {
            return Tasks.forException(new IllegalArgumentException("Nedostaje identifikator notifikacije"));
        }
        return notification(uid, notificationId).get();
    }

    public Task<Void> createTestNotifications(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }

        WriteBatch batch = db.batch();
        addTestNotification(batch, uid, "CHAT", "Nova poruka",
                "Stigla je nova poruka iz regionalnog ceta.", "CHAT");
        addTestNotification(batch, uid, "RANKING", "Promena plasmana",
                "Tvoj polozaj na rang listi je azuriran.", "RANKING");
        addTestNotification(batch, uid, "REWARD", "Dostupna nagrada",
                "Osvojeni su dodatni tokeni za aktivnost.", "REWARD");
        addTestNotification(batch, uid, "FRIEND_INVITE", "Poziv od igraca",
                "Jedan igrac zeli da te doda u prijatelje.", "FRIEND_INVITE");
        addTestNotification(batch, uid, "LEAGUE", "Promena lige",
                "Proveri novu ligu na profilu.", "LEAGUE");
        addTestNotification(batch, uid, "OTHER", "Sistemsko obavestenje",
                "Aplikacija Slagalica ima novo obavestenje za tvoj nalog.", "OTHER");
        return batch.commit();
    }

    public Map<String, Object> notificationData(String notificationId, String type, String title,
                                                String message, String actionType,
                                                String actionTargetId, String senderUid,
                                                String senderName) {
        String cleanType = normalize(type, "OTHER");
        String cleanAction = normalize(actionType, cleanType);
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notificationId);
        data.put("id", notificationId);
        data.put("type", cleanType);
        data.put("title", normalize(title, ""));
        data.put("message", normalize(message, ""));
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("read", false);
        data.put("actionType", cleanAction);
        data.put("actionTargetId", normalize(actionTargetId, ""));
        data.put("senderUid", normalize(senderUid, ""));
        data.put("senderName", normalize(senderName, ""));
        data.put("targetScreen", cleanAction);
        return data;
    }

    private void addTestNotification(WriteBatch batch, String uid, String type, String title,
                                     String message, String actionType) {
        DocumentReference doc = notifications(uid).document();
        batch.set(doc, notificationData(doc.getId(), type, title, message, actionType, "", "", ""));
    }

    private com.google.firebase.firestore.CollectionReference notifications(String uid) {
        return db.collection("users").document(uid).collection("notifications");
    }

    private DocumentReference notification(String uid, String notificationId) {
        return notifications(uid).document(notificationId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
