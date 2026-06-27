package rs.ac.uns.ftn.slagalica;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.LeaderboardRepository;

public class RewardActivity extends AppCompatActivity {
    public static final String EXTRA_CYCLE_TYPE = "cycleType";
    public static final String EXTRA_PLACEMENT = "placement";
    public static final String EXTRA_TOKENS = "tokens";

    private LeaderboardRepository repository;
    private FirebaseAuthRepository authRepository;
    private ToneGenerator toneGenerator;
    private TextView details;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reward);
        repository = new LeaderboardRepository(this);
        authRepository = new FirebaseAuthRepository(this);

        long placement = getIntent().getLongExtra(EXTRA_PLACEMENT, 0);
        long tokens = getIntent().getLongExtra(EXTRA_TOKENS, 0);
        String type = getIntent().getStringExtra(EXTRA_CYCLE_TYPE);
        if (type == null) type = LeaderboardRepository.WEEKLY;

        details = findViewById(R.id.tvRewardDetails);
        if (placement > 0 && tokens > 0) {
            showReward(type, placement, tokens);
        } else {
            loadPendingReward();
        }
        findViewById(R.id.btnRewardClose).setOnClickListener(v -> finish());
        playAnimation(findViewById(R.id.rewardPanel), findViewById(R.id.tvConfetti));
        playSound();
        clearPendingReward();
    }

    private void loadPendingReward() {
        FirebaseUser user = authRepository.currentUser();
        if (user == null || !repository.isReady()) {
            showReward(LeaderboardRepository.WEEKLY, 0, 0);
            return;
        }
        repository.getPendingReward(user.getUid())
                .addOnSuccessListener(this::showPendingReward)
                .addOnFailureListener(e -> showReward(LeaderboardRepository.WEEKLY, 0, 0));
    }

    private void showPendingReward(DocumentSnapshot doc) {
        showReward(doc.getString("pendingRewardCycleType"),
                longValue(doc.get("pendingRewardPlacement")),
                longValue(doc.get("pendingRewardTokens")));
    }

    private void showReward(String type, long placement, long tokens) {
        if (type == null) type = LeaderboardRepository.WEEKLY;
        details.setText("Plasman: " + placement + ". mesto\nCiklus: "
                + (LeaderboardRepository.WEEKLY.equals(type) ? "nedeljni" : "mesecni")
                + "\nNagrada: " + tokens + " tokena");
    }

    private void playAnimation(View panel, View confetti) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(panel, View.SCALE_X, 0.85f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(panel, View.SCALE_Y, 0.85f, 1.05f, 1f);
        ObjectAnimator fade = ObjectAnimator.ofFloat(panel, View.ALPHA, 0f, 1f);
        ObjectAnimator confettiMove = ObjectAnimator.ofFloat(confetti, View.TRANSLATION_Y, -30f, 20f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, fade, confettiMove);
        set.setDuration(900);
        set.start();
    }

    private void playSound() {
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 250);
    }

    private void clearPendingReward() {
        FirebaseUser user = authRepository.currentUser();
        if (user != null && repository.isReady()) {
            repository.clearPendingReward(user.getUid());
        }
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }
}
