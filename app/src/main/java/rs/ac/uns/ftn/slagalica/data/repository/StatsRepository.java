package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class StatsRepository {
    private static final String TAG = "StatsRepository";
    private static final String GAME_KNOW_IT = "Ko zna zna";
    private static final String GAME_MY_NUMBER = "Moj broj";
    private static final String GAME_STEP = "Korak po korak";
    private static final String GAME_CONNECTIONS = "Spojnice";
    private static final String GAME_ASSOCIATIONS = "Asocijacije";
    private static final String GAME_SKOCKO = "Skocko";

    private final FirebaseFirestore db;

    public StatsRepository(Context context) {
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

    public Task<Void> ensureDefaultStats(String uid) {
        if (db == null || uid == null || uid.isEmpty()) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        DocumentReference userRef = db.collection("users").document(uid);
        return db.runTransaction(transaction -> {
            DocumentReference summaryRef = userRef.collection("stats").document("summary");
            DocumentReference knowItRef = userRef.collection("stats").document("ko_zna_zna");
            DocumentReference myNumberRef = userRef.collection("stats").document("moj_broj");
            DocumentReference stepRef = userRef.collection("stats").document("korak_po_korak");
            DocumentReference connectionsRef = userRef.collection("stats").document("spojnice");
            DocumentReference associationsRef = userRef.collection("stats").document("asocijacije");
            DocumentReference skockoRef = userRef.collection("stats").document("skocko");
            DocumentSnapshot summary = transaction.get(summaryRef);
            DocumentSnapshot knowIt = transaction.get(knowItRef);
            DocumentSnapshot myNumber = transaction.get(myNumberRef);
            DocumentSnapshot step = transaction.get(stepRef);
            DocumentSnapshot connections = transaction.get(connectionsRef);
            DocumentSnapshot associations = transaction.get(associationsRef);
            DocumentSnapshot skocko = transaction.get(skockoRef);
            setIfMissing(transaction, summaryRef, summary, defaultSummary());
            setIfMissing(transaction, knowItRef, knowIt, defaultKnowIt());
            setIfMissing(transaction, myNumberRef, myNumber, defaultMyNumber());
            setIfMissing(transaction, stepRef, step, defaultStep());
            setIfMissing(transaction, connectionsRef, connections, defaultConnections());
            setIfMissing(transaction, associationsRef, associations, defaultAssociations());
            setIfMissing(transaction, skockoRef, skocko, defaultSkocko());
            return null;
        });
    }

    public Task<Void> recordKnowItGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference roundRef = gameRef.collection("rounds").document("know_it_round_1");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot round = transaction.get(roundRef);
            if (!isFinishedGame(game) || Boolean.TRUE.equals(game.getBoolean("statsApplied_ko_zna_zna"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "ko_zna_zna");
            StatsDocs p2Docs = readStats(transaction, p2, "ko_zna_zna");
            KnowItResult r1 = knowItResult(round, p1);
            KnowItResult r2 = knowItResult(round, p2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeKnowIt(transaction, p1Docs, r1, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeKnowIt(transaction, p2Docs, r2, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_ko_zna_zna", true), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> recordMyNumberGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference r1Ref = gameRef.collection("rounds").document("my_number_round_1");
        DocumentReference r2Ref = gameRef.collection("rounds").document("my_number_round_2");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot r1 = transaction.get(r1Ref);
            DocumentSnapshot r2 = transaction.get(r2Ref);
            if (!isFinishedGame(game) || Boolean.TRUE.equals(game.getBoolean("statsApplied_moj_broj"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "moj_broj");
            StatsDocs p2Docs = readStats(transaction, p2, "moj_broj");
            MyNumberResult p1Result = myNumberResult(p1, r1, r2);
            MyNumberResult p2Result = myNumberResult(p2, r1, r2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeMyNumber(transaction, p1Docs, p1Result, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeMyNumber(transaction, p2Docs, p2Result, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_moj_broj", true), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> recordStepGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference r1Ref = gameRef.collection("rounds").document("step_round_1");
        DocumentReference r2Ref = gameRef.collection("rounds").document("step_round_2");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot r1 = transaction.get(r1Ref);
            DocumentSnapshot r2 = transaction.get(r2Ref);
            if (!isFinishedRound(r2) || Boolean.TRUE.equals(game.getBoolean("statsApplied_korak_po_korak"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "korak_po_korak");
            StatsDocs p2Docs = readStats(transaction, p2, "korak_po_korak");
            StepResult p1Result = stepResult(p1, r1, r2);
            StepResult p2Result = stepResult(p2, r1, r2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeStep(transaction, p1Docs, p1Result, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeStep(transaction, p2Docs, p2Result, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_korak_po_korak", true), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> recordConnectionsGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference r1Ref = gameRef.collection("rounds").document("connections_round_1");
        DocumentReference r2Ref = gameRef.collection("rounds").document("connections_round_2");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot r1 = transaction.get(r1Ref);
            DocumentSnapshot r2 = transaction.get(r2Ref);
            if (!isFinishedGame(game) || Boolean.TRUE.equals(game.getBoolean("statsApplied_spojnice"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "spojnice");
            StatsDocs p2Docs = readStats(transaction, p2, "spojnice");
            ConnectionsResult p1Result = connectionsResult(p1, r1, r2);
            ConnectionsResult p2Result = connectionsResult(p2, r1, r2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeConnections(transaction, p1Docs, p1Result, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeConnections(transaction, p2Docs, p2Result, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_spojnice", true), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> recordAssociationsGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference r1Ref = gameRef.collection("rounds").document("associations_round_1");
        DocumentReference r2Ref = gameRef.collection("rounds").document("associations_round_2");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot r1 = transaction.get(r1Ref);
            DocumentSnapshot r2 = transaction.get(r2Ref);
            if (!isFinishedGame(game) || Boolean.TRUE.equals(game.getBoolean("statsApplied_asocijacije"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "asocijacije");
            StatsDocs p2Docs = readStats(transaction, p2, "asocijacije");
            AssociationsResult p1Result = associationsResult(p1, r1, r2);
            AssociationsResult p2Result = associationsResult(p2, r1, r2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeAssociations(transaction, p1Docs, p1Result, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeAssociations(transaction, p2Docs, p2Result, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_asocijacije", true), SetOptions.merge());
            return null;
        });
    }

    public Task<Void> recordSkockoGame(String gameId) {
        if (db == null || gameId == null || gameId.isEmpty()) {
            return Tasks.forResult(null);
        }
        DocumentReference gameRef = db.collection("games").document(gameId);
        DocumentReference r1Ref = gameRef.collection("rounds").document("skocko_round_1");
        DocumentReference r2Ref = gameRef.collection("rounds").document("skocko_round_2");
        return db.runTransaction(transaction -> {
            DocumentSnapshot game = transaction.get(gameRef);
            DocumentSnapshot r1 = transaction.get(r1Ref);
            DocumentSnapshot r2 = transaction.get(r2Ref);
            if (!isFinishedGame(game) || Boolean.TRUE.equals(game.getBoolean("statsApplied_skocko"))) {
                return null;
            }
            String p1 = game.getString("player1Uid");
            String p2 = game.getString("player2Uid");
            StatsDocs p1Docs = readStats(transaction, p1, "skocko");
            StatsDocs p2Docs = readStats(transaction, p2, "skocko");
            SkockoResult p1Result = skockoResult(p1, r1, r2);
            SkockoResult p2Result = skockoResult(p2, r1, r2);
            int p1Score = intValue(game.get("player1Score"));
            int p2Score = intValue(game.get("player2Score"));
            writeSkocko(transaction, p1Docs, p1Result, p1Score, p1Score > p2Score, p1Score < p2Score);
            writeSkocko(transaction, p2Docs, p2Result, p2Score, p2Score > p1Score, p2Score < p1Score);
            transaction.set(gameRef, mapOf("statsApplied_skocko", true), SetOptions.merge());
            return null;
        });
    }

    private void setIfMissing(Transaction transaction, DocumentReference ref, DocumentSnapshot snapshot, Map<String, Object> defaults) {
        if (!snapshot.exists()) {
            transaction.set(ref, defaults, SetOptions.merge());
        }
    }

    private StatsDocs readStats(Transaction transaction, String uid, String docId) throws FirebaseFirestoreException {
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference summaryRef = userRef.collection("stats").document("summary");
        DocumentReference specificRef = userRef.collection("stats").document(docId);
        return new StatsDocs(uid, summaryRef, specificRef, transaction.get(summaryRef), transaction.get(specificRef));
    }

    private void writeSummary(Transaction transaction, StatsDocs docs, String gameName, int score, boolean won, boolean lost) {
        long gamesPlayed = longValue(docs.summary.get("gamesPlayed")) + 1;
        long gamesWon = longValue(docs.summary.get("gamesWon")) + (won ? 1 : 0);
        long gamesLost = longValue(docs.summary.get("gamesLost")) + (lost ? 1 : 0);
        long totalScore = longValue(docs.summary.get("totalScore")) + score;
        Map<String, Object> totalScores = mapFrom(docs.summary, "totalScoresByGame");
        Map<String, Object> played = mapFrom(docs.summary, "playedByGame");
        Map<String, Object> averages = mapFrom(docs.summary, "averageScoresByGame");
        long gameTotal = longValue(totalScores.get(gameName)) + score;
        long gamePlayed = longValue(played.get(gameName)) + 1;
        totalScores.put(gameName, gameTotal);
        played.put(gameName, gamePlayed);
        averages.put(gameName, gamePlayed == 0 ? 0 : (gameTotal * 1.0) / gamePlayed);
        transaction.set(docs.summaryRef, mapOf(
                "gamesPlayed", gamesPlayed,
                "gamesWon", gamesWon,
                "gamesLost", gamesLost,
                "winPercent", percent(gamesWon, gamesPlayed),
                "lossPercent", percent(gamesLost, gamesPlayed),
                "totalScore", totalScore,
                "totalScoresByGame", totalScores,
                "playedByGame", played,
                "averageScoresByGame", averages
        ), SetOptions.merge());
    }

    private void writeKnowIt(Transaction transaction, StatsDocs docs, KnowItResult result, int score, boolean won, boolean lost) {
        long correct = longValue(docs.specific.get("correctAnswers")) + result.correct;
        long wrong = longValue(docs.specific.get("wrongAnswers")) + result.wrong;
        long unanswered = longValue(docs.specific.get("unansweredQuestions")) + result.unanswered;
        long total = longValue(docs.specific.get("totalQuestions")) + result.total;
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "correctAnswers", correct,
                "wrongAnswers", wrong,
                "unansweredQuestions", unanswered,
                "totalQuestions", total,
                "correctPercent", percent(correct, total),
                "wrongPercent", percent(wrong, total),
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_KNOW_IT, score, won, lost);
    }

    private void writeMyNumber(Transaction transaction, StatsDocs docs, MyNumberResult result, int score, boolean won, boolean lost) {
        long rounds = longValue(docs.specific.get("roundsPlayed")) + result.roundsPlayed;
        long exact = longValue(docs.specific.get("exactHits")) + result.exactHits;
        long nonExact = longValue(docs.specific.get("nonExactAttempts")) + result.nonExactAttempts;
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "roundsPlayed", rounds,
                "exactHits", exact,
                "nonExactAttempts", nonExact,
                "exactHitPercent", percent(exact, rounds),
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_MY_NUMBER, score, won, lost);
    }

    private void writeStep(Transaction transaction, StatsDocs docs, StepResult result, int score, boolean won, boolean lost) {
        Map<String, Object> attempts = mergeStepMap(mapFrom(docs.specific, "attemptsByStep"), result.attemptsByStep);
        Map<String, Object> hits = mergeStepMap(mapFrom(docs.specific, "hitsByStep"), result.hitsByStep);
        Map<String, Object> percentByStep = new HashMap<>();
        for (int i = 1; i <= 7; i++) {
            String key = String.valueOf(i);
            percentByStep.put(key, percent(longValue(hits.get(key)), longValue(attempts.get(key))));
        }
        long opponentHits = longValue(docs.specific.get("opponentChanceHits")) + result.opponentChanceHits;
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "attemptsByStep", attempts,
                "hitsByStep", hits,
                "percentByStep", percentByStep,
                "opponentChanceHits", opponentHits,
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_STEP, score, won, lost);
    }

    private void writeConnections(Transaction transaction, StatsDocs docs, ConnectionsResult result, int score, boolean won, boolean lost) {
        long attempted = longValue(docs.specific.get("attemptedPairs")) + result.attemptedPairs;
        long successful = longValue(docs.specific.get("successfulPairs")) + result.successfulPairs;
        long failed = longValue(docs.specific.get("failedPairs")) + result.failedPairs;
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "attemptedPairs", attempted,
                "successfulPairs", successful,
                "failedPairs", failed,
                "successPercent", percent(successful, attempted),
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_CONNECTIONS, score, won, lost);
    }

    private void writeAssociations(Transaction transaction, StatsDocs docs, AssociationsResult result, int score, boolean won, boolean lost) {
        long solved = longValue(docs.specific.get("solved")) + result.solved;
        long unsolved = longValue(docs.specific.get("unsolved")) + result.unsolved;
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "solved", solved,
                "unsolved", unsolved,
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_ASSOCIATIONS, score, won, lost);
    }

    private void writeSkocko(Transaction transaction, StatsDocs docs, SkockoResult result, int score, boolean won, boolean lost) {
        Map<String, Object> attempts = mergeStepMap(mapFrom(docs.specific, "attemptsByAttempt"), result.attemptsByAttempt);
        Map<String, Object> hits = mergeStepMap(mapFrom(docs.specific, "hitsByAttempt"), result.hitsByAttempt);
        Map<String, Object> percentByAttempt = new HashMap<>();
        for (int i = 1; i <= 6; i++) {
            String key = String.valueOf(i);
            percentByAttempt.put(key, percent(longValue(hits.get(key)), longValue(attempts.get(key))));
        }
        long games = longValue(docs.specific.get("gamesPlayed")) + 1;
        long scoreTotal = longValue(docs.specific.get("totalScore")) + score;
        transaction.set(docs.specificRef, mapOf(
                "attemptsByAttempt", attempts,
                "hitsByAttempt", hits,
                "percentByAttempt", percentByAttempt,
                "gamesPlayed", games,
                "totalScore", scoreTotal,
                "averageScore", scoreTotal * 1.0 / games
        ), SetOptions.merge());
        writeSummary(transaction, docs, GAME_SKOCKO, score, won, lost);
    }

    private KnowItResult knowItResult(DocumentSnapshot round, String uid) {
        int correct = 0;
        int wrong = 0;
        int unanswered = 0;
        for (int i = 0; i < 5; i++) {
            Map<String, Object> answers = nestedMap((Map<String, Object>) round.get("answersByQuestion"), String.valueOf(i));
            Map<String, Object> correctness = nestedMap((Map<String, Object>) round.get("correctnessByQuestion"), String.valueOf(i));
            if (!answers.containsKey(uid)) {
                unanswered++;
            } else if (Boolean.TRUE.equals(correctness.get(uid))) {
                correct++;
            } else {
                wrong++;
            }
        }
        return new KnowItResult(correct, wrong, unanswered, 5);
    }

    private MyNumberResult myNumberResult(String uid, DocumentSnapshot... rounds) {
        int played = 0;
        int exact = 0;
        int nonExact = 0;
        for (DocumentSnapshot round : rounds) {
            if (round == null || !round.exists()) continue;
            played++;
            int target = intValue(round.get("targetNumber"));
            Map<String, Object> results = (Map<String, Object>) round.get("resultsByPlayer");
            Map<String, Object> valid = (Map<String, Object>) round.get("validByPlayer");
            boolean isValid = valid != null && Boolean.TRUE.equals(valid.get(uid));
            double result = results == null || !(results.get(uid) instanceof Number) ? 0 : ((Number) results.get(uid)).doubleValue();
            if (isValid && Math.abs(result - target) < 0.000001) {
                exact++;
            } else {
                nonExact++;
            }
        }
        return new MyNumberResult(played, exact, nonExact);
    }

    private StepResult stepResult(String uid, DocumentSnapshot... rounds) {
        StepResult result = new StepResult();
        for (DocumentSnapshot round : rounds) {
            if (round == null || !round.exists()) continue;
            int step = Math.max(1, Math.min(7, intValue(round.get("openedStepIndex")) + 1));
            String active = round.getString("activePlayerUid");
            String winner = round.getString("winnerUid");
            boolean isActive = uid != null && uid.equals(active);
            boolean isWinner = uid != null && uid.equals(winner);
            if (isActive) {
                increment(result.attemptsByStep, step);
                if (isWinner) {
                    increment(result.hitsByStep, step);
                }
            } else if (isWinner) {
                result.opponentChanceHits++;
            }
        }
        return result;
    }

    private ConnectionsResult connectionsResult(String uid, DocumentSnapshot... rounds) {
        int attempted = 0;
        int successful = 0;
        for (DocumentSnapshot round : rounds) {
            if (round == null || !round.exists()) continue;
            Map<String, Object> attempts = (Map<String, Object>) round.get("attemptsByPlayer");
            attempted += intListSize(attempts == null ? null : attempts.get(uid));
            Map<String, Object> matched = (Map<String, Object>) round.get("matchedPairs");
            if (matched != null) {
                for (Object value : matched.values()) {
                    if (value instanceof Map && uid != null && uid.equals(((Map<String, Object>) value).get("uid"))) {
                        successful++;
                    }
                }
            }
        }
        return new ConnectionsResult(attempted, successful, Math.max(0, attempted - successful));
    }

    private AssociationsResult associationsResult(String uid, DocumentSnapshot... rounds) {
        int solved = 0;
        int unsolved = 0;
        for (DocumentSnapshot round : rounds) {
            if (round == null || !round.exists()) continue;
            String finalSolvedByUid = round.getString("finalSolvedByUid");
            if (uid != null && uid.equals(finalSolvedByUid)) {
                solved++;
            } else {
                unsolved++;
            }
        }
        return new AssociationsResult(solved, unsolved);
    }

    private SkockoResult skockoResult(String uid, DocumentSnapshot... rounds) {
        SkockoResult result = new SkockoResult();
        for (DocumentSnapshot round : rounds) {
            if (round == null || !round.exists() || uid == null || !uid.equals(round.getString("activePlayerUid"))) {
                continue;
            }
            Map<String, Object> attemptsByPlayer = (Map<String, Object>) round.get("attemptsByPlayer");
            Object rawAttempts = attemptsByPlayer == null ? null : attemptsByPlayer.get(uid);
            if (!(rawAttempts instanceof java.util.List) || ((java.util.List<?>) rawAttempts).isEmpty()) {
                continue;
            }
            java.util.List<?> attempts = (java.util.List<?>) rawAttempts;
            int attemptNumber = Math.max(1, Math.min(6, attempts.size()));
            increment(result.attemptsByAttempt, attemptNumber);
            Object lastAttempt = attempts.get(attempts.size() - 1);
            if (lastAttempt instanceof Map && Boolean.TRUE.equals(((Map<String, Object>) lastAttempt).get("isCorrect"))) {
                increment(result.hitsByAttempt, attemptNumber);
            }
        }
        return result;
    }

    private boolean isFinishedGame(DocumentSnapshot game) {
        return game != null && game.exists() && "finished".equals(game.getString("status"));
    }

    private boolean isFinishedRound(DocumentSnapshot round) {
        return round != null && round.exists()
                && (Boolean.TRUE.equals(round.getBoolean("finished")) || "FINISHED".equals(round.getString("phase")));
    }

    private Map<String, Object> mergeStepMap(Map<String, Object> current, Map<String, Long> delta) {
        for (int i = 1; i <= 7; i++) {
            String key = String.valueOf(i);
            current.put(key, longValue(current.get(key)) + (delta.containsKey(key) ? delta.get(key) : 0));
        }
        return current;
    }

    private void increment(Map<String, Long> map, int step) {
        String key = String.valueOf(step);
        map.put(key, (map.containsKey(key) ? map.get(key) : 0) + 1);
    }

    private int intListSize(Object raw) {
        return raw instanceof java.util.List ? ((java.util.List<?>) raw).size() : 0;
    }

    private Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        if (root == null || !(root.get(key) instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) root.get(key);
    }

    private Map<String, Object> mapFrom(DocumentSnapshot doc, String field) {
        if (doc == null || !doc.exists() || !(doc.get(field) instanceof Map)) {
            return new HashMap<>();
        }
        return new HashMap<>((Map<String, Object>) doc.get(field));
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private double percent(long value, long total) {
        return total == 0 ? 0 : (value * 100.0) / total;
    }

    private Map<String, Object> defaultSummary() {
        return mapOf("gamesPlayed", 0, "gamesWon", 0, "gamesLost", 0, "winPercent", 0,
                "lossPercent", 0, "totalScore", 0, "averageScoresByGame", new HashMap<String, Object>(),
                "totalScoresByGame", new HashMap<String, Object>(), "playedByGame", new HashMap<String, Object>());
    }

    private Map<String, Object> defaultKnowIt() {
        return mapOf("correctAnswers", 0, "wrongAnswers", 0, "unansweredQuestions", 0,
                "totalQuestions", 0, "correctPercent", 0, "wrongPercent", 0, "averageScore", 0);
    }

    private Map<String, Object> defaultMyNumber() {
        return mapOf("roundsPlayed", 0, "exactHits", 0, "nonExactAttempts", 0,
                "exactHitPercent", 0, "averageScore", 0);
    }

    private Map<String, Object> defaultStep() {
        return mapOf("attemptsByStep", emptyStepMap(7), "hitsByStep", emptyStepMap(7),
                "percentByStep", emptyStepMap(7), "opponentChanceHits", 0, "averageScore", 0);
    }

    private Map<String, Object> defaultConnections() {
        return mapOf("attemptedPairs", 0, "successfulPairs", 0, "failedPairs", 0,
                "successPercent", 0, "averageScore", 0);
    }

    private Map<String, Object> defaultAssociations() {
        return mapOf("solved", 0, "unsolved", 0, "averageScore", 0);
    }

    private Map<String, Object> defaultSkocko() {
        return mapOf("hitsByAttempt", emptyStepMap(6), "attemptsByAttempt", emptyStepMap(6),
                "percentByAttempt", emptyStepMap(6), "averageScore", 0);
    }

    private Map<String, Object> emptyStepMap(int count) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            map.put(String.valueOf(i), 0);
        }
        return map;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static class StatsDocs {
        final String uid;
        final DocumentReference summaryRef;
        final DocumentReference specificRef;
        final DocumentSnapshot summary;
        final DocumentSnapshot specific;

        StatsDocs(String uid, DocumentReference summaryRef, DocumentReference specificRef,
                  DocumentSnapshot summary, DocumentSnapshot specific) {
            this.uid = uid;
            this.summaryRef = summaryRef;
            this.specificRef = specificRef;
            this.summary = summary;
            this.specific = specific;
        }
    }

    private static class KnowItResult {
        final int correct;
        final int wrong;
        final int unanswered;
        final int total;

        KnowItResult(int correct, int wrong, int unanswered, int total) {
            this.correct = correct;
            this.wrong = wrong;
            this.unanswered = unanswered;
            this.total = total;
        }
    }

    private static class MyNumberResult {
        final int roundsPlayed;
        final int exactHits;
        final int nonExactAttempts;

        MyNumberResult(int roundsPlayed, int exactHits, int nonExactAttempts) {
            this.roundsPlayed = roundsPlayed;
            this.exactHits = exactHits;
            this.nonExactAttempts = nonExactAttempts;
        }
    }

    private static class StepResult {
        final Map<String, Long> attemptsByStep = new HashMap<>();
        final Map<String, Long> hitsByStep = new HashMap<>();
        int opponentChanceHits = 0;
    }

    private static class ConnectionsResult {
        final int attemptedPairs;
        final int successfulPairs;
        final int failedPairs;

        ConnectionsResult(int attemptedPairs, int successfulPairs, int failedPairs) {
            this.attemptedPairs = attemptedPairs;
            this.successfulPairs = successfulPairs;
            this.failedPairs = failedPairs;
        }
    }

    private static class AssociationsResult {
        final int solved;
        final int unsolved;

        AssociationsResult(int solved, int unsolved) {
            this.solved = solved;
            this.unsolved = unsolved;
        }
    }

    private static class SkockoResult {
        final Map<String, Long> attemptsByAttempt = new HashMap<>();
        final Map<String, Long> hitsByAttempt = new HashMap<>();
    }
}
