package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.service.StepByStepService;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameHeaderHelper;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class KorakPoKorakActivity extends AppCompatActivity {
    private static final String TAG = "KorakPoKorakActivity";
    private final TextView[] stepViews = new TextView[7];
    private final StepByStepService stepService = new StepByStepService();
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private CountDownTimer timer;
    private String uid;
    private String gameId;
    private int roundNumber = 1;
    private int openedSteps = 0;
    private String phase = "";
    private String activePlayerUid = "";
    private String opponentUid = "";
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean statsRecordRequested = false;
    private TextView tvTimer;
    private TextView tvPoints;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;
    private TextView tvResult;
    private TextView tvStepStatus;
    private EditText etSolution;
    private android.widget.Button btnCheckSolution;
    private GameHeaderHelper headerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from KorakPoKorakActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);

        stepViews[0] = findViewById(R.id.step1);
        stepViews[1] = findViewById(R.id.step2);
        stepViews[2] = findViewById(R.id.step3);
        stepViews[3] = findViewById(R.id.step4);
        stepViews[4] = findViewById(R.id.step5);
        stepViews[5] = findViewById(R.id.step6);
        stepViews[6] = findViewById(R.id.step7);

        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);
        etSolution = findViewById(R.id.etSolution);
        tvResult = findViewById(R.id.tvResult);
        tvStepStatus = findViewById(R.id.tvStepStatus);
        btnCheckSolution = findViewById(R.id.btnCheckSolution);
        headerHelper = new GameHeaderHelper(this, findViewById(android.R.id.content));
        headerHelper.updateGameTitle("Korak po korak");

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            setControls(false);
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "Current uid=" + uid);
        gameRepository.seedStepQuestionsIfNeeded()
                .addOnFailureListener(e -> Log.e(TAG, "Seed step questions failed", e));
        gameRepository.joinOrCreateGame(uid, GameRepository.MINI_STEP_BY_STEP)
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "Step gameId=" + gameId);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Step matchmaking failed", e);
                    show(e.getMessage());
                });

        btnCheckSolution.setOnClickListener(v -> {
            String solution = etSolution.getText().toString().trim();
            if (solution.isEmpty()) {
                show(getString(R.string.error_fill_fields));
                return;
            }
            boolean allowed = ("ACTIVE_PLAYER".equals(phase) && uid.equals(activePlayerUid))
                    || ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponentUid));
            if (!allowed) {
                show(getString(R.string.not_your_turn));
                return;
            }
            gameRepository.submitStepAnswer(gameId, roundNumber, uid, solution)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Submit step answer failed", e);
                        show(e.getMessage());
                    });
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
            Long p1 = snapshot.getLong("player1Score");
            Long p2 = snapshot.getLong("player2Score");
            player1Score = p1 == null ? 0 : p1.intValue();
            player2Score = p2 == null ? 0 : p2.intValue();
            tvPlayer1Score.setText(getString(R.string.player_points, player1Score));
            tvPlayer2Score.setText(getString(R.string.player_points, player2Score));
            headerHelper.updatePlayers(snapshot.getString("player1Uid"), player1Score,
                    snapshot.getString("player2Uid"), player2Score);
            if ("waiting".equals(snapshot.getString("status")) || value(snapshot.getString("player2Uid")).isEmpty()) {
                tvResult.setText(R.string.waiting_opponent);
                setControls(false);
                setStatusText("Čeka se protivnik");
                return;
            }
            if (!GameRepository.MINI_STEP_BY_STEP.equals(snapshot.getString("currentMiniGame"))) {
                tvResult.setText(getString(R.string.result_text, "Korak po korak je zavrsen. Predjite na Moj broj."));
                setControls(false);
                setStatusText("Korak po korak je završen");
                return;
            }
            if (getString(R.string.waiting_opponent).contentEquals(tvResult.getText())) {
                tvResult.setText("");
            }
            Log.d(TAG, "Game became active, currentUserUid=" + uid + ", gameId=" + gameId
                    + ", player1Uid=" + snapshot.getString("player1Uid")
                    + ", player2Uid=" + snapshot.getString("player2Uid")
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            gameRepository.ensureStepRound(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ensure step round failed", e);
                        show(e.getMessage());
                    });
            listenRound();
        });
    }

    private void listenRound() {
        if (roundListener != null) {
            return;
        }
        roundListener = gameRepository.listenRound(gameId, gameRepository.stepRoundId(roundNumber), (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Round snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            Log.d(TAG, "Step round snapshot id=" + snapshot.getId()
                    + ", roundIndex=" + snapshot.getLong("roundIndex")
                    + ", phase=" + snapshot.getString("phase")
                    + ", activePlayerUid=" + snapshot.getString("activePlayerUid")
                    + ", opponentUid=" + snapshot.getString("opponentUid")
                    + ", openedStepIndex=" + snapshot.getLong("openedStepIndex")
                    + ", phaseStartedAt=" + snapshot.getTimestamp("phaseStartedAt"));
            bindRound(snapshot);
        });
    }

    private void bindRound(DocumentSnapshot round) {
        activePlayerUid = value(round.getString("activePlayerUid"));
        opponentUid = value(round.getString("opponentUid"));
        phase = value(round.getString("phase"));
        Long opened = round.getLong("openedStepIndex");
        openedSteps = opened == null ? 0 : opened.intValue();
        List<String> steps = (List<String>) round.get("steps");
        Log.d(TAG, "Round state id=" + round.getId()
                + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid
                + ", opponentUid=" + opponentUid
                + ", openedStepIndex=" + openedSteps);
        for (int i = 0; i < stepViews.length; i++) {
            if (steps != null && i <= openedSteps && i < steps.size()) {
                stepViews[i].setText((i + 1) + ". " + steps.get(i));
            } else {
                stepViews[i].setText((i + 1) + ". ?");
            }
        }
        int currentPoints = "OPPONENT_CHANCE".equals(phase) ? 5 : stepService.pointsForStep(openedSteps);
        tvPoints.setText(getString(R.string.points_text, currentPoints));
        setControls(("ACTIVE_PLAYER".equals(phase) && uid.equals(activePlayerUid))
                || ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponentUid)));
        if ("FINISHED".equals(phase)) {
            moveToNextStepRound();
            return;
        }
        startTurnTimer(round);
    }

    private void startTurnTimer(DocumentSnapshot round) {
        if (timer != null) {
            timer.cancel();
        }
        Timestamp startedAt = round.getTimestamp("phaseStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, 10000 - elapsedMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            handleStepTimerFinished();
            return;
        }
        timer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                handleStepTimerFinished();
            }
        }.start();
    }

    private void handleStepTimerFinished() {
        if ("ACTIVE_PLAYER".equals(phase) && uid.equals(activePlayerUid)) {
            gameRepository.openNextStep(gameId, roundNumber, uid, true)
                    .addOnFailureListener(e -> Log.e(TAG, "Open next step on timeout failed", e));
        } else if ("OPPONENT_CHANCE".equals(phase) && uid.equals(opponentUid)) {
            gameRepository.finishStepRound(gameId, roundNumber)
                    .addOnFailureListener(e -> Log.e(TAG, "Finish step round on timeout failed", e));
        }
    }

    private void moveToNextStepRound() {
        if (roundNumber == 1) {
            roundNumber = 2;
            if (roundListener != null) {
                roundListener.remove();
                roundListener = null;
            }
            etSolution.setText("");
            gameRepository.ensureStepRound(gameId, roundNumber)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ensure second step round failed", e);
                        show(e.getMessage());
                    });
            listenRound();
        } else {
            recordStatsOnce();
            gameRepository.getGame(gameId)
                    .addOnSuccessListener(game -> {
                        Long p1 = game.getLong("player1Score");
                        Long p2 = game.getLong("player2Score");
                        player1Score = p1 == null ? player1Score : p1.intValue();
                        player2Score = p2 == null ? player2Score : p2.intValue();
                        tvPlayer1Score.setText(getString(R.string.player_points, player1Score));
                        tvPlayer2Score.setText(getString(R.string.player_points, player2Score));
                        tvResult.setText(getString(R.string.result_text,
                                "Korak po korak je zavrsen. Konacan rezultat: Igrac 1 "
                                        + player1Score + " - Igrac 2 " + player2Score));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Load final step score failed", e);
                        tvResult.setText(getString(R.string.result_text,
                                "Korak po korak je zavrsen. Konacan rezultat: Igrac 1 "
                                        + player1Score + " - Igrac 2 " + player2Score));
                    });
            tvTimer.setText(getString(R.string.timer_text, 0));
            setControls(false);
        }
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordStepGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record step stats failed", e));
    }

    private void setControls(boolean enabled) {
        boolean canSubmit = enabled && openedSteps >= 0;
        btnCheckSolution.setEnabled(canSubmit);
        etSolution.setEnabled(canSubmit);
        if ("ACTIVE_PLAYER".equals(phase)) {
            if (uid != null && uid.equals(activePlayerUid)) {
                setStatusText("Vaš potez");
            } else {
                setStatusText("Protivnik igra");
            }
        } else if ("OPPONENT_CHANCE".equals(phase)) {
            if (uid != null && uid.equals(opponentUid)) {
                setStatusText("Vaša šansa za 5 poena");
            } else {
                setStatusText("Protivnik pokušava za 5 poena");
            }
        } else if ("FINISHED".equals(phase)) {
            if (roundNumber == 1) {
                setStatusText("Priprema sledeće runde");
            } else {
                setStatusText("Korak po korak je završen");
            }
        } else {
            setStatusText("Koraci se otvaraju automatski");
        }
    }

    private void setStatusText(String statusText) {
        tvStepStatus.setText(statusText);
        headerHelper.updateStatus(statusText);
        Log.d(TAG, "Status text=" + statusText + ", uid=" + uid
                + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid
                + ", opponentUid=" + opponentUid + ", openedStepIndex=" + openedSteps);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        if (gameListener != null) {
            gameListener.remove();
        }
        if (roundListener != null) {
            roundListener.remove();
        }
        super.onDestroy();
    }
}
