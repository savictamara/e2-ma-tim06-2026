package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class FriendRepository {
    private static final String TAG = "FriendRepository";
    private static final String DEBUG_TAG = "FriendlyInviteDebug";
    private final FirebaseFirestore db;
    private final NotificationRepository notificationRepository;

    public FriendRepository(Context context) {
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firestore nije dostupan", e);
        }
        db = instance;
        notificationRepository = new NotificationRepository(context);
    }

    public boolean isReady() {
        return db != null;
    }

    public Task<QuerySnapshot> searchByUsername(String username) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        String clean = username == null ? "" : username.trim();
        if (clean.isEmpty()) return Tasks.forException(new IllegalArgumentException("Unesite korisnicko ime"));
        return db.collection("users")
                .whereEqualTo("usernameLowercase", clean.toLowerCase(Locale.US))
                .limit(10)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    if (!task.getResult().isEmpty()) return Tasks.forResult(task.getResult());
                    return db.collection("users").whereEqualTo("username", clean).limit(10).get();
                });
    }

    public Task<QuerySnapshot> getFriends(String uid) {
        if (db == null || isBlank(uid)) return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        return db.collection("users").document(uid).collection("friends").get();
    }

    public Task<DocumentSnapshot> getUser(String uid) {
        if (db == null || isBlank(uid)) return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        return db.collection("users").document(uid).get();
    }

    public ListenerRegistration listenIncomingFriendRequests(String uid, EventListener<QuerySnapshot> listener) {
        if (db == null || isBlank(uid)) return null;
        return db.collection("friendRequests")
                .whereEqualTo("toUid", uid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenIncomingMatchInvites(String uid, EventListener<QuerySnapshot> listener) {
        if (db == null || isBlank(uid)) return null;
        return db.collection("friendlyMatchInvites")
                .whereEqualTo("toUid", uid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenInvite(String inviteId, EventListener<DocumentSnapshot> listener) {
        if (db == null || isBlank(inviteId)) return null;
        return db.collection("friendlyMatchInvites").document(inviteId).addSnapshotListener(listener);
    }

    public Task<String> sendFriendRequest(String fromUid, String toUid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        if (isBlank(fromUid) || isBlank(toUid) || fromUid.equals(toUid)) {
            return Tasks.forException(new IllegalArgumentException("Ne mozete dodati sebe"));
        }
        return db.runTransaction(transaction -> {
            DocumentReference fromRef = db.collection("users").document(fromUid);
            DocumentReference toRef = db.collection("users").document(toUid);
            DocumentSnapshot from = transaction.get(fromRef);
            DocumentSnapshot to = transaction.get(toRef);
            if (!from.exists() || !to.exists()) throw new IllegalArgumentException("Korisnik nije pronadjen");
            DocumentReference friendRef = fromRef.collection("friends").document(toUid);
            if (transaction.get(friendRef).exists()) throw new IllegalArgumentException("Vec ste prijatelji");
            String requestId = requestId(fromUid, toUid);
            DocumentReference requestRef = db.collection("friendRequests").document(requestId);
            DocumentSnapshot existing = transaction.get(requestRef);
            if (existing.exists() && "PENDING".equals(existing.getString("status"))) {
                throw new IllegalArgumentException("Zahtev je vec poslat");
            }
            String fromUsername = username(from);
            String toUsername = username(to);
            transaction.set(requestRef, mapOf(
                    "requestId", requestId,
                    "fromUid", fromUid,
                    "fromUsername", fromUsername,
                    "toUid", toUid,
                    "toUsername", toUsername,
                    "status", "PENDING",
                    "createdAt", FieldValue.serverTimestamp(),
                    "respondedAt", null
            ), SetOptions.merge());
            DocumentReference notificationRef = toRef.collection("notifications").document();
            transaction.set(notificationRef, notificationRepository.notificationData(notificationRef.getId(),
                    "FRIEND_INVITE",
                    "Novi zahtev za prijateljstvo",
                    fromUsername + " zeli da vas doda za prijatelja.",
                    "FRIEND_INVITE",
                    requestId,
                    fromUid,
                    fromUsername), SetOptions.merge());
            return requestId;
        });
    }

    public Task<Void> respondFriendRequest(String requestId, boolean accept) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference requestRef = db.collection("friendRequests").document(requestId);
            DocumentSnapshot request = transaction.get(requestRef);
            if (!request.exists() || !"PENDING".equals(request.getString("status"))) return null;
            String fromUid = request.getString("fromUid");
            String toUid = request.getString("toUid");
            DocumentSnapshot from = transaction.get(db.collection("users").document(fromUid));
            DocumentSnapshot to = transaction.get(db.collection("users").document(toUid));
            if (accept) {
                transaction.set(db.collection("users").document(fromUid).collection("friends").document(toUid), friendData(to), SetOptions.merge());
                transaction.set(db.collection("users").document(toUid).collection("friends").document(fromUid), friendData(from), SetOptions.merge());
            }
            transaction.set(requestRef, mapOf("status", accept ? "ACCEPTED" : "DECLINED",
                    "respondedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<String> sendFriendlyInvite(String fromUid, String toUid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        if (isBlank(fromUid) || isBlank(toUid) || fromUid.equals(toUid)) {
            return Tasks.forException(new IllegalArgumentException("Ne mozete izazvati sebe"));
        }
        return findPendingInvite(fromUid, toUid).continueWithTask(existingTask -> {
            if (!existingTask.isSuccessful()) {
                throw existingTask.getException();
            }
            if (!isBlank(existingTask.getResult())) {
                Log.d(DEBUG_TAG, "duplicate invite found inviteId=" + existingTask.getResult()
                        + ", fromUid=" + fromUid + ", toUid=" + toUid);
                throw new IllegalArgumentException("Poziv je vec poslat");
            }
            return createFriendlyInvite(fromUid, toUid);
        });
    }

    private Task<String> createFriendlyInvite(String fromUid, String toUid) {
        return db.runTransaction(transaction -> {
            DocumentSnapshot from = transaction.get(db.collection("users").document(fromUid));
            DocumentSnapshot to = transaction.get(db.collection("users").document(toUid));
            if (!available(to)) throw new IllegalArgumentException(unavailableReason(to));
            String inviteId = db.collection("friendlyMatchInvites").document().getId();
            DocumentReference inviteRef = db.collection("friendlyMatchInvites").document(inviteId);
            Timestamp expiresAt = new Timestamp(new java.util.Date(System.currentTimeMillis() + 10_000L));
            transaction.set(inviteRef, mapOf(
                    "inviteId", inviteId,
                    "fromUid", fromUid,
                    "fromUsername", username(from),
                    "toUid", toUid,
                    "toUsername", username(to),
                    "status", "PENDING",
                    "gameId", "",
                    "matchType", "FRIENDLY",
                    "createdAt", FieldValue.serverTimestamp(),
                    "expiresAt", expiresAt,
                    "respondedAt", null
            ));
            DocumentReference notificationRef = db.collection("users").document(toUid).collection("notifications").document();
            Map<String, Object> notification = notificationRepository.notificationData(notificationRef.getId(),
                    "FRIENDLY_MATCH_INVITE",
                    "Poziv za prijateljsku partiju",
                    username(from) + " te je pozvao/la na partiju",
                    "FRIENDLY_MATCH_INVITE",
                    inviteId,
                    fromUid,
                    username(from));
            notification.put("inviteId", inviteId);
            notification.put("fromUid", fromUid);
            notification.put("body", username(from) + " te je pozvao/la na partiju");
            transaction.set(notificationRef, notification, SetOptions.merge());
            Log.d(DEBUG_TAG, "invite created inviteId=" + inviteId
                    + ", fromUid=" + fromUid + ", toUid=" + toUid);
            Log.d(DEBUG_TAG, "notification created notificationId=" + notificationRef.getId()
                    + ", toUid=" + toUid + ", inviteId=" + inviteId);
            return inviteId;
        });
    }

    private Task<String> findPendingInvite(String uidA, String uidB) {
        return db.collection("friendlyMatchInvites")
                .whereEqualTo("fromUid", uidA)
                .whereEqualTo("toUid", uidB)
                .whereEqualTo("status", "PENDING")
                .limit(1)
                .get()
                .continueWithTask(first -> {
                    if (!first.isSuccessful()) throw first.getException();
                    if (!first.getResult().isEmpty()) {
                        return Tasks.forResult(first.getResult().getDocuments().get(0).getId());
                    }
                    return db.collection("friendlyMatchInvites")
                            .whereEqualTo("fromUid", uidB)
                            .whereEqualTo("toUid", uidA)
                            .whereEqualTo("status", "PENDING")
                            .limit(1)
                            .get()
                            .continueWith(second -> {
                                if (!second.isSuccessful()) throw second.getException();
                                return second.getResult().isEmpty()
                                        ? ""
                                        : second.getResult().getDocuments().get(0).getId();
                            });
                });
    }

    public Task<Void> cancelInvite(String inviteId) {
        return setInviteStatus(inviteId, "CANCELLED");
    }

    public Task<Void> declineInvite(String inviteId) {
        return setInviteStatus(inviteId, "DECLINED");
    }

    public Task<Void> expireInvite(String inviteId) {
        return db.runTransaction(transaction -> {
            DocumentReference ref = db.collection("friendlyMatchInvites").document(inviteId);
            DocumentSnapshot invite = transaction.get(ref);
            if (invite.exists() && "PENDING".equals(invite.getString("status"))) {
                transaction.set(ref, mapOf("status", "EXPIRED", "respondedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            }
            return null;
        });
    }

    private Task<Void> setInviteStatus(String inviteId, String status) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference ref = db.collection("friendlyMatchInvites").document(inviteId);
            DocumentSnapshot invite = transaction.get(ref);
            if (invite.exists() && "PENDING".equals(invite.getString("status"))) {
                transaction.set(ref, mapOf("status", status, "respondedAt", FieldValue.serverTimestamp(),
                        status.toLowerCase(Locale.US) + "At", FieldValue.serverTimestamp()), SetOptions.merge());
                Log.d(DEBUG_TAG, "invite " + status.toLowerCase(Locale.US) + " inviteId=" + inviteId);
            }
            return null;
        });
    }

    public Task<String> acceptInvite(String inviteId) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference inviteRef = db.collection("friendlyMatchInvites").document(inviteId);
            DocumentSnapshot invite = transaction.get(inviteRef);
            if (!invite.exists() || !"PENDING".equals(invite.getString("status"))) {
                throw new IllegalStateException("Poziv vise nije aktivan");
            }
            Timestamp expiresAt = invite.getTimestamp("expiresAt");
            if (expiresAt != null && expiresAt.toDate().getTime() < System.currentTimeMillis()) {
                transaction.set(inviteRef, mapOf("status", "EXPIRED", "respondedAt", FieldValue.serverTimestamp()), SetOptions.merge());
                throw new IllegalStateException("Poziv je istekao");
            }
            String fromUid = invite.getString("fromUid");
            String toUid = invite.getString("toUid");
            DocumentReference fromRef = db.collection("users").document(fromUid);
            DocumentReference toRef = db.collection("users").document(toUid);
            DocumentSnapshot from = transaction.get(fromRef);
            DocumentSnapshot to = transaction.get(toRef);
            if (!available(from) || !available(to)) {
                throw new IllegalStateException("Igrac nije dostupan");
            }
            DocumentReference gameRef = db.collection("games").document();
            Map<String, Object> game = new HashMap<>();
            game.put("player1Uid", fromUid);
            game.put("player2Uid", toUid);
            game.put("currentPlayerUid", fromUid);
            game.put("status", "active");
            game.put("currentMiniGame", GameRepository.FULL_MATCH_ORDER[0]);
            game.put("fullMatch", true);
            game.put("friendly", true);
            game.put("matchType", "FRIENDLY");
            game.put("matchIndex", 0);
            game.put("player1Score", 0);
            game.put("player2Score", 0);
            game.put("createdAt", FieldValue.serverTimestamp());
            game.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(gameRef, game);
            transaction.set(inviteRef, mapOf("status", "ACCEPTED", "gameId", gameRef.getId(),
                    "acceptedAt", FieldValue.serverTimestamp(),
                    "respondedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            transaction.set(fromRef, gameState(gameRef.getId()), SetOptions.merge());
            transaction.set(toRef, gameState(gameRef.getId()), SetOptions.merge());
            Log.d(DEBUG_TAG, "invite accepted inviteId=" + inviteId
                    + ", fromUid=" + fromUid + ", toUid=" + toUid);
            Log.d(DEBUG_TAG, "game created gameId=" + gameRef.getId() + ", matchType=FRIENDLY");
            Log.d(DEBUG_TAG, "both users activeGameId set gameId=" + gameRef.getId());
            return gameRef.getId();
        });
    }

    private boolean available(DocumentSnapshot user) {
        if (user == null || !user.exists()) return false;
        String active = firstNonEmpty(user.getString("activeGameId"), user.getString("currentGameId"));
        return !Boolean.TRUE.equals(user.getBoolean("inGame"))
                && isBlank(active);
    }

    private String unavailableReason(DocumentSnapshot user) {
        if (user == null || !user.exists()) return "Korisnik nije pronadjen";
        return "U partiji";
    }

    private Map<String, Object> gameState(String gameId) {
        return mapOf("online", true, "inGame", true, "currentGameId", gameId,
                "activeGameId", gameId, "lastActiveAt", FieldValue.serverTimestamp());
    }

    private Map<String, Object> friendData(DocumentSnapshot user) {
        return mapOf("uid", user.getId(),
                "username", username(user),
                "photoUrl", firstNonEmpty(user.getString("photoUrl"), user.getString("avatarUrl"), user.getString("profileImageUrl")),
                "stars", longValue(user.get("stars")),
                "league", longValue(user.get("league")),
                "leagueName", firstNonEmpty(user.getString("leagueName"), ""),
                "leagueIconName", firstNonEmpty(user.getString("leagueIconName"), user.getString("leagueIcon")),
                "monthlyRank", longValue(user.get("monthlyRank")),
                "createdAt", FieldValue.serverTimestamp());
    }

    private String requestId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    private String username(DocumentSnapshot user) {
        return firstNonEmpty(user.getString("username"), user.getString("email"), user.getId());
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (!isBlank(value)) return value.trim();
        return "";
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
