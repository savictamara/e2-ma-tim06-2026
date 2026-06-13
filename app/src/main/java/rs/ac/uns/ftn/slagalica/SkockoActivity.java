package rs.ac.uns.ftn.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class SkockoActivity extends AppCompatActivity {
    private static final String TAG = "SkockoActivity";
    private static final String PHASE_ACTIVE_PLAYER = "ACTIVE_PLAYER";
    private static final String PHASE_OPPONENT_CHANCE = "OPPONENT_CHANCE";
    private static final String PHASE_FINISHED = "FINISHED";
    private static final long ACTIVE_PLAYER_MS = 30_000;
    private static final long OPPONENT_CHANCE_MS = 10_000;

    private static final List<String> SYMBOLS = Arrays.asList(
            "SKOCKO", "KVADRAT", "KRUG", "SRCE", "TROUGAO", "ZVEZDA"
    );
    private static final int[] SYMBOL_DRAWABLES = {
            R.drawable.skocko,
            R.drawable.black_square,
            R.drawable.circle,
            R.drawable.heart_2,
            R.drawable.triangle,
            R.drawable.star
    };

    private final int[][] rowSlotIds = {
            {R.id.row1slot1, R.id.row1slot2, R.id.row1slot3, R.id.row1slot4},
            {R.id.row2slot1, R.id.row2slot2, R.id.row2slot3, R.id.row2slot4},
            {R.id.row3slot1, R.id.row3slot2, R.id.row3slot3, R.id.row3slot4},
            {R.id.row4slot1, R.id.row4slot2, R.id.row4slot3, R.id.row4slot4},
            {R.id.row5slot1, R.id.row5slot2, R.id.row5slot3, R.id.row5slot4},
            {R.id.row6slot1, R.id.row6slot2, R.id.row6slot3, R.id.row6slot4}
    };
    private final int[] rowFeedbackIds = {
            R.id.row1feedback, R.id.row2feedback, R.id.row3feedback,
            R.id.row4feedback, R.id.row5feedback, R.id.row6feedback
    };
    private final int[] draftSymbolIndex = {-1, -1, -1, -1};

    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private CountDownTimer timer;

    private String uid = "";
    private String gameId = "";
    private String player1Uid = "";
    private String player2Uid = "";
    private String phase = "";
    private String activePlayerUid = "";
    private String opponentUid = "";
    private int roundNumber = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean statsRecordRequested = false;
    private boolean controlsEnabled = false;

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScoreP1;
    private TextView tvScoreP2;
    private TextView tvStatus;
    private ImageView[] guessSlots;
    private ViewGroup paletteBar;
    private Button btnCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from SkockoActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);

        tvRound = findViewById(R.id.tvRound);
        tvCurrentPlayer = findViewById(R.id.tvCurrentPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScoreP1 = findViewById(R.id.tvScoreP1);
        tvScoreP2 = findViewById(R.id.tvScoreP2);
        tvStatus = findViewById(R.id.tvStatus);
        paletteBar = findViewById(R.id.paletteBar);
        guessSlots = new ImageView[] {
                findViewById(R.id.ivGuessSlot1),
                findViewById(R.id.ivGuessSlot2),
                findViewById(R.id.ivGuessSlot3),
                findViewById(R.id.ivGuessSlot4)
        };
        btnCheck = findViewById(R.id.btnCheck);

        bindPaletteSymbols();
        resetBoardRows();
        resetDraftGuess();
        setControls(false);

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "currentUserUid=" + uid);
        gameRepository.joinOrCreateGame(uid, GameRepository.MINI_SKOCKO)
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "Skocko gameId=" + gameId + ", currentUserUid=" + uid);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Skocko matchmaking failed", e);
                    show(e.getMessage());
                });

        btnCheck.setOnClickListener(v -> submitGuess());
    }

    private void bindPaletteSymbols() {
        setPaletteClick(findViewById(R.id.ivPaletteS), 0);
        setPaletteClick(findViewById(R.id.ivPaletteK), 1);
        setPaletteClick(findViewById(R.id.ivPaletteO), 2);
        setPaletteClick(findViewById(R.id.ivPaletteH), 3);
        setPaletteClick(findViewById(R.id.ivPaletteT), 4);
        setPaletteClick(findViewById(R.id.ivPaletteZ), 5);
    }

    private void setPaletteClick(ImageView view, final int symbolIndex) {
        view.setOnClickListener(v -> appendSymbolDraft(symbolIndex));
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
            String status = value(snapshot.getString("status"));
            player1Uid = value(snapshot.getString("player1Uid"));
            player2Uid = value(snapshot.getString("player2Uid"));
            player1Score = intValue(snapshot.get("player1Score"));
            player2Score = intValue(snapshot.get("player2Score"));
            Log.d(TAG, "Game snapshot currentUserUid=" + uid + ", gameId=" + snapshot.getId()
                    + ", status=" + status + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame")
                    + ", score=" + player1Score + ":" + player2Score);
            updateScoreViews();
            if ("waiting".equals(status) || player2Uid.isEmpty()) {
                setStatusText("Čeka se drugi igrač");
                setControls(false);
                tvRound.setText("Runda: -/2");
                return;
            }
            if ("finished".equals(status)) {
                setStatusText("Skočko je završen");
                setControls(false);
                tvTimer.setText(getString(R.string.timer_text, 0));
                recordStatsOnce();
                return;
            }
            if (!GameRepository.MINI_SKOCKO.equals(snapshot.getString("currentMiniGame"))) {
                setControls(false);
                return;
            }
            ensureAndListenRound(roundNumber);
        });
    }

    private void ensureAndListenRound(int targetRound) {
        gameRepository.ensureSkockoRound(gameId, targetRound, SYMBOLS)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ensure skocko round failed", e);
                    show(e.getMessage());
                });
        listenRound(targetRound);
    }

    private void listenRound(int targetRound) {
        if (roundListener != null && targetRound == roundNumber) {
            return;
        }
        if (roundListener != null) {
            roundListener.remove();
            roundListener = null;
        }
        roundNumber = targetRound;
        String roundId = gameRepository.skockoRoundId(roundNumber);
        roundListener = gameRepository.listenRound(gameId, roundId, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Round snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            Log.d(TAG, "Round snapshot gameId=" + gameId + ", roundId=" + snapshot.getId()
                    + ", phase=" + snapshot.getString("phase")
                    + ", activePlayerUid=" + snapshot.getString("activePlayerUid")
                    + ", opponentUid=" + snapshot.getString("opponentUid")
                    + ", currentAttemptIndex=" + snapshot.getLong("currentAttemptIndex"));
            bindRound(snapshot);
        });
    }

    private void bindRound(DocumentSnapshot round) {
        phase = value(round.getString("phase"));
        activePlayerUid = value(round.getString("activePlayerUid"));
        opponentUid = value(round.getString("opponentUid"));
        int currentAttemptIndex = intValue(round.get("currentAttemptIndex"));
        tvRound.setText("Runda: " + roundNumber + "/2");
        tvCurrentPlayer.setText(currentPlayerText());
        renderAttempts(round);
        updateStatusForPhase();
        boolean canPlay = canCurrentUserPlay();
        setControls(canPlay);
        Log.d(TAG, "Bound skocko round currentUserUid=" + uid + ", gameId=" + gameId
                + ", roundId=" + round.getId() + ", phase=" + phase
                + ", activePlayerUid=" + activePlayerUid + ", opponentUid=" + opponentUid
                + ", currentAttemptIndex=" + currentAttemptIndex + ", canPlay=" + canPlay);
        if (PHASE_FINISHED.equals(phase)) {
            stopTimer();
            tvTimer.setText(getString(R.string.timer_text, 0));
            setControls(false);
            if (roundNumber == 1) {
                setStatusText("Priprema druge runde");
                ensureAndListenRound(2);
            } else {
                setStatusText("Skočko je završen");
                recordStatsOnce();
            }
            return;
        }
        startPhaseTimer(round);
    }

    private void submitGuess() {
        if (!controlsEnabled || !canCurrentUserPlay()) {
            show("Protivnik igra");
            return;
        }
        if (!isDraftComplete()) {
            show(getString(R.string.skocko_need_four));
            return;
        }
        List<String> guess = readDraftGuess();
        Log.d(TAG, "Submit clicked currentUserUid=" + uid + ", gameId=" + gameId
                + ", roundId=" + gameRepository.skockoRoundId(roundNumber)
                + ", phase=" + phase + ", attempt=" + guess);
        setControls(false);
        gameRepository.submitSkockoAttempt(gameId, roundNumber, uid, guess)
                .addOnSuccessListener(unused -> resetDraftGuess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Submit skocko attempt failed", e);
                    show(e.getMessage());
                    setControls(canCurrentUserPlay());
                });
    }

    private void startPhaseTimer(DocumentSnapshot round) {
        stopTimer();
        Timestamp startedAt = round.getTimestamp("phaseStartedAt");
        long duration = PHASE_OPPONENT_CHANCE.equals(phase) ? OPPONENT_CHANCE_MS : ACTIVE_PLAYER_MS;
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, duration - elapsedMs);
        Log.d(TAG, "Timer gameId=" + gameId + ", roundId=" + round.getId()
                + ", phase=" + phase + ", phaseStartedAt=" + startedAt + ", remainingMs=" + remainingMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            handleTimerFinished();
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
                handleTimerFinished();
            }
        }.start();
    }

    private void handleTimerFinished() {
        if (!PHASE_ACTIVE_PLAYER.equals(phase) && !PHASE_OPPONENT_CHANCE.equals(phase)) {
            return;
        }
        Log.d(TAG, "Timer finished currentUserUid=" + uid + ", gameId=" + gameId
                + ", roundId=" + gameRepository.skockoRoundId(roundNumber) + ", phase=" + phase);
        gameRepository.handleSkockoTimeout(gameId, roundNumber, phase)
                .addOnFailureListener(e -> Log.e(TAG, "Skocko timeout transaction failed", e));
    }

    private void renderAttempts(DocumentSnapshot round) {
        resetBoardRows();
        List<Object> activeAttempts = attemptsFor(round, activePlayerUid);
        List<Object> activeFeedback = feedbackFor(round, activePlayerUid);
        for (int i = 0; i < activeAttempts.size() && i < rowSlotIds.length; i++) {
            Map<String, Object> attempt = asMap(activeAttempts.get(i));
            Map<String, Object> feedback = i < activeFeedback.size() ? asMap(activeFeedback.get(i)) : attempt;
            fillGuessRow(i, stringList(attempt.get("symbols")),
                    intValue(feedback.get("exactMatches")), intValue(feedback.get("partialMatches")));
        }
    }

    private void fillGuessRow(int rowIndex, List<String> symbols, int exact, int partial) {
        for (int col = 0; col < 4 && col < symbols.size(); col++) {
            int symbolIndex = SYMBOLS.indexOf(symbols.get(col));
            if (symbolIndex >= 0) {
                ImageView slot = findViewById(rowSlotIds[rowIndex][col]);
                slot.setImageResource(SYMBOL_DRAWABLES[symbolIndex]);
            }
        }
        TextView feedback = findViewById(rowFeedbackIds[rowIndex]);
        feedback.setText(buildFeedbackSquares(exact, partial));
        feedback.setTextColor(Color.BLACK);
    }

    private CharSequence buildFeedbackSquares(int exact, int partial) {
        String squares = "\u25A0\u25A0\u25A0\u25A0";
        SpannableString span = new SpannableString(squares);
        for (int i = 0; i < 4; i++) {
            int color = Color.parseColor("#BDBDBD");
            if (i < exact) {
                color = Color.parseColor("#EF5350");
            } else if (i < exact + partial) {
                color = Color.parseColor("#FFD54F");
            }
            span.setSpan(new ForegroundColorSpan(color), i, i + 1, 0);
        }
        return span;
    }

    private void resetBoardRows() {
        for (int rowIndex = 0; rowIndex < rowSlotIds.length; rowIndex++) {
            for (int col = 0; col < 4; col++) {
                ImageView slot = findViewById(rowSlotIds[rowIndex][col]);
                slot.setImageDrawable(null);
            }
            TextView feedback = findViewById(rowFeedbackIds[rowIndex]);
            feedback.setText(buildFeedbackSquares(0, 0));
            feedback.setTextColor(Color.BLACK);
            feedback.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void appendSymbolDraft(int symbolIndex) {
        if (!canCurrentUserPlay()) {
            show("Protivnik igra");
            return;
        }
        for (int i = 0; i < draftSymbolIndex.length; i++) {
            if (draftSymbolIndex[i] < 0) {
                draftSymbolIndex[i] = symbolIndex;
                break;
            }
        }
        redrawDraftSlots();
        refreshCheckAvailability();
    }

    private boolean canCurrentUserPlay() {
        return (PHASE_ACTIVE_PLAYER.equals(phase) && uid.equals(activePlayerUid))
                || (PHASE_OPPONENT_CHANCE.equals(phase) && uid.equals(opponentUid));
    }

    private boolean isDraftComplete() {
        for (int value : draftSymbolIndex) {
            if (value < 0) {
                return false;
            }
        }
        return true;
    }

    private List<String> readDraftGuess() {
        List<String> guess = new ArrayList<>();
        for (int index : draftSymbolIndex) {
            guess.add(SYMBOLS.get(index));
        }
        return guess;
    }

    private void redrawDraftSlots() {
        for (int i = 0; i < guessSlots.length; i++) {
            int idx = draftSymbolIndex[i];
            if (idx < 0) {
                guessSlots[i].setImageDrawable(null);
            } else {
                guessSlots[i].setImageResource(SYMBOL_DRAWABLES[idx]);
            }
        }
    }

    private void resetDraftGuess() {
        Arrays.fill(draftSymbolIndex, -1);
        redrawDraftSlots();
        refreshCheckAvailability();
    }

    private void setControls(boolean enabled) {
        controlsEnabled = enabled;
        if (paletteBar != null) {
            paletteBar.setEnabled(enabled);
            for (int i = 0; i < paletteBar.getChildCount(); i++) {
                View child = paletteBar.getChildAt(i);
                child.setEnabled(enabled);
                child.setClickable(enabled);
                child.setAlpha(enabled ? 1f : 0.42f);
            }
        }
        refreshCheckAvailability();
    }

    private void refreshCheckAvailability() {
        boolean canSubmit = controlsEnabled && canCurrentUserPlay() && isDraftComplete();
        btnCheck.setEnabled(canSubmit);
        if (paletteBar != null && controlsEnabled && canCurrentUserPlay()) {
            boolean full = isDraftComplete();
            for (int i = 0; i < paletteBar.getChildCount(); i++) {
                View child = paletteBar.getChildAt(i);
                child.setEnabled(!full);
                child.setClickable(!full);
                child.setAlpha(full ? 0.42f : 1f);
            }
        }
    }

    private void updateStatusForPhase() {
        if (PHASE_ACTIVE_PLAYER.equals(phase)) {
            setStatusText(uid.equals(activePlayerUid) ? "Vaš potez" : "Protivnik igra");
        } else if (PHASE_OPPONENT_CHANCE.equals(phase)) {
            setStatusText(uid.equals(opponentUid) ? "Vaša šansa za 10 poena" : "Protivnik pokušava za 10 poena");
        } else if (PHASE_FINISHED.equals(phase)) {
            setStatusText(roundNumber == 1 ? "Priprema druge runde" : "Skočko je završen");
        }
    }

    private String currentPlayerText() {
        if (PHASE_OPPONENT_CHANCE.equals(phase)) {
            return uid.equals(opponentUid) ? "Na potezu: vi" : "Na potezu: protivnik";
        }
        return uid.equals(activePlayerUid) ? "Na potezu: vi" : "Na potezu: protivnik";
    }

    private void updateScoreViews() {
        tvScoreP1.setText("Igrač 1: " + player1Score + " poena");
        tvScoreP2.setText("Igrač 2: " + player2Score + " poena");
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady() || gameId.isEmpty()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordSkockoGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record skocko stats failed", e));
    }

    private List<Object> attemptsFor(DocumentSnapshot round, String playerUid) {
        Map<String, Object> attemptsByPlayer = (Map<String, Object>) round.get("attemptsByPlayer");
        Object raw = attemptsByPlayer == null ? null : attemptsByPlayer.get(playerUid);
        return raw instanceof List ? new ArrayList<>((List<Object>) raw) : new ArrayList<>();
    }

    private List<Object> feedbackFor(DocumentSnapshot round, String playerUid) {
        Map<String, Object> feedbackByPlayer = (Map<String, Object>) round.get("feedbackByPlayer");
        Object raw = feedbackByPlayer == null ? null : feedbackByPlayer.get(playerUid);
        return raw instanceof List ? new ArrayList<>((List<Object>) raw) : new ArrayList<>();
    }

    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map ? (Map<String, Object>) raw : new java.util.HashMap<>();
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

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void setStatusText(String status) {
        tvStatus.setText(status);
        Log.d(TAG, "Status text=" + status + ", currentUserUid=" + uid + ", gameId=" + gameId
                + ", roundId=" + (gameId.isEmpty() ? "" : gameRepository.skockoRoundId(roundNumber))
                + ", phase=" + phase + ", activePlayerUid=" + activePlayerUid
                + ", opponentUid=" + opponentUid);
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        if (gameListener != null) {
            gameListener.remove();
        }
        if (roundListener != null) {
            roundListener.remove();
        }
        super.onDestroy();
    }
}
