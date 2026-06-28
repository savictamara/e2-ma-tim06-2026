package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.util.GuestSession;
import rs.ac.uns.ftn.slagalica.util.GameFlow;

public class ChallengeRunActivity extends AppCompatActivity {
    public static final String EXTRA_CHALLENGE_ID = "challengeId";

    private GameRepository gameRepository;
    private FirebaseAuthRepository authRepository;
    private String challengeId = "";
    private String uid = "";
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_run);

        gameRepository = new GameRepository(this);
        authRepository = new FirebaseAuthRepository(this);
        challengeId = getIntent().getStringExtra(EXTRA_CHALLENGE_ID);
        FirebaseUser user = authRepository.currentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();

        tvStatus = findViewById(R.id.tvChallengeRunStatus);
        Button submit = findViewById(R.id.btnSubmitChallengeRun);
        submit.setOnClickListener(v -> startChallengeGames());

        if (TextUtils.isEmpty(challengeId)) {
            tvStatus.setText("Nedostaje ID izazova.");
            submit.setEnabled(false);
        }
    }

    private void startChallengeGames() {
        if (!gameRepository.isReady()) {
            Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        tvStatus.setText("Pokretanje mini-igara...");
        gameRepository.createChallengeRunGame(uid, challengeId)
                .addOnSuccessListener(gameId -> {
                    Intent intent = new Intent(this, GameFlow.activityClassFor(GameRepository.CHALLENGE_MATCH_ORDER[0]));
                    intent.putExtra(GameFlow.EXTRA_GAME_ID, gameId);
                    intent.putExtra(GameFlow.EXTRA_FULL_MATCH, true);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null ? "Izazov nije pokrenut" : e.getMessage();
                    tvStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }
}
