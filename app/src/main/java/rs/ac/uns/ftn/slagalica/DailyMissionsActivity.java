package rs.ac.uns.ftn.slagalica;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import rs.ac.uns.ftn.slagalica.data.repository.DailyMissionRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class DailyMissionsActivity extends AppCompatActivity {
    private DailyMissionRepository repository;
    private ListenerRegistration listener;
    private String uid = "";
    private TextView tvDate;
    private TextView tvProgress;
    private TextView tvReward;
    private LinearLayout missionContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_missions);
        FirebaseInitializer.ensure(this);
        repository = new DailyMissionRepository(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        tvDate = findViewById(R.id.tvDailyMissionDate);
        tvProgress = findViewById(R.id.tvDailyMissionProgress);
        tvReward = findViewById(R.id.tvDailyMissionReward);
        missionContainer = findViewById(R.id.dailyMissionContainer);
        findViewById(R.id.btnDailyMissionBack).setOnClickListener(v -> finish());
        if (repository == null || !repository.isReady()) {
            show("Firebase nije spreman.");
            return;
        }
        repository.ensureToday(uid)
                .addOnSuccessListener(unused -> attachListener())
                .addOnFailureListener(e -> show(e == null ? "Misije nisu dostupne" : e.getMessage()));
    }

    private void attachListener() {
        if (listener != null) listener.remove();
        listener = repository.listenCurrent(uid, (snapshot, error) -> {
            if (error != null) {
                show(error.getMessage());
                return;
            }
            render(snapshot);
        });
    }

    private void render(DocumentSnapshot mission) {
        missionContainer.removeAllViews();
        if (mission == null || !mission.exists()) {
            tvDate.setText("");
            tvProgress.setText("0/4");
            return;
        }
        boolean win = done(mission, DailyMissionRepository.WIN_MATCH);
        boolean chat = done(mission, DailyMissionRepository.SEND_CHAT);
        boolean friendly = done(mission, DailyMissionRepository.PLAY_FRIENDLY);
        boolean tournament = done(mission, DailyMissionRepository.WIN_TOURNAMENT);
        int completed = (win ? 1 : 0) + (chat ? 1 : 0) + (friendly ? 1 : 0) + (tournament ? 1 : 0);
        tvDate.setText("Datum: " + value(mission.getString("date")));
        tvProgress.setText("Progress: " + completed + "/4");
        tvReward.setText("Nagrada: svaka misija +3 zvezde. Sve 4: +2 tokena i +3 zvezde.");
        addMission("Pobedi partiju", win);
        addMission("Posalji poruku u cet", chat);
        addMission("Odigraj prijateljsku partiju", friendly);
        addMission("Pobedi partiju u turniru", tournament);
    }

    private void addMission(String title, boolean complete) {
        TextView row = new TextView(this);
        row.setText((complete ? "✓ " : "○ ") + title + "\n" + (complete ? "Zavrseno +3 zvezde" : "Nije zavrseno"));
        row.setTextColor(getColor(R.color.text_main));
        row.setTextSize(16);
        row.setTypeface(Typeface.DEFAULT, complete ? Typeface.BOLD : Typeface.NORMAL);
        row.setBackgroundResource(complete ? R.drawable.bg_button_secondary : R.drawable.bg_card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        missionContainer.addView(row);
    }

    private boolean done(DocumentSnapshot doc, String field) {
        return Boolean.TRUE.equals(doc.getBoolean(field));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? "Greska" : message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (listener != null) listener.remove();
        super.onDestroy();
    }
}
