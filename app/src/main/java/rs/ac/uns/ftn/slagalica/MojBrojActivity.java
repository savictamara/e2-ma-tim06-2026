package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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
import rs.ac.uns.ftn.slagalica.domain.service.MyNumberService;
import rs.ac.uns.ftn.slagalica.util.ExpressionEvaluator;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GuestSession;
import rs.ac.uns.ftn.slagalica.util.ShakeDetector;

public class MojBrojActivity extends AppCompatActivity {
    private static final String TAG = "MojBrojActivity";
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
    private TextView tvTarget;
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
    private Button btnConfirm;

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
        btnConfirm = findViewById(R.id.btnConfirm);

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
        userRepository.currentGameId(uid)
                .continueWithTask(task -> {
                    String existingGameId = task.isSuccessful() ? task.getResult() : "";
                    if (existingGameId != null && !existingGameId.isEmpty()) {
                        return gameRepository.getGame(existingGameId).continueWithTask(gameTask -> {
                            if (gameTask.isSuccessful() && isValidMyNumberGame(gameTask.getResult())) {
                                return com.google.android.gms.tasks.Tasks.forResult(existingGameId);
                            }
                            return gameRepository.joinOrCreateGame(uid, GameRepository.MINI_MY_NUMBER);
                        });
                    }
                    return gameRepository.joinOrCreateGame(uid, GameRepository.MINI_MY_NUMBER);
                })
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "My number gameId=" + gameId);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "My number matchmaking failed", e);
                    show(e.getMessage());
                });

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
            String expression = etExpression.getText().toString();
            if (!expression.isEmpty()) {
                etExpression.setText(expression.substring(0, expression.length() - 1));
                etExpression.setSelection(etExpression.getText().length());
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
            Log.d(TAG, "Game snapshot gameId=" + snapshot.getId() + ", status=" + snapshot.getString("status")
                    + ", player1=" + snapshot.getString("player1Uid")
                    + ", player2=" + snapshot.getString("player2Uid")
                    + ", miniGame=" + snapshot.getString("currentMiniGame"));
            Long p1 = snapshot.getLong("player1Score");
            Long p2 = snapshot.getLong("player2Score");
            player1Score = p1 == null ? 0 : p1.intValue();
            player2Score = p2 == null ? 0 : p2.intValue();
            tvPlayer1.setText(getString(R.string.player_points, player1Score));
            tvPlayer2.setText(getString(R.string.player_points, player2Score));
            String status = snapshot.getString("status");
            String player2Uid = snapshot.getString("player2Uid");
            if ("waiting".equals(status) || player2Uid == null) {
                setStatusText("Čeka se drugi igrač");
                tvResult.setText(R.string.waiting_opponent);
                setPlayControls(false);
                return;
            }
            if ("finished".equals(status)) {
                setStatusText("Moj broj je završen");
                tvResult.setText(getString(R.string.result_text,
                        "Finalni rezultat: " + player1Score + " : " + player2Score));
                setPlayControls(false);
                userRepository.updateUserState(uid, true, false, "");
                return;
            }
            if (phase.isEmpty()) {
                setStatusText("");
            } else {
                updateStatusForPhase();
            }
            gameRepository.ensureMyNumberRound(gameId, roundNumber);
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
    }

    private void bindRound(DocumentSnapshot round) {
        phase = value(round.getString("phase"));
        activePlayerUid = value(round.getString("activePlayerUid"));
        Long roundIndex = round.getLong("roundIndex");
        currentRoundIndex = roundIndex == null ? roundNumber : roundIndex.intValue();
        Long target = round.getLong("targetNumber");
        targetNumber = target == null ? 0 : target.intValue();
        tvTarget.setText(getString(R.string.target_number, targetNumber));
        availableNumbers = readNumbers(round);
        tvNumbers.setText(formatNumbers(availableNumbers));
        Map<String, Object> submissions = (Map<String, Object>) round.get("submissionsByPlayer");
        Map<String, Object> results = (Map<String, Object>) round.get("resultsByPlayer");
        submitted = submissions != null && submissions.containsKey(uid);
        Log.d(TAG, "Round state id=" + round.getId() + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid
                + ", targetNumber=" + targetNumber
                + ", numbers=" + availableNumbers
                + ", submissions=" + submissions
                + ", results=" + results);
        updateStatusForPhase();
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
            if (roundNumber == 2) {
                recordStatsOnce();
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
        btnConfirm.setEnabled(enabled);
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

    private void setStatusText(String status) {
        if (tvStatus != null) {
            tvStatus.setText(status);
        }
        Log.d(TAG, "Status text=" + status + ", gameId=" + gameId + ", round=" + roundNumber
                + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid);
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

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }
}
