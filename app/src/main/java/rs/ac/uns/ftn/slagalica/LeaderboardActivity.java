package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.LeaderboardRepository;
import rs.ac.uns.ftn.slagalica.domain.model.LeaderboardDashboard;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class LeaderboardActivity extends AppCompatActivity {
    private static final long REFRESH_MS = 120_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private LeaderboardRepository repository;
    private FirebaseAuthRepository authRepository;
    private LeaderboardAdapter adapter;
    private String currentType = LeaderboardRepository.WEEKLY;
    private String uid = "";
    private TextView tvRange;
    private TextView tvStatus;
    private Button btnWeekly;
    private Button btnMonthly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        repository = new LeaderboardRepository(this);
        authRepository = new FirebaseAuthRepository(this);
        FirebaseUser user = authRepository.currentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();

        tvRange = findViewById(R.id.tvCycleRange);
        tvStatus = findViewById(R.id.tvLeaderboardStatus);
        btnWeekly = findViewById(R.id.btnWeekly);
        btnMonthly = findViewById(R.id.btnMonthly);
        RecyclerView recycler = findViewById(R.id.leaderboardRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter();
        recycler.setAdapter(adapter);

        btnWeekly.setOnClickListener(v -> switchType(LeaderboardRepository.WEEKLY));
        btnMonthly.setOnClickListener(v -> switchType(LeaderboardRepository.MONTHLY));
        load();
    }

    private void switchType(String type) {
        currentType = type;
        btnWeekly.setEnabled(!LeaderboardRepository.WEEKLY.equals(type));
        btnMonthly.setEnabled(!LeaderboardRepository.MONTHLY.equals(type));
        load();
    }

    private void load() {
        if (!repository.isReady()) {
            tvStatus.setText(R.string.firebase_not_ready);
            return;
        }
        repository.loadLeaderboard(currentType, uid)
                .addOnSuccessListener(this::show)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null
                            ? getString(R.string.firebase_not_ready)
                            : e.getMessage();
                    tvStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void show(LeaderboardDashboard dashboard) {
        tvRange.setText(dashboard.dateRange);
        adapter.submit(dashboard.entries);
        tvStatus.setText("Prikazano igraca: " + dashboard.entries.size());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }
}
