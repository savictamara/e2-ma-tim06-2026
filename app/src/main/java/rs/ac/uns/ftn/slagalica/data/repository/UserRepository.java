package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rs.ac.uns.ftn.slagalica.domain.model.LeagueDefinition;
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

    public Task<DocumentSnapshot> getUser(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("users").document(uid).get();
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
            String existingUsername = doc.exists() ? doc.getString("username") : null;
            putIfMissing(updates, doc, "usernameLowercase", defaultUsername(existingUsername == null ? email : existingUsername, uid).toLowerCase(java.util.Locale.US));
            putIfMissing(updates, doc, "region", "");
            putIfMissing(updates, doc, "regionId", "");
            putIfMissing(updates, doc, "regionName", "");
            putIfMissing(updates, doc, "monthlyRegionStars", 0L);
            putIfMissing(updates, doc, "avatarFrame", "NONE");
            putIfMissing(updates, doc, "avatarId", "star");
            putIfMissing(updates, doc, "avatarFrameType", "default");
            putIfMissing(updates, doc, "avatarFrameColor", "#8A2BE2");
            putIfMissing(updates, doc, "tokens", 5L);
            putIfMissing(updates, doc, "stars", 0L);
            putIfMissing(updates, doc, "league", 0L);
            putIfMissing(updates, doc, "weeklyStars", 0L);
            putIfMissing(updates, doc, "monthlyStars", 0L);
            putIfMissing(updates, doc, "weeklyMatchesPlayed", 0L);
            putIfMissing(updates, doc, "monthlyMatchesPlayed", 0L);
            putIfMissing(updates, doc, "weeklyLeaderboardEligible", false);
            putIfMissing(updates, doc, "monthlyLeaderboardEligible", false);
            putIfMissing(updates, doc, "pendingReward", false);
            putIfMissing(updates, doc, "leagueIconName", "ic_league_0");
            putIfMissing(updates, doc, "lastDailyTokenClaimDate", "");
            putIfMissing(updates, doc, "pendingLeagueDialog", false);
            putIfMissing(updates, doc, "leagueName", "Početna liga");
            putIfMissing(updates, doc, "leagueIcon", "star");
            putIfMissing(updates, doc, "createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            putIfMissing(updates, doc, "online", true);
            putIfMissing(updates, doc, "lastActiveAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            putIfMissing(updates, doc, "inGame", false);
            putIfMissing(updates, doc, "currentGameId", "");
            putIfMissing(updates, doc, "activeGameId", "");
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

    public Task<Void> grantDailyLeagueTokens(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        DocumentReference userRef = db.collection("users").document(uid);
        return db.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            if (!user.exists()) {
                return null;
            }
            if (today.equals(user.getString("lastDailyTokenClaimDate"))) {
                return null;
            }
            long stars = longValue(user.get("stars"));
            LeagueDefinition league = leagueFromUser(user, stars);
            long tokens = longValue(user.get("tokens"));
            int grant = LeagueDefinition.dailyTokensFor(league.id);
            Map<String, Object> updates = new HashMap<>();
            updates.put("tokens", tokens + grant);
            updates.put("lastDailyTokenClaimDate", today);
            updates.put("lastDailyTokenGrant", today);
            updates.put("league", league.id);
            updates.put("leagueName", league.name);
            updates.put("leagueIcon", league.iconName);
            updates.put("leagueIconName", league.iconName);
            transaction.set(userRef, updates, SetOptions.merge());
            DocumentReference notificationRef = userRef.collection("notifications").document();
            transaction.set(notificationRef, dailyTokenNotification(notificationRef.getId(), grant, league.name));
            Log.d(TAG, "League recalculation uid=" + uid
                    + ", oldStars=" + stars
                    + ", newStars=" + stars
                    + ", oldLeague=" + league.id
                    + ", newLeague=" + league.id
                    + ", reason=DAILY_TOKEN_GRANT");
            return null;
        });
    }

    public Task<Void> ensureLeagueConsistency(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference userRef = db.collection("users").document(uid);
        return db.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            if (!user.exists()) {
                return null;
            }
            long stars = Math.max(0, longValue(user.get("stars")));
            LeagueDefinition expected = LeagueDefinition.forStars(stars);
            LeagueDefinition stored = leagueFromUserField(user);
            Log.d(TAG, "LEAGUE_CONSISTENCY_CHECK uid=" + uid
                    + ", stars=" + stars
                    + ", storedLeague=" + stored.id
                    + ", expectedLeague=" + expected.id);
            if (stored.id == expected.id) {
                return null;
            }

            String direction = LeagueDefinition.direction(stored.id, expected.id);
            String title = "PROMOTION".equals(direction)
                    ? "Presli ste u novu ligu!"
                    : "Pali ste u nizu ligu";
            String message = "PROMOTION".equals(direction)
                    ? "Cestitamo! Presli ste iz " + stored.name + " u " + expected.name + "."
                    : "Presli ste iz " + stored.name + " u " + expected.name + ".";

            Map<String, Object> updates = new HashMap<>();
            updates.put("league", expected.id);
            updates.put("leagueName", expected.name);
            updates.put("leagueIcon", expected.iconName);
            updates.put("leagueIconName", expected.iconName);
            updates.put("lastLeagueChangeAt", FieldValue.serverTimestamp());
            updates.put("pendingLeagueDialog", true);
            updates.put("pendingLeagueOldLevel", stored.id);
            updates.put("pendingLeagueNewLevel", expected.id);
            updates.put("pendingLeagueDirection", direction);
            updates.put("pendingLeagueMessage", message);
            transaction.set(userRef, updates, SetOptions.merge());

            String notificationId = "league_consistency_" + stored.id + "_" + expected.id + "_" + stars;
            DocumentReference notificationRef = userRef.collection("notifications").document(notificationId);
            transaction.set(notificationRef, leagueNotification(notificationId, title, message, expected.id), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> clearPendingLeagueDialog(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forResult(null);
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("pendingLeagueDialog", false);
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
        updates.put("activeGameId", currentGameId == null ? "" : currentGameId);
        updates.put("lastActiveAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
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
        updates.put("activeGameId", null);
        updates.put("lastActiveAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return db.collection("users").document(uid).set(updates, SetOptions.merge());
    }

    private void putIfMissing(Map<String, Object> updates, DocumentSnapshot doc, String key, Object value) {
        if (!doc.exists() || !doc.contains(key) || doc.get(key) == null) {
            updates.put(key, value);
        }
    }

    private LeagueDefinition leagueFromUser(DocumentSnapshot user, long stars) {
        Object league = user.get("league");
        if (league instanceof Number) {
            return LeagueDefinition.byId(((Number) league).longValue());
        }
        return LeagueDefinition.forStars(stars);
    }

    private LeagueDefinition leagueFromUserField(DocumentSnapshot user) {
        Object league = user.get("league");
        if (league instanceof Number) {
            return LeagueDefinition.byId(((Number) league).longValue());
        }
        if (league instanceof String) {
            String value = ((String) league).trim().toLowerCase(Locale.US);
            if (value.contains("bronz")) return LeagueDefinition.byId(1);
            if (value.contains("srebr")) return LeagueDefinition.byId(2);
            if (value.contains("zlat")) return LeagueDefinition.byId(3);
            if (value.contains("dijamant")) return LeagueDefinition.byId(4);
            if (value.contains("sampion") || value.contains("champion")) return LeagueDefinition.byId(5);
        }
        return LeagueDefinition.byId(0);
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private Map<String, Object> dailyTokenNotification(String notificationId, int tokens, String leagueName) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notificationId);
        data.put("id", notificationId);
        data.put("type", "REWARD");
        data.put("title", "Dnevni tokeni");
        data.put("message", "Dobili ste " + tokens + " tokena za ligu " + leagueName + ".");
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("read", false);
        data.put("actionType", "REWARD");
        data.put("actionTargetId", "daily_tokens");
        data.put("senderUid", "");
        data.put("senderName", "");
        data.put("targetScreen", "REWARD");
        return data;
    }

    private Map<String, Object> leagueNotification(String notificationId, String title, String message, int newLeague) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notificationId);
        data.put("id", notificationId);
        data.put("type", "LEAGUE");
        data.put("title", title);
        data.put("message", message);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("read", false);
        data.put("actionType", "LEAGUE");
        data.put("actionTargetId", String.valueOf(newLeague));
        data.put("senderUid", "");
        data.put("senderName", "");
        data.put("targetScreen", "LEAGUE");
        return data;
    }

    private String defaultUsername(String email, String uid) {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return uid == null || uid.length() < 6 ? "igrac" : "igrac_" + uid.substring(0, 6);
    }
}
