package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;

import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;

public class FinalResultActivity extends AppCompatActivity {
    private GameRepository gameRepository;
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
        int player1Score = intValue(game.get("player1Score"));
        int player2Score = intValue(game.get("player2Score"));
        String player1Uid = value(game.getString("player1Uid"));
        String winnerUid = value(game.getString("winnerUid"));
        String winnerName = winnerUid.equals(player1Uid) ? player1Name : player2Name;
        long p1Stars = longValue(game.get("player1StarsDelta"));
        long p2Stars = longValue(game.get("player2StarsDelta"));
        long p1Tokens = longValue(game.get("player1TokensAwarded"));
        long p2Tokens = longValue(game.get("player2TokensAwarded"));

        tvPlayers.setText("Igrac 1: " + player1Name + "\nIgrac 2: " + player2Name);
        tvScores.setText("Rezultat\n" + player1Name + ": " + player1Score
                + "\n" + player2Name + ": " + player2Score);
        tvWinner.setText("Pobednik: " + winnerName);
        tvRewards.setText("Zvezde i tokeni\n"
                + player1Name + ": " + signed(p1Stars) + " zvezda, +" + p1Tokens + " tokena\n"
                + player2Name + ": " + signed(p2Stars) + " zvezda, +" + p2Tokens + " tokena");
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

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
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
