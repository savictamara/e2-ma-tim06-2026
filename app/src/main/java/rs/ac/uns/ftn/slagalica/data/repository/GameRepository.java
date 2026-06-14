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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class GameRepository {
    public static final String MINI_STEP_BY_STEP = "STEP_BY_STEP";
    public static final String MINI_MY_NUMBER = "MY_NUMBER";
    public static final String MINI_KNOW_IT = "KNOW_IT";
    public static final String MINI_CONNECTIONS = "CONNECTIONS";
    public static final String MINI_ASSOCIATIONS = "ASSOCIATIONS";
    public static final String MINI_SKOCKO = "SKOCKO";
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
        if (isBlank(uid) || isBlank(miniGame)) {
            return Tasks.forException(new IllegalArgumentException("Nedostaje uid ili tip igre"));
        }
        Log.d(TAG, "joinOrCreate called uid=" + uid + ", miniGameType=" + miniGame);
        return joinOrCreateGameInternal(uid, miniGame, 0)
                .addOnSuccessListener(gameId -> Log.d(TAG, "returned gameId="
                        + gameId + ", uid=" + uid + ", miniGameType=" + miniGame))
                .addOnFailureListener(e -> Log.e(TAG, "joinOrCreateGame failed, uid=" + uid
                        + ", miniGameType=" + miniGame, e));
    }

    private Task<String> joinOrCreateGameInternal(String uid, String miniGame, int attempt) {
        Log.d(TAG, "Searching waiting game, miniGameType=" + miniGame + ", uid=" + uid + ", attempt=" + attempt);
        return db.collection("games")
                .whereEqualTo("status", "waiting")
                .whereEqualTo("currentMiniGame", miniGame)
                .limit(50)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Waiting game query failed", task.getException());
                        throw task.getException();
                    }
                    Log.d(TAG, "found waiting candidates count=" + task.getResult().size()
                            + ", uid=" + uid + ", miniGameType=" + miniGame);
                    QueryDocumentSnapshot candidate = null;
                    QueryDocumentSnapshot ownWaiting = null;
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String player1 = doc.getString("player1Uid");
                        String player2 = doc.getString("player2Uid");
                        Log.d(TAG, "candidate gameId=" + doc.getId()
                                + ", player1Uid=" + player1 + ", player2Uid=" + player2);
                        Log.d(TAG, "Waiting candidate scan gameId=" + doc.getId()
                                + ", uid=" + uid + ", miniGameType=" + miniGame
                                + ", player1Uid=" + player1 + ", player2Uid=" + player2
                                + ", createdAt=" + doc.getTimestamp("createdAt"));
                        if (isJoinableWaiting(doc, uid, miniGame)) {
                            if (candidate == null || createdAtMillis(doc) < createdAtMillis(candidate)) {
                                candidate = doc;
                            }
                        } else if (isOwnWaiting(doc, uid, miniGame)) {
                            if (ownWaiting == null || createdAtMillis(doc) < createdAtMillis(ownWaiting)) {
                                ownWaiting = doc;
                            }
                        }
                    }
                    if (candidate != null) {
                        DocumentReference candidateRef = candidate.getReference();
                        QueryDocumentSnapshot finalOwnWaiting = ownWaiting;
                        Log.d(TAG, "found waiting gameId=" + candidate.getId()
                                + ", found player1Uid=" + candidate.getString("player1Uid")
                                + ", uid=" + uid + ", miniGameType=" + miniGame);
                        return joinWaitingGame(candidateRef, uid, miniGame)
                                .continueWithTask(joinTask -> {
                                    if (!joinTask.isSuccessful()) {
                                        throw joinTask.getException();
                                    }
                                    String joinedGameId = joinTask.getResult();
                                    if (!isBlank(joinedGameId)) {
                                        return Tasks.forResult(joinedGameId);
                                    }
                                    if (attempt < 2) {
                                        Log.d(TAG, "transaction join retry uid=" + uid
                                                + ", miniGameType=" + miniGame + ", attempt=" + (attempt + 1));
                                        return joinOrCreateGameInternal(uid, miniGame, attempt + 1);
                                    }
                                    if (finalOwnWaiting != null) {
                                        Log.d(TAG, "Reusing own waiting after retry gameId=" + finalOwnWaiting.getId()
                                                + ", uid=" + uid + ", miniGameType=" + miniGame);
                                        return Tasks.forResult(finalOwnWaiting.getId());
                                    }
                                    return db.runTransaction(transaction -> createGameInTransaction(transaction, uid, miniGame));
                                });
                    }
                    if (ownWaiting != null) {
                        Log.d(TAG, "Reusing own waiting gameId=" + ownWaiting.getId()
                                + ", uid=" + uid + ", miniGameType=" + miniGame);
                        return Tasks.forResult(ownWaiting.getId());
                    }
                    return db.runTransaction(transaction -> createGameInTransaction(transaction, uid, miniGame));
                });
    }

    private Task<String> joinWaitingGame(DocumentReference gameRef, String uid, String miniGame) {
        return db.runTransaction(transaction -> {
            DocumentSnapshot waiting = transaction.get(gameRef);
            String statusBefore = waiting.getString("status");
            String currentMiniGame = waiting.getString("currentMiniGame");
            String player1 = waiting.getString("player1Uid");
            String player2 = waiting.getString("player2Uid");
            Log.d(TAG, "Join transaction before gameId=" + gameRef.getId()
                    + ", uid=" + uid + ", miniGameType=" + miniGame
                    + ", status=" + statusBefore + ", currentMiniGame=" + currentMiniGame
                    + ", player1Uid=" + player1 + ", player2Uid=" + player2);
            if (waiting.exists()
                    && "waiting".equals(statusBefore)
                    && miniGame.equals(currentMiniGame)
                    && !isBlank(player1)
                    && !player1.equals(uid)
                    && isBlank(player2)) {
                transaction.update(gameRef, "player2Uid", uid, "status", "active",
                        "currentPlayerUid", player1, "updatedAt", FieldValue.serverTimestamp());
                Log.d(TAG, "transaction join success gameId=" + gameRef.getId() + ", uid=" + uid
                        + ", miniGameType=" + miniGame + ", status before=waiting, status after=active");
                return gameRef.getId();
                            }
            Log.d(TAG, "Waiting game not joinable in transaction gameId=" + gameRef.getId()
                    + ", uid=" + uid + ", miniGameType=" + miniGame
                    + ", status before=" + statusBefore + ", player1Uid=" + player1
                    + ", player2Uid=" + player2);
            return "";
        });
    }

    private String createGameInTransaction(Transaction transaction, String uid, String miniGame) {
        DocumentReference ref = db.collection("games").document();
        Log.d(TAG, "Creating new gameId=" + ref.getId() + ", miniGameType=" + miniGame + ", uid=" + uid);
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
        Log.d(TAG, "created new waiting gameId=" + ref.getId() + ", uid=" + uid
                + ", miniGameType=" + miniGame + ", status=waiting");
        return ref.getId();
    }

    public static boolean isGameReady(DocumentSnapshot game) {
        if (game == null || !game.exists()) {
            return false;
        }
        String player1 = game.getString("player1Uid");
        String player2 = game.getString("player2Uid");
        return "active".equals(game.getString("status"))
                && isNonEmpty(player1)
                && isNonEmpty(player2)
                && !player1.equals(player2);
    }

    public static boolean isGameReady(Map<String, Object> game) {
        if (game == null) {
            return false;
        }
        Object status = game.get("status");
        Object player1 = game.get("player1Uid");
        Object player2 = game.get("player2Uid");
        String p1 = player1 == null ? "" : String.valueOf(player1);
        String p2 = player2 == null ? "" : String.valueOf(player2);
        return "active".equals(status)
                && isNonEmpty(p1)
                && isNonEmpty(p2)
                && !p1.equals(p2);
    }

    public static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String getOtherPlayerUid(String currentUid, String player1Uid, String player2Uid) {
        if (currentUid == null) {
            return "";
        }
        if (currentUid.equals(player1Uid)) {
            return player2Uid == null ? "" : player2Uid;
        }
        if (currentUid.equals(player2Uid)) {
            return player1Uid == null ? "" : player1Uid;
        }
        return "";
    }

    public static boolean isPlayer1(String currentUid, String player1Uid) {
        return isNonEmpty(currentUid) && currentUid.equals(player1Uid);
    }

    public static boolean isPlayer2(String currentUid, String player2Uid) {
        return isNonEmpty(currentUid) && currentUid.equals(player2Uid);
    }

    public Task<Void> repairRoundPlayers(String gameId, String roundId, int roundNumber, boolean repairCurrentTurn) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!round.exists() || !isGameReady(game)) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            Map<String, Object> updates = new HashMap<>();
            String existingActive = round.getString("activePlayerUid");
            String existingOpponent = round.getString("opponentUid");
            if (!active.equals(existingActive) || !opponent.equals(existingOpponent)) {
                updates.put("activePlayerUid", active);
                updates.put("opponentUid", opponent);
            }
            String currentTurn = round.getString("currentTurnUid");
            if (repairCurrentTurn && !isNonEmpty(currentTurn)) {
                updates.put("currentTurnUid", active);
            }
            if (!updates.isEmpty()) {
                updates.put("updatedAt", FieldValue.serverTimestamp());
                Log.e(TAG, "Repairing round players gameId=" + gameId + ", roundId=" + roundId
                        + ", activePlayerUid=" + existingActive + "->" + active
                        + ", opponentUid=" + existingOpponent + "->" + opponent
                        + ", currentTurnUid=" + currentTurn
                        + ", repairCurrentTurn=" + repairCurrentTurn);
                transaction.update(roundRef, updates);
            }
            return null;
        });
    }

    private boolean isJoinableWaiting(DocumentSnapshot doc, String uid, String miniGame) {
        String player1 = doc.getString("player1Uid");
        return doc.exists()
                && "waiting".equals(doc.getString("status"))
                && miniGame.equals(doc.getString("currentMiniGame"))
                && !isBlank(player1)
                && !player1.equals(uid)
                && isBlank(doc.getString("player2Uid"));
    }

    private boolean isOwnWaiting(DocumentSnapshot doc, String uid, String miniGame) {
        String player1 = doc.getString("player1Uid");
        return doc.exists()
                && "waiting".equals(doc.getString("status"))
                && miniGame.equals(doc.getString("currentMiniGame"))
                && uid.equals(player1)
                && isBlank(doc.getString("player2Uid"));
    }

    private long createdAtMillis(DocumentSnapshot doc) {
        Timestamp createdAt = doc.getTimestamp("createdAt");
        return createdAt == null ? Long.MAX_VALUE : createdAt.toDate().getTime();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
            if (!isGameReady(game)) {
                throw new IllegalStateException("Korak po korak ne moze da pocne bez dva razlicita igraca");
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
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
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
                transaction.update(gameRef, "currentPlayerUid", round.getString("opponentUid"),
                        "updatedAt", FieldValue.serverTimestamp());
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
            if (!isGameReady(game)) {
                throw new IllegalStateException("Moj broj ne moze da pocne bez dva razlicita igraca");
            }
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
            round.put("phaseStartedAt", FieldValue.serverTimestamp());
            round.put("playStartedAt", null);
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
            if (!PHASE_WAITING_TARGET_STOP.equals(round.getString("phase"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            int target = 100 + random.nextInt(900);
            Log.d(TAG, "Stop target gameId=" + gameId + ", round=" + roundNumber + ", target=" + target);
            transaction.update(roundRef, "targetNumber", target,
                    "phase", PHASE_WAITING_NUMBERS_STOP,
                    "phaseStartedAt", FieldValue.serverTimestamp(),
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> stopNumbers(String gameId, int roundNumber) {
        DocumentReference roundRef = db.collection("games").document(gameId).collection("rounds").document(myNumberRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!PHASE_WAITING_NUMBERS_STOP.equals(round.getString("phase"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            List<Integer> numbers = Arrays.asList(1 + random.nextInt(9), 1 + random.nextInt(9),
                    1 + random.nextInt(9), 1 + random.nextInt(9),
                    Arrays.asList(10, 15, 20).get(random.nextInt(3)),
                    Arrays.asList(25, 50, 75, 100).get(random.nextInt(4)));
            Log.d(TAG, "Stop numbers gameId=" + gameId + ", round=" + roundNumber + ", numbers=" + numbers);
            transaction.update(roundRef, "numbers", numbers,
                    "phase", PHASE_PLAYING,
                    "phaseStartedAt", FieldValue.serverTimestamp(),
                    "playStartedAt", FieldValue.serverTimestamp(),
                    "updatedAt", FieldValue.serverTimestamp());
            transaction.update(db.collection("games").document(gameId), "currentPlayerUid", null,
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
            if (!PHASE_PLAYING.equals(round.getString("phase"))
                    || Boolean.TRUE.equals(round.getBoolean("scoreApplied"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!uid.equals(p1) && !uid.equals(p2)) {
                Log.d(TAG, "Ignoring my number submit from non-player uid=" + uid + ", gameId=" + gameId);
                return null;
            }
            Map<String, Object> submissions = (Map<String, Object>) round.get("submissionsByPlayer");
            boolean p1AlreadySubmitted = submissions != null && submissions.containsKey(p1);
            boolean p2AlreadySubmitted = submissions != null && submissions.containsKey(p2);
            boolean p1Submitted = uid.equals(p1) || p1AlreadySubmitted;
            boolean p2Submitted = uid.equals(p2) || p2AlreadySubmitted;
            Log.d(TAG, "Submit my number gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", expression=" + expression
                    + ", result=" + result + ", valid=" + valid
                    + ", p1Submitted=" + p1Submitted + ", p2Submitted=" + p2Submitted);
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
            if (Boolean.TRUE.equals(round.getBoolean("scoreApplied"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            Log.d(TAG, "Finish my number round by timeout gameId=" + gameId + ", round=" + roundNumber);
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

    public Task<Void> seedKnowItQuestionsIfNeeded() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("knowItQuestions").limit(1).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            if (!task.getResult().isEmpty()) {
                return Tasks.forResult(null);
            }
            Log.d(TAG, "Seeding knowItQuestions");
            return Tasks.whenAll(
                    db.collection("knowItQuestions").document("q1").set(knowItQuestion("Koji element ima hemijski simbol Au?",
                            Arrays.asList("Zlato", "Srebro", "Gvozdje", "Kiseonik"), 0)),
                    db.collection("knowItQuestions").document("q2").set(knowItQuestion("Koliko kontinenata postoji?",
                            Arrays.asList("5", "6", "7", "8"), 2)),
                    db.collection("knowItQuestions").document("q3").set(knowItQuestion("Koja planeta je poznata kao crvena planeta?",
                            Arrays.asList("Venera", "Mars", "Jupiter", "Merkur"), 1)),
                    db.collection("knowItQuestions").document("q4").set(knowItQuestion("Ko je napisao Na Drini cuprija?",
                            Arrays.asList("Mesa Selimovic", "Ivo Andric", "Branko Copic", "Milos Crnjanski"), 1)),
                    db.collection("knowItQuestions").document("q5").set(knowItQuestion("Koji je glavni grad Francuske?",
                            Arrays.asList("Madrid", "Pariz", "Rim", "Berlin"), 1)),
                    db.collection("knowItQuestions").document("q6").set(knowItQuestion("Koliko minuta ima jedan sat?",
                            Arrays.asList("30", "45", "60", "90"), 2)),
                    db.collection("knowItQuestions").document("q7").set(knowItQuestion("Koja reka protiče kroz Novi Sad?",
                            Arrays.asList("Sava", "Tisa", "Dunav", "Morava"), 2)),
                    db.collection("knowItQuestions").document("q8").set(knowItQuestion("Koji sport koristi reket?",
                            Arrays.asList("Tenis", "Fudbal", "Kosarka", "Odbojka"), 0)));
        });
    }

    private Map<String, Object> knowItQuestion(String text, List<String> options, int correctIndex) {
        Map<String, Object> data = new HashMap<>();
        data.put("questionText", text);
        data.put("options", options);
        data.put("correctAnswerIndex", correctIndex);
        return data;
    }

    public Task<Void> ensureKnowItRound(String gameId) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return seedKnowItQuestionsIfNeeded().continueWithTask(seedTask -> {
            if (!seedTask.isSuccessful()) {
                Log.e(TAG, "Seed know it questions failed", seedTask.getException());
                throw seedTask.getException();
            }
            return db.collection("knowItQuestions").limit(8).get();
        }).continueWithTask(questionTask -> {
            if (!questionTask.isSuccessful()) {
                Log.e(TAG, "Loading know it questions failed", questionTask.getException());
                throw questionTask.getException();
            }
            List<Map<String, Object>> questions = new ArrayList<>();
            for (DocumentSnapshot doc : questionTask.getResult().getDocuments()) {
                String questionText = doc.getString("questionText");
                List<String> options = (List<String>) doc.get("options");
                Long correct = doc.getLong("correctAnswerIndex");
                if (questionText != null && options != null && options.size() == 4 && correct != null) {
                    Map<String, Object> question = new HashMap<>();
                    question.put("questionId", doc.getId());
                    question.put("questionText", questionText);
                    question.put("options", options);
                    question.put("correctAnswerIndex", correct.intValue());
                    questions.add(question);
                }
                if (questions.size() == 5) {
                    break;
                }
            }
            if (questions.size() < 5) {
                return Tasks.forException(new IllegalStateException("Nema dovoljno Ko zna zna pitanja u Firestore-u"));
            }
            return createKnowItRoundIfMissing(gameId, questions);
        });
    }

    private Task<Void> createKnowItRoundIfMissing(String gameId, List<Map<String, Object>> questions) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(knowItRoundId());
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!isGameReady(game)) {
                throw new IllegalStateException("Ko zna zna ne moze da pocne bez dva razlicita igraca");
            }
            Log.d(TAG, "Creating know it round, gameId=" + gameId + ", p1=" + p1 + ", p2=" + p2);
            Map<String, Object> round = new HashMap<>();
            round.put("id", knowItRoundId());
            round.put("gameId", gameId);
            round.put("type", MINI_KNOW_IT);
            round.put("roundIndex", 1);
            round.put("phase", PHASE_PLAYING);
            round.put("currentQuestionIndex", 0);
            round.put("questionStartedAt", FieldValue.serverTimestamp());
            round.put("questions", questions);
            round.put("answersByQuestion", new HashMap<String, Object>());
            round.put("answerTimesByQuestion", new HashMap<String, Object>());
            round.put("correctnessByQuestion", new HashMap<String, Object>());
            round.put("scoredQuestions", new HashMap<String, Boolean>());
            round.put("finished", false);
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_KNOW_IT, "currentPlayerUid", null,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> submitKnowItAnswer(String gameId, String uid, int selectedAnswerIndex, long answerTimeMillis) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(knowItRoundId());
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!"active".equals(game.getString("status")) || !PHASE_PLAYING.equals(round.getString("phase"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!uid.equals(p1) && !uid.equals(p2)) {
                return null;
            }
            Long indexLong = round.getLong("currentQuestionIndex");
            int questionIndex = indexLong == null ? 0 : indexLong.intValue();
            Timestamp startedAt = round.getTimestamp("questionStartedAt");
            long elapsed = startedAt == null ? 0 : answerTimeMillis - startedAt.toDate().getTime();
            if (elapsed > 5000) {
                Log.d(TAG, "Ignoring late know it answer, uid=" + uid + ", question=" + questionIndex + ", elapsed=" + elapsed);
                return null;
            }
            String qKey = String.valueOf(questionIndex);
            Map<String, Object> answersByQuestion = (Map<String, Object>) round.get("answersByQuestion");
            Map<String, Object> currentAnswers = nestedMap(answersByQuestion, qKey);
            if (currentAnswers.containsKey(uid)) {
                return null;
            }
            int correctIndex = correctIndexForQuestion(round, questionIndex);
            boolean correct = selectedAnswerIndex == correctIndex;
            Log.d(TAG, "Submit know it answer gameId=" + gameId + ", uid=" + uid
                    + ", questionIndex=" + questionIndex + ", selected=" + selectedAnswerIndex
                    + ", correct=" + correct + ", answerTime=" + answerTimeMillis);
            transaction.update(roundRef,
                    "answersByQuestion." + qKey + "." + uid, selectedAnswerIndex,
                    "answerTimesByQuestion." + qKey + "." + uid, answerTimeMillis,
                    "correctnessByQuestion." + qKey + "." + uid, correct,
                    "updatedAt", FieldValue.serverTimestamp());
            boolean p1Answered = uid.equals(p1) || currentAnswers.containsKey(p1);
            boolean p2Answered = uid.equals(p2) || currentAnswers.containsKey(p2);
            if (p1Answered && p2Answered) {
                scoreAndAdvanceKnowItQuestion(transaction, gameRef, roundRef, game, round,
                        questionIndex, uid, selectedAnswerIndex, correct, answerTimeMillis);
            }
            return null;
        });
    }

    public Task<Void> advanceKnowItQuestion(String gameId, int expectedQuestionIndex) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(knowItRoundId());
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            Long indexLong = round.getLong("currentQuestionIndex");
            int questionIndex = indexLong == null ? 0 : indexLong.intValue();
            if (questionIndex != expectedQuestionIndex || !PHASE_PLAYING.equals(round.getString("phase"))
                    || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            Log.d(TAG, "Know it timeout advance gameId=" + gameId + ", questionIndex=" + questionIndex);
            scoreAndAdvanceKnowItQuestion(transaction, gameRef, roundRef, game, round,
                    questionIndex, null, -1, false, 0);
            return null;
        });
    }

    private void scoreAndAdvanceKnowItQuestion(Transaction transaction, DocumentReference gameRef,
                                               DocumentReference roundRef, DocumentSnapshot game,
                                               DocumentSnapshot round, int questionIndex, String pendingUid,
                                               int pendingAnswer, boolean pendingCorrect, long pendingTime) {
        String qKey = String.valueOf(questionIndex);
        Map<String, Object> scored = (Map<String, Object>) round.get("scoredQuestions");
        if (scored != null && Boolean.TRUE.equals(scored.get(qKey))) {
            return;
        }
        String p1 = game.getString("player1Uid");
        String p2 = game.getString("player2Uid");
        Map<String, Object> answers = nestedMap((Map<String, Object>) round.get("answersByQuestion"), qKey);
        Map<String, Object> correctness = nestedMap((Map<String, Object>) round.get("correctnessByQuestion"), qKey);
        Map<String, Object> times = nestedMap((Map<String, Object>) round.get("answerTimesByQuestion"), qKey);
        if (pendingUid != null) {
            answers.put(pendingUid, pendingAnswer);
            correctness.put(pendingUid, pendingCorrect);
            times.put(pendingUid, pendingTime);
        }
        boolean p1Answered = answers.containsKey(p1);
        boolean p2Answered = answers.containsKey(p2);
        boolean p1Correct = Boolean.TRUE.equals(correctness.get(p1));
        boolean p2Correct = Boolean.TRUE.equals(correctness.get(p2));
        int p1Delta = 0;
        int p2Delta = 0;
        if (p1Answered && p2Answered) {
            if (p1Correct && p2Correct) {
                long t1 = longValue(times.get(p1));
                long t2 = longValue(times.get(p2));
                if (t1 <= t2) {
                    p1Delta = 10;
                } else {
                    p2Delta = 10;
                }
            } else {
                p1Delta = p1Correct ? 10 : -5;
                p2Delta = p2Correct ? 10 : -5;
            }
        } else if (p1Answered) {
            p1Delta = p1Correct ? 10 : -5;
        } else if (p2Answered) {
            p2Delta = p2Correct ? 10 : -5;
        }
        if (p1Delta != 0) {
            transaction.update(gameRef, "player1Score", FieldValue.increment(p1Delta),
                    "updatedAt", FieldValue.serverTimestamp());
        }
        if (p2Delta != 0) {
            transaction.update(gameRef, "player2Score", FieldValue.increment(p2Delta),
                    "updatedAt", FieldValue.serverTimestamp());
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("scoredQuestions." + qKey, true);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        if (questionIndex < 4) {
            updates.put("currentQuestionIndex", questionIndex + 1);
            updates.put("questionStartedAt", FieldValue.serverTimestamp());
        } else {
            updates.put("phase", PHASE_FINISHED);
            updates.put("finished", true);
            transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
        }
        Log.d(TAG, "Know it score update question=" + questionIndex + ", p1Delta=" + p1Delta
                + ", p2Delta=" + p2Delta + ", p1Correct=" + p1Correct + ", p2Correct=" + p2Correct);
        transaction.update(roundRef, updates);
    }

    private Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        if (root == null) {
            return new HashMap<>();
        }
        Object value = root.get(key);
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        return new HashMap<>();
    }

    private int correctIndexForQuestion(DocumentSnapshot round, int questionIndex) {
        List<Object> questions = (List<Object>) round.get("questions");
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            return -1;
        }
        Object item = questions.get(questionIndex);
        if (!(item instanceof Map)) {
            return -1;
        }
        Object value = ((Map<String, Object>) item).get("correctAnswerIndex");
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.MAX_VALUE;
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

    public String knowItRoundId() {
        return "know_it_round_1";
    }

    public String skockoRoundId(int roundNumber) {
        return "skocko_round_" + roundNumber;
    }

    public Task<Void> ensureSkockoRound(String gameId, int roundNumber, List<String> allowedSymbols) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(skockoRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!isGameReady(game)) {
                throw new IllegalStateException("Skočko ne može da počne bez dva različita igrača");
            }
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            Map<String, Object> round = new HashMap<>();
            round.put("id", skockoRoundId(roundNumber));
            round.put("gameId", gameId);
            round.put("type", MINI_SKOCKO);
            round.put("roundIndex", roundNumber);
            round.put("activePlayerUid", active);
            round.put("opponentUid", opponent);
            round.put("phase", "ACTIVE_PLAYER");
            round.put("secretCombination", randomSkockoCombination(allowedSymbols));
            round.put("attemptsByPlayer", new HashMap<String, Object>());
            round.put("feedbackByPlayer", new HashMap<String, Object>());
            round.put("currentAttemptIndex", 0);
            round.put("opponentAttempt", null);
            round.put("finished", false);
            round.put("roundScoreAwarded", false);
            round.put("scoredByUid", null);
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            round.put("phaseStartedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Creating skocko round gameId=" + gameId + ", roundId=" + skockoRoundId(roundNumber)
                    + ", activePlayerUid=" + active + ", opponentUid=" + opponent);
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_SKOCKO, "currentPlayerUid", active,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> submitSkockoAttempt(String gameId, int roundNumber, String uid, List<String> symbols) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(skockoRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!round.exists() || Boolean.TRUE.equals(round.getBoolean("finished")) || symbols == null || symbols.size() != 4) {
                return null;
            }
            String phase = round.getString("phase");
            String active = round.getString("activePlayerUid");
            String opponent = round.getString("opponentUid");
            List<String> secret = stringList(round.get("secretCombination"));
            if (secret.size() != 4) {
                throw new IllegalStateException("Skočko runda nema ispravnu tajnu kombinaciju");
            }
            SkockoFeedback feedback = calculateSkockoFeedback(secret, symbols);
            Map<String, Object> attempt = skockoAttempt(symbols, feedback);
            Map<String, Object> feedbackMap = skockoFeedbackMap(feedback);
            Log.d(TAG, "Submit skocko attempt gameId=" + gameId + ", roundId=" + skockoRoundId(roundNumber)
                    + ", uid=" + uid + ", phase=" + phase + ", activePlayerUid=" + active
                    + ", opponentUid=" + opponent + ", attempt=" + symbols + ", feedback=" + feedbackMap);
            if ("ACTIVE_PLAYER".equals(phase)) {
                if (uid == null || !uid.equals(active)) {
                    return null;
                }
                int currentAttemptIndex = intValue(round.get("currentAttemptIndex"));
                if (currentAttemptIndex >= 6) {
                    return null;
                }
                List<Object> attempts = objectListFromPlayerMap(round, "attemptsByPlayer", uid);
                List<Object> feedbacks = objectListFromPlayerMap(round, "feedbackByPlayer", uid);
                attempts.add(attempt);
                feedbacks.add(feedbackMap);
                int nextAttemptIndex = currentAttemptIndex + 1;
                Map<String, Object> updates = new HashMap<>();
                updates.put("attemptsByPlayer." + uid, attempts);
                updates.put("feedbackByPlayer." + uid, feedbacks);
                updates.put("currentAttemptIndex", nextAttemptIndex);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                if (feedback.isCorrect) {
                    int points = skockoScoreForAttempt(nextAttemptIndex);
                    if (!Boolean.TRUE.equals(round.getBoolean("roundScoreAwarded"))) {
                        applyScore(transaction, gameRef, game, uid, points);
                        updates.put("roundScoreAwarded", true);
                        updates.put("scoredByUid", uid);
                        updates.put("awardedPoints", points);
                    }
                    updates.put("phase", "FINISHED");
                    updates.put("finished", true);
                    updates.put("phaseStartedAt", FieldValue.serverTimestamp());
                    finishSkockoGameIfNeeded(transaction, gameRef, roundNumber);
                } else if (nextAttemptIndex >= 6) {
                    updates.put("phase", "OPPONENT_CHANCE");
                    updates.put("phaseStartedAt", FieldValue.serverTimestamp());
                    transaction.update(gameRef, "currentPlayerUid", opponent, "updatedAt", FieldValue.serverTimestamp());
                }
                transaction.update(roundRef, updates);
            } else if ("OPPONENT_CHANCE".equals(phase)) {
                if (uid == null || !uid.equals(opponent) || round.get("opponentAttempt") != null) {
                    return null;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("opponentAttempt", attempt);
                updates.put("attemptsByPlayer." + uid, Collections.singletonList(attempt));
                updates.put("feedbackByPlayer." + uid, Collections.singletonList(feedbackMap));
                updates.put("phase", "FINISHED");
                updates.put("finished", true);
                updates.put("phaseStartedAt", FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                if (feedback.isCorrect && !Boolean.TRUE.equals(round.getBoolean("roundScoreAwarded"))) {
                    applyScore(transaction, gameRef, game, uid, 10);
                    updates.put("roundScoreAwarded", true);
                    updates.put("scoredByUid", uid);
                    updates.put("awardedPoints", 10);
                }
                finishSkockoGameIfNeeded(transaction, gameRef, roundNumber);
                transaction.update(roundRef, updates);
            }
            return null;
        });
    }

    public Task<Void> handleSkockoTimeout(String gameId, int roundNumber, String expectedPhase) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(skockoRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!round.exists() || Boolean.TRUE.equals(round.getBoolean("finished"))
                    || !expectedPhase.equals(round.getString("phase"))) {
                return null;
            }
            Log.d(TAG, "Skocko timeout gameId=" + gameId + ", roundId=" + skockoRoundId(roundNumber)
                    + ", phase=" + expectedPhase + ", currentAttemptIndex=" + round.getLong("currentAttemptIndex"));
            if ("ACTIVE_PLAYER".equals(expectedPhase)) {
                transaction.update(roundRef, "phase", "OPPONENT_CHANCE",
                        "phaseStartedAt", FieldValue.serverTimestamp(), "updatedAt", FieldValue.serverTimestamp());
                transaction.update(gameRef, "currentPlayerUid", round.getString("opponentUid"),
                        "updatedAt", FieldValue.serverTimestamp());
            } else if ("OPPONENT_CHANCE".equals(expectedPhase)) {
                transaction.update(roundRef, "phase", "FINISHED", "finished", true,
                        "phaseStartedAt", FieldValue.serverTimestamp(), "updatedAt", FieldValue.serverTimestamp());
                finishSkockoGameIfNeeded(transaction, gameRef, roundNumber);
            }
            return null;
        });
    }

    private void finishSkockoGameIfNeeded(Transaction transaction, DocumentReference gameRef, int roundNumber) {
        if (roundNumber == 2) {
            transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
        }
    }

    private List<String> randomSkockoCombination(List<String> allowedSymbols) {
        if (allowedSymbols == null || allowedSymbols.isEmpty()) {
            throw new IllegalArgumentException("Skočko nema definisane dozvoljene znakove");
        }
        List<String> combination = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            combination.add(allowedSymbols.get(random.nextInt(allowedSymbols.size())));
        }
        return combination;
    }

    private SkockoFeedback calculateSkockoFeedback(List<String> secret, List<String> attempt) {
        boolean[] secretUsed = new boolean[4];
        boolean[] attemptUsed = new boolean[4];
        int exact = 0;
        for (int i = 0; i < 4; i++) {
            if (secret.get(i).equals(attempt.get(i))) {
                exact++;
                secretUsed[i] = true;
                attemptUsed[i] = true;
            }
        }
        int partial = 0;
        for (int i = 0; i < 4; i++) {
            if (attemptUsed[i]) {
                continue;
            }
            for (int j = 0; j < 4; j++) {
                if (!secretUsed[j] && attempt.get(i).equals(secret.get(j))) {
                    partial++;
                    secretUsed[j] = true;
                    break;
                }
            }
        }
        return new SkockoFeedback(exact, partial);
    }

    private Map<String, Object> skockoAttempt(List<String> symbols, SkockoFeedback feedback) {
        return mapOf("symbols", new ArrayList<>(symbols),
                "exactMatches", feedback.exactMatches,
                "partialMatches", feedback.partialMatches,
                "isCorrect", feedback.isCorrect,
                "submittedAt", Timestamp.now());
    }

    private Map<String, Object> skockoFeedbackMap(SkockoFeedback feedback) {
        return mapOf("exactMatches", feedback.exactMatches,
                "partialMatches", feedback.partialMatches,
                "isCorrect", feedback.isCorrect);
    }

    private int skockoScoreForAttempt(int attemptNumber) {
        if (attemptNumber <= 2) return 20;
        if (attemptNumber <= 4) return 15;
        return 10;
    }

    private List<Object> objectListFromPlayerMap(DocumentSnapshot round, String field, String uid) {
        Map<String, Object> root = (Map<String, Object>) round.get(field);
        if (root == null || !(root.get(uid) instanceof List)) {
            return new ArrayList<>();
        }
        return new ArrayList<>((List<Object>) root.get(uid));
    }

    private List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        }
        return values;
    }

    private static class SkockoFeedback {
        final int exactMatches;
        final int partialMatches;
        final boolean isCorrect;

        SkockoFeedback(int exactMatches, int partialMatches) {
            this.exactMatches = exactMatches;
            this.partialMatches = partialMatches;
            this.isCorrect = exactMatches == 4;
        }
    }

    public Task<Void> seedConnectionQuestionsIfNeeded() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("connectionQuestions").limit(1).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            if (!task.getResult().isEmpty()) {
                return Tasks.forResult(null);
            }
            Log.d(TAG, "Seeding connectionQuestions");
            return Tasks.whenAll(
                    db.collection("connectionQuestions").document("poznati").set(connectionQuestion("Poznate licnosti",
                            Arrays.asList("Tesla", "Andric", "Novak", "Nusic", "Mokranjac"),
                            Arrays.asList("Naucnik", "Pisac", "Teniser", "Dramaturg", "Kompozitor"))),
                    db.collection("connectionQuestions").document("gradovi").set(connectionQuestion("Drzave i gradovi",
                            Arrays.asList("Francuska", "Italija", "Spanija", "Nemacka", "Grcka"),
                            Arrays.asList("Pariz", "Rim", "Madrid", "Berlin", "Atina"))),
                    db.collection("connectionQuestions").document("sport").set(connectionQuestion("Sport i oprema",
                            Arrays.asList("Tenis", "Fudbal", "Kosarka", "Hokej", "Boks"),
                            Arrays.asList("Reket", "Lopta", "Kos", "Pak", "Rukavice"))),
                    db.collection("connectionQuestions").document("hemija").set(connectionQuestion("Elementi i simboli",
                            Arrays.asList("Zlato", "Srebro", "Gvozdje", "Kiseonik", "Vodonik"),
                            Arrays.asList("Au", "Ag", "Fe", "O", "H"))),
                    db.collection("connectionQuestions").document("knjige").set(connectionQuestion("Dela i autori",
                            Arrays.asList("Na Drini cuprija", "Tvrdjava", "Seobe", "Necista krv", "Zona Zamfirova"),
                            Arrays.asList("Ivo Andric", "Mesa Selimovic", "Milos Crnjanski", "Bora Stankovic", "Stevan Sremac"))));
        });
    }

    private Map<String, Object> connectionQuestion(String title, List<String> leftItems, List<String> rightItems) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> correctPairs = new HashMap<>();
        for (int i = 0; i < leftItems.size(); i++) {
            correctPairs.put(String.valueOf(i), i);
        }
        data.put("title", title);
        data.put("leftItems", leftItems);
        data.put("rightItems", rightItems);
        data.put("correctPairs", correctPairs);
        return data;
    }

    public Task<Void> ensureConnectionsRound(String gameId, int roundNumber) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return seedConnectionQuestionsIfNeeded().continueWithTask(seedTask -> {
            if (!seedTask.isSuccessful()) {
                Log.e(TAG, "Seed connection questions failed", seedTask.getException());
                throw seedTask.getException();
            }
            return db.collection("connectionQuestions").get();
        }).continueWithTask(questionTask -> {
            if (!questionTask.isSuccessful()) {
                Log.e(TAG, "Loading connection questions failed", questionTask.getException());
                throw questionTask.getException();
            }
            List<DocumentSnapshot> questions = questionTask.getResult().getDocuments();
            if (questions.isEmpty()) {
                return Tasks.forException(new IllegalStateException("Nema Spojnice zadataka u Firestore-u"));
            }
            DocumentSnapshot question = questions.get(Math.abs(roundNumber - 1) % questions.size());
            String title = question.getString("title");
            List<String> leftItems = (List<String>) question.get("leftItems");
            List<String> rightItems = (List<String>) question.get("rightItems");
            Map<String, Object> correctPairs = (Map<String, Object>) question.get("correctPairs");
            if (leftItems == null || rightItems == null || correctPairs == null
                    || leftItems.size() != 5 || rightItems.size() != 5) {
                return Tasks.forException(new IllegalStateException("Spojnice zadatak nema 5 parova"));
            }
            return createConnectionsRoundIfMissing(gameId, roundNumber, title, leftItems, rightItems, correctPairs, question.getId());
        });
    }

    private Task<Void> createConnectionsRoundIfMissing(String gameId, int roundNumber, String title,
                                                       List<String> leftItems, List<String> rightItems,
                                                       Map<String, Object> correctPairs, String questionId) {
        String roundId = connectionsRoundId(roundNumber);
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!isGameReady(game)) {
                throw new IllegalStateException("Spojnice ne mogu da pocnu bez dva razlicita igraca");
            }
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            ShuffledConnections shuffled = shuffleConnectionRights(rightItems, correctPairs);
            Log.d(TAG, "Creating connections round gameId=" + gameId + ", roundId=" + roundId
                    + ", questionId=" + questionId + ", active=" + active + ", opponent=" + opponent);
            Map<String, Object> round = new HashMap<>();
            round.put("id", roundId);
            round.put("gameId", gameId);
            round.put("type", MINI_CONNECTIONS);
            round.put("roundIndex", roundNumber);
            round.put("title", title == null ? "" : title);
            round.put("activePlayerUid", active);
            round.put("opponentUid", opponent);
            round.put("phase", "ACTIVE_PLAYER");
            round.put("leftItems", leftItems);
            round.put("rightItems", shuffled.rightItems);
            round.put("correctPairs", shuffled.correctPairs);
            round.put("matchedPairs", new HashMap<String, Object>());
            round.put("attemptsByPlayer", new HashMap<String, Object>());
            round.put("usedLeftIndexes", new ArrayList<Integer>());
            round.put("remainingLeftIndexes", Arrays.asList(0, 1, 2, 3, 4));
            round.put("currentLeftIndex", null);
            round.put("currentSelection", null);
            round.put("finished", false);
            round.put("phaseStartedAt", FieldValue.serverTimestamp());
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_CONNECTIONS, "currentPlayerUid", active,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Boolean> submitConnectionPair(String gameId, int roundNumber, String uid, int leftIndex, int rightIndex) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(connectionsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            String phase = round.getString("phase");
            String active = round.getString("activePlayerUid");
            String opponent = round.getString("opponentUid");
            Boolean finished = round.getBoolean("finished");
            if (Boolean.TRUE.equals(finished) || (!"ACTIVE_PLAYER".equals(phase) && !"OPPONENT_CHANCE".equals(phase))) {
                return false;
            }
            boolean canPlay = ("ACTIVE_PLAYER".equals(phase) && uid.equals(active))
                    || ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponent));
            if (!canPlay) {
                return false;
            }
            Map<String, Object> matchedPairs = (Map<String, Object>) round.get("matchedPairs");
            if (matchedPairs != null && matchedPairs.containsKey(String.valueOf(leftIndex))) {
                return false;
            }
            Map<String, Object> attemptsByPlayer = (Map<String, Object>) round.get("attemptsByPlayer");
            Map<String, Object> updatedAttemptsByPlayer = attemptsByPlayer == null
                    ? new HashMap<>()
                    : new HashMap<>(attemptsByPlayer);
            List<Integer> playerAttempts = attemptsByPlayer == null
                    ? new ArrayList<>()
                    : intList(attemptsByPlayer.get(uid));
            if (playerAttempts.contains(leftIndex)) {
                return false;
            }
            playerAttempts.add(leftIndex);
            List<Integer> usedLeftIndexes = intList(round.get("usedLeftIndexes"));
            if (!usedLeftIndexes.contains(leftIndex)) {
                usedLeftIndexes.add(leftIndex);
            }
            int correctRight = intFromMap((Map<String, Object>) round.get("correctPairs"), String.valueOf(leftIndex), -1);
            boolean correct = correctRight == rightIndex;
            Log.d(TAG, "Submit connection pair gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", phase=" + phase + ", selectedLeftIndex=" + leftIndex
                    + ", selectedRightIndex=" + rightIndex + ", correct=" + correct);
            updatedAttemptsByPlayer.put(uid, playerAttempts);
            if (!correct) {
                Map<String, Object> updates = connectionProgressUpdates(round, roundNumber, phase, uid,
                        playerAttempts, intList(round.get("remainingLeftIndexes")));
                updates.put("attemptsByPlayer", updatedAttemptsByPlayer);
                updates.put("usedLeftIndexes", usedLeftIndexes);
                updates.put("currentSelection", null);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(roundRef, updates);
                updateConnectionsGameForPhase(transaction, gameRef, game, round, roundNumber, updates);
                return false;
            }
            Map<String, Object> match = new HashMap<>();
            match.put("rightIndex", rightIndex);
            match.put("uid", uid);
            match.put("correct", true);
            List<Integer> remaining = intList(round.get("remainingLeftIndexes"));
            remaining.remove(Integer.valueOf(leftIndex));
            Map<String, Object> updates = connectionProgressUpdates(round, roundNumber, phase, uid,
                    playerAttempts, remaining);
            updates.put("attemptsByPlayer", updatedAttemptsByPlayer);
            updates.put("usedLeftIndexes", usedLeftIndexes);
            updates.put("matchedPairs." + leftIndex, match);
            updates.put("remainingLeftIndexes", remaining);
            updates.put("currentSelection", null);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(roundRef, updates);
            applyScore(transaction, gameRef, game, uid, 2);
            updateConnectionsGameForPhase(transaction, gameRef, game, round, roundNumber, updates);
            Log.d(TAG, "Connections score update uid=" + uid + ", points=2, remainingLeftIndexes=" + remaining);
            return true;
        });
    }

    public Task<Void> updateConnectionSelection(String gameId, int roundNumber, String uid,
                                                int leftIndex, Integer rightIndex) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference roundRef = db.collection("games").document(gameId)
                .collection("rounds").document(connectionsRoundId(roundNumber));
        Map<String, Object> selection = new HashMap<>();
        selection.put("uid", uid);
        selection.put("leftIndex", leftIndex);
        selection.put("rightIndex", rightIndex);
        selection.put("updatedAt", FieldValue.serverTimestamp());
        return roundRef.update("currentSelection", selection, "updatedAt", FieldValue.serverTimestamp());
    }

    private Map<String, Object> connectionProgressUpdates(DocumentSnapshot round, int roundNumber,
                                                          String phase, String uid,
                                                          List<Integer> playerAttempts,
                                                          List<Integer> remaining) {
        Map<String, Object> updates = new HashMap<>();
        if (remaining.isEmpty()) {
            updates.put("phase", "FINISHED");
            updates.put("finished", true);
            return updates;
        }
        boolean activePlayerDone = "ACTIVE_PLAYER".equals(phase)
                && (playerAttempts.size() >= 5 || playerAttempts.containsAll(remaining));
        boolean opponentDone = "OPPONENT_CHANCE".equals(phase) && playerAttempts.containsAll(remaining);
        if (activePlayerDone) {
            updates.put("phase", "OPPONENT_CHANCE");
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        } else if (opponentDone) {
            updates.put("phase", "FINISHED");
            updates.put("finished", true);
        }
        Log.d(TAG, "Connections progress check round=" + roundNumber
                + ", phase=" + phase + ", uid=" + uid
                + ", playerAttempts=" + playerAttempts
                + ", remainingLeftIndexes=" + remaining
                + ", updates=" + updates);
        return updates;
    }

    private void updateConnectionsGameForPhase(Transaction transaction, DocumentReference gameRef,
                                               DocumentSnapshot game, DocumentSnapshot round,
                                               int roundNumber, Map<String, Object> roundUpdates) {
        Object nextPhase = roundUpdates.get("phase");
        if ("OPPONENT_CHANCE".equals(nextPhase)) {
            transaction.update(gameRef, "currentPlayerUid", round.getString("opponentUid"),
                    "updatedAt", FieldValue.serverTimestamp());
        } else if ("FINISHED".equals(nextPhase) && roundNumber == 2) {
            transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
        }
    }

    public Task<Void> advanceConnectionsPhase(String gameId, int roundNumber, String expectedPhase) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(connectionsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            String phase = round.getString("phase");
            if (!expectedPhase.equals(phase) || Boolean.TRUE.equals(round.getBoolean("finished"))) {
                return null;
            }
            List<Integer> remaining = intList(round.get("remainingLeftIndexes"));
            Map<String, Object> updates = new HashMap<>();
            if ("ACTIVE_PLAYER".equals(phase) && !remaining.isEmpty()) {
                updates.put("phase", "OPPONENT_CHANCE");
                updates.put("phaseStartedAt", FieldValue.serverTimestamp());
                updates.put("currentSelection", null);
                transaction.update(gameRef, "currentPlayerUid", round.getString("opponentUid"),
                        "updatedAt", FieldValue.serverTimestamp());
            } else {
                updates.put("phase", "FINISHED");
                updates.put("finished", true);
                updates.put("currentSelection", null);
                if (roundNumber == 2) {
                    transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
                }
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Advance connections phase gameId=" + gameId + ", round=" + roundNumber
                    + ", oldPhase=" + phase + ", remainingLeftIndexes=" + remaining + ", updates=" + updates);
            transaction.update(roundRef, updates);
            return null;
        });
    }

    private ShuffledConnections shuffleConnectionRights(List<String> rightItems, Map<String, Object> correctPairs) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rightItems.size(); i++) {
            order.add(i);
        }
        Map<String, Object> shuffledPairs = new HashMap<>();
        int fixedPairs = rightItems.size();
        for (int attempt = 0; attempt < 10 && fixedPairs > 2; attempt++) {
            Collections.shuffle(order, random);
            shuffledPairs = remapConnectionPairs(order, correctPairs);
            fixedPairs = countFixedConnectionPairs(shuffledPairs);
        }
        for (int attempt = 0; attempt < rightItems.size() && fixedPairs > 2; attempt++) {
            Collections.rotate(order, 1);
            shuffledPairs = remapConnectionPairs(order, correctPairs);
            fixedPairs = countFixedConnectionPairs(shuffledPairs);
        }
        List<String> shuffledRightItems = new ArrayList<>();
        for (Integer oldIndex : order) {
            shuffledRightItems.add(rightItems.get(oldIndex));
        }
        return new ShuffledConnections(shuffledRightItems, shuffledPairs);
    }

    private Map<String, Object> remapConnectionPairs(List<Integer> order, Map<String, Object> correctPairs) {
        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (int newIndex = 0; newIndex < order.size(); newIndex++) {
            oldToNew.put(order.get(newIndex), newIndex);
        }
        Map<String, Object> shuffledPairs = new HashMap<>();
        for (int leftIndex = 0; leftIndex < order.size(); leftIndex++) {
            int oldRightIndex = intFromMap(correctPairs, String.valueOf(leftIndex), leftIndex);
            Integer newRightIndex = oldToNew.get(oldRightIndex);
            shuffledPairs.put(String.valueOf(leftIndex), newRightIndex == null ? oldRightIndex : newRightIndex);
        }
        return shuffledPairs;
    }

    private int countFixedConnectionPairs(Map<String, Object> correctPairs) {
        int count = 0;
        for (int i = 0; i < correctPairs.size(); i++) {
            if (intFromMap(correctPairs, String.valueOf(i), -1) == i) {
                count++;
            }
        }
        return count;
    }

    private static class ShuffledConnections {
        final List<String> rightItems;
        final Map<String, Object> correctPairs;

        ShuffledConnections(List<String> rightItems, Map<String, Object> correctPairs) {
            this.rightItems = rightItems;
            this.correctPairs = correctPairs;
        }
    }

    public String connectionsRoundId(int roundNumber) {
        return "connections_round_" + roundNumber;
    }

    public Task<Void> seedAssociationQuestionsIfNeeded() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return db.collection("associationQuestions").limit(1).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            if (!task.getResult().isEmpty()) {
                return Tasks.forResult(null);
            }
            Log.d(TAG, "Seeding associationQuestions");
            return Tasks.whenAll(
                    db.collection("associationQuestions").document("godisnja_doba").set(associationQuestion(
                            "Godisnja doba",
                            associationColumn("TOPLO", "LETO", "PLAZA", "SUNCE", "MORE"),
                            associationColumn("HLADNO", "SNEG", "LED", "SKIJANJE", "ZIMA"),
                            associationColumn("VETAR", "MUNJA", "OBLAK", "KISA", "VREME"),
                            associationColumn("JUTRO", "NOC", "KALENDAR", "MESEC", "DAN"))),
                    db.collection("associationQuestions").document("sport").set(associationQuestion(
                            "Sport",
                            associationColumn("REKET", "MREZA", "SERVIS", "SET", "TENIS"),
                            associationColumn("GOL", "KOPACKE", "STADION", "LOPTA", "FUDBAL"),
                            associationColumn("KOS", "TABLA", "DRIBLING", "TROJKA", "KOSARKA"),
                            associationColumn("PAK", "LED", "PALICA", "KLIZALJKE", "HOKEJ"))),
                    db.collection("associationQuestions").document("srbija").set(associationQuestion(
                            "Srbija",
                            associationColumn("KALEMEGDAN", "SAVA", "DUNAV", "AVALA", "BEOGRAD"),
                            associationColumn("PETROVARADIN", "EXIT", "DUNAVSKA", "KEJ", "NOVI SAD"),
                            associationColumn("MERAK", "TVRDJAVA", "CAIR", "NISAVA", "NIS"),
                            associationColumn("ZLATIBOR", "TARA", "KOPAONIK", "RTANJ", "PLANINA"))),
                    db.collection("associationQuestions").document("film").set(associationQuestion(
                            "Film",
                            associationColumn("KAMERA", "SCENA", "REZISER", "GLUMAC", "FILM"),
                            associationColumn("OSKAR", "CRVENI TEPIH", "STATUA", "NAGRADA", "HOLIVUD"),
                            associationColumn("KOKICE", "PLATNO", "SEDISTA", "PROJEKTOR", "BIOSKOP"),
                            associationColumn("EPIZODA", "SEZONA", "LIK", "ZAPLET", "SERIJA"))),
                    db.collection("associationQuestions").document("muzika").set(associationQuestion(
                            "Muzika",
                            associationColumn("DIRKE", "PEDALE", "KONCERT", "KLAVIR", "PIJANINO"),
                            associationColumn("ZICE", "TRZALICA", "AKORD", "SOLO", "GITARA"),
                            associationColumn("RITAM", "PALICE", "CINELE", "BUBANJ", "BUBNJEVI"),
                            associationColumn("GLAS", "TEKST", "REFREN", "STROFA", "PESMA"))));
        });
    }

    private Map<String, Object> associationQuestion(String finalAnswer, Map<String, Object> c1,
                                                    Map<String, Object> c2, Map<String, Object> c3,
                                                    Map<String, Object> c4) {
        Map<String, Object> data = new HashMap<>();
        data.put("finalAnswer", finalAnswer);
        data.put("columns", Arrays.asList(c1, c2, c3, c4));
        return data;
    }

    private Map<String, Object> associationColumn(String clue1, String clue2, String clue3, String clue4,
                                                  String answer) {
        Map<String, Object> column = new HashMap<>();
        column.put("clues", Arrays.asList(clue1, clue2, clue3, clue4));
        column.put("columnAnswer", answer);
        return column;
    }

    public Task<Void> ensureAssociationsRound(String gameId, int roundNumber) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return seedAssociationQuestionsIfNeeded().continueWithTask(seedTask -> {
            if (!seedTask.isSuccessful()) {
                throw seedTask.getException();
            }
            return db.collection("associationQuestions").get();
        }).continueWithTask(questionTask -> {
            if (!questionTask.isSuccessful()) {
                throw questionTask.getException();
            }
            List<DocumentSnapshot> questions = questionTask.getResult().getDocuments();
            if (questions.isEmpty()) {
                return Tasks.forException(new IllegalStateException("Nema Asocijacije zadataka u Firestore-u"));
            }
            DocumentSnapshot question = questions.get(Math.abs(roundNumber - 1) % questions.size());
            List<Object> columns = (List<Object>) question.get("columns");
            String finalAnswer = question.getString("finalAnswer");
            if (columns == null || columns.size() != 4 || finalAnswer == null || finalAnswer.trim().isEmpty()) {
                return Tasks.forException(new IllegalStateException("Asocijacije zadatak nije ispravan"));
            }
            return createAssociationsRoundIfMissing(gameId, roundNumber, columns, finalAnswer, question.getId());
        });
    }

    private Task<Void> createAssociationsRoundIfMissing(String gameId, int roundNumber, List<Object> columns,
                                                        String finalAnswer, String questionId) {
        String roundId = associationsRoundId(roundNumber);
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(roundId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot existing = transaction.get(roundRef);
            if (existing.exists()) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            if (!isGameReady(game)) {
                throw new IllegalStateException("Asocijacije ne mogu da pocnu bez dva igraca");
            }
            String active = roundNumber == 1 ? p1 : p2;
            String opponent = roundNumber == 1 ? p2 : p1;
            Map<String, Object> round = new HashMap<>();
            round.put("id", roundId);
            round.put("gameId", gameId);
            round.put("type", MINI_ASSOCIATIONS);
            round.put("roundIndex", roundNumber);
            round.put("activePlayerUid", active);
            round.put("opponentUid", opponent);
            round.put("currentTurnUid", active);
            round.put("phase", PHASE_PLAYING);
            round.put("columns", columns);
            round.put("finalAnswer", finalAnswer);
            round.put("openedFields", new HashMap<String, Object>());
            round.put("solvedColumns", new HashMap<String, Object>());
            round.put("solvedColumnAnswers", new HashMap<String, Object>());
            round.put("finalSolvedByUid", null);
            round.put("lastOpenedByUid", null);
            round.put("mustGuessAfterOpen", false);
            round.put("canContinueGuessingUid", null);
            round.put("roundAwardedPoints", 0);
            round.put("finished", false);
            round.put("phaseStartedAt", FieldValue.serverTimestamp());
            round.put("createdAt", FieldValue.serverTimestamp());
            round.put("updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Creating associations round gameId=" + gameId + ", roundId=" + roundId
                    + ", questionId=" + questionId + ", active=" + active + ", opponent=" + opponent);
            transaction.set(roundRef, round);
            transaction.set(gameRef, mapOf("currentMiniGame", MINI_ASSOCIATIONS, "currentPlayerUid", active,
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            return null;
        });
    }

    public Task<Boolean> openAssociationField(String gameId, int roundNumber, String uid, int columnIndex, int fieldIndex) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(associationsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!canAssociationPlayerAct(round, uid)) {
                return false;
            }
            if (Boolean.TRUE.equals(round.getBoolean("mustGuessAfterOpen"))) {
                return false;
            }
            if (columnIndex < 0 || columnIndex > 3 || fieldIndex < 0 || fieldIndex > 3) {
                return false;
            }
            Map<String, Object> solved = (Map<String, Object>) round.get("solvedColumns");
            if (solved != null && solved.containsKey(String.valueOf(columnIndex))) {
                return false;
            }
            String key = associationFieldKey(columnIndex, fieldIndex);
            Map<String, Object> opened = (Map<String, Object>) round.get("openedFields");
            if (opened != null && Boolean.TRUE.equals(opened.get(key))) {
                return false;
            }
            Log.d(TAG, "Open association field gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", key=" + key);
            transaction.update(roundRef, "openedFields." + key, true,
                    "lastOpenedByUid", uid,
                    "mustGuessAfterOpen", true,
                    "canContinueGuessingUid", uid,
                    "currentTurnUid", uid,
                    "updatedAt", FieldValue.serverTimestamp());
            transaction.update(gameRef, "currentPlayerUid", uid, "updatedAt", FieldValue.serverTimestamp());
            return true;
        });
    }

    public Task<Void> endAssociationTurn(String gameId, int roundNumber, String uid) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(associationsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (!canAssociationPlayerAct(round, uid)) {
                return null;
            }
            String next = associationOtherPlayer(round, uid);
            Log.d(TAG, "End association turn gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", next=" + next);
            transaction.update(roundRef,
                    "currentTurnUid", next,
                    "mustGuessAfterOpen", false,
                    "canContinueGuessingUid", null,
                    "updatedAt", FieldValue.serverTimestamp());
            transaction.update(gameRef, "currentPlayerUid", next, "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Boolean> guessAssociationColumn(String gameId, int roundNumber, String uid, int columnIndex, String answer) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(associationsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!canAssociationPlayerAct(round, uid) || columnIndex < 0 || columnIndex > 3) {
                return false;
            }
            Map<String, Object> solved = (Map<String, Object>) round.get("solvedColumns");
            String colKey = String.valueOf(columnIndex);
            if (solved != null && solved.containsKey(colKey)) {
                return false;
            }
            String expected = associationColumnAnswer(round, columnIndex);
            boolean correct = expected != null && expected.trim().equalsIgnoreCase(answer == null ? "" : answer.trim());
            Log.d(TAG, "Guess association column gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", column=" + columnIndex + ", correct=" + correct);
            if (!correct) {
                String next = associationOtherPlayer(round, uid);
                transaction.update(roundRef,
                        "currentTurnUid", next,
                        "mustGuessAfterOpen", false,
                        "canContinueGuessingUid", null,
                        "updatedAt", FieldValue.serverTimestamp());
                transaction.update(gameRef, "currentPlayerUid", next, "updatedAt", FieldValue.serverTimestamp());
                return false;
            }
            int points = Math.min(calculateAssociationColumnPoints(round, columnIndex),
                    Math.max(0, 30 - intValue(round.get("roundAwardedPoints"))));
            Map<String, Object> updates = new HashMap<>();
            updates.put("solvedColumns." + colKey, uid);
            updates.put("solvedColumnAnswers." + colKey, true);
            updates.put("roundAwardedPoints", intValue(round.get("roundAwardedPoints")) + points);
            updates.put("currentTurnUid", uid);
            updates.put("mustGuessAfterOpen", false);
            updates.put("canContinueGuessingUid", uid);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(roundRef, updates);
            if (points > 0) {
                applyScore(transaction, gameRef, game, uid, points);
            }
            transaction.update(gameRef, "currentPlayerUid", uid, "updatedAt", FieldValue.serverTimestamp());
            Log.d(TAG, "Association column score uid=" + uid + ", points=" + points);
            return true;
        });
    }

    public Task<Boolean> guessAssociationFinal(String gameId, int roundNumber, String uid, String answer) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(associationsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!canAssociationPlayerAct(round, uid) || round.getString("finalSolvedByUid") != null) {
                return false;
            }
            String expected = round.getString("finalAnswer");
            boolean correct = expected != null && expected.trim().equalsIgnoreCase(answer == null ? "" : answer.trim());
            Log.d(TAG, "Guess association final gameId=" + gameId + ", round=" + roundNumber
                    + ", uid=" + uid + ", correct=" + correct);
            if (!correct) {
                String next = associationOtherPlayer(round, uid);
                transaction.update(roundRef,
                        "currentTurnUid", next,
                        "mustGuessAfterOpen", false,
                        "canContinueGuessingUid", null,
                        "updatedAt", FieldValue.serverTimestamp());
                transaction.update(gameRef, "currentPlayerUid", next, "updatedAt", FieldValue.serverTimestamp());
                return false;
            }
            int points = calculateAssociationFinalPoints(round);
            Map<String, Object> updates = new HashMap<>();
            updates.put("finalSolvedByUid", uid);
            updates.put("roundAwardedPoints", intValue(round.get("roundAwardedPoints")) + points);
            updates.put("currentTurnUid", uid);
            updates.put("mustGuessAfterOpen", false);
            updates.put("canContinueGuessingUid", null);
            updates.put("phase", PHASE_FINISHED);
            updates.put("finished", true);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(roundRef, updates);
            if (points > 0) {
                applyScore(transaction, gameRef, game, uid, points);
            }
            if (roundNumber == 2) {
                transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
            }
            Log.d(TAG, "Association final score uid=" + uid + ", points=" + points);
            return true;
        });
    }

    public Task<Void> finishAssociationsRound(String gameId, int roundNumber) {
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document(associationsRoundId(roundNumber));
        return db.runTransaction(transaction -> {
            DocumentSnapshot round = transaction.get(roundRef);
            if (Boolean.TRUE.equals(round.getBoolean("finished")) || PHASE_FINISHED.equals(round.getString("phase"))) {
                return null;
            }
            Log.d(TAG, "Finish associations round by timeout gameId=" + gameId + ", round=" + roundNumber);
            transaction.update(roundRef, "phase", PHASE_FINISHED, "finished", true,
                    "mustGuessAfterOpen", false, "canContinueGuessingUid", null,
                    "updatedAt", FieldValue.serverTimestamp());
            if (roundNumber == 2) {
                transaction.update(gameRef, "status", "finished", "updatedAt", FieldValue.serverTimestamp());
            }
            return null;
        });
    }

    public String associationsRoundId(int roundNumber) {
        return "associations_round_" + roundNumber;
    }

    private boolean canAssociationPlayerAct(DocumentSnapshot round, String uid) {
        return round != null && round.exists()
                && !Boolean.TRUE.equals(round.getBoolean("finished"))
                && PHASE_PLAYING.equals(round.getString("phase"))
                && uid != null && uid.equals(round.getString("currentTurnUid"));
    }

    private String associationOtherPlayer(DocumentSnapshot round, String uid) {
        String active = round.getString("activePlayerUid");
        String opponent = round.getString("opponentUid");
        return uid != null && uid.equals(active) ? opponent : active;
    }

    private String associationFieldKey(int columnIndex, int fieldIndex) {
        return columnIndex + "_" + fieldIndex;
    }

    private String associationColumnAnswer(DocumentSnapshot round, int columnIndex) {
        List<Object> columns = (List<Object>) round.get("columns");
        if (columns == null || columnIndex < 0 || columnIndex >= columns.size()
                || !(columns.get(columnIndex) instanceof Map)) {
            return null;
        }
        Object answer = ((Map<String, Object>) columns.get(columnIndex)).get("columnAnswer");
        return answer == null ? null : String.valueOf(answer);
    }

    private int calculateAssociationColumnPoints(DocumentSnapshot round, int columnIndex) {
        int opened = associationOpenedCount(round, columnIndex);
        return 2 + Math.max(0, 4 - opened);
    }

    private int calculateAssociationFinalPoints(DocumentSnapshot round) {
        int points = 7;
        Map<String, Object> solved = (Map<String, Object>) round.get("solvedColumns");
        for (int col = 0; col < 4; col++) {
            if (solved != null && solved.containsKey(String.valueOf(col))) {
                continue;
            }
            int opened = associationOpenedCount(round, col);
            points += opened == 0 ? 6 : 2 + Math.max(0, 4 - opened);
        }
        int available = Math.max(0, 30 - intValue(round.get("roundAwardedPoints")));
        return Math.min(points, available);
    }

    private int associationOpenedCount(DocumentSnapshot round, int columnIndex) {
        Map<String, Object> opened = (Map<String, Object>) round.get("openedFields");
        int count = 0;
        for (int i = 0; i < 4; i++) {
            if (opened != null && Boolean.TRUE.equals(opened.get(associationFieldKey(columnIndex, i)))) {
                count++;
            }
        }
        return count;
    }

    private int intFromMap(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private List<Integer> intList(Object raw) {
        List<Integer> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item instanceof Number) {
                    values.add(((Number) item).intValue());
                }
            }
        }
        return values;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
