package rs.ac.uns.ftn.slagalica;

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
import java.util.Locale;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameHeaderHelper;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class AsocijacijeActivity extends AppCompatActivity {
    private static final String TAG = "AsocijacijeActivity";
    private static final long ROUND_DURATION_MS = 120_000;

    private final int[] cellIds = {
            R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4,
            R.id.cell5, R.id.cell6, R.id.cell7, R.id.cell8,
            R.id.cell9, R.id.cell10, R.id.cell11, R.id.cell12,
            R.id.cell13, R.id.cell14, R.id.cell15, R.id.cell16
    };

    private final TextView[] cells = new TextView[16];
    private EditText[] columnInputs;
    private Button[] columnGuessButtons;
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private CountDownTimer timer;
    private String uid;
    private String gameId;
    private String player1Uid = "";
    private String player2Uid = "";
    private String currentTurnUid = "";
    private String phase = "";
    private int roundNumber = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean statsRecordRequested = false;
    private boolean mustGuessAfterOpen = false;
    private boolean gameReady = false;
    private String canContinueGuessingUid = "";

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScoreP1;
    private TextView tvScoreP2;
    private TextView tvStatus;
    private EditText etFinal;
    private Button btnEndTurn;
    private Button btnGuessFinal;
    private Button btnNextRound;
    private GameHeaderHelper headerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from AsocijacijeActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);
        bindViews();

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            setControls(false);
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "Current association uid=" + uid);
        Log.d(TAG, "onCreate join called uid=" + uid + ", miniGameType=" + GameRepository.MINI_ASSOCIATIONS);
        gameRepository.joinOrCreateGame(uid, GameRepository.MINI_ASSOCIATIONS)
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "received gameId=" + gameId);
                    Log.d(TAG, "Associations gameId=" + gameId + ", uid=" + uid);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Associations matchmaking failed", e);
                    show(e.getMessage());
                });
    }

    private void bindViews() {
        tvRound = findViewById(R.id.tvRound);
        tvCurrentPlayer = findViewById(R.id.tvCurrentPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScoreP1 = findViewById(R.id.tvScoreP1);
        tvScoreP2 = findViewById(R.id.tvScoreP2);
        tvStatus = findViewById(R.id.tvStatus);
        etFinal = findViewById(R.id.etFinal);
        btnEndTurn = findViewById(R.id.btnOpenField);
        btnGuessFinal = findViewById(R.id.btnGuessFinal);
        btnNextRound = findViewById(R.id.btnNextRound);
        headerHelper = new GameHeaderHelper(this, findViewById(android.R.id.content));
        headerHelper.updateGameTitle("Asocijacije");
        columnInputs = new EditText[] {
                findViewById(R.id.etAnswerA),
                findViewById(R.id.etAnswerB),
                findViewById(R.id.etAnswerC),
                findViewById(R.id.etAnswerD)
        };
        columnGuessButtons = new Button[] {
                findViewById(R.id.btnGuessA),
                findViewById(R.id.btnGuessB),
                findViewById(R.id.btnGuessC),
                findViewById(R.id.btnGuessD)
        };
        for (int i = 0; i < cells.length; i++) {
            final int columnIndex = i / 4;
            final int fieldIndex = i % 4;
            cells[i] = findViewById(cellIds[i]);
            cells[i].setTextColor(getColor(R.color.game_text_color));
            cells[i].setOnClickListener(v -> openField(columnIndex, fieldIndex));
        }
        for (int i = 0; i < columnGuessButtons.length; i++) {
            final int columnIndex = i;
            columnInputs[i].setTextColor(getColor(R.color.game_text_color));
            columnInputs[i].setHintTextColor(getColor(R.color.input_hint_color));
            columnGuessButtons[i].setOnClickListener(v -> guessColumn(columnIndex));
        }
        etFinal.setTextColor(getColor(R.color.game_text_color));
        etFinal.setHintTextColor(getColor(R.color.input_hint_color));
        btnEndTurn.setText("Završi potez");
        btnEndTurn.setText("Ne znam / Zavrsi potez");
        btnEndTurn.setOnClickListener(v -> endTurn());
        btnGuessFinal.setOnClickListener(v -> guessFinal());
        btnNextRound.setOnClickListener(v -> moveToSecondRound());
        btnNextRound.setEnabled(false);
    }

    private void listenGame() {
        gameListener = gameRepository.listenGame(gameId, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Game listener failed", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            String status = snapshot.getString("status");
            player1Uid = value(snapshot.getString("player1Uid"));
            player2Uid = value(snapshot.getString("player2Uid"));
            player1Score = intValue(snapshot.get("player1Score"));
            player2Score = intValue(snapshot.get("player2Score"));
            updateScore();
            headerHelper.updatePlayers(player1Uid, player1Score, player2Uid, player2Score);
            Log.d(TAG, "Game snapshot currentUserUid=" + uid
                    + ", gameId=" + gameId + ", status=" + status
                    + ", p1=" + player1Uid + ", p2=" + player2Uid + ", round=" + roundNumber);
            Log.d(TAG, "Activity game snapshot: status=" + status
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid);
            if (!GameRepository.MINI_ASSOCIATIONS.equals(snapshot.getString("currentMiniGame"))) {
                setStatus("Asocijacije nisu aktivna igra");
                setControls(false);
                Log.d(TAG, "Ignoring game snapshot for different mini game, gameId=" + gameId
                        + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
                return;
            }
            if ("waiting".equals(status) || player2Uid.isEmpty()) {
                setStatus("Čeka se drugi igrač");
                setControls(false);
                return;
            }
            gameReady = GameRepository.isGameReady(snapshot);
            Log.d(TAG, "isGameReady " + gameReady);
            if (!gameReady) {
                setStatus("");
                setControls(false);
                return;
            }
            Log.d(TAG, "Game became active, currentUserUid=" + uid + ", gameId=" + gameId
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            if (phase.isEmpty()) {
                setStatus("");
            }
            Log.d(TAG, "round creation attempted");
            gameRepository.ensureAssociationsRound(gameId, roundNumber)
                    .addOnFailureListener(e -> Log.e(TAG, "Ensure associations round failed", e));
            listenRound();
        });
    }

    private void listenRound() {
        if (roundListener != null) {
            return;
        }
        String roundId = gameRepository.associationsRoundId(roundNumber);
        roundListener = gameRepository.listenRound(gameId, roundId, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Round listener failed", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            bindRound(snapshot);
        });
        Log.d(TAG, "round listener attached gameId=" + gameId + ", roundId=" + roundId);
    }

    private void bindRound(DocumentSnapshot round) {
        Long roundIndex = round.getLong("roundIndex");
        roundNumber = roundIndex == null ? roundNumber : roundIndex.intValue();
        phase = value(round.getString("phase"));
        currentTurnUid = value(round.getString("currentTurnUid"));
        mustGuessAfterOpen = Boolean.TRUE.equals(round.getBoolean("mustGuessAfterOpen"));
        canContinueGuessingUid = value(round.getString("canContinueGuessingUid"));
        Log.d(TAG, "Round snapshot roundId=" + round.getId() + ", phase=" + phase
                + ", currentTurnUid=" + currentTurnUid + ", uid=" + uid
                + ", mustGuessAfterOpen=" + mustGuessAfterOpen
                + ", canContinueGuessingUid=" + canContinueGuessingUid);

        tvRound.setText("Runda " + roundNumber + " / 2");
        tvCurrentPlayer.setText(uid.equals(currentTurnUid) ? "Na potezu: vi" : "Na potezu: protivnik");
        renderBoard(round);
        updateScore();

        boolean finished = Boolean.TRUE.equals(round.getBoolean("finished")) || GameRepository.PHASE_FINISHED.equals(phase);
        if (finished) {
            stopTimer();
            setControls(false);
            tvTimer.setText(getString(R.string.timer_text, 0));
            if (roundNumber == 1) {
                setStatus("Priprema druge runde");
                moveToSecondRound();
            } else {
                setStatus("Asocijacije su završene");
                recordStatsOnce();
            }
            return;
        }
        updateTurnStatus();
        setControls(uid.equals(currentTurnUid));
        startTimer(round);
    }

    private void renderBoard(DocumentSnapshot round) {
        List<Object> columns = (List<Object>) round.get("columns");
        Map<String, Object> opened = (Map<String, Object>) round.get("openedFields");
        Map<String, Object> solved = (Map<String, Object>) round.get("solvedColumns");
        String finalSolvedByUid = round.getString("finalSolvedByUid");
        boolean canOpenField = uid.equals(currentTurnUid) && !mustGuessAfterOpen;
        for (int col = 0; col < 4; col++) {
            Map<String, Object> column = columnAt(columns, col);
            List<String> clues = stringList(column.get("clues"));
            String answer = value((String) column.get("columnAnswer"));
            boolean solvedColumn = solved != null && solved.containsKey(String.valueOf(col));
            if (solvedColumn) {
                columnInputs[col].setText(answer);
            }
            for (int field = 0; field < 4; field++) {
                TextView cell = cells[col * 4 + field];
                boolean isOpen = solvedColumn || (opened != null && Boolean.TRUE.equals(opened.get(col + "_" + field)));
                String label = String.format(Locale.ROOT, "%c%d", 'A' + col, field + 1);
                cell.setText(isOpen && field < clues.size() ? label + ": " + clues.get(field) : label);
                cell.setEnabled(canOpenField && !solvedColumn && !isOpen);
                cell.setBackgroundResource(solvedColumn ? R.drawable.bg_answer_correct : R.drawable.bg_step);
            }
        }
        if (finalSolvedByUid != null && !finalSolvedByUid.isEmpty()) {
            etFinal.setText(value(round.getString("finalAnswer")));
        }
    }

    private void openField(int columnIndex, int fieldIndex) {
        if (!uid.equals(currentTurnUid)) {
            show("Protivnik igra");
            return;
        }
        if (mustGuessAfterOpen) {
            show("Prvo pogodite ili zavrsite potez");
            return;
        }
        Log.d(TAG, "Open field request gameId=" + gameId + ", round=" + roundNumber
                + ", col=" + columnIndex + ", field=" + fieldIndex);
        gameRepository.openAssociationField(gameId, roundNumber, uid, columnIndex, fieldIndex)
                .addOnSuccessListener(opened -> {
                    if (!Boolean.TRUE.equals(opened)) {
                        show("Polje nije moguće otvoriti");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Open field failed", e);
                    show(e.getMessage());
                });
    }

    private void guessColumn(int columnIndex) {
        if (!uid.equals(currentTurnUid)) {
            show("Protivnik igra");
            return;
        }
        String answer = columnInputs[columnIndex].getText().toString();
        Log.d(TAG, "Guess column request gameId=" + gameId + ", round=" + roundNumber
                + ", column=" + columnIndex + ", answer=" + answer);
        gameRepository.guessAssociationColumn(gameId, roundNumber, uid, columnIndex, answer)
                .addOnSuccessListener(correct -> setStatus(Boolean.TRUE.equals(correct)
                        ? "Tačno! Možete nastaviti."
                        : "Netačno. Protivnik je na potezu."))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Guess column failed", e);
                    show(e.getMessage());
                });
    }

    private void guessFinal() {
        if (!uid.equals(currentTurnUid)) {
            show("Protivnik igra");
            return;
        }
        String answer = etFinal.getText().toString();
        Log.d(TAG, "Guess final request gameId=" + gameId + ", round=" + roundNumber + ", answer=" + answer);
        gameRepository.guessAssociationFinal(gameId, roundNumber, uid, answer)
                .addOnSuccessListener(correct -> setStatus(Boolean.TRUE.equals(correct)
                        ? "Tačno konačno rešenje."
                        : "Netačno. Protivnik je na potezu."))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Guess final failed", e);
                    show(e.getMessage());
                });
    }

    private void endTurn() {
        if (!uid.equals(currentTurnUid)) {
            show("Protivnik igra");
            return;
        }
        gameRepository.endAssociationTurn(gameId, roundNumber, uid)
                .addOnFailureListener(e -> Log.e(TAG, "End turn failed", e));
    }

    private void moveToSecondRound() {
        if (roundNumber != 1) {
            return;
        }
        roundNumber = 2;
        clearInputs();
        if (roundListener != null) {
            roundListener.remove();
            roundListener = null;
        }
        if (!gameReady) {
            Log.d(TAG, "Activity: game ready false");
            return;
        }
        Log.d(TAG, "round creation attempted");
        gameRepository.ensureAssociationsRound(gameId, roundNumber)
                .addOnFailureListener(e -> Log.e(TAG, "Ensure associations round 2 failed", e));
        listenRound();
    }

    private void startTimer(DocumentSnapshot round) {
        stopTimer();
        Timestamp startedAt = round.getTimestamp("phaseStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, ROUND_DURATION_MS - elapsedMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            finishRoundByTimeout();
            return;
        }
        timer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, Math.max(0, millisUntilFinished / 1000)));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                finishRoundByTimeout();
            }
        }.start();
    }

    private void finishRoundByTimeout() {
        Log.d(TAG, "Finish associations by timeout gameId=" + gameId + ", round=" + roundNumber);
        gameRepository.finishAssociationsRound(gameId, roundNumber)
                .addOnFailureListener(e -> Log.e(TAG, "Finish associations round failed", e));
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordAssociationsGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record associations stats failed", e));
    }

    private void setControls(boolean enabled) {
        for (EditText input : columnInputs) {
            input.setEnabled(enabled);
        }
        for (Button button : columnGuessButtons) {
            button.setEnabled(enabled);
        }
        btnEndTurn.setEnabled(enabled);
        btnGuessFinal.setEnabled(enabled);
        etFinal.setEnabled(enabled);
    }

    private void updateTurnStatus() {
        if (!GameRepository.isNonEmpty(currentTurnUid)) {
            Log.e(TAG, "Missing currentTurnUid in Asocijacije, currentUid=" + uid
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", phase=" + phase + ", round=" + roundNumber);
            setStatus("Priprema igre");
            gameRepository.repairRoundPlayers(gameId, gameRepository.associationsRoundId(roundNumber), roundNumber, true);
            return;
        }
        if (uid.equals(currentTurnUid) && mustGuessAfterOpen) {
            setStatus("Pogodite kolonu, konačno rešenje ili završite potez");
            return;
        }
        setStatus(uid.equals(currentTurnUid) ? "Vaš potez" : "Protivnik igra");
    }

    private void updateScore() {
        tvScoreP1.setText("Igrač 1: " + player1Score + " poena");
        tvScoreP2.setText("Igrač 2: " + player2Score + " poena");
    }

    private void clearInputs() {
        for (EditText input : columnInputs) {
            input.setText("");
        }
        etFinal.setText("");
    }

    private Map<String, Object> columnAt(List<Object> columns, int index) {
        if (columns == null || index < 0 || index >= columns.size() || !(columns.get(index) instanceof Map)) {
            return new java.util.HashMap<>();
        }
        return (Map<String, Object>) columns.get(index);
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

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void setStatus(String status) {
        tvStatus.setText(status);
        headerHelper.updateStatus(status);
        Log.d(TAG, "Status text=" + status + ", gameId=" + gameId + ", round=" + roundNumber
                + ", phase=" + phase + ", currentTurnUid=" + currentTurnUid + ", uid=" + uid);
        boolean isCurrentUsersTurn = uid != null && uid.equals(currentTurnUid);
        Log.d(TAG, "Turn status currentUid=" + uid
                + ", player1Uid=" + player1Uid
                + ", player2Uid=" + player2Uid
                + ", activePlayerUid="
                + ", opponentUid="
                + ", currentTurnUid=" + currentTurnUid
                + ", phase=" + phase
                + ", calculatedStatusText=" + status
                + ", isCurrentUsersTurn=" + isCurrentUsersTurn);
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
