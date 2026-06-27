package rs.ac.uns.ftn.slagalica;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import rs.ac.uns.ftn.slagalica.data.repository.ChallengeRepository;
import rs.ac.uns.ftn.slagalica.domain.model.ChallengeParticipant;

public class ChallengeResultActivity extends AppCompatActivity {
    public static final String EXTRA_CHALLENGE_ID = "challengeId";

    private ChallengeRepository challengeRepository;
    private String challengeId = "";
    private TextView tvStatus;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_result);
        challengeRepository = new ChallengeRepository(this);
        challengeId = getIntent().getStringExtra(EXTRA_CHALLENGE_ID);
        if (TextUtils.isEmpty(challengeId)) {
            challengeId = getIntent().getStringExtra("actionTargetId");
        }
        tvStatus = findViewById(R.id.tvChallengeResultStatus);
        container = findViewById(R.id.challengeResultsContainer);
        if (TextUtils.isEmpty(challengeId)) {
            tvStatus.setText("Nedostaje ID izazova.");
            return;
        }
        loadResults();
    }

    private void loadResults() {
        tvStatus.setText("Ucitavanje rezultata...");
        challengeRepository.getParticipants(challengeId)
                .addOnSuccessListener(this::showResults)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null ? "Rezultati nisu ucitani" : e.getMessage();
                    tvStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void showResults(QuerySnapshot snapshot) {
        container.removeAllViews();
        List<ChallengeParticipant> participants = new ArrayList<>();
        if (snapshot != null) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                participants.add(challengeRepository.participantFrom(doc));
            }
        }
        participants.sort((a, b) -> {
            if (a.placement == 0 && b.placement == 0) return Long.compare(b.totalScore, a.totalScore);
            if (a.placement == 0) return 1;
            if (b.placement == 0) return -1;
            return Long.compare(a.placement, b.placement);
        });
        if (participants.isEmpty()) {
            tvStatus.setText("Nema ucesnika.");
            return;
        }
        boolean finished = true;
        for (ChallengeParticipant p : participants) {
            if (p.placement == 0) {
                finished = false;
                break;
            }
        }
        tvStatus.setText(finished ? "Izazov je zavrsen." : "Cekaju se svi igraci.");
        for (ChallengeParticipant participant : participants) {
            container.addView(resultRow(participant));
        }
    }

    private LinearLayout resultRow(ChallengeParticipant participant) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundResource(participant.placement == 1 ? R.drawable.bg_button_secondary : R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);

        String place = participant.placement == 0 ? "-" : String.valueOf(participant.placement);
        row.addView(text(place + ". " + participant.username, 16, true));
        row.addView(text("Rezultat: " + participant.totalScore, 14, false));
        row.addView(text("Dobitak: " + participant.rewardStars + " zvezda, "
                + participant.rewardTokens + " tokena", 14, false));
        return row;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(Color.rgb(53, 43, 69));
        textView.setGravity(Gravity.START);
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return textView;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
