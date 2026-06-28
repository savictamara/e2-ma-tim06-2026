package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.service.MyNumberService;
import rs.ac.uns.ftn.slagalica.util.ExpressionEvaluator;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;
import rs.ac.uns.ftn.slagalica.util.GameHeaderHelper;
import rs.ac.uns.ftn.slagalica.util.GuestSession;
import rs.ac.uns.ftn.slagalica.util.ShakeDetector;

public class MojBrojActivity extends AppCompatActivity {
    private static final String TAG = "MojBrojActivity";
    private static final String DEBUG_TAG = "MojBrojDebug";
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private final MyNumberService myNumberService = new MyNumberService();
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private Sensor accelerometer;
    private String uid;
    private String gameId;
    private String phase = "";
    private String activePlayerUid = "";
    private int roundNumber = 1;
    private int currentRoundIndex = 1;
    private int targetNumber = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private List<Integer> availableNumbers = new ArrayList<>();
    private CountDownTimer roundTimer;
    private CountDownTimer autoStopTimer;
    private boolean submitted = false;
    private boolean statsRecordRequested = false;
    private boolean gameReady = false;
    private boolean fullMatch = false;
    private boolean challengeRun = false;
    private boolean completingMiniGame = false;
    private boolean finalResultOpening = false;
    private String gameStatus = "";
    private String currentPlayer1Uid = "";
    private String currentPlayer2Uid = "";
    private TextView tvTarget;
    private TextView tvRound;
    private TextView tvTimer;
    private TextView tvNumbers;
    private TextView tvResult;
    private TextView tvPoints;
    private TextView tvStatus;
    private TextView tvPlayer1;
    private TextView tvPlayer2;
    private EditText etExpression;
    private Button btnStopTarget;
    private Button btnStopNumbers;
    private Button btnDelete;
    private Button btnClearExpression;
    private Button btnConfirm;
    private LinearLayout llNumberButtons;
    private LinearLayout llOperatorButtons;
    private final List<Button> numberButtons = new ArrayList<>();
    private final List<Token> expressionTokens = new ArrayList<>();
    private final Set<Integer> usedNumberIndexes = new HashSet<>();
    private boolean updatingExpressionProgrammatically = false;
    private GameHeaderHelper headerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from MojBrojActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);

        tvTarget = findViewById(R.id.tvTarget);
        tvRound = findViewById(R.id.tvMojBrojRound);
        tvTimer = findViewById(R.id.tvMojBrojTimer);
        tvNumbers = findViewById(R.id.tvNumbers);
        tvResult = findViewById(R.id.tvMojBrojResult);
        tvPoints = findViewById(R.id.tvMojBrojPoints);
        tvStatus = findViewById(R.id.tvMojBrojStatus);
        tvPlayer1 = findViewById(R.id.tvMojBrojPlayer1);
        tvPlayer2 = findViewById(R.id.tvMojBrojPlayer2);
        etExpression = findViewById(R.id.etExpression);

        btnStopTarget = findViewById(R.id.btnStopTarget);
        btnStopNumbers = findViewById(R.id.btnStopNumbers);
        btnDelete = findViewById(R.id.btnDelete);
        btnClearExpression = findViewById(R.id.btnClearExpression);
        btnConfirm = findViewById(R.id.btnConfirm);
        llNumberButtons = findViewById(R.id.llNumberButtons);
        llOperatorButtons = findViewById(R.id.llOperatorButtons);
        headerHelper = new GameHeaderHelper(this, findViewById(android.R.id.content));
        headerHelper.updateGameTitle("Moj broj");

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector(this::stopByCurrentPhase);

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            setPlayControls(false);
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "Current uid=" + uid);
        fullMatch = GameFlow.isFullMatch(getIntent());
        if (GameFlow.hasExistingGame(getIntent())) {
            gameId = GameFlow.existingGameId(getIntent());
            userRepository.updateUserState(uid, true, true, gameId);
            listenGame();
        } else {
            Log.d(TAG, "onCreate join called uid=" + uid + ", miniGameType=" + GameRepository.MINI_MY_NUMBER);
            gameRepository.joinOrCreateGame(uid, GameRepository.MINI_MY_NUMBER)
                    .addOnSuccessListener(id -> {
                        gameId = id;
                        Log.d(TAG, "received gameId=" + gameId);
                        Log.d(TAG, "My number gameId=" + gameId);
                        userRepository.updateUserState(uid, true, true, gameId);
                        listenGame();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "My number matchmaking failed", e);
                        show(e.getMessage());
                    });
        }

        btnStopTarget.setOnClickListener(v -> {
            if (!uid.equals(activePlayerUid)) {
                show(getString(R.string.not_your_turn));
                return;
            }
            Log.d(TAG, "STOP target clicked, uid=" + uid + ", gameId=" + gameId + ", round=" + roundNumber);
            gameRepository.stopTarget(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Stop target failed", e);
                        show(e.getMessage());
                    });
        });

        btnStopNumbers.setOnClickListener(v -> {
            if (!uid.equals(activePlayerUid)) {
                show(getString(R.string.not_your_turn));
                return;
            }
            Log.d(TAG, "STOP numbers clicked, uid=" + uid + ", gameId=" + gameId + ", round=" + roundNumber);
            gameRepository.stopNumbers(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Stop numbers failed", e);
                        show(e.getMessage());
                    });
        });

        btnDelete.setOnClickListener(v -> {
            deleteLastExpressionToken();
        });
        btnClearExpression.setOnClickListener(v -> clearExpression());
        buildOperatorButtons();
        etExpression.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!updatingExpressionProgrammatically) {
                    expressionTokens.clear();
                    recalculateUsedNumbersFromExpression(s.toString());
                    refreshNumberButtons();
                }
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (!GameRepository.PHASE_PLAYING.equals(phase) || submitted) {
                return;
            }
            String expression = etExpression.getText().toString().trim();
            if (expression.isEmpty()) {
                tvResult.setText(getString(R.string.result_text, "Unesite izraz."));
                submitMyNumberExpression("", 0, false);
                return;
            }
            if (!myNumberService.usesOnlyAvailableNumbers(expression, availableNumbers)) {
                tvResult.setText(getString(R.string.result_text, "Koristite samo ponudjene brojeve."));
                submitMyNumberExpression(expression, 0, false);
                return;
            }
            try {
                double result = evaluator.evaluate(expression);
                tvResult.setText(getString(R.string.result_text, "Rezultat izraza: " + result));
                submitMyNumberExpression(expression, result, true);
            } catch (IllegalArgumentException e) {
                tvResult.setText(getString(R.string.result_text, e.getMessage()));
                submitMyNumberExpression(expression, 0, false);
            }
        });
    }

    private void submitMyNumberExpression(String expression, double result, boolean valid) {
        submitted = true;
        gameRepository.submitMyNumber(gameId, roundNumber, uid, expression, result, valid)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Submit my number failed", e);
                    submitted = false;
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
            Log.d(TAG, "Game snapshot currentUserUid=" + uid
                    + ", gameId=" + snapshot.getId() + ", status=" + snapshot.getString("status")
                    + ", player1=" + snapshot.getString("player1Uid")
                    + ", player2=" + snapshot.getString("player2Uid")
                    + ", miniGame=" + snapshot.getString("currentMiniGame"));
            Log.d(TAG, "Activity game snapshot: status=" + snapshot.getString("status")
                    + ", player1Uid=" + snapshot.getString("player1Uid")
                    + ", player2Uid=" + snapshot.getString("player2Uid"));
            Long p1 = snapshot.getLong("player1Score");
            Long p2 = snapshot.getLong("player2Score");
            player1Score = p1 == null ? 0 : p1.intValue();
            player2Score = p2 == null ? 0 : p2.intValue();
            challengeRun = Boolean.TRUE.equals(snapshot.getBoolean("challengeRun"));
            tvPlayer1.setText(getString(R.string.player_points, player1Score));
            tvPlayer2.setVisibility(challengeRun ? View.GONE : View.VISIBLE);
            tvPlayer2.setText(getString(R.string.player_points, player2Score));
            headerHelper.setChallengeMode(challengeRun, intValue(snapshot.get("matchIndex")) + 1, GameRepository.FULL_MATCH_ORDER.length);
            headerHelper.updateGameTitle("Moj broj");
            headerHelper.updatePlayers(snapshot.getString("player1Uid"), player1Score,
                    snapshot.getString("player2Uid"), player2Score);
            String status = snapshot.getString("status");
            String player1Uid = value(snapshot.getString("player1Uid"));
            String player2Uid = value(snapshot.getString("player2Uid"));
            gameStatus = value(status);
            currentPlayer1Uid = player1Uid;
            currentPlayer2Uid = player2Uid;
            if (!GameRepository.MINI_MY_NUMBER.equals(snapshot.getString("currentMiniGame"))) {
                if (fullMatch && GameFlow.openMiniGame(this, gameId, snapshot.getString("currentMiniGame"))) {
                    return;
                }
                setStatusText("Moj broj nije aktivna igra");
                setPlayControls(false);
                btnStopTarget.setEnabled(false);
                btnStopNumbers.setEnabled(false);
                Log.d(TAG, "Ignoring game snapshot for different mini game, gameId=" + gameId
                        + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
                return;
            }
            if (!challengeRun && ("waiting".equals(status) || player2Uid.isEmpty())) {
                setStatusText("Čeka se drugi igrač");
                tvResult.setText(R.string.waiting_opponent);
                setPlayControls(false);
                return;
            }
            clearWaitingResultText();
            gameReady = GameRepository.isGameReady(snapshot);
            Log.d(TAG, "isGameReady " + gameReady);
            if (!gameReady) {
                updateStatusForPhase(status, player2Uid);
                setPlayControls(false);
                return;
            }
            Log.d(TAG, "Game became active, currentUserUid=" + uid + ", gameId=" + gameId
                    + ", player1Uid=" + snapshot.getString("player1Uid")
                    + ", player2Uid=" + snapshot.getString("player2Uid")
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            if ("finished".equals(status)) {
                setStatusText("Moj broj je završen");
                tvResult.setText(getString(R.string.result_text,
                        "Finalni rezultat: " + player1Score + " : " + player2Score));
                setPlayControls(false);
                if (fullMatch) {
                    completeMiniGameOnce();
                } else {
                    userRepository.updateUserState(uid, true, false, "");
                }
                return;
            }
            if (phase.isEmpty()) {
                setStatusText("");
            } else {
                updateStatusForPhase(status, player2Uid);
            }
            Log.d(TAG, "round creation attempted");
            gameRepository.ensureMyNumberRound(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ensure my number round failed", e);
                        show(e.getMessage());
                    });
            listenRound();
        });
    }

    private void listenRound() {
        if (roundListener != null) {
            return;
        }
        roundListener = gameRepository.listenRound(gameId, gameRepository.myNumberRoundId(roundNumber), (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Round snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            Log.d(TAG, "My number round snapshot id=" + snapshot.getId()
                    + ", phase=" + snapshot.getString("phase")
                    + ", activePlayerUid=" + snapshot.getString("activePlayerUid")
                    + ", targetNumber=" + snapshot.getLong("targetNumber")
                    + ", numbers=" + snapshot.get("numbers"));
            bindRound(snapshot);
        });
        Log.d(TAG, "round listener attached gameId=" + gameId + ", roundId=" + gameRepository.myNumberRoundId(roundNumber));
    }

    private void bindRound(DocumentSnapshot round) {
        phase = value(round.getString("phase"));
        activePlayerUid = value(round.getString("activePlayerUid"));
        Long roundIndex = round.getLong("roundIndex");
        currentRoundIndex = roundIndex == null ? roundNumber : roundIndex.intValue();
        tvRound.setText("Runda: " + currentRoundIndex + "/2");
        Long target = round.getLong("targetNumber");
        targetNumber = target == null ? 0 : target.intValue();
        tvTarget.setText(getString(R.string.target_number, targetNumber));
        availableNumbers = readNumbers(round);
        tvNumbers.setText(formatNumbers(availableNumbers));
        buildNumberButtons();
        Map<String, Object> submissions = (Map<String, Object>) round.get("submissionsByPlayer");
        Map<String, Object> results = (Map<String, Object>) round.get("resultsByPlayer");
        submitted = submissions != null && submissions.containsKey(uid);
        Log.d(TAG, "Round state id=" + round.getId() + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid
                + ", targetNumber=" + targetNumber
                + ", numbers=" + availableNumbers
                + ", submissions=" + submissions
                + ", results=" + results);
        updateStatusForPhase(gameStatus, currentPlayer2Uid);
        btnStopTarget.setEnabled(uid.equals(activePlayerUid) && GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase));
        btnStopNumbers.setEnabled(uid.equals(activePlayerUid) && GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase));
        setPlayControls(GameRepository.PHASE_PLAYING.equals(phase) && !submitted);
        if ((GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase) || GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase))
                && uid.equals(activePlayerUid)) {
            startAutoStopTimer(round);
        } else if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }
        if (GameRepository.PHASE_PLAYING.equals(phase)) {
            startTimer(round);
        } else if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (GameRepository.PHASE_FINISHED.equals(phase)) {
            if (roundTimer != null) {
                roundTimer.cancel();
                roundTimer = null;
            }
            if (autoStopTimer != null) {
                autoStopTimer.cancel();
                autoStopTimer = null;
            }
            Long points = round.getLong("awardedPoints");
            tvPoints.setText(getString(R.string.points_text, points == null ? 0 : points.intValue()));
            tvResult.setText(getString(R.string.result_text,
                    roundNumber == 1 ? "Runda je završena." : "Finalni rezultat: " + player1Score + " : " + player2Score));
            if (challengeRun || roundNumber == 2) {
                recordStatsOnce();
                if (fullMatch) {
                    completeMiniGameOnce();
                }
                return;
            }
            moveToNextNumberRound();
        }
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordMyNumberGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record my number stats failed", e));
    }

    private void completeMiniGameOnce() {
        if (completingMiniGame || gameRepository == null || gameId == null || gameId.isEmpty()) {
            return;
        }
        completingMiniGame = true;
        gameRepository.completeMiniGame(gameId, GameRepository.MINI_MY_NUMBER)
                .addOnSuccessListener(ignored -> openFinalResult())
                .addOnFailureListener(e -> Log.e(TAG, "Complete my number in match failed", e));
    }

    private void openFinalResult() {
        if (!fullMatch || finalResultOpening || gameId == null || gameId.isEmpty()) {
            return;
        }
        finalResultOpening = true;
        GameFlow.openFinalResult(this, gameId);
    }

    private void startTimer(DocumentSnapshot round) {
        if (roundTimer != null) {
            return;
        }
        tvTimer.setText(getString(R.string.timer_text_60));
        Timestamp startedAt = round.getTimestamp("playStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, 60000 - elapsedMs);
        Log.d(TAG, "Round timer gameId=" + gameId + ", round=" + roundNumber
                + ", playStartedAt=" + startedAt + ", remainingMs=" + remainingMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            handleMyNumberRoundTimeout();
            return;
        }
        roundTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                tvResult.setText(getString(R.string.moj_broj_round_end));
                setPlayControls(false);
                handleMyNumberRoundTimeout();
            }
        };
        roundTimer.start();
    }

    private void startAutoStopTimer(DocumentSnapshot round) {
        if (autoStopTimer != null) {
            return;
        }
        Timestamp startedAt = round.getTimestamp("phaseStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, 5000 - elapsedMs);
        Log.d(TAG, "Auto-stop timer gameId=" + gameId + ", round=" + roundNumber
                + ", phase=" + phase + ", phaseStartedAt=" + startedAt + ", remainingMs=" + remainingMs);
        if (remainingMs == 0) {
            Log.d(TAG, "Auto-stop immediate, phase=" + phase + ", round=" + roundNumber);
            stopByCurrentPhase();
            return;
        }
        autoStopTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "Auto-stop fired, phase=" + phase + ", round=" + roundNumber);
                stopByCurrentPhase();
                autoStopTimer = null;
            }
        }.start();
    }

    private void handleMyNumberRoundTimeout() {
        gameRepository.finishMyNumberRound(gameId, roundNumber)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Finish my number round failed", e);
                    show(e.getMessage());
                });
    }

    private void stopByCurrentPhase() {
        Log.d(TAG, "Shake/auto STOP event, uid=" + uid + ", activePlayerUid=" + activePlayerUid
                + ", phase=" + phase + ", gameId=" + gameId + ", round=" + roundNumber);
        if (!uid.equals(activePlayerUid)) {
            return;
        }
        if (GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase)) {
            gameRepository.stopTarget(gameId, roundNumber)
                    .addOnFailureListener(e -> Log.e(TAG, "Shake stop target failed", e));
        } else if (GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase)) {
            gameRepository.stopNumbers(gameId, roundNumber)
                    .addOnFailureListener(e -> Log.e(TAG, "Shake stop numbers failed", e));
        }
    }

    private boolean isValidMyNumberGame(DocumentSnapshot game) {
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
        boolean valid = GameRepository.MINI_MY_NUMBER.equals(miniGame)
                && containsUser
                && (waitingAsPlayer1 || activeWithBothPlayers);
        Log.d(TAG, "Validate currentGameId=" + game.getId() + ", valid=" + valid
                + ", status=" + status + ", player1=" + p1 + ", player2=" + p2 + ", miniGame=" + miniGame);
        return valid;
    }

    private void moveToNextNumberRound() {
        if (challengeRun) {
            setStatusText("Moj broj je zavrÅ¡en");
            setPlayControls(false);
            btnStopTarget.setEnabled(false);
            btnStopNumbers.setEnabled(false);
            if (fullMatch) {
                completeMiniGameOnce();
            }
            return;
        }
        if (roundNumber == 1) {
            setStatusText("Priprema druge runde");
            roundNumber = 2;
            submitted = false;
            etExpression.setText("");
            if (roundTimer != null) {
                roundTimer.cancel();
                roundTimer = null;
            }
            if (roundListener != null) {
                roundListener.remove();
                roundListener = null;
            }
            if (!gameReady) {
                Log.d(TAG, "Activity: game ready false");
                return;
            }
            Log.d(TAG, "round creation attempted");
            gameRepository.ensureMyNumberRound(gameId, roundNumber);
            listenRound();
        } else {
            setStatusText("Moj broj je završen");
            setPlayControls(false);
            btnStopTarget.setEnabled(false);
            btnStopNumbers.setEnabled(false);
        }
    }

    private List<Integer> readNumbers(DocumentSnapshot round) {
        List<Integer> values = new ArrayList<>();
        List<Object> raw = (List<Object>) round.get("numbers");
        if (raw != null) {
            for (Object item : raw) {
                if (item instanceof Number) {
                    values.add(((Number) item).intValue());
                }
            }
        }
        return values;
    }

    private String formatNumbers(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "- - - - - -";
        }
        StringBuilder builder = new StringBuilder();
        for (Integer number : numbers) {
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(number);
        }
        return builder.toString();
    }

    private void setPlayControls(boolean enabled) {
        etExpression.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnClearExpression.setEnabled(enabled);
        btnConfirm.setEnabled(enabled);
        refreshNumberButtons();
        refreshOperatorButtons(enabled);
        Log.d(TAG, "Play controls enabled=" + enabled + ", gameId=" + gameId + ", roundId="
                + gameRepository.myNumberRoundId(roundNumber) + ", currentUserUid=" + uid
                + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid
                + ", submitted=" + submitted);
    }

    private void updateStatusForPhase(String status, String player2Uid) {
        String statusText;
        if ("waiting".equals(status) || value(player2Uid).isEmpty()) {
            statusText = "Čeka se drugi igrač";
        } else if (GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase)) {
            if (isInvalidActivePlayer()) {
                Log.e(TAG, "Missing activePlayerUid in Moj broj, currentUid=" + uid
                        + ", phase=" + phase + ", game.status=" + status + ", player2Uid=" + player2Uid);
                setStatusText("Priprema igre");
                gameRepository.repairRoundPlayers(gameId, gameRepository.myNumberRoundId(roundNumber), roundNumber, false);
                return;
            }
            statusText = uid.equals(activePlayerUid) ? "Zaustavite traženi broj" : "Protivnik bira traženi broj";
        } else if (GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase)) {
            if (isInvalidActivePlayer()) {
                Log.e(TAG, "Missing activePlayerUid in Moj broj, currentUid=" + uid
                        + ", phase=" + phase + ", game.status=" + status + ", player2Uid=" + player2Uid);
                setStatusText("Priprema igre");
                gameRepository.repairRoundPlayers(gameId, gameRepository.myNumberRoundId(roundNumber), roundNumber, false);
                return;
            }
            statusText = uid.equals(activePlayerUid) ? "Zaustavite ponuđene brojeve" : "Protivnik bira brojeve";
        } else if (GameRepository.PHASE_PLAYING.equals(phase)) {
            statusText = "Unesite izraz";
        } else if (GameRepository.PHASE_FINISHED.equals(phase)) {
            statusText = currentRoundIndex == 1 ? "Priprema druge runde" : "Moj broj je završen";
        } else {
            statusText = "";
        }
        setStatusText(statusText);
    }

    private boolean isInvalidActivePlayer() {
        if (!GameRepository.isNonEmpty(activePlayerUid)) {
            return true;
        }
        return !challengeRun
                && !activePlayerUid.equals(currentPlayer1Uid)
                && !activePlayerUid.equals(currentPlayer2Uid);
    }

    private void updateStatusForPhase() {
        String status;
        if (GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase)) {
            status = uid.equals(activePlayerUid) ? "Zaustavite traženi broj" : "Protivnik bira traženi broj";
        } else if (GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase)) {
            status = uid.equals(activePlayerUid) ? "Zaustavite ponuđene brojeve" : "Protivnik bira brojeve";
        } else if (GameRepository.PHASE_PLAYING.equals(phase)) {
            status = "Unesite izraz";
        } else if (GameRepository.PHASE_FINISHED.equals(phase)) {
            status = currentRoundIndex == 1 ? "Priprema druge runde" : "Moj broj je završen";
        } else {
            status = "";
        }
        setStatusText(status);
    }

    private void clearWaitingResultText() {
        if (tvResult != null && getString(R.string.waiting_opponent).contentEquals(tvResult.getText())) {
            tvResult.setText("");
        }
    }

    private void setStatusText(String status) {
        if (challengeRun) {
            status = challengeStatus(status);
        }
        if (tvStatus != null) {
            tvStatus.setText(status);
        }
        headerHelper.updateStatus(status);
        Log.d(TAG, "Status text=" + status + ", gameId=" + gameId + ", round=" + roundNumber
                + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid);
        Log.d(TAG, "Moj broj status display: game.status=" + gameStatus
                + ", player2Uid=" + currentPlayer2Uid
                + ", phase=" + phase
                + ", statusText=" + status);
        boolean isCurrentUsersTurn = (GameRepository.PHASE_WAITING_TARGET_STOP.equals(phase)
                || GameRepository.PHASE_WAITING_NUMBERS_STOP.equals(phase))
                && uid != null && uid.equals(activePlayerUid);
        Log.d(DEBUG_TAG, "uid=" + uid
                + ", player1Uid=" + currentPlayer1Uid
                + ", player2Uid=" + currentPlayer2Uid
                + ", currentPlayerUid=" + activePlayerUid
                + ", phase=" + phase
                + ", status=" + gameStatus
                + ", isMyTurn=" + isCurrentUsersTurn
                + ", text=" + status);
        Log.d(TAG, "Turn status currentUid=" + uid
                + ", player1Uid=" + currentPlayer1Uid + ", player2Uid=" + currentPlayer2Uid
                + ", activePlayerUid=" + activePlayerUid
                + ", opponentUid="
                + ", currentTurnUid="
                + ", phase=" + phase
                + ", calculatedStatusText=" + status
                + ", isCurrentUsersTurn=" + isCurrentUsersTurn);
    }

    private void buildNumberButtons() {
        if (llNumberButtons == null) {
            return;
        }
        llNumberButtons.removeAllViews();
        numberButtons.clear();
        LinearLayout row = null;
        for (int i = 0; i < availableNumbers.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, i == 0 ? 0 : dp(5), 0, 0);
                llNumberButtons.addView(row, rowParams);
            }
            final int index = i;
            Button button = tokenButton(String.valueOf(availableNumbers.get(i)));
            button.setOnClickListener(v -> appendNumberToken(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
            params.setMargins(i % 3 == 0 ? 0 : dp(6), 0, 0, 0);
            row.addView(button, params);
            numberButtons.add(button);
        }
        recalculateUsedNumbersFromExpression(etExpression.getText().toString());
        refreshNumberButtons();
    }

    private void buildOperatorButtons() {
        if (llOperatorButtons == null) {
            return;
        }
        llOperatorButtons.removeAllViews();
        String[] operators = {"+", "-", "*", "/", "(", ")"};
        LinearLayout row = null;
        for (int i = 0; i < operators.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, i == 0 ? 0 : dp(5), 0, 0);
                llOperatorButtons.addView(row, rowParams);
            }
            String operator = operators[i];
            Button button = tokenButton(operator);
            button.setOnClickListener(v -> appendOperatorToken(operator));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            params.setMargins(i % 3 == 0 ? 0 : dp(6), 0, 0, 0);
            row.addView(button, params);
        }
    }

    private Button tokenButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(53, 43, 69));
        button.setBackgroundResource(R.drawable.bg_number_chip);
        return button;
    }

    private void appendNumberToken(int index) {
        if (index < 0 || index >= availableNumbers.size() || usedNumberIndexes.contains(index) || !etExpression.isEnabled()) {
            return;
        }
        usedNumberIndexes.add(index);
        expressionTokens.add(new Token(String.valueOf(availableNumbers.get(index)), index));
        setExpressionFromTokens();
        refreshNumberButtons();
    }

    private void appendOperatorToken(String operator) {
        if (!etExpression.isEnabled()) {
            return;
        }
        expressionTokens.add(new Token(operator, -1));
        setExpressionFromTokens();
    }

    private void deleteLastExpressionToken() {
        if (!expressionTokens.isEmpty() && etExpression.getText().toString().equals(joinTokens())) {
            Token token = expressionTokens.remove(expressionTokens.size() - 1);
            if (token.numberIndex >= 0) {
                usedNumberIndexes.remove(token.numberIndex);
            }
            setExpressionFromTokens();
            refreshNumberButtons();
            return;
        }
        String expression = etExpression.getText().toString();
        if (!expression.isEmpty()) {
            updatingExpressionProgrammatically = true;
            etExpression.setText(expression.substring(0, expression.length() - 1));
            etExpression.setSelection(etExpression.getText().length());
            updatingExpressionProgrammatically = false;
            expressionTokens.clear();
            recalculateUsedNumbersFromExpression(etExpression.getText().toString());
            refreshNumberButtons();
        }
    }

    private void clearExpression() {
        expressionTokens.clear();
        usedNumberIndexes.clear();
        updatingExpressionProgrammatically = true;
        etExpression.setText("");
        updatingExpressionProgrammatically = false;
        refreshNumberButtons();
    }

    private void setExpressionFromTokens() {
        updatingExpressionProgrammatically = true;
        etExpression.setText(joinTokens());
        etExpression.setSelection(etExpression.getText().length());
        updatingExpressionProgrammatically = false;
    }

    private String joinTokens() {
        StringBuilder builder = new StringBuilder();
        for (Token token : expressionTokens) {
            builder.append(token.text);
        }
        return builder.toString();
    }

    private void recalculateUsedNumbersFromExpression(String expression) {
        usedNumberIndexes.clear();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (Character.isDigit(c)) {
                int start = i;
                while (i < expression.length() && Character.isDigit(expression.charAt(i))) {
                    i++;
                }
                int value = Integer.parseInt(expression.substring(start, i));
                int index = firstUnusedNumberIndex(value);
                if (index >= 0) {
                    usedNumberIndexes.add(index);
                }
            } else {
                i++;
            }
        }
    }

    private int firstUnusedNumberIndex(int value) {
        for (int i = 0; i < availableNumbers.size(); i++) {
            if (!usedNumberIndexes.contains(i) && availableNumbers.get(i) == value) {
                return i;
            }
        }
        return -1;
    }

    private void refreshNumberButtons() {
        boolean controlsEnabled = etExpression != null && etExpression.isEnabled();
        for (int i = 0; i < numberButtons.size(); i++) {
            Button button = numberButtons.get(i);
            boolean used = usedNumberIndexes.contains(i);
            button.setEnabled(controlsEnabled && !used);
            button.setBackgroundResource(used ? R.drawable.bg_number_chip_used : R.drawable.bg_number_chip);
            button.setTextColor(used ? Color.rgb(124, 112, 146) : Color.rgb(53, 43, 69));
        }
    }

    private void refreshOperatorButtons(boolean enabled) {
        if (llOperatorButtons == null) {
            return;
        }
        for (int i = 0; i < llOperatorButtons.getChildCount(); i++) {
            View row = llOperatorButtons.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout linearRow = (LinearLayout) row;
                for (int j = 0; j < linearRow.getChildCount(); j++) {
                    linearRow.getChildAt(j).setEnabled(enabled);
                }
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class Token {
        final String text;
        final int numberIndex;

        Token(String text, int numberIndex) {
            this.text = text;
            this.numberIndex = numberIndex;
        }
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
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
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
