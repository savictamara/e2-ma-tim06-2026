package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.domain.model.User;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private final FirebaseFirestore db;

    public UserRepository(Context context) {
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

    public Task<Void> createUser(User user) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("users").document(user.uid).set(user);
    }

    public ListenerRegistration listenUser(String uid, EventListener<DocumentSnapshot> listener) {
        if (db == null || uid == null || uid.isEmpty()) {
            return null;
        }
        return db.collection("users").document(uid).addSnapshotListener(listener);
    }

    public ListenerRegistration listenUserStats(String uid, String statDocument, EventListener<DocumentSnapshot> listener) {
        if (db == null || uid == null || uid.isEmpty()) {
            return null;
        }
        return db.collection("users").document(uid).collection("stats").document(statDocument)
                .addSnapshotListener(listener);
    }

    public Task<Void> ensureProfileDefaults(String uid, String email) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("users").document(uid).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            DocumentSnapshot doc = task.getResult();
            Map<String, Object> updates = new HashMap<>();
            putIfMissing(updates, doc, "uid", uid);
            putIfMissing(updates, doc, "email", email == null ? "" : email);
            putIfMissing(updates, doc, "username", defaultUsername(email, uid));
            putIfMissing(updates, doc, "region", "");
            putIfMissing(updates, doc, "avatarId", "star");
            putIfMissing(updates, doc, "avatarFrameType", "default");
            putIfMissing(updates, doc, "avatarFrameColor", "#8A2BE2");
            putIfMissing(updates, doc, "tokens", 5L);
            putIfMissing(updates, doc, "stars", 0L);
            putIfMissing(updates, doc, "leagueName", "Početna liga");
            putIfMissing(updates, doc, "leagueIcon", "star");
            putIfMissing(updates, doc, "createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            putIfMissing(updates, doc, "online", true);
            putIfMissing(updates, doc, "inGame", false);
            putIfMissing(updates, doc, "currentGameId", "");
            if (updates.isEmpty()) {
                return Tasks.forResult(null);
            }
            return db.collection("users").document(uid).set(updates, SetOptions.merge());
        });
    }

    public Task<Void> updateAvatar(String uid, String avatarId) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("avatarId", avatarId == null ? "star" : avatarId);
        return db.collection("users").document(uid).set(updates, SetOptions.merge());
    }

    public Task<String> emailForIdentity(String identity) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (identity.contains("@")) {
            return Tasks.forResult(identity);
        }
        return db.collection("users").whereEqualTo("username", identity).limit(1).get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    if (task.getResult().isEmpty()) {
                        throw new IllegalArgumentException("Korisnicko ime nije pronadjeno");
                    }
                    DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                    String email = doc.getString("email");
                    if (email == null || email.isEmpty()) {
                        throw new IllegalArgumentException("Email nije pronadjen za korisnika");
                    }
                    return email;
                });
    }

    public Task<Void> setOnline(String uid, boolean online) {
        return updateUserState(uid, online, false, "");
    }

    public Task<String> currentGameId(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("users").document(uid).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            String gameId = task.getResult().getString("currentGameId");
            return gameId == null ? "" : gameId;
        });
    }

    public Task<Void> updateUserState(String uid, boolean online, boolean inGame, String currentGameId) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("online", online);
        updates.put("inGame", inGame);
        updates.put("currentGameId", currentGameId == null ? "" : currentGameId);
        return db.collection("users").document(uid).set(updates, SetOptions.merge());
    }

    public Task<Void> logoutUserState(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("online", false);
        updates.put("inGame", false);
        updates.put("currentGameId", null);
        return db.collection("users").document(uid).set(updates, SetOptions.merge());
    }

    private void putIfMissing(Map<String, Object> updates, DocumentSnapshot doc, String key, Object value) {
        if (!doc.exists() || !doc.contains(key) || doc.get(key) == null) {
            updates.put(key, value);
        }
    }

    private String defaultUsername(String email, String uid) {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return uid == null || uid.length() < 6 ? "igrac" : "igrac_" + uid.substring(0, 6);
    }
}
