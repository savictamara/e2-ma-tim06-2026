package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.ChallengeRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.model.ChallengeParticipant;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;

public class FinalResultActivity extends AppCompatActivity {
    private static final String CHALLENGE_TAG = "FinalResultChallengeDebug";
    private static final String COMPLETION_TAG = "ChallengeCompletionDebug";
    private GameRepository gameRepository;
    private ChallengeRepository challengeRepository;
    private UserRepository userRepository;
    private TextView tvPlayers;
    private TextView tvScores;
    private TextView tvWinner;
    private TextView tvRewards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_final_result);

        tvPlayers = findViewById(R.id.tvFinalPlayers);
        tvScores = findViewById(R.id.tvFinalScores);
        tvWinner = findViewById(R.id.tvFinalWinner);
        tvRewards = findViewById(R.id.tvFinalRewards);
        Button btnMainMenu = findViewById(R.id.btnMainMenu);

        btnMainMenu.setOnClickListener(v -> returnToMainMenu());

        if (!FirebaseInitializer.ensure(this)) {
            show(getString(R.string.firebase_not_ready));
            return;
        }
        gameRepository = new GameRepository(this);
        challengeRepository = new ChallengeRepository(this);
        userRepository = new UserRepository(this);
        String gameId = GameFlow.existingGameId(getIntent());
        if (gameId.isEmpty() || !gameRepository.isReady() || !userRepository.isReady()) {
            show(getString(R.string.firebase_not_ready));
            return;
        }
        loadResult(gameId);
    }

    private void loadResult(String gameId) {
        gameRepository.getGame(gameId)
                .addOnSuccessListener(game -> {
                    String p1 = value(game.getString("player1Uid"));
                    String p2 = value(game.getString("player2Uid"));
                    Tasks.whenAllSuccess(userRepository.getUser(p1), userRepository.getUser(p2))
                            .addOnSuccessListener(users -> {
                                DocumentSnapshot u1 = (DocumentSnapshot) users.get(0);
                                DocumentSnapshot u2 = (DocumentSnapshot) users.get(1);
                                bindResult(game, displayName(u1, p1), displayName(u2, p2));
                            })
                            .addOnFailureListener(e -> bindResult(game, p1, p2));
                })
                .addOnFailureListener(e -> show(e.getMessage()));
    }

    private void bindResult(DocumentSnapshot game, String player1Name, String player2Name) {
        if (Boolean.TRUE.equals(game.getBoolean("challengeRun"))) {
            boolean challengeCompleted = getIntent().getBooleanExtra("challengeCompleted", false);
            Log.d(CHALLENGE_TAG, "challengeCompleted extra=" + challengeCompleted
                    + ", challenge status game=" + game.getString("status")
                    + ", currentMiniGame=" + game.getString("currentMiniGame"));
            if (!challengeCompleted) {
                tvPlayers.setText("Izazov");
                tvScores.setText("Challenge rezultat nije poslat jer finalna igra nije potvrdjena.");
                tvWinner.setText("");
                tvRewards.setText("Vratite se na Regions ekran i nastavite izazov.");
                return;
            }
            submitChallengeResult(game);
            return;
        }
        int player1Score = intValue(game.get("player1Score"));
        int player2Score = intValue(game.get("player2Score"));
        String player1Uid = value(game.getString("player1Uid"));
        String winnerUid = value(game.getString("winnerUid"));
        String winnerName = winnerUid.equals(player1Uid) ? player1Name : player2Name;
        long p1Stars = longValue(game.get("player1StarsDelta"));
        long p2Stars = longValue(game.get("player2StarsDelta"));
        long p1Tokens = longValue(game.get("player1TokensAwarded"));
        long p2Tokens = longValue(game.get("player2TokensAwarded"));
        boolean friendly = Boolean.TRUE.equals(game.getBoolean("friendly"))
                || "FRIENDLY".equals(game.getString("matchType"));

        tvPlayers.setText((friendly ? "Prijateljska partija\n" : "")
                + "Igrac 1: " + player1Name + "\nIgrac 2: " + player2Name);
        tvScores.setText("Rezultat\n" + player1Name + ": " + player1Score
                + "\n" + player2Name + ": " + player2Score);
        tvWinner.setText("Pobednik: " + winnerName);
        if (friendly) {
            tvRewards.setText("Prijateljska partija ne dodeljuje zvezde, tokene, statistiku ni rang poene.");
        } else {
            tvRewards.setText("Zvezde i tokeni\n"
                    + player1Name + ": " + signed(p1Stars) + " zvezda, +" + p1Tokens + " tokena\n"
                    + player2Name + ": " + signed(p2Stars) + " zvezda, +" + p2Tokens + " tokena");
        }
    }

    private void submitChallengeResult(DocumentSnapshot game) {
        String challengeId = value(game.getString("challengeId"));
        String challengePlayerUid = value(game.getString("challengePlayerUid"));
        if (challengeId.isEmpty() || challengePlayerUid.isEmpty() || !challengeRepository.isReady()) {
            show("Rezultat izazova nije moguce sacuvati.");
            return;
        }
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Object> rawScores = mapValue(game.get("challengeScores"));
        for (String miniGame : GameRepository.CHALLENGE_MATCH_ORDER) {
            scores.put(miniGame, intValue(rawScores.get(miniGame)));
        }
        tvPlayers.setText("Izazov");
        tvScores.setText("Ukupno poena: " + sum(scores));
        tvWinner.setText("Cuvanje rezultata izazova...");
        tvRewards.setText("");
        challengeRepository.submitRun(challengeId, challengePlayerUid, scores)
                .addOnSuccessListener(unused -> {
                    Log.d(COMPLETION_TAG, "participant completed written"
                            + ", regionChallengeId=" + challengeId
                            + ", currentUserUid=" + challengePlayerUid
                            + ", totalScore=" + sum(scores)
                            + ", results screen opened=true");
                    showChallengeResults(challengeId);
                })
                .addOnFailureListener(e -> {
                    show(e == null || e.getMessage() == null ? "Rezultat izazova nije sacuvan" : e.getMessage());
                    showChallengeResults(challengeId);
                });
    }

    private void showChallengeResults(String challengeId) {
        challengeRepository.getChallenge(challengeId)
                .addOnSuccessListener(challenge -> challengeRepository.getParticipants(challengeId)
                        .addOnSuccessListener(parts -> bindChallengeResults(challenge, parts))
                        .addOnFailureListener(e -> {
                            Log.e(CHALLENGE_TAG, "participants load failed", e);
                            openRegions();
                        }))
                .addOnFailureListener(e -> {
                    Log.e(CHALLENGE_TAG, "challenge load failed", e);
                    openRegions();
                });
    }

    private void bindChallengeResults(DocumentSnapshot challenge, QuerySnapshot snapshot) {
        List<ChallengeParticipant> participants = new ArrayList<>();
        int completedCount = 0;
        if (snapshot != null) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ChallengeParticipant participant = challengeRepository.participantFrom(doc);
                participants.add(participant);
                if (participant.finished) completedCount++;
            }
        }
        participants.sort((a, b) -> {
            if (a.placement == 0 && b.placement == 0) return Long.compare(b.totalScore, a.totalScore);
            if (a.placement == 0) return 1;
            if (b.placement == 0) return -1;
            return Long.compare(a.placement, b.placement);
        });
        String status = challenge == null ? "" : value(challenge.getString("status"));
        Log.d(CHALLENGE_TAG, "challenge status=" + status
                + ", participants loaded=" + participants.size()
                + ", completedCount=" + completedCount
                + ", waiting vs finished UI=" + ChallengeRepository.FINISHED.equals(status));
        tvPlayers.setText("Izazov");
        tvScores.setText("Ucesnici: " + completedCount + "/" + participants.size());
        if (!ChallengeRepository.FINISHED.equals(status)) {
            tvWinner.setText("Zavrsili ste izazov. Ceka se rezultat ostalih igraca.");
            tvRewards.setText(challengeRows(participants, false));
            Log.d(COMPLETION_TAG, "waiting state opened=true"
                    + ", joinedCount=" + participants.size()
                    + ", completedCount=" + completedCount
                    + ", finish condition=false");
            return;
        }
        tvWinner.setText("Rezultati izazova");
        tvRewards.setText(challengeRows(participants, true));
        Log.d(COMPLETION_TAG, "finish condition=true"
                + ", joinedCount=" + participants.size()
                + ", completedCount=" + completedCount);
    }

    private String challengeRows(List<ChallengeParticipant> participants, boolean finished) {
        StringBuilder builder = new StringBuilder();
        for (ChallengeParticipant participant : participants) {
            String place = participant.placement == 0 ? "-" : String.valueOf(participant.placement);
            builder.append(place).append(". ")
                    .append(value(participant.username, participant.uid))
                    .append(" - ").append(participant.totalScore).append(" poena");
            if (finished) {
                if (participant.placement == 2) {
                    builder.append(" - Vracen ulog");
                } else if (participant.rewardStars > 0 || participant.rewardTokens > 0) {
                    builder.append(" - Nagrada: ")
                            .append(participant.rewardStars).append(" zvezda, ")
                            .append(participant.rewardTokens).append(" tokena");
                } else {
                    builder.append(" - Bez nagrade");
                }
            } else if (participant.finished) {
                builder.append(" - zavrsio");
            } else {
                builder.append(" - igra se ceka");
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private void openRegions() {
        Intent intent = new Intent(this, RegionsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String displayName(DocumentSnapshot user, String fallback) {
        if (user != null && user.exists()) {
            String username = user.getString("username");
            if (username != null && !username.trim().isEmpty()) {
                return username;
            }
            String email = user.getString("email");
            if (email != null && !email.trim().isEmpty()) {
                return email;
            }
        }
        return fallback == null || fallback.isEmpty() ? "Igrac" : fallback;
    }

    private String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private int sum(Map<String, Integer> scores) {
        int total = 0;
        for (Integer score : scores.values()) {
            total += score == null ? 0 : score;
        }
        return total;
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void returnToMainMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }
}
