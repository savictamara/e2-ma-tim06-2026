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
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class GameRepository {
    public static final String MINI_STEP_BY_STEP = "STEP_BY_STEP";
    public static final String MINI_MY_NUMBER = "MY_NUMBER";
    public static final String PHASE_WAITING_TARGET_STOP = "WAITING_TARGET_STOP";
    public static final String PHASE_WAITING_NUMBERS_STOP = "WAITING_NUMBERS_STOP";
    public static final String PHASE_PLAYING = "PLAYING";
    public static final String PHASE_FINISHED = "FINISHED";
    private static final String TAG = "GameRepository";
    private final FirebaseFirestore db;
    private final Random random = new Random();

    public GameRepository(Context context) {
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

    public Task<String> joinOrCreateGame(String uid) {
        return joinOrCreateGame(uid, MINI_STEP_BY_STEP);
    }

    public Task<String> joinOrCreateGame(String uid, String miniGame) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        Log.d(TAG, "Searching waiting game, miniGame=" + miniGame + ", uid=" + uid);
        return db.collection("games")
                .whereEqualTo("status", "waiting")
                .whereEqualTo("currentMiniGame", miniGame)
                .limit(10)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Waiting game query failed", task.getException());
                        throw task.getException();
                    }
                    DocumentReference candidate = null;
                    DocumentReference ownWaiting = null;
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String player1 = doc.getString("player1Uid");
                        String player2 = doc.getString("player2Uid");
                        if (player1 != null && !player1.equals(uid) && player2 == null) {
                            candidate = doc.getReference();
                            break;
                        } else if (player1 != null && player1.equals(uid) && player2 == null) {
                            ownWaiting = doc.getReference();
                        }
                    }
                    if (candidate != null) {
                        DocumentReference finalCandidate = candidate;
                        return db.runTransaction(transaction -> {
                            DocumentSnapshot waiting = transaction.get(finalCandidate);
                            String player1 = waiting.getString("player1Uid");
                            String player2 = waiting.getString("player2Uid");
                            if (waiting.exists() && "waiting".equals(waiting.getString("status"))
                                    && player1 != null && !player1.equals(uid) && player2 == null) {
                                Log.d(TAG, "Joining existing gameId=" + finalCandidate.getId() + ", uid=" + uid);
                                transaction.update(finalCandidate, "player2Uid", uid, "status", "active",
                                        "currentPlayerUid", player1, "updatedAt", FieldValue.serverTimestamp());
                                return finalCandidate.getId();
                            }
                            return createGameInTransaction(transaction, uid, miniGame);
                        });
                    }
                    if (ownWaiting != null) {
                        Log.d(TAG, "Reusing own waiting gameId=" + ownWaiting.getId() + ", uid=" + uid);
                        return Tasks.forResult(ownWaiting.getId());
                    }
                    return db.runTransaction(transaction -> createGameInTransaction(transaction, uid, miniGame));
                });
    }

    private String createGameInTransaction(Transaction transaction, String uid, String miniGame) {
        DocumentReference ref = db.collection("games").document();
        Log.d(TAG, "Creating new gameId=" + ref.getId() + ", miniGame=" + miniGame + ", uid=" + uid);
        Map<String, Object> game = new HashMap<>();
        game.put("player1Uid", uid);
        game.put("player2Uid", null);
        game.put("currentPlayerUid", uid);
        game.put("status", "waiting");
        game.put("currentMiniGame", miniGame);
        game.put("player1Score", 0);
        game.put("player2Score", 0);
        game.put("createdAt", FieldValue.serverTimestamp());
        game.put("updatedAt", FieldValue.serverTimestamp());
        transaction.set(ref, game);
        return ref.getId();
    }

    public ListenerRegistration listenGame(String gameId, EventListener<DocumentSnapshot> listener) {
        if (db == null) {
            return null;
        }
        Log.d(TAG, "Listening gameId=" + gameId);
        return db.collection("games").document(gameId).addSnapshotListener(listener);
    }

    public ListenerRegistration listenRound(String gameId, String roundId, EventListener<DocumentSnapshot> listener) {
        if (db == null) {
            return null;
        }
        Log.d(TAG, "Listening round gameId=" + gameId + ", roundId=" + roundId);
        return db.collection("games").document(gameId).collection("rounds").document(roundId).addSnapshotListener(listener);
    }

    public Task<DocumentSnapshot> getGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("games").document(gameId).get();
    }

    public Task<Void> seedStepQuestionsIfNeeded() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("stepByStepQuestions").limit(1).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            if (!task.getResult().isEmpty()) {
                return Tasks.forResult(null);
            }
            Map<String, Object> q1 = question(Arrays.asList("Muzicar", "Novi Sad", "Tamburica", "Balada", "Panonski", "Olivera", "Djordje"), "Balasevic");
            Map<String, Object> q2 = question(Arrays.asList("Sport", "Lopta", "Mreza", "Tim", "Gol", "Stadion", "Najvaznija sporedna stvar"), "Fudbal");
            Map<String, Object> q3 = question(Arrays.asList("Planeta", "Crvena", "Svemir", "Rover", "Cetvrta", "Olimp", "Rimski bog rata"), "Mars");
            return Tasks.whenAll(db.collection("stepByStepQuestions").document("balasevic").set(q1),
                    db.collection("stepByStepQuestions").document("fudbal").set(q2),
                    db.collection("stepByStepQuestions").document("mars").set(q3));
        });
    }

    private Map<String, Object> question(List<String> steps, String answer) {
        Map<String, Object> data = new HashMap<>();
        data.put("steps", steps);
        data.put("answer", answer);
        return data;
    }

    public Task<Void> ensureStepRound(String gameId, int roundNumber) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return seedStepQuestionsIfNeeded().continueWithTask(seedTask -> {
            if (!seedTask.isSuccessful()) {
                Log.e(TAG, "Seed step questions failed before round create", seedTask.getException());
                throw seedTask.getException();
            }
            return db.collection("stepByStepQuestions").get();
        }).continueWithTask(questionTask -> {
            if (!questionTask.isSuccessful()) {
                Log.e(TAG, "Loading step questions failed", questionTask.getException());
                throw questionTask.getException();
            }
            List<DocumentSnapshot> questions = questionTask.getResult().getDocuments();
            if (questions.isEmpty()) {
                return Tasks.forException(new IllegalStateException("Nema Korak po korak pitanja u Firestore-u"));
            }
            DocumentSnapshot question = questions.get(Math.abs(roundNumber - 1) % questions.size());
            List<String> steps = (List<String>) question.get("steps");
            String answer = question.getString("answer");
            if (steps == null || steps.size() != 7 || answer == null || answer.trim().isEmpty()) {
                return Tasks.forException(new IllegalStateException("Korak po korak pitanje nema 7 koraka ili odgovor"));
            }
            return createStepRoundIfMissing(gameId, roundNumber, steps, answer, question.getId());
        });
    }

    private Task<Void> createStepRoundIfMissing(String gameId, int roundNumber, List<String> steps, String answer, String questionId) {
        String roundId = stepRoundId(roundNumber);
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            Log.d(TAG, "Creating step round, gameId=" + gameId + ", roundNumber=" + roundNumber
                    + ", roundId=" + roundId + ", questionId=" + questionId);
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (p1 == null || p2 == null) {
                throw new IllegalStateException("Korak po korak ne moze da pocne bez dva igraca");
            }
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            Map<String, Object> round = new HashMap<>();
            round.put("id", roundId);
            round.put("gameId", gameId);
            round.put("roundIndex", roundNumber);
            round.put("roundNumber", roundNumber);
            round.put("type", MINI_STEP_BY_STEP);
            round.put("activePlayerUid", active);
            round.put("opponentUid", opponent);
            round.put("openedStepIndex", 0);
            round.put("steps", steps);
            round.put("answer", answer);
            round.put("phase", "ACTIVE_PLAYER");
            round.put("finished", false);
            round.put("winnerUid", null);
            round.put("awardedPoints", 0);
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            round.put("phaseStartedAt", FieldValue.serverTimestamp());
            round.put("scoreApplied", false);
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_STEP_BY_STEP, "currentPlayerUid", active,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> openNextStep(String gameId, int roundNumber, String uid, boolean timeout) {
        String roundId = stepRoundId(roundNumber);
        DocumentReference roundRef = db.collection("games").document(gameId).collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            String phase = round.getString("phase");
            String active = round.getString("activePlayerUid");
            Boolean finished = round.getBoolean("finished");
            Long opened = round.getLong("openedStepIndex");
            int index = opened == null ? 0 : opened.intValue();
            if (Boolean.TRUE.equals(finished) || !"ACTIVE_PLAYER".equals(phase)
                    || (uid != null && !uid.equals(active)) || index > 6) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            if (index < 6) {
                updates.put("openedStepIndex", index + 1);
            } else {
                updates.put("phase", "OPPONENT_CHANCE");
            }
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Step timeout/update gameId=" + gameId + ", roundId=" + roundId
                    + ", oldIndex=" + index + ", updates=" + updates);
            transaction.update(roundRef, updates);
            return null;
        });
    }

    public Task<Void> submitStepAnswer(String gameId, int roundNumber, String uid, String answer) {
        String roundId = stepRoundId(roundNumber);
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            DocumentSnapshot game = transaction.get(gameRef);
            String phase = round.getString("phase");
            String active = round.getString("activePlayerUid");
            String opponent = round.getString("opponentUid");
            String expected = round.getString("answer");
            Boolean finished = round.getBoolean("finished");
            Long opened = round.getLong("openedStepIndex");
            int index = opened == null ? 0 : opened.intValue();
            boolean correct = expected != null && expected.equalsIgnoreCase(answer.trim());
            Log.d(TAG, "Submit step answer gameId=" + gameId + ", roundId=" + roundId
                    + ", uid=" + uid + ", phase=" + phase + ", active=" + active
                    + ", opponent=" + opponent + ", openedStepIndex=" + index + ", correct=" + correct);
            if (Boolean.TRUE.equals(finished) || Boolean.TRUE.equals(round.getBoolean("scoreApplied"))) {
                return null;
            }
            if (!correct) {
                return null;
            }
            int awardedPoints;
            if ("ACTIVE_PLAYER".equals(phase) && uid.equals(active)) {
                awardedPoints = getStepPoints(index);
                applyScore(transaction, gameRef, game, uid, awardedPoints);
            } else if ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponent)) {
                awardedPoints = 5;
                applyScore(transaction, gameRef, game, uid, awardedPoints);
            } else {
                return null;
            }
            transaction.update(roundRef, "phase", "FINISHED", "winnerUid", uid, "scoreApplied", true,
                    "finished", true, "awardedPoints", awardedPoints,
                    "phaseStartedAt", FieldValue.serverTimestamp(), "updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Step score update uid=" + uid + ", points=" + awardedPoints + ", roundId=" + roundId);
            return null;
        });
    }

    public Task<Void> finishStepRound(String gameId, int roundNumber) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(stepRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (Boolean.TRUE.equals(round.getBoolean("finished")) || "FINISHED".equals(round.getString("phase"))) {
                return null;
            }
            transaction.update(roundRef, "phase", "FINISHED", "finished", true, "phaseStartedAt", FieldValue.serverTimestamp(),
                    "updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Finish step round without score gameId=" + gameId + ", round=" + roundNumber);
            return null;
        });
    }

    public int getStepPoints(int openedStepIndex) {
        int normalized = Math.max(0, Math.min(6, openedStepIndex));
        return 20 - (normalized * 2);
    }

    public Task<Void> ensureMyNumberRound(String gameId, int roundNumber) {
        String roundId = myNumberRoundId(roundNumber);
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            Log.d(TAG, "Creating my number round, gameId=" + gameId + ", roundNumber=" + roundNumber);
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            Map<String, Object> round = new HashMap<>();
            round.put("id", roundId);
            round.put("gameId", gameId);
            round.put("roundIndex", roundNumber);
            round.put("roundNumber", roundNumber);
            round.put("type", MINI_MY_NUMBER);
            round.put("activePlayerUid", active);
            round.put("opponentUid", opponent);
            round.put("phase", PHASE_WAITING_TARGET_STOP);
            round.put("finished", false);
            round.put("targetNumber", 0);
            round.put("numbers", new ArrayList<Integer>());
            round.put("submissionsByPlayer", new HashMap<String, String>());
            round.put("resultsByPlayer", new HashMap<String, Double>());
            round.put("validByPlayer", new HashMap<String, Boolean>());
            round.put("winnerUid", null);
            round.put("awardedPoints", 0);
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            round.put("startedAt", Timestamp.now());
            round.put("scoreApplied", false);
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_MY_NUMBER, "currentPlayerUid", active,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> stopTarget(String gameId, int roundNumber) {
        DocumentReference roundRef = db.collection("games").document(gameId).collection("rounds").document(myNumberRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!PHASE_WAITING_TARGET_STOP.equals(round.getString("phase"))) {
                return null;
            }
            transaction.update(roundRef, "targetNumber", 100 + random.nextInt(900), "phase", PHASE_WAITING_NUMBERS_STOP,
                    "startedAt", Timestamp.now(), "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> stopNumbers(String gameId, int roundNumber) {
        DocumentReference roundRef = db.collection("games").document(gameId).collection("rounds").document(myNumberRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!PHASE_WAITING_NUMBERS_STOP.equals(round.getString("phase"))) {
                return null;
            }
            List<Integer> numbers = Arrays.asList(1 + random.nextInt(9), 1 + random.nextInt(9),
                    1 + random.nextInt(9), 1 + random.nextInt(9),
                    Arrays.asList(10, 15, 20).get(random.nextInt(3)),
                    Arrays.asList(25, 50, 75, 100).get(random.nextInt(4)));
            transaction.update(roundRef, "numbers", numbers, "phase", PHASE_PLAYING, "startedAt", Timestamp.now(),
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> submitMyNumber(String gameId, int roundNumber, String uid, String expression, double result, boolean valid) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(myNumberRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!PHASE_PLAYING.equals(round.getString("phase")) || Boolean.TRUE.equals(round.getBoolean("scoreApplied"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            Map<String, Object> submissions = (Map<String, Object>) round.get("submissionsByPlayer");
            boolean p1AlreadySubmitted = submissions != null && submissions.containsKey(p1);
            boolean p2AlreadySubmitted = submissions != null && submissions.containsKey(p2);
            boolean p1Submitted = uid.equals(p1) || p1AlreadySubmitted;
            boolean p2Submitted = uid.equals(p2) || p2AlreadySubmitted;
            transaction.update(roundRef, "submissionsByPlayer." + uid, expression,
                    "resultsByPlayer." + uid, result,
                    "validByPlayer." + uid, valid,
                    "updatedAt", FieldValue.serverTimestamp());
            if (p1Submitted && p2Submitted) {
                applyMyNumberScore(transaction, gameRef, roundRef, game, round, uid, result, valid, roundNumber);
            }
            return null;
        });
    }

    public Task<Void> finishMyNumberRound(String gameId, int roundNumber) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(myNumberRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (Boolean.TRUE.equals(round.getBoolean("scoreApplied"))) {
                return null;
            }
            applyMyNumberScore(transaction, gameRef, roundRef, game, round, null, 0, false, roundNumber);
            return null;
        });
    }

    private void applyMyNumberScore(Transaction transaction, DocumentReference gameRef, DocumentReference roundRef,
                                    DocumentSnapshot game, DocumentSnapshot round, String pendingUid,
                                    double pendingResult, boolean pendingValid, int roundNumber) {
        Map<String, Object> results = (Map<String, Object>) round.get("resultsByPlayer");
        Map<String, Object> validMap = (Map<String, Object>) round.get("validByPlayer");
        if (results == null) {
            results = new HashMap<>();
        }
        if (validMap == null) {
            validMap = new HashMap<>();
        }
        Long targetLong = round.getLong("targetNumber");
        int target = targetLong == null ? 0 : targetLong.intValue();
        String p1 = game.getString("player1Uid");
        String p2 = game.getString("player2Uid");
        String active = round.getString("activePlayerUid");
        double r1 = valueForPlayer(results, pendingUid, pendingResult, p1);
        double r2 = valueForPlayer(results, pendingUid, pendingResult, p2);
        boolean v1 = validForPlayer(validMap, pendingUid, pendingValid, p1);
        boolean v2 = validForPlayer(validMap, pendingUid, pendingValid, p2);
        boolean p1Exact = v1 && almostEqual(r1, target);
        boolean p2Exact = v2 && almostEqual(r2, target);
        String winner = "";
        int points = 0;
        if (p1Exact && p2Exact) {
            winner = active;
            points = 10;
            applyScore(transaction, gameRef, game, p1, 10);
            applyScore(transaction, gameRef, game, p2, 10);
        } else if (p1Exact) {
            winner = p1;
            points = 10;
            applyScore(transaction, gameRef, game, winner, points);
        } else if (p2Exact) {
            winner = p2;
            points = 10;
            applyScore(transaction, gameRef, game, winner, points);
        } else {
            boolean p1HasResult = v1 && Math.abs(r1) > 0.000001;
            boolean p2HasResult = v2 && Math.abs(r2) > 0.000001;
            if (p1HasResult || p2HasResult) {
                double d1 = p1HasResult ? Math.abs(target - r1) : Double.MAX_VALUE;
                double d2 = p2HasResult ? Math.abs(target - r2) : Double.MAX_VALUE;
                if (almostEqual(d1, d2)) {
                    winner = active;
                    points = 5;
                } else {
                    winner = d1 < d2 ? p1 : p2;
                    points = 5;
                }
                applyScore(transaction, gameRef, game, winner, points);
            }
        }
        Log.d(TAG, "MyNumber score round=" + roundNumber + ", target=" + target
                + ", p1Result=" + r1 + ", p2Result=" + r2
                + ", p1Valid=" + v1 + ", p2Valid=" + v2
                + ", winner=" + winner + ", points=" + points);
        transaction.update(roundRef, "phase", PHASE_FINISHED, "winnerUid", winner, "awardedPoints", points,
                "scoreApplied", true, "finished", true, "updatedAt", FieldValue.serverTimestamp());
        if (roundNumber == 2) {
            transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
        }
    }

    private double valueForPlayer(Map<String, Object> results, String pendingUid, double pendingResult, String playerUid) {
        if (playerUid != null && playerUid.equals(pendingUid)) {
            return pendingResult;
        }
        Object value = results.get(playerUid);
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private boolean validForPlayer(Map<String, Object> validMap, String pendingUid, boolean pendingValid, String playerUid) {
        if (playerUid != null && playerUid.equals(pendingUid)) {
            return pendingValid;
        }
        Object value = validMap.get(playerUid);
        return value instanceof Boolean && (Boolean) value;
    }

    private boolean almostEqual(double left, double right) {
        return Math.abs(left - right) < 0.000001;
    }

    private void applyScore(Transaction transaction, DocumentReference gameRef, DocumentSnapshot game, String uid, int points) {
        String p1 = game.getString("player1Uid");
        String field = uid.equals(p1) ? "player1Score" : "player2Score";
        transaction.update(gameRef, field, FieldValue.increment(points), "updatedAt", FieldValue.serverTimestamp());
    }

    public String stepRoundId(int roundNumber) {
        return "step_round_" + roundNumber;
    }

    public String myNumberRoundId(int roundNumber) {
        return "my_number_round_" + roundNumber;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
