package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rs.ac.uns.ftn.slagalica.domain.model.ChallengeItem;
import rs.ac.uns.ftn.slagalica.domain.model.ChallengeParticipant;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;

public class ChallengeRepository {
    private static final String TAG = "ChallengeRepository";
    public static final String WAITING = "WAITING";
    public static final String ACTIVE = "ACTIVE";
    public static final String FINISHED = "FINISHED";
    public static final String CANCELLED = "CANCELLED";
    private final FirebaseFirestore db;
    private final NotificationRepository notificationRepository;

    public ChallengeRepository(Context context) {
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

    public Task<QuerySnapshot> listOpenChallenges() {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.collection("challenges")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .get();
    }

    public Task<DocumentSnapshot> getChallenge(String challengeId) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.collection("challenges").document(challengeId).get();
    }

    public Task<QuerySnapshot> getParticipants(String challengeId) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.collection("challenges").document(challengeId).collection("participants")
                .orderBy("placement", Query.Direction.ASCENDING)
                .get();
    }

    public Task<String> createChallenge(String uid, long stakeStars, long stakeTokens) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        if (stakeStars < 0 || stakeStars > 10 || stakeTokens < 0 || stakeTokens > 2) {
            return Tasks.forException(new IllegalArgumentException("Ulog je van dozvoljenih granica"));
        }
        return db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot user = transaction.get(userRef);
            assertStake(user, stakeStars, stakeTokens);
            DocumentReference challengeRef = db.collection("challenges").document();
            String challengeId = challengeRef.getId();
            String username = username(user);
            transaction.set(userRef, mapOf(
                    "stars", longValue(user.get("stars")) - stakeStars,
                    "tokens", longValue(user.get("tokens")) - stakeTokens
            ), SetOptions.merge());
            transaction.set(challengeRef, mapOf(
                    "challengeId", challengeId,
                    "creatorUid", uid,
                    "creatorUsername", username,
                    "stakeStars", stakeStars,
                    "stakeTokens", stakeTokens,
                    "status", WAITING,
                    "maxPlayers", 4,
                    "currentPlayers", 1,
                    "createdAt", FieldValue.serverTimestamp(),
                    "startedAt", null,
                    "finishedAt", null,
                    "winnerUid", "",
                    "secondPlaceUid", "",
                    "challengePoolStars", stakeStars,
                    "challengePoolTokens", stakeTokens
            ));
            transaction.set(challengeRef.collection("participants").document(uid), participantData(uid, username));
            return challengeId;
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            notifyRegionPlayers(uid, task.getResult());
            return Tasks.forResult(task.getResult());
        });
    }

    public Task<Void> joinChallenge(String challengeId, String uid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference challengeRef = db.collection("challenges").document(challengeId);
            DocumentSnapshot challenge = transaction.get(challengeRef);
            if (!challenge.exists() || !WAITING.equals(challenge.getString("status"))) {
                throw new IllegalStateException("Izazov nije otvoren");
            }
            if (uid.equals(challenge.getString("creatorUid"))) throw new IllegalArgumentException("Vec ste u izazovu");
            long currentPlayers = longValue(challenge.get("currentPlayers"));
            if (currentPlayers >= 4) throw new IllegalStateException("Izazov je popunjen");
            DocumentReference participantRef = challengeRef.collection("participants").document(uid);
            if (transaction.get(participantRef).exists()) throw new IllegalArgumentException("Vec ste se pridruzili");
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot user = transaction.get(userRef);
            long stakeStars = longValue(challenge.get("stakeStars"));
            long stakeTokens = longValue(challenge.get("stakeTokens"));
            assertStake(user, stakeStars, stakeTokens);
            transaction.set(userRef, mapOf(
                    "stars", longValue(user.get("stars")) - stakeStars,
                    "tokens", longValue(user.get("tokens")) - stakeTokens
            ), SetOptions.merge());
            transaction.set(participantRef, participantData(uid, username(user)));
            transaction.set(challengeRef, mapOf(
                    "currentPlayers", FieldValue.increment(1),
                    "challengePoolStars", FieldValue.increment(stakeStars),
                    "challengePoolTokens", FieldValue.increment(stakeTokens)
            ), SetOptions.merge());
            return currentPlayers + 1;
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            if (task.getResult() >= 4) return startChallenge(challengeId, uid, true);
            return Tasks.forResult(null);
        });
    }

    public Task<Void> startChallenge(String challengeId, String uid, boolean auto) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference challengeRef = db.collection("challenges").document(challengeId);
            DocumentSnapshot challenge = transaction.get(challengeRef);
            if (!challenge.exists() || !WAITING.equals(challenge.getString("status"))) return null;
            if (!auto && !uid.equals(challenge.getString("creatorUid"))) {
                throw new IllegalArgumentException("Samo kreator moze pokrenuti izazov");
            }
            if (longValue(challenge.get("currentPlayers")) < 2) {
                throw new IllegalStateException("Potrebna su najmanje 2 igraca");
            }
            transaction.set(challengeRef, mapOf("status", ACTIVE, "startedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            notifyParticipants(challengeId, "CHALLENGE_START", "Izazov je pokrenut", "Izazov je spreman za igru.");
            return Tasks.forResult(null);
        });
    }

    public Task<Void> submitRun(String challengeId, String uid, Map<String, Integer> scores) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        for (String miniGame : GameRepository.FULL_MATCH_ORDER) {
            if (!scores.containsKey(miniGame)) {
                return Tasks.forException(new IllegalArgumentException("Svaka igra mora biti odigrana jednom"));
            }
        }
        long total = 0;
        for (Integer score : scores.values()) total += Math.max(0, score == null ? 0 : score);
        long finalTotal = total;
        return db.runTransaction(transaction -> {
            DocumentReference challengeRef = db.collection("challenges").document(challengeId);
            DocumentSnapshot challenge = transaction.get(challengeRef);
            if (!challenge.exists() || !ACTIVE.equals(challenge.getString("status"))) {
                throw new IllegalStateException("Izazov nije aktivan");
            }
            DocumentReference participantRef = challengeRef.collection("participants").document(uid);
            DocumentSnapshot participant = transaction.get(participantRef);
            if (!participant.exists()) throw new IllegalArgumentException("Niste ucesnik");
            if (Boolean.TRUE.equals(participant.getBoolean("finished"))) throw new IllegalStateException("Vec ste zavrsili izazov");
            transaction.set(participantRef, mapOf(
                    "finished", true,
                    "totalScore", finalTotal,
                    "scores", scores
            ), SetOptions.merge());
            return null;
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return finishIfReady(challengeId);
        });
    }

    public Task<Void> finishIfReady(String challengeId) {
        return db.collection("challenges").document(challengeId).collection("participants").get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        if (!Boolean.TRUE.equals(doc.getBoolean("finished"))) return Tasks.forResult(null);
                    }
                    return finishChallenge(challengeId, task.getResult().getDocuments());
                });
    }

    private Task<Void> finishChallenge(String challengeId, List<DocumentSnapshot> docs) {
        return db.runTransaction(transaction -> {
            DocumentReference challengeRef = db.collection("challenges").document(challengeId);
            DocumentSnapshot challenge = transaction.get(challengeRef);
            if (!challenge.exists() || FINISHED.equals(challenge.getString("status"))) return null;
            List<DocumentSnapshot> sorted = new ArrayList<>(docs);
            sorted.sort((a, b) -> Long.compare(longValue(b.get("totalScore")), longValue(a.get("totalScore"))));
            long poolStars = longValue(challenge.get("challengePoolStars"));
            long poolTokens = longValue(challenge.get("challengePoolTokens"));
            long stakeStars = longValue(challenge.get("stakeStars"));
            long stakeTokens = longValue(challenge.get("stakeTokens"));
            for (int i = 0; i < sorted.size(); i++) {
                DocumentSnapshot p = sorted.get(i);
                long rewardStars = i == 0 ? (long) Math.floor(poolStars * 0.75d) : i == 1 ? stakeStars : 0;
                long rewardTokens = i == 0 ? (long) Math.floor(poolTokens * 0.75d) : i == 1 ? stakeTokens : 0;
                transaction.set(p.getReference(), mapOf(
                        "placement", i + 1,
                        "rewardStars", rewardStars,
                        "rewardTokens", rewardTokens
                ), SetOptions.merge());
                if (rewardStars > 0 || rewardTokens > 0) {
                    transaction.set(db.collection("users").document(p.getId()), mapOf(
                            "stars", FieldValue.increment(rewardStars),
                            "tokens", FieldValue.increment(rewardTokens)
                    ), SetOptions.merge());
                }
            }
            transaction.set(challengeRef, mapOf(
                    "status", FINISHED,
                    "finishedAt", FieldValue.serverTimestamp(),
                    "winnerUid", sorted.isEmpty() ? "" : sorted.get(0).getId(),
                    "secondPlaceUid", sorted.size() < 2 ? "" : sorted.get(1).getId()
            ), SetOptions.merge());
            return null;
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            notifyParticipants(challengeId, "CHALLENGE_RESULT", "Izazov je zavrsen", "Rezultati izazova su spremni.");
            return Tasks.forResult(null);
        });
    }

    private void notifyRegionPlayers(String creatorUid, String challengeId) {
        db.collection("users").document(creatorUid).get().addOnSuccessListener(creator -> {
            String regionId = firstNonEmpty(creator.getString("regionId"), creator.getString("region"));
            if (regionId.isEmpty()) return;
            notifyUsersByRegionField(creator, creatorUid, challengeId, regionId, "regionId", new HashSet<>())
                    .addOnSuccessListener(notified ->
                            notifyUsersByRegionField(creator, creatorUid, challengeId, regionId, "region", notified));
        });
    }

    private Task<Set<String>> notifyUsersByRegionField(DocumentSnapshot creator, String creatorUid, String challengeId,
                                                       String regionId, String field, Set<String> alreadyNotified) {
        return db.collection("users").whereEqualTo(field, regionId).get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            WriteBatch batch = db.batch();
            for (DocumentSnapshot user : task.getResult().getDocuments()) {
                if (user.getId().equals(creatorUid) || alreadyNotified.contains(user.getId())) continue;
                DocumentReference ref = user.getReference().collection("notifications").document();
                batch.set(ref, notificationRepository.notificationData(ref.getId(), "CHALLENGE", "Novi izazov",
                        username(creator) + " je postavio izazov.", "CHALLENGE", challengeId, creatorUid, username(creator)));
                alreadyNotified.add(user.getId());
            }
            return batch.commit().continueWith(unused -> alreadyNotified);
        });
    }

    private void notifyParticipants(String challengeId, String type, String title, String message) {
        db.collection("challenges").document(challengeId).collection("participants").get().addOnSuccessListener(parts -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot p : parts.getDocuments()) {
                DocumentReference ref = db.collection("users").document(p.getId()).collection("notifications").document();
                batch.set(ref, notificationRepository.notificationData(ref.getId(), type, title, message, type, challengeId, "", ""));
            }
            batch.commit();
        });
    }

    public ChallengeItem itemFrom(DocumentSnapshot doc) {
        ChallengeItem item = new ChallengeItem();
        item.challengeId = doc.getId();
        item.creatorUid = doc.getString("creatorUid");
        item.creatorUsername = doc.getString("creatorUsername");
        item.stakeStars = longValue(doc.get("stakeStars"));
        item.stakeTokens = longValue(doc.get("stakeTokens"));
        item.status = doc.getString("status");
        item.maxPlayers = longValue(doc.get("maxPlayers"));
        item.currentPlayers = longValue(doc.get("currentPlayers"));
        return item;
    }

    public ChallengeParticipant participantFrom(DocumentSnapshot doc) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.uid = doc.getId();
        p.username = doc.getString("username");
        p.finished = Boolean.TRUE.equals(doc.getBoolean("finished"));
        p.totalScore = longValue(doc.get("totalScore"));
        p.placement = longValue(doc.get("placement"));
        p.rewardStars = longValue(doc.get("rewardStars"));
        p.rewardTokens = longValue(doc.get("rewardTokens"));
        return p;
    }

    private Map<String, Object> participantData(String uid, String username) {
        return mapOf("uid", uid, "username", username, "joinedAt", FieldValue.serverTimestamp(),
                "finished", false, "totalScore", 0, "placement", 0, "rewardStars", 0, "rewardTokens", 0);
    }

    private void assertStake(DocumentSnapshot user, long stars, long tokens) {
        if (!user.exists()) throw new IllegalArgumentException("Korisnik nije pronadjen");
        if (longValue(user.get("stars")) < stars || longValue(user.get("tokens")) < tokens) {
            throw new IllegalArgumentException("Nemate dovoljno zvezda ili tokena");
        }
    }

    private String username(DocumentSnapshot user) {
        return firstNonEmpty(user.getString("username"), user.getString("email"), user.getId());
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }
}
