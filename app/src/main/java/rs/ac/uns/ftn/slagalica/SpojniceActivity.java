package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;
import rs.ac.uns.ftn.slagalica.util.GameHeaderHelper;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class SpojniceActivity extends AppCompatActivity {
    private static final String TAG = "SpojniceActivity";
    private static final String DEBUG_TAG = "SpojniceDebug";
    private static final long PHASE_DURATION_MS = 30000;

    private final Button[] leftButtons = new Button[5];
    private final Button[] rightButtons = new Button[5];
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private CountDownTimer phaseTimer;
    private String uid;
    private String gameId;
    private String player1Uid;
    private String player2Uid;
    private String gameStatus = "";
    private String phase = "";
    private String activePlayerUid = "";
    private String opponentUid = "";
    private int roundNumber = 1;
    private int selectedLeftIndex = -1;
    private int selectedRightIndex = -1;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean statsRecordRequested = false;
    private boolean gameReady = false;
    private boolean fullMatch = false;
    private boolean challengeRun = false;
    private boolean completingMiniGame = false;
    private Map<String, Object> matchedPairs;
    private Map<String, Object> attemptsByPlayer;
    private List<Integer> usedLeftIndexes = new ArrayList<>();
    private List<Integer> remainingLeftIndexes = new ArrayList<>();
    private String currentSelectionUid = "";
    private int currentSelectionLeftIndex = -1;
    private int currentSelectionRightIndex = -1;
    private String wrongSelectionUid = "";
    private int wrongSelectionLeftIndex = -1;
    private int wrongSelectionRightIndex = -1;
    private String lastWrongSelectionKey = "";
    private int visualSelectedLeftIndex = -1;
    private int visualSelectedRightIndex = -1;
    private String lastGameplayKey = "";

    private TextView tvRound;
    private TextView tvCriterion;
    private TextView tvTimer;
    private TextView tvPoints;
    private TextView tvStatus;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;
    private Button btnCheckRound;
    private GameHeaderHelper headerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from SpojniceActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);

        tvRound = findViewById(R.id.tvRound);
        tvCriterion = findViewById(R.id.tvCriterion);
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayer1Score = findViewById(R.id.tvSpojnicePlayer1);
        tvPlayer2Score = findViewById(R.id.tvSpojnicePlayer2);
        btnCheckRound = findViewById(R.id.btnCheckRound);
        btnCheckRound.setVisibility(View.GONE);
        headerHelper = new GameHeaderHelper(this, findViewById(android.R.id.content));
        headerHelper.updateGameTitle("Spojnice");

        leftButtons[0] = findViewById(R.id.btnLeft1);
        leftButtons[1] = findViewById(R.id.btnLeft2);
        leftButtons[2] = findViewById(R.id.btnLeft3);
        leftButtons[3] = findViewById(R.id.btnLeft4);
        leftButtons[4] = findViewById(R.id.btnLeft5);
        rightButtons[0] = findViewById(R.id.btnRight1);
        rightButtons[1] = findViewById(R.id.btnRight2);
        rightButtons[2] = findViewById(R.id.btnRight3);
        rightButtons[3] = findViewById(R.id.btnRight4);
        rightButtons[4] = findViewById(R.id.btnRight5);

        for (int i = 0; i < leftButtons.length; i++) {
            final int leftIndex = i;
            leftButtons[i].setOnClickListener(v -> onLeftSelected(leftIndex));
        }
        for (int i = 0; i < rightButtons.length; i++) {
            final int rightIndex = i;
            rightButtons[i].setOnClickListener(v -> onRightSelected(rightIndex));
        }

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            setPairButtonsEnabled(false);
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "Current uid=" + uid);
        fullMatch = GameFlow.isFullMatch(getIntent());
        if (GameFlow.hasExistingGame(getIntent())) {
            gameId = GameFlow.existingGameId(getIntent());
            userRepository.updateUserState(uid, true, true, gameId);
            listenGame();
            return;
        }
        Log.d(TAG, "onCreate join called uid=" + uid + ", miniGameType=" + GameRepository.MINI_CONNECTIONS);
        gameRepository.joinOrCreateGame(uid, GameRepository.MINI_CONNECTIONS)
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "received gameId=" + gameId);
                    Log.d(TAG, "Connections gameId=" + gameId);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Connections matchmaking failed", e);
                    show(e.getMessage());
                });
    }

    private void listenGame() {
        gameListener = gameRepository.listenGame(gameId, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Game snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            String status = snapshot.getString("status");
            gameStatus = value(status);
            player1Uid = value(snapshot.getString("player1Uid"));
            player2Uid = value(snapshot.getString("player2Uid"));
            Long p1 = snapshot.getLong("player1Score");
            Long p2 = snapshot.getLong("player2Score");
            player1Score = p1 == null ? 0 : p1.intValue();
            player2Score = p2 == null ? 0 : p2.intValue();
            challengeRun = Boolean.TRUE.equals(snapshot.getBoolean("challengeRun"));
            tvPlayer1Score.setText(getString(R.string.player_points, player1Score));
            tvPlayer2Score.setVisibility(challengeRun ? View.GONE : View.VISIBLE);
            tvPlayer2Score.setText(getString(R.string.player_points, player2Score));
            tvPoints.setText(challengeRun ? "Ukupan rezultat: " + player1Score
                    : getString(R.string.points_text, uid.equals(player1Uid) ? player1Score : player2Score));
            headerHelper.setChallengeMode(challengeRun, intValue(snapshot.get("matchIndex")) + 1, GameRepository.FULL_MATCH_ORDER.length);
            headerHelper.updateGameTitle("Spojnice");
            headerHelper.updatePlayers(player1Uid, player1Score, player2Uid, player2Score);
            Log.d(TAG, "Game snapshot currentUserUid=" + uid
                    + ", gameId=" + snapshot.getId() + ", status=" + status
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            Log.d(TAG, "Activity game snapshot: status=" + status
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid);
            if (!GameRepository.MINI_CONNECTIONS.equals(snapshot.getString("currentMiniGame"))) {
                if (fullMatch && GameFlow.openMiniGame(this, gameId, snapshot.getString("currentMiniGame"))) {
                    return;
                }
                setStatus("Spojnice nisu aktivna igra");
                setPairButtonsEnabled(false);
                Log.d(TAG, "Ignoring game snapshot for different mini game, gameId=" + gameId
                        + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
                return;
            }
            if (!challengeRun && ("waiting".equals(status) || player2Uid.isEmpty())) {
                setStatus("Čeka se drugi igrač");
                setPairButtonsEnabled(false);
                return;
            }
            gameReady = GameRepository.isGameReady(snapshot);
            Log.d(TAG, "isGameReady " + gameReady);
            if (!gameReady) {
                updateStatus();
                setPairButtonsEnabled(false);
                return;
            }
            Log.d(TAG, "Game became active, currentUserUid=" + uid + ", gameId=" + gameId
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            if (phase.isEmpty()) {
                setStatus("");
            }
            if ("finished".equals(status) && "FINISHED".equals(phase)) {
                setStatus("Spojnice su završene");
                setPairButtonsEnabled(false);
                if (fullMatch) {
                    completeMiniGameOnce();
                } else {
                    userRepository.updateUserState(uid, true, false, "");
                }
                return;
            }
            Log.d(TAG, "round creation attempted");
            gameRepository.ensureConnectionsRound(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ensure connections round failed", e);
                        show(e.getMessage());
                    });
            listenRound();
        });
    }

    private void listenRound() {
        if (roundListener != null) {
            return;
        }
        roundListener = gameRepository.listenRound(gameId, gameRepository.connectionsRoundId(roundNumber), (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Round snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            Log.d(TAG, "Round snapshot roundId=" + snapshot.getId()
                    + ", phase=" + snapshot.getString("phase")
                    + ", activePlayerUid=" + snapshot.getString("activePlayerUid")
                    + ", opponentUid=" + snapshot.getString("opponentUid")
                    + ", remainingLeftIndexes=" + snapshot.get("remainingLeftIndexes"));
            bindRound(snapshot);
        });
        Log.d(TAG, "round listener attached gameId=" + gameId + ", roundId=" + gameRepository.connectionsRoundId(roundNumber));
    }

    private void bindRound(DocumentSnapshot round) {
        phase = value(round.getString("phase"));
        activePlayerUid = value(round.getString("activePlayerUid"));
        opponentUid = value(round.getString("opponentUid"));
        Long roundIndex = round.getLong("roundIndex");
        roundNumber = roundIndex == null ? roundNumber : roundIndex.intValue();
        matchedPairs = (Map<String, Object>) round.get("matchedPairs");
        attemptsByPlayer = (Map<String, Object>) round.get("attemptsByPlayer");
        usedLeftIndexes = intList(round.get("usedLeftIndexes"));
        remainingLeftIndexes = intList(round.get("remainingLeftIndexes"));
        bindCurrentSelection((Map<String, Object>) round.get("currentSelection"));
        bindVisualState((Map<String, Object>) round.get("spojniceVisualState"));
        String gameplayKey = gameplayKey(round);
        if (gameplayKey.equals(lastGameplayKey)) {
            renderConnectionButtons();
            logSpojniceDebug("visual-only");
            return;
        }
        lastGameplayKey = gameplayKey;
        tvRound.setText(getString(R.string.spojnice_round, roundNumber, 2));
        tvCriterion.setText("Kriterijum: " + connectionCriterion(round));

        List<String> leftItems = stringList(round.get("leftItems"));
        List<String> rightItems = stringList(round.get("rightItems"));
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(i < leftItems.size() ? leftItems.get(i) : "");
            rightButtons[i].setText(i < rightItems.size() ? rightItems.get(i) : "");
        }
        renderConnectionButtons();
        updateStatus();

        if ("FINISHED".equals(phase) || Boolean.TRUE.equals(round.getBoolean("finished"))) {
            if (phaseTimer != null) {
                phaseTimer.cancel();
                phaseTimer = null;
            }
            setPairButtonsEnabled(false);
            if (challengeRun) {
                setStatus("Spojnice su zavrÅ¡ene");
                recordStatsOnce();
                if (fullMatch) {
                    completeMiniGameOnce();
                }
                tvTimer.setText(getString(R.string.timer_text, 0));
            } else if (roundNumber == 1) {
                moveToSecondRound();
            } else {
                setStatus("Spojnice su završene");
                recordStatsOnce();
                if (fullMatch) {
                    completeMiniGameOnce();
                }
                tvTimer.setText(getString(R.string.timer_text, 0));
            }
            return;
        }
        setPairButtonsEnabled(canCurrentUserPlay());
        startPhaseTimer(round);
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordConnectionsGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record connections stats failed", e));
    }

    private void completeMiniGameOnce() {
        if (completingMiniGame || gameRepository == null || gameId == null || gameId.isEmpty()) {
            return;
        }
        completingMiniGame = true;
        gameRepository.completeMiniGame(gameId, GameRepository.MINI_CONNECTIONS)
                .addOnFailureListener(e -> Log.e(TAG, "Complete connections in match failed", e));
    }

    private void onLeftSelected(int leftIndex) {
        if (!canCurrentUserPlay()) {
            show("Protivnik igra");
            return;
        }
        if (isLeftMatched(leftIndex) || hasCurrentPlayerAttempted(leftIndex)) {
            return;
        }
        selectedLeftIndex = leftIndex;
        wrongSelectionLeftIndex = -1;
        wrongSelectionRightIndex = -1;
        visualSelectedLeftIndex = leftIndex;
        visualSelectedRightIndex = -1;
        Log.d(TAG, "selectedLeftIndex=" + selectedLeftIndex + ", gameId=" + gameId + ", round=" + roundNumber);
        Log.d(DEBUG_TAG, "click uid=" + uid + ", isMyTurn=" + canCurrentUserPlay()
                + ", side=left, index=" + leftIndex
                + ", localSelectedLeft=" + selectedLeftIndex
                + ", localSelectedRight=" + selectedRightIndex);
        logSpojniceDebug("left-click");
        renderConnectionButtons();
        if (!challengeRun) {
            gameRepository.updateConnectionVisualState(gameId, roundNumber, uid, leftIndex, null, null, null)
                    .addOnSuccessListener(unused -> logVisualWrite("left", leftIndex, null, null, null))
                    .addOnFailureListener(e -> Log.e(TAG, "Update connection visual left failed", e));
        }
    }

    private void onRightSelected(int rightIndex) {
        if (!canCurrentUserPlay()) {
            show("Protivnik igra");
            return;
        }
        if (selectedLeftIndex < 0 || isRightMatched(rightIndex)) {
            return;
        }
        int leftIndex = selectedLeftIndex;
        selectedRightIndex = rightIndex;
        wrongSelectionLeftIndex = -1;
        wrongSelectionRightIndex = -1;
        visualSelectedLeftIndex = leftIndex;
        visualSelectedRightIndex = rightIndex;
        renderConnectionButtons();
        Log.d(DEBUG_TAG, "click uid=" + uid + ", isMyTurn=" + canCurrentUserPlay()
                + ", side=right, index=" + rightIndex
                + ", localSelectedLeft=" + selectedLeftIndex
                + ", localSelectedRight=" + selectedRightIndex);
        logSpojniceDebug("right-click");
        if (!challengeRun) {
            gameRepository.updateConnectionVisualState(gameId, roundNumber, uid, leftIndex, rightIndex, null, null)
                    .addOnSuccessListener(unused -> logVisualWrite("right", leftIndex, rightIndex, null, null))
                    .addOnFailureListener(e -> Log.e(TAG, "Update connection visual right failed", e));
        }
        Log.d(TAG, "Submit pair selectedLeftIndex=" + leftIndex + ", selectedRightIndex=" + rightIndex
                + ", phase=" + phase + ", uid=" + uid);
        submitSelectedPair(leftIndex, rightIndex);
    }

    private void submitSelectedPair(int leftIndex, int rightIndex) {
        gameRepository.submitConnectionPair(gameId, roundNumber, uid, leftIndex, rightIndex)
                .addOnSuccessListener(correct -> {
                    selectedLeftIndex = -1;
                    selectedRightIndex = -1;
                    renderConnectionButtons();
                    if (!Boolean.TRUE.equals(correct)) {
                        show("Netačno");
                    }
                })
                .addOnFailureListener(e -> {
                    selectedLeftIndex = -1;
                    selectedRightIndex = -1;
                    renderConnectionButtons();
                    Log.e(TAG, "Submit connection pair failed", e);
                    show(e.getMessage());
                });
    }

    private void startPhaseTimer(DocumentSnapshot round) {
        if (phaseTimer != null) {
            phaseTimer.cancel();
        }
        Timestamp startedAt = round.getTimestamp("phaseStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, PHASE_DURATION_MS - elapsedMs);
        Log.d(TAG, "Phase timer roundId=" + round.getId() + ", phase=" + phase
                + ", phaseStartedAt=" + startedAt + ", remainingMs=" + remainingMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            advancePhaseByTimeout();
            return;
        }
        phaseTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                advancePhaseByTimeout();
            }
        }.start();
    }

    private void advancePhaseByTimeout() {
        Log.d(TAG, "Timeout phase advance gameId=" + gameId + ", round=" + roundNumber + ", phase=" + phase);
        gameRepository.advanceConnectionsPhase(gameId, roundNumber, phase)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Advance connections phase failed", e);
                    show(e.getMessage());
                });
    }

    private void moveToSecondRound() {
        setStatus("Priprema druge runde");
        roundNumber = 2;
        selectedLeftIndex = -1;
        selectedRightIndex = -1;
        if (roundListener != null) {
            roundListener.remove();
            roundListener = null;
        }
        if (!gameReady) {
            Log.d(TAG, "Activity: game ready false");
            return;
        }
        Log.d(TAG, "round creation attempted");
        gameRepository.ensureConnectionsRound(gameId, roundNumber)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ensure second connections round failed", e);
                    show(e.getMessage());
                });
        listenRound();
    }

    private boolean isValidConnectionsGame(DocumentSnapshot game) {
        if (game == null || !game.exists()) {
            return false;
        }
        String status = game.getString("status");
        String miniGame = game.getString("currentMiniGame");
        String p1 = game.getString("player1Uid");
        String p2 = game.getString("player2Uid");
        boolean containsUser = uid.equals(p1) || uid.equals(p2);
        boolean waitingAsPlayer1 = "waiting".equals(status) && uid.equals(p1) && p2 == null;
        boolean activeWithBothPlayers = "active".equals(status) && p1 != null && p2 != null;
        boolean valid = GameRepository.MINI_CONNECTIONS.equals(miniGame)
                && containsUser
                && (waitingAsPlayer1 || activeWithBothPlayers);
        Log.d(TAG, "Validate currentGameId=" + game.getId() + ", valid=" + valid
                + ", status=" + status + ", player1=" + p1 + ", player2=" + p2 + ", miniGame=" + miniGame);
        return valid;
    }

    private void renderConnectionButtons() {
        for (int i = 0; i < leftButtons.length; i++) {
            if (isLeftMatched(i)) {
                applyConnectionStyle(leftButtons[i], R.drawable.bg_answer_correct, false);
                Log.d(DEBUG_TAG, "render left index=" + i + ", style=correct");
            } else if (i == wrongSelectionLeftIndex) {
                applyConnectionStyle(leftButtons[i], R.drawable.bg_answer_wrong,
                        canCurrentUserPlay() && !hasCurrentPlayerAttempted(i));
                Log.d(DEBUG_TAG, "render left index=" + i + ", style=wrong");
            } else if (i == selectedLeftIndex || i == currentSelectionLeftIndex || i == visualSelectedLeftIndex) {
                applyConnectionStyle(leftButtons[i], R.drawable.bg_answer_selected,
                        canCurrentUserPlay() && !hasCurrentPlayerAttempted(i));
                Log.d(DEBUG_TAG, "render left index=" + i + ", style=selected");
            } else {
                applyConnectionStyle(leftButtons[i], R.drawable.bg_step,
                        canCurrentUserPlay() && !hasCurrentPlayerAttempted(i));
            }
        }
        for (int i = 0; i < rightButtons.length; i++) {
            if (isRightMatched(i)) {
                applyConnectionStyle(rightButtons[i], R.drawable.bg_answer_correct, false);
                Log.d(DEBUG_TAG, "render right index=" + i + ", style=correct");
            } else if (i == wrongSelectionRightIndex) {
                applyConnectionStyle(rightButtons[i], R.drawable.bg_answer_wrong, canCurrentUserPlay());
                Log.d(DEBUG_TAG, "render right index=" + i + ", style=wrong");
            } else if (i == selectedRightIndex || i == currentSelectionRightIndex || i == visualSelectedRightIndex) {
                applyConnectionStyle(rightButtons[i], R.drawable.bg_answer_selected, canCurrentUserPlay());
                Log.d(DEBUG_TAG, "render right index=" + i + ", style=selected");
            } else {
                applyConnectionStyle(rightButtons[i], R.drawable.bg_step, canCurrentUserPlay());
            }
        }
    }

    private void applyConnectionStyle(Button button, int backgroundRes, boolean clickable) {
        button.setBackgroundTintList((ColorStateList) null);
        button.setBackgroundResource(backgroundRes);
        button.setEnabled(true);
        button.setClickable(clickable);
        button.setAlpha(1f);
        button.setTextColor(Color.rgb(53, 43, 69));
    }

    private boolean canCurrentUserPlay() {
        return ("ACTIVE_PLAYER".equals(phase) && uid.equals(activePlayerUid))
                || ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponentUid));
    }

    private boolean isLeftMatched(int leftIndex) {
        return matchedPairs != null && matchedPairs.containsKey(String.valueOf(leftIndex));
    }

    private boolean hasCurrentPlayerAttempted(int leftIndex) {
        if (attemptsByPlayer == null || uid == null) {
            return false;
        }
        return intList(attemptsByPlayer.get(uid)).contains(leftIndex);
    }

    private boolean isRightMatched(int rightIndex) {
        if (matchedPairs == null) {
            return false;
        }
        for (Object value : matchedPairs.values()) {
            if (value instanceof Map) {
                Object right = ((Map<String, Object>) value).get("rightIndex");
                if (right instanceof Number && ((Number) right).intValue() == rightIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setPairButtonsEnabled(boolean enabled) {
        for (int i = 0; i < leftButtons.length; i++) {
            leftButtons[i].setEnabled(true);
            rightButtons[i].setEnabled(true);
            leftButtons[i].setClickable(enabled && !isLeftMatched(i) && !hasCurrentPlayerAttempted(i));
            rightButtons[i].setClickable(enabled && !isRightMatched(i));
        }
        renderConnectionButtons();
        Log.d(TAG, "Pair buttons enabled=" + enabled + ", gameId=" + gameId
                + ", roundId=" + gameRepository.connectionsRoundId(roundNumber)
                + ", currentUserUid=" + uid + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid + ", opponentUid=" + opponentUid
                + ", selectedLeftIndex=" + selectedLeftIndex);
    }

    private void updateStatus() {
        if ("FINISHED".equals(phase)) {
            setStatus(roundNumber == 1 ? "Priprema druge runde" : "Spojnice su završene");
            return;
        }
        if ("waiting".equals(gameStatus) || player2Uid == null || player2Uid.trim().isEmpty()) {
            setStatus("Čeka se drugi igrač");
            return;
        }
        if ("ACTIVE_PLAYER".equals(phase)) {
            if (!GameRepository.isNonEmpty(activePlayerUid)) {
                Log.e(TAG, "Missing activePlayerUid in Spojnice, currentUid=" + uid
                        + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                        + ", phase=" + phase);
                setStatus("Priprema igre");
                gameRepository.repairRoundPlayers(gameId, gameRepository.connectionsRoundId(roundNumber), roundNumber, false);
                return;
            }
            setStatus(uid.equals(activePlayerUid) ? "Vaš potez" : "Protivnik povezuje");
            return;
        }
        if ("OPPONENT_CHANCE".equals(phase)) {
            if (!GameRepository.isNonEmpty(opponentUid)) {
                Log.e(TAG, "Missing opponentUid in Spojnice, currentUid=" + uid
                        + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                        + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid);
                setStatus("Priprema igre");
                gameRepository.repairRoundPlayers(gameId, gameRepository.connectionsRoundId(roundNumber), roundNumber, false);
                return;
            }
            setStatus(uid.equals(opponentUid) ? "Vaša šansa za preostale parove" : "Protivnik pokušava preostale parove");
            return;
        }
        if ("FINISHED".equals(phase)) {
            setStatus(roundNumber == 1 ? "Priprema druge runde" : "Spojnice su završene");
            return;
        }
        if (!currentSelectionUid.isEmpty() && !uid.equals(currentSelectionUid)) {
            setStatus("Protivnik bira par");
        } else if ("ACTIVE_PLAYER".equals(phase)) {
            setStatus(uid.equals(activePlayerUid) ? "Vaš potez" : "Protivnik povezuje");
        } else if ("OPPONENT_CHANCE".equals(phase)) {
            setStatus(uid.equals(opponentUid) ? "Vaša šansa za preostale parove" : "Protivnik pokušava preostale parove");
        } else if ("FINISHED".equals(phase)) {
            setStatus(roundNumber == 1 ? "Priprema druge runde" : "Spojnice su završene");
        }
    }

    private List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private String connectionCriterion(DocumentSnapshot round) {
        String criterion = round.getString("criterion");
        if (criterion == null || criterion.trim().isEmpty()) {
            criterion = round.getString("title");
        }
        if (criterion == null || criterion.trim().isEmpty()) {
            criterion = round.getString("category");
        }
        return criterion == null || criterion.trim().isEmpty() ? "Spoji povezane pojmove" : criterion;
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

    private void bindCurrentSelection(Map<String, Object> selection) {
        currentSelectionUid = "";
        currentSelectionLeftIndex = -1;
        currentSelectionRightIndex = -1;
        if (selection == null) {
            return;
        }
        Object selectionUid = selection.get("uid");
        Object left = selection.get("leftIndex");
        Object right = selection.get("rightIndex");
        currentSelectionUid = selectionUid == null ? "" : String.valueOf(selectionUid);
        if (left instanceof Number) {
            currentSelectionLeftIndex = ((Number) left).intValue();
        }
        if (right instanceof Number) {
            currentSelectionRightIndex = ((Number) right).intValue();
        }
        Log.d(TAG, "Current selection uid=" + currentSelectionUid
                + ", leftIndex=" + currentSelectionLeftIndex
                + ", rightIndex=" + currentSelectionRightIndex
                + ", currentUserUid=" + uid);
    }

    private void bindVisualState(Map<String, Object> visualState) {
        visualSelectedLeftIndex = -1;
        visualSelectedRightIndex = -1;
        wrongSelectionUid = "";
        wrongSelectionLeftIndex = -1;
        wrongSelectionRightIndex = -1;
        if (visualState == null) {
            return;
        }
        Object updatedBy = visualState.get("updatedBy");
        Object selectedLeft = visualState.get("selectedLeft");
        Object selectedRight = visualState.get("selectedRight");
        Object wrongLeft = visualState.get("wrongLeft");
        Object wrongRight = visualState.get("wrongRight");
        wrongSelectionUid = updatedBy == null ? "" : String.valueOf(updatedBy);
        if (selectedLeft instanceof Number) {
            visualSelectedLeftIndex = ((Number) selectedLeft).intValue();
        }
        if (selectedRight instanceof Number) {
            visualSelectedRightIndex = ((Number) selectedRight).intValue();
        }
        if (wrongLeft instanceof Number) {
            wrongSelectionLeftIndex = ((Number) wrongLeft).intValue();
        }
        if (wrongRight instanceof Number) {
            wrongSelectionRightIndex = ((Number) wrongRight).intValue();
        }
        scheduleWrongSelectionClear();
    }

    private String gameplayKey(DocumentSnapshot round) {
        return value(round.getString("phase"))
                + "|" + value(round.getString("activePlayerUid"))
                + "|" + value(round.getString("opponentUid"))
                + "|" + round.getBoolean("finished")
                + "|" + String.valueOf(round.get("matchedPairs"))
                + "|" + String.valueOf(round.get("attemptsByPlayer"))
                + "|" + String.valueOf(round.get("usedLeftIndexes"))
                + "|" + String.valueOf(round.get("remainingLeftIndexes"))
                + "|" + String.valueOf(round.getLong("roundIndex"));
    }

    private void logSpojniceDebug(String event) {
        int matchedSize = matchedPairs == null ? 0 : matchedPairs.size();
        Log.d(DEBUG_TAG, "event=" + event
                + ", uid=" + uid
                + ", isMyTurn=" + canCurrentUserPlay()
                + ", selectedLeft=" + selectedLeftIndex
                + ", selectedRight=" + selectedRightIndex
                + ", visualSelectedLeft=" + visualSelectedLeftIndex
                + ", visualSelectedRight=" + visualSelectedRightIndex
                + ", visualWrongLeft=" + wrongSelectionLeftIndex
                + ", visualWrongRight=" + wrongSelectionRightIndex
                + ", matchedPairsSize=" + matchedSize
                + ", phase=" + phase
                + ", status=" + gameStatus);
    }

    private void logVisualWrite(String event, Integer selectedLeft, Integer selectedRight,
                                Integer wrongLeft, Integer wrongRight) {
        Log.d(DEBUG_TAG, "visual-write event=" + event
                + ", uid=" + uid
                + ", selectedLeft=" + selectedLeft
                + ", selectedRight=" + selectedRight
                + ", wrongLeft=" + wrongLeft
                + ", wrongRight=" + wrongRight);
    }

    private void setStatus(String status) {
        if (challengeRun) {
            status = challengeStatus(status);
        }
        tvStatus.setText(status);
        headerHelper.updateStatus(status);
        Log.d(TAG, "Status text=" + status + ", gameId=" + gameId + ", roundId="
                + gameRepository.connectionsRoundId(roundNumber) + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid + ", opponentUid=" + opponentUid
                + ", remainingLeftIndexes=" + remainingLeftIndexes
                + ", usedLeftIndexes=" + usedLeftIndexes
                + ", currentSelectionUid=" + currentSelectionUid
                + ", currentSelectionLeftIndex=" + currentSelectionLeftIndex
                + ", currentSelectionRightIndex=" + currentSelectionRightIndex);
        Log.d(TAG, "Spojnice status display: game.status=" + gameStatus
                + ", player2Uid=" + player2Uid
                + ", phase=" + phase
                + ", roundIndex=" + roundNumber
                + ", statusText=" + status);
        boolean isCurrentUsersTurn = ("ACTIVE_PLAYER".equals(phase) && uid != null && uid.equals(activePlayerUid))
                || ("OPPONENT_CHANCE".equals(phase) && uid != null && uid.equals(opponentUid));
        Log.d(TAG, "Turn status currentUid=" + uid
                + ", player1Uid=" + player1Uid
                + ", player2Uid=" + player2Uid
                + ", activePlayerUid=" + activePlayerUid
                + ", opponentUid=" + opponentUid
                + ", currentTurnUid="
                + ", phase=" + phase
                + ", calculatedStatusText=" + status
                + ", isCurrentUsersTurn=" + isCurrentUsersTurn);
    }

    private void bindWrongSelection(Map<String, Object> selection) {
        wrongSelectionUid = "";
        wrongSelectionLeftIndex = -1;
        wrongSelectionRightIndex = -1;
        if (selection == null) {
            return;
        }
        Object selectionUid = selection.get("uid");
        Object left = selection.get("leftIndex");
        Object right = selection.get("rightIndex");
        wrongSelectionUid = selectionUid == null ? "" : String.valueOf(selectionUid);
        if (left instanceof Number) {
            wrongSelectionLeftIndex = ((Number) left).intValue();
        }
        if (right instanceof Number) {
            wrongSelectionRightIndex = ((Number) right).intValue();
        }
        Log.d(TAG, "Wrong selection uid=" + wrongSelectionUid
                + ", leftIndex=" + wrongSelectionLeftIndex
                + ", rightIndex=" + wrongSelectionRightIndex
                + ", currentUserUid=" + uid);
    }

    private void scheduleWrongSelectionClear() {
        if (wrongSelectionLeftIndex < 0 || wrongSelectionRightIndex < 0) {
            lastWrongSelectionKey = "";
            return;
        }
        String key = wrongSelectionUid + ":" + wrongSelectionLeftIndex + ":" + wrongSelectionRightIndex + ":" + roundNumber;
        if (key.equals(lastWrongSelectionKey)) {
            return;
        }
        lastWrongSelectionKey = key;
        findViewById(android.R.id.content).postDelayed(() -> {
            if (key.equals(lastWrongSelectionKey)) {
                wrongSelectionLeftIndex = -1;
                wrongSelectionRightIndex = -1;
                renderConnectionButtons();
                if (!challengeRun && gameRepository != null && gameId != null && !gameId.isEmpty()) {
                    gameRepository.clearConnectionVisualState(gameId, roundNumber)
                            .addOnFailureListener(e -> Log.e(TAG, "Clear connection visual state failed", e));
                }
            }
        }, 700);
    }

    private String challengeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "Samostalna partija";
        if (status.contains("ÄŒeka") || status.contains("Čeka") || status.contains("Protivnik")
                || status.contains("protivnik") || status.contains("drugi")) {
            return "Samostalna partija";
        }
        return status.replace("Priprema druge runde", "Samostalna partija");
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @Override
    protected void onDestroy() {
        if (phaseTimer != null) {
            phaseTimer.cancel();
        }
        if (gameListener != null) {
            gameListener.remove();
        }
        if (roundListener != null) {
            roundListener.remove();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fullMatch && gameRepository != null && gameId != null && !gameId.isEmpty()) {
            gameRepository.abandonGame(gameId, uid)
                    .addOnFailureListener(e -> Log.e(TAG, "Abandon match failed", e));
        }
        super.onBackPressed();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }
}
