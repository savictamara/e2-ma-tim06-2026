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
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class TournamentRepository {
    private static final String TAG = "TournamentPersistenceDebug";
    public static final String WAITING = "WAITING";
    public static final String SEMIFINALS = "SEMIFINALS";
    public static final String FINAL = "FINAL";
    public static final String FINISHED = "FINISHED";
    public static final String CANCELLED = "CANCELLED";
    public static final String READY = "READY";
    public static final String PLAYING = "PLAYING";
    public static final long ENTRY_COST = 3;

    private final FirebaseFirestore db;

    public TournamentRepository(Context context) {
        FirebaseFirestore instance = null;
        if (FirebaseInitializer.ensure(context)) {
            instance = FirebaseFirestore.getInstance();
        }
        db = instance;
    }

    public boolean isReady() {
        return db != null;
    }

    public Task<DocumentSnapshot> findActiveTournamentForUser(String uid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.collection("tournaments")
                .whereArrayContains("playerIds", uid)
                .limit(20)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    DocumentSnapshot best = null;
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        String status = doc.getString("status");
                        if (WAITING.equals(status) || SEMIFINALS.equals(status) || FINAL.equals(status)) {
                            if (best == null || createdAtMillis(doc) > createdAtMillis(best)) best = doc;
                        }
                    }
                    Log.d(TAG, "active tournament " + (best == null ? "not found" : "found=" + best.getId())
                            + ", uid=" + uid);
                    return best;
                });
    }

    public Task<DocumentSnapshot> findJoinableTournament() {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.collection("tournaments")
                .whereEqualTo("status", WAITING)
                .limit(20)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    DocumentSnapshot best = null;
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        if (longValue(doc.get("currentPlayers")) < 4 && stringList(doc.get("playerIds")).size() < 4) {
                            if (best == null || createdAtMillis(doc) > createdAtMillis(best)) best = doc;
                        }
                    }
                    Log.d(TAG, "joinable tournament " + (best == null ? "not found" : "found=" + best.getId()));
                    return best;
                });
    }

    public Task<String> joinTournament(String uid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        Log.d(TAG, "join clicked uid=" + uid);
        return findActiveTournamentForUser(uid).continueWithTask(activeTask -> {
            if (!activeTask.isSuccessful()) throw activeTask.getException();
            DocumentSnapshot active = activeTask.getResult();
            if (active != null && active.exists()) {
                Log.d(TAG, "already joined true tournamentId=" + active.getId() + ", token deducted=false");
                return Tasks.forResult(active.getId());
            }
            return findJoinableTournament().continueWithTask(waitingTask -> {
                if (!waitingTask.isSuccessful()) throw waitingTask.getException();
                DocumentSnapshot target = waitingTask.getResult();
                DocumentReference tournamentRef = target == null
                        ? db.collection("tournaments").document()
                        : target.getReference();
                return db.runTransaction(transaction -> joinTransaction(transaction, tournamentRef, uid));
            });
        });
    }

    private String joinTransaction(Transaction transaction, DocumentReference tournamentRef, String uid)
            throws FirebaseFirestoreException {
        DocumentSnapshot tournament = transaction.get(tournamentRef);
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentSnapshot user = transaction.get(userRef);
        List<String> players = tournament.exists() ? stringList(tournament.get("playerIds")) : new ArrayList<>();
        if (players.contains(uid)) {
            Log.d(TAG, "already joined true tournamentId=" + tournamentRef.getId() + ", token deducted=false");
            return tournamentRef.getId();
        }
        if (tournament.exists() && !WAITING.equals(tournament.getString("status"))) {
            throw new IllegalStateException("Turnir je vec poceo.");
        }
        if (players.size() >= 4 || longValue(tournament.get("currentPlayers")) >= 4) {
            throw new IllegalStateException("Turnir je popunjen.");
        }
        long tokens = longValue(user.get("tokens"));
        if (tokens < ENTRY_COST) throw new IllegalStateException("Nemate dovoljno tokena");
        players.add(uid);
        transaction.set(userRef, mapOf("tokens", tokens - ENTRY_COST), SetOptions.merge());
        Map<String, Object> updates = mapOf(
                "tournamentId", tournamentRef.getId(),
                "status", WAITING,
                "entryCostTokens", ENTRY_COST,
                "playerIds", players,
                "currentPlayers", players.size(),
                "updatedAt", FieldValue.serverTimestamp()
        );
        if (!tournament.exists()) {
            updates.putAll(mapOf(
                    "createdAt", FieldValue.serverTimestamp(),
                    "startedAt", null,
                    "finishedAt", null,
                    "sf1Status", WAITING,
                    "sf2Status", WAITING,
                    "finalStatus", WAITING,
                    "sf1Paid", false,
                    "sf2Paid", false,
                    "finalPaid", false
            ));
        }
        transaction.set(tournamentRef, updates, SetOptions.merge());
        if (players.size() == 4) startSemifinals(transaction, tournamentRef, players);
        Log.d(TAG, "transaction result tournamentId=" + tournamentRef.getId()
                + ", already joined=false, token deducted=true, currentPlayers=" + players.size());
        return tournamentRef.getId();
    }

    private void startSemifinals(Transaction transaction, DocumentReference tournamentRef, List<String> players) {
        List<String> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        DocumentReference sf1 = createTournamentGame(transaction, tournamentRef.getId(), "SF1", shuffled.get(0), shuffled.get(1));
        DocumentReference sf2 = createTournamentGame(transaction, tournamentRef.getId(), "SF2", shuffled.get(2), shuffled.get(3));
        transaction.set(tournamentRef, mapOf(
                "status", SEMIFINALS,
                "startedAt", FieldValue.serverTimestamp(),
                "sf1Player1Uid", shuffled.get(0),
                "sf1Player2Uid", shuffled.get(1),
                "sf2Player1Uid", shuffled.get(2),
                "sf2Player2Uid", shuffled.get(3),
                "sf1MatchId", sf1.getId(),
                "sf2MatchId", sf2.getId(),
                "sf1Status", READY,
                "sf2Status", READY,
                "finalStatus", WAITING,
                "sf1WinnerUid", "",
                "sf1LoserUid", "",
                "sf2WinnerUid", "",
                "sf2LoserUid", "",
                "finalWinnerUid", "",
                "finalLoserUid", "",
                "sf1Paid", false,
                "sf2Paid", false,
                "finalPaid", false
        ), SetOptions.merge());
        Log.d(TAG, "semifinals generated tournamentId=" + tournamentRef.getId());
    }

    public Task<Void> markMatchPlaying(String tournamentId, String round) {
        if (db == null || isBlank(tournamentId)) return Tasks.forResult(null);
        String field = statusField(round);
        if (isBlank(field)) return Tasks.forResult(null);
        return db.collection("tournaments").document(tournamentId)
                .set(mapOf(field, PLAYING, "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
    }

    public Task<Void> completeTournamentMatch(String tournamentId, String round, String winnerUid,
                                              String loserUid, long winnerScore, long loserScore) {
        if (db == null || isBlank(tournamentId) || isBlank(round) || isBlank(winnerUid)) {
            return Tasks.forResult(null);
        }
        return db.runTransaction(transaction -> {
            DocumentReference ref = db.collection("tournaments").document(tournamentId);
            DocumentSnapshot tournament = transaction.get(ref);
            if (!tournament.exists() || FINISHED.equals(tournament.getString("status"))) return null;
            if ("SF1".equals(round) || "SF2".equals(round)) {
                completeSemifinal(transaction, ref, tournament, round, winnerUid, loserUid, winnerScore);
            } else if ("FINAL".equals(round)) {
                completeFinal(transaction, ref, tournament, winnerUid, loserUid, winnerScore, loserScore);
            }
            return null;
        });
    }

    private void completeSemifinal(Transaction transaction, DocumentReference ref, DocumentSnapshot tournament,
                                   String round, String winnerUid, String loserUid, long winnerScore) {
        String statusField = statusField(round);
        String paidField = "SF1".equals(round) ? "sf1Paid" : "sf2Paid";
        if (FINISHED.equals(tournament.getString(statusField))) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put(statusField, FINISHED);
        updates.put("SF1".equals(round) ? "sf1WinnerUid" : "sf2WinnerUid", winnerUid);
        updates.put("SF1".equals(round) ? "sf1LoserUid" : "sf2LoserUid", loserUid);
        if (!Boolean.TRUE.equals(tournament.getBoolean(paidField))) {
            applyRegularStars(transaction, winnerUid, winnerScore, true, false);
            transaction.set(db.collection("users").document(winnerUid),
                    mapOf("tokens", FieldValue.increment(2)), SetOptions.merge());
            updates.put(paidField, true);
        }
        String otherWinner = "SF1".equals(round) ? tournament.getString("sf2WinnerUid") : tournament.getString("sf1WinnerUid");
        if (!isBlank(otherWinner)) {
            updates.put("status", FINAL);
            updates.put("finalPlayer1Uid", "SF1".equals(round) ? winnerUid : otherWinner);
            updates.put("finalPlayer2Uid", "SF1".equals(round) ? otherWinner : winnerUid);
            DocumentReference finalGame = createTournamentGame(transaction, ref.getId(), "FINAL",
                    String.valueOf(updates.get("finalPlayer1Uid")), String.valueOf(updates.get("finalPlayer2Uid")));
            updates.put("finalMatchId", finalGame.getId());
            updates.put("finalStatus", READY);
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(ref, updates, SetOptions.merge());
        Log.d(TAG, "complete semifinal round=" + round + ", winner=" + winnerUid + ", loser=" + loserUid);
    }

    private void completeFinal(Transaction transaction, DocumentReference ref, DocumentSnapshot tournament,
                               String winnerUid, String loserUid, long winnerScore, long loserScore) {
        if (FINISHED.equals(tournament.getString("finalStatus"))) return;
        Map<String, Object> updates = mapOf(
                "status", FINISHED,
                "finalStatus", FINISHED,
                "finalWinnerUid", winnerUid,
                "finalLoserUid", loserUid,
                "winnerUid", winnerUid,
                "finishedAt", FieldValue.serverTimestamp(),
                "updatedAt", FieldValue.serverTimestamp()
        );
        if (!Boolean.TRUE.equals(tournament.getBoolean("finalPaid"))) {
            applyRegularStars(transaction, winnerUid, winnerScore, true, false);
            applyRegularStars(transaction, loserUid, loserScore, false, true);
            transaction.set(db.collection("users").document(winnerUid), mapOf(
                    "tokens", FieldValue.increment(3),
                    "stars", FieldValue.increment(10)
            ), SetOptions.merge());
            updates.put("finalPaid", true);
        }
        transaction.set(ref, updates, SetOptions.merge());
        Log.d(TAG, "complete final winner=" + winnerUid + ", loser=" + loserUid);
    }

    public Task<Void> cancelActiveTournamentForUser(String uid) {
        return findActiveTournamentForUser(uid).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            DocumentSnapshot active = task.getResult();
            if (active == null || !active.exists()) return Tasks.forResult(null);
            return active.getReference().set(mapOf(
                    "status", CANCELLED,
                    "cancelledAt", FieldValue.serverTimestamp(),
                    "updatedAt", FieldValue.serverTimestamp()
            ), SetOptions.merge());
        });
    }

    public ListenerRegistration listenTournament(String tournamentId, EventListener<DocumentSnapshot> listener) {
        if (db == null || isBlank(tournamentId)) return null;
        return db.collection("tournaments").document(tournamentId).addSnapshotListener(listener);
    }

    public ListenerRegistration listenJoinableTournament(EventListener<QuerySnapshot> listener) {
        if (db == null) return null;
        return db.collection("tournaments").whereEqualTo("status", WAITING).limit(20).addSnapshotListener(listener);
    }

    private DocumentReference createTournamentGame(Transaction transaction, String tournamentId, String round, String p1, String p2) {
        DocumentReference gameRef = db.collection("games").document();
        Map<String, Object> game = mapOf(
                "player1Uid", p1,
                "player2Uid", p2,
                "currentPlayerUid", p1,
                "status", "active",
                "currentMiniGame", GameRepository.FULL_MATCH_ORDER[0],
                "fullMatch", true,
                "friendly", false,
                "matchType", "TOURNAMENT",
                "tournamentRun", true,
                "tournamentId", tournamentId,
                "tournamentRound", round,
                "matchIndex", 0,
                "player1Score", 0,
                "player2Score", 0,
                "createdAt", FieldValue.serverTimestamp(),
                "updatedAt", FieldValue.serverTimestamp()
        );
        transaction.set(gameRef, game);
        transaction.set(db.collection("users").document(p1), gameState(gameRef.getId()), SetOptions.merge());
        transaction.set(db.collection("users").document(p2), gameState(gameRef.getId()), SetOptions.merge());
        return gameRef;
    }

    private void applyRegularStars(Transaction transaction, String uid, long score, boolean winner, boolean loser) {
        if (isBlank(uid)) return;
        DocumentReference userRef = db.collection("users").document(uid);
        long scoreStars = Math.max(0, score) / 40;
        long delta = winner ? 10 + scoreStars : loser ? -10 + scoreStars : scoreStars;
        transaction.set(userRef, mapOf("stars", FieldValue.increment(delta)), SetOptions.merge());
    }

    private String statusField(String round) {
        if ("SF1".equals(round)) return "sf1Status";
        if ("SF2".equals(round)) return "sf2Status";
        if ("FINAL".equals(round)) return "finalStatus";
        return "";
    }

    private Map<String, Object> gameState(String gameId) {
        return mapOf("online", true, "inGame", true, "currentGameId", gameId,
                "activeGameId", gameId, "lastActiveAt", FieldValue.serverTimestamp());
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }

    private static List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) if (item != null) list.add(String.valueOf(item));
        }
        return list;
    }

    private static long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static long createdAtMillis(DocumentSnapshot doc) {
        Timestamp timestamp = doc == null ? null : doc.getTimestamp("createdAt");
        return timestamp == null ? 0 : timestamp.toDate().getTime();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
