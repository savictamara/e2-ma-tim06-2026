package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
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
import rs.ac.uns.ftn.slagalica.util.GameHeaderHelper;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class KoZnaZnaActivity extends AppCompatActivity {
    private static final String TAG = "KoZnaZnaActivity";
    private static final int QUESTION_COUNT = 5;
    private static final long QUESTION_DURATION_MS = 5000;

    private final Button[] answerButtons = new Button[4];
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private GameRepository gameRepository;
    private StatsRepository statsRepository;
    private ListenerRegistration gameListener;
    private ListenerRegistration roundListener;
    private CountDownTimer questionTimer;
    private String uid;
    private String gameId;
    private String player1Uid;
    private String player2Uid;
    private String phase = "";
    private int currentQuestionIndex = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean answeredCurrentQuestion = false;
    private boolean statsRecordRequested = false;

    private TextView tvTimer;
    private TextView tvQuestionIndex;
    private TextView tvPoints;
    private TextView tvQuestion;
    private TextView tvRoundResult;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;
    private Button btnNextQuestion;
    private GameHeaderHelper headerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from KoZnaZnaActivity=" + firebaseReady);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        gameRepository = new GameRepository(this);
        statsRepository = new StatsRepository(this);

        tvTimer = findViewById(R.id.tvTimer);
        tvQuestionIndex = findViewById(R.id.tvQuestionIndex);
        tvPoints = findViewById(R.id.tvPoints);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvRoundResult = findViewById(R.id.tvRoundResult);
        tvPlayer1Score = findViewById(R.id.tvKznzPlayer1);
        tvPlayer2Score = findViewById(R.id.tvKznzPlayer2);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        btnNextQuestion.setVisibility(View.GONE);
        headerHelper = new GameHeaderHelper(this, findViewById(android.R.id.content));
        headerHelper.updateGameTitle("Ko zna zna");

        answerButtons[0] = findViewById(R.id.btnAnswer1);
        answerButtons[1] = findViewById(R.id.btnAnswer2);
        answerButtons[2] = findViewById(R.id.btnAnswer3);
        answerButtons[3] = findViewById(R.id.btnAnswer4);
        for (int i = 0; i < answerButtons.length; i++) {
            final int answerIndex = i;
            answerButtons[i].setOnClickListener(v -> submitAnswer(answerIndex));
        }

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            setAnswerButtonsEnabled(false);
            return;
        }
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        Log.d(TAG, "Current uid=" + uid);
        gameRepository.joinOrCreateGame(uid, GameRepository.MINI_KNOW_IT)
                .addOnSuccessListener(id -> {
                    gameId = id;
                    Log.d(TAG, "Know it gameId=" + gameId);
                    userRepository.updateUserState(uid, true, true, gameId);
                    listenGame();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Know it matchmaking failed", e);
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
            player1Uid = value(snapshot.getString("player1Uid"));
            player2Uid = value(snapshot.getString("player2Uid"));
            Long p1 = snapshot.getLong("player1Score");
            Long p2 = snapshot.getLong("player2Score");
            player1Score = p1 == null ? 0 : p1.intValue();
            player2Score = p2 == null ? 0 : p2.intValue();
            tvPlayer1Score.setText(getString(R.string.player_points, player1Score));
            tvPlayer2Score.setText(getString(R.string.player_points, player2Score));
            tvPoints.setText(getString(R.string.points_text, uid.equals(player1Uid) ? player1Score : player2Score));
            headerHelper.updatePlayers(player1Uid, player1Score, player2Uid, player2Score);
            Log.d(TAG, "Game snapshot currentUserUid=" + uid
                    + ", gameId=" + snapshot.getId() + ", status=" + status
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            if (!GameRepository.MINI_KNOW_IT.equals(snapshot.getString("currentMiniGame"))) {
                setStatus("Ko zna zna nije aktivna igra");
                setAnswerButtonsEnabled(false);
                Log.d(TAG, "Ignoring game snapshot for different mini game, gameId=" + gameId
                        + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
                return;
            }
            if ("waiting".equals(status) || player2Uid.isEmpty()) {
                setStatus("Čeka se drugi igrač");
                setAnswerButtonsEnabled(false);
                return;
            }
            Log.d(TAG, "Game became active, currentUserUid=" + uid + ", gameId=" + gameId
                    + ", player1Uid=" + player1Uid + ", player2Uid=" + player2Uid
                    + ", currentMiniGame=" + snapshot.getString("currentMiniGame"));
            if ("finished".equals(status) && GameRepository.PHASE_FINISHED.equals(phase)) {
                setStatus("Ko zna zna je završeno");
                setAnswerButtonsEnabled(false);
                userRepository.updateUserState(uid, true, false, "");
                return;
            }
            gameRepository.ensureKnowItRound(gameId)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ensure know it round failed", e);
                        show(e.getMessage());
                    });
            listenRound();
        });
    }

    private void listenRound() {
        if (roundListener != null) {
            return;
        }
        roundListener = gameRepository.listenRound(gameId, gameRepository.knowItRoundId(), (snapshot, error) -> {
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
                    + ", currentQuestionIndex=" + snapshot.getLong("currentQuestionIndex")
                    + ", questionStartedAt=" + snapshot.getTimestamp("questionStartedAt"));
            bindRound(snapshot);
        });
    }

    private void bindRound(DocumentSnapshot round) {
        phase = value(round.getString("phase"));
        Long indexLong = round.getLong("currentQuestionIndex");
        currentQuestionIndex = indexLong == null ? 0 : indexLong.intValue();
        List<Map<String, Object>> questions = readQuestions(round);
        Map<String, Object> question = currentQuestionIndex < questions.size() ? questions.get(currentQuestionIndex) : null;
        if (GameRepository.PHASE_FINISHED.equals(phase) || Boolean.TRUE.equals(round.getBoolean("finished"))) {
            showFinished();
            return;
        }
        if (question == null) {
            setStatus("Nema pitanja za prikaz");
            setAnswerButtonsEnabled(false);
            return;
        }
        tvQuestionIndex.setText(getString(R.string.kznz_question_counter, currentQuestionIndex + 1, QUESTION_COUNT));
        tvQuestion.setText(value((String) question.get("questionText")));
        List<String> options = (List<String>) question.get("options");
        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(options != null && i < options.size() ? options.get(i) : "");
            answerButtons[i].setBackgroundResource(R.drawable.bg_step);
        }
        Map<String, Object> answersByQuestion = (Map<String, Object>) round.get("answersByQuestion");
        Map<String, Object> currentAnswers = nestedMap(answersByQuestion, String.valueOf(currentQuestionIndex));
        answeredCurrentQuestion = currentAnswers.containsKey(uid);
        String otherPlayerUid = uid.equals(player1Uid) ? player2Uid : player1Uid;
        boolean otherPlayerAnswered = otherPlayerUid != null && currentAnswers.containsKey(otherPlayerUid);
        if (answeredCurrentQuestion && otherPlayerAnswered) {
            setStatus("Oba igrača su odgovorila", answeredCurrentQuestion, otherPlayerAnswered);
        } else if (answeredCurrentQuestion) {
            setStatus("Odgovor je poslat", true, otherPlayerAnswered);
        } else {
            setStatus("Odgovorite na pitanje", false, otherPlayerAnswered);
        }
        setAnswerButtonsEnabled(!answeredCurrentQuestion && GameRepository.PHASE_PLAYING.equals(phase));
        startQuestionTimer(round);
    }

    private void submitAnswer(int selectedAnswerIndex) {
        if (!GameRepository.PHASE_PLAYING.equals(phase) || answeredCurrentQuestion) {
            return;
        }
        long answerTime = System.currentTimeMillis();
        Log.d(TAG, "Submit answer uid=" + uid + ", gameId=" + gameId
                + ", questionIndex=" + currentQuestionIndex
                + ", selectedAnswerIndex=" + selectedAnswerIndex
                + ", answerTime=" + answerTime);
        answeredCurrentQuestion = true;
        setAnswerButtonsEnabled(false);
        setStatus("Odgovor je poslat", true, false);
        gameRepository.submitKnowItAnswer(gameId, uid, selectedAnswerIndex, answerTime)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Submit know it answer failed", e);
                    answeredCurrentQuestion = false;
                    setAnswerButtonsEnabled(true);
                    show(e.getMessage());
                });
    }

    private void startQuestionTimer(DocumentSnapshot round) {
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        Timestamp startedAt = round.getTimestamp("questionStartedAt");
        long elapsedMs = startedAt == null ? 0 : Math.max(0, System.currentTimeMillis() - startedAt.toDate().getTime());
        long remainingMs = Math.max(0, QUESTION_DURATION_MS - elapsedMs);
        Log.d(TAG, "Question timer questionIndex=" + currentQuestionIndex
                + ", questionStartedAt=" + startedAt + ", remainingMs=" + remainingMs);
        if (remainingMs == 0) {
            tvTimer.setText(getString(R.string.timer_text, 0));
            advanceQuestionByTimeout();
            return;
        }
        questionTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, Math.max(1, millisUntilFinished / 1000)));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                setAnswerButtonsEnabled(false);
                advanceQuestionByTimeout();
            }
        }.start();
    }

    private void advanceQuestionByTimeout() {
        Log.d(TAG, "Timeout advance gameId=" + gameId + ", questionIndex=" + currentQuestionIndex);
        setStatus("Sledeće pitanje", answeredCurrentQuestion, false);
        gameRepository.advanceKnowItQuestion(gameId, currentQuestionIndex)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Advance know it question failed", e);
                    show(e.getMessage());
                });
    }

    private boolean isValidKnowItGame(DocumentSnapshot game) {
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
        boolean valid = GameRepository.MINI_KNOW_IT.equals(miniGame)
                && containsUser
                && (waitingAsPlayer1 || activeWithBothPlayers);
        Log.d(TAG, "Validate currentGameId=" + game.getId() + ", valid=" + valid
                + ", status=" + status + ", player1=" + p1 + ", player2=" + p2 + ", miniGame=" + miniGame);
        return valid;
    }

    private void showFinished() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
        tvQuestionIndex.setText(getString(R.string.kznz_question_counter, QUESTION_COUNT, QUESTION_COUNT));
        tvTimer.setText(getString(R.string.timer_text, 0));
        tvQuestion.setText("Finalni rezultat: " + player1Score + " : " + player2Score);
        setStatus("Ko zna zna je završeno");
        setAnswerButtonsEnabled(false);
        for (Button answerButton : answerButtons) {
            answerButton.setBackgroundResource(R.drawable.bg_step);
        }
        recordStatsOnce();
    }

    private void recordStatsOnce() {
        if (statsRecordRequested || statsRepository == null || !statsRepository.isReady()) {
            return;
        }
        statsRecordRequested = true;
        statsRepository.recordKnowItGame(gameId)
                .addOnFailureListener(e -> Log.e(TAG, "Record know it stats failed", e));
    }

    private List<Map<String, Object>> readQuestions(DocumentSnapshot round) {
        List<Map<String, Object>> questions = new ArrayList<>();
        List<Object> raw = (List<Object>) round.get("questions");
        if (raw == null) {
            return questions;
        }
        for (Object item : raw) {
            if (item instanceof Map) {
                questions.add((Map<String, Object>) item);
            }
        }
        return questions;
    }

    private Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        if (root == null) {
            return new java.util.HashMap<>();
        }
        Object value = root.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new java.util.HashMap<>();
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        for (Button answerButton : answerButtons) {
            answerButton.setEnabled(enabled);
        }
        Log.d(TAG, "Answer buttons enabled=" + enabled + ", gameId=" + gameId
                + ", roundId=" + gameRepository.knowItRoundId()
                + ", currentUserUid=" + uid + ", phase=" + phase
                + ", currentQuestionIndex=" + currentQuestionIndex
                + ", answeredCurrentQuestion=" + answeredCurrentQuestion);
    }

    private void setStatus(String status) {
        setStatus(status, answeredCurrentQuestion, false);
    }

    private void setStatus(String status, boolean hasCurrentUserAnswered, boolean hasOtherPlayerAnswered) {
        tvRoundResult.setText(status);
        headerHelper.updateStatus(status);
        Log.d(TAG, "Status text=" + status + ", gameId=" + gameId
                + ", currentQuestionIndex=" + currentQuestionIndex
                + ", currentUserUid=" + uid
                + ", hasCurrentUserAnswered=" + hasCurrentUserAnswered
                + ", hasOtherPlayerAnswered=" + hasOtherPlayerAnswered
                + ", phase=" + phase);
    }

    @Override
    protected void onDestroy() {
        if (questionTimer != null) {
            questionTimer.cancel();
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
