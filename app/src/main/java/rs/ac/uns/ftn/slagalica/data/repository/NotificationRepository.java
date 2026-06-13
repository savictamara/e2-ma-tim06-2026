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
        if (db == null || uid == null || uid.isEmpty()) {
            return null;
        }
        return notifications(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }

    public Task<Void> markRead(String uid, String notificationId) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (uid == null || uid.isEmpty() || notificationId == null || notificationId.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Nedostaje identifikator notifikacije"));
        }
        return notification(uid, notificationId).update("read", true);
    }

    public Task<Void> markAllRead(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (uid == null || uid.isEmpty()) {
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
        if (uid == null || uid.isEmpty() || notificationId == null || notificationId.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Nedostaje identifikator notifikacije"));
        }
        return notification(uid, notificationId).get();
    }

    public Task<Void> createTestNotifications(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }

        WriteBatch batch = db.batch();
        addTestNotification(batch, uid, "CHAT", "Nova poruka u regionalnom četu",
                "Stigla je nova poruka od igrača iz tvog regiona. Otvori čet i nastavi razgovor.", "CHAT");
        addTestNotification(batch, uid, "RANKING", "Promena plasmana",
                "Tvoj položaj na rang listi je ažuriran nakon poslednjih odigranih partija.", "RANKING");
        addTestNotification(batch, uid, "REWARD", "Dostupna nagrada",
                "Osvojila si dodatne tokene za aktivnost i plasman u prethodnom ciklusu.", "PROFILE");
        addTestNotification(batch, uid, "FRIEND_REQUEST", "Poziv od igrača",
                "Jedan igrač želi da te doda u prijatelje i pozove na partiju.", "FRIENDS");
        addTestNotification(batch, uid, "LEAGUE", "Promena lige",
                "Broj zvezda se promenio, pa proveri da li si prešla u novu ligu.", "PROFILE");
        addTestNotification(batch, uid, "OTHER", "Sistemsko obaveštenje",
                "Aplikacija Slagalica ima novo obaveštenje za tvoj nalog.", "NOTIFICATIONS");
        return batch.commit();
    }

    private void addTestNotification(WriteBatch batch, String uid, String type, String title,
                                     String message, String targetScreen) {
        DocumentReference doc = notifications(uid).document();
        Map<String, Object> data = new HashMap<>();
        data.put("id", doc.getId());
        data.put("type", type);
        data.put("title", title);
        data.put("message", message);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("targetScreen", targetScreen);
        batch.set(doc, data);
    }

    private com.google.firebase.firestore.CollectionReference notifications(String uid) {
        return db.collection("users").document(uid).collection("notifications");
    }

    private DocumentReference notification(String uid, String notificationId) {
        return notifications(uid).document(notificationId);
    }
}
