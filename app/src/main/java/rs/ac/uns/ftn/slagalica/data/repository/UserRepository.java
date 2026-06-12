package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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
}
