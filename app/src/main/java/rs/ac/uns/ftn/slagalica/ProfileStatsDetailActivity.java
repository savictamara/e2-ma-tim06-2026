package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;

public class ProfileStatsDetailActivity extends AppCompatActivity {
    public static final String EXTRA_DETAIL_TYPE = "detailType";
    public static final String TYPE_AVERAGES = "AVERAGES";
    public static final String TYPE_GAME_DETAILS = "GAME_DETAILS";
    public static final String TYPE_SUCCESS = "SUCCESS";

    private static final String[] STAT_DOCS = {
            "summary", "ko_zna_zna", "moj_broj", "korak_po_korak", "spojnice", "asocijacije", "skocko"
    };

    private final Map<String, DocumentSnapshot> stats = new HashMap<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private UserRepository userRepository;
    private LinearLayout container;
    private TextView title;
    private String detailType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_stats_detail);
        userRepository = new UserRepository(this);
        FirebaseAuthRepository authRepository = new FirebaseAuthRepository(this);
        FirebaseUser user = authRepository.currentUser();
        detailType = getIntent().getStringExtra(EXTRA_DETAIL_TYPE);
        if (detailType == null) detailType = TYPE_AVERAGES;
        title = findViewById(R.id.tvStatsDetailTitle);
        container = findViewById(R.id.statsDetailContainer);
        Button back = findViewById(R.id.btnBackStats);
        back.setOnClickListener(v -> finish());
        title.setText(titleForType());
        if (user == null || user.isAnonymous() || !userRepository.isReady()) {
            Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        for (String doc : STAT_DOCS) {
            ListenerRegistration registration = userRepository.listenUserStats(user.getUid(), doc, (snapshot, error) -> {
                if (snapshot != null && snapshot.exists()) {
                    stats.put(doc, snapshot);
                }
                render();
            });
            if (registration != null) listeners.add(registration);
        }
    }

    private void render() {
        container.removeAllViews();
        if (TYPE_GAME_DETAILS.equals(detailType)) {
            renderGameDetails();
        } else if (TYPE_SUCCESS.equals(detailType)) {
            renderSuccess();
        } else {
            renderAverages();
        }
    }

    private void renderAverages() {
        DocumentSnapshot summary = stats.get("summary");
        Map<String, Object> avg = mapFrom(summary, "averageScoresByGame");
        Map<String, Object> played = mapFrom(summary, "playedByGame");
        String[] games = {"Ko zna zna", "Moj broj", "Korak po korak", "Spojnice", "Asocijacije", "Skočko"};
        for (String game : games) {
            addMetricCard(game, formatNumber(doubleValue(avg.get(game))) + " poena prosečno",
                    longValue(played.get(game)) + " partija", Math.min(100, (int) doubleValue(avg.get(game))));
        }
    }

    private void renderGameDetails() {
        DocumentSnapshot knowIt = stats.get("ko_zna_zna");
        long correct = longFrom(knowIt, "correctAnswers");
        long wrong = longFrom(knowIt, "wrongAnswers");
        addMetricCard("Ko zna zna", correct + " tačno / " + wrong + " netačno", formatPercent(percent(correct, correct + wrong)), (int) percent(correct, correct + wrong));

        DocumentSnapshot myNumber = stats.get("moj_broj");
        long exact = longFrom(myNumber, "exactHits");
        long rounds = longFrom(myNumber, "roundsPlayed");
        addMetricCard("Moj broj", exact + " tačan broj / " + rounds + " pokušaja", formatPercent(percent(exact, rounds)), (int) percent(exact, rounds));

        addMapCards("Korak po korak", mapFrom(stats.get("korak_po_korak"), "percentByStep"), 7, "Korak ");

        DocumentSnapshot connections = stats.get("spojnice");
        long success = longFrom(connections, "successfulPairs");
        long attempted = longFrom(connections, "attemptedPairs");
        addMetricCard("Spojnice", success + " povezano / " + attempted + " pokušaja", formatPercent(percent(success, attempted)), (int) percent(success, attempted));

        DocumentSnapshot associations = stats.get("asocijacije");
        long solved = longFrom(associations, "solved");
        long unsolved = longFrom(associations, "unsolved");
        addMetricCard("Asocijacije", solved + " rešeno / " + unsolved + " nerešeno", formatPercent(percent(solved, solved + unsolved)), (int) percent(solved, solved + unsolved));

        addMapCards("Skočko", mapFrom(stats.get("skocko"), "percentByAttempt"), 6, "Pokušaj ");
    }

    private void renderSuccess() {
        DocumentSnapshot knowIt = stats.get("ko_zna_zna");
        long correct = longFrom(knowIt, "correctAnswers");
        long wrong = longFrom(knowIt, "wrongAnswers");
        addMetricCard("Ko zna zna", "Tačnost odgovora", formatPercent(percent(correct, correct + wrong)), (int) percent(correct, correct + wrong));

        DocumentSnapshot myNumber = stats.get("moj_broj");
        addMetricCard("Moj broj", "Pogađanje tačnog broja", formatPercent(percent(longFrom(myNumber, "exactHits"), longFrom(myNumber, "roundsPlayed"))),
                (int) percent(longFrom(myNumber, "exactHits"), longFrom(myNumber, "roundsPlayed")));

        addMetricCard("Korak po korak", "Prosečna uspešnost koraka", formatPercent(averagePercent(mapFrom(stats.get("korak_po_korak"), "percentByStep"), 7)),
                (int) averagePercent(mapFrom(stats.get("korak_po_korak"), "percentByStep"), 7));

        DocumentSnapshot connections = stats.get("spojnice");
        addMetricCard("Spojnice", "Uspešno povezivanje", formatPercent(percent(longFrom(connections, "successfulPairs"), longFrom(connections, "attemptedPairs"))),
                (int) percent(longFrom(connections, "successfulPairs"), longFrom(connections, "attemptedPairs")));

        DocumentSnapshot associations = stats.get("asocijacije");
        addMetricCard("Asocijacije", "Rešene asocijacije", formatPercent(percent(longFrom(associations, "solved"), longFrom(associations, "solved") + longFrom(associations, "unsolved"))),
                (int) percent(longFrom(associations, "solved"), longFrom(associations, "solved") + longFrom(associations, "unsolved")));

        addMetricCard("Skočko", "Najbolji procenat po pokušajima", formatPercent(maxPercent(mapFrom(stats.get("skocko"), "percentByAttempt"), 6)),
                (int) maxPercent(mapFrom(stats.get("skocko"), "percentByAttempt"), 6));
    }

    private void addMapCards(String titlePrefix, Map<String, Object> values, int count, String labelPrefix) {
        TextView heading = text(titlePrefix, 18, true);
        heading.setPadding(0, dp(8), 0, 0);
        container.addView(heading);
        for (int i = 1; i <= count; i++) {
            double value = doubleValue(values.get(String.valueOf(i)));
            addMetricCard(labelPrefix + i, formatPercent(value), "", (int) value);
        }
    }

    private void addMetricCard(String heading, String primary, String secondary, int progress) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_profile_chart_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(params);
        card.addView(text(heading, 16, true));
        card.addView(text(primary, 14, false));
        if (!secondary.isEmpty()) card.addView(text(secondary, 13, false));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, progress)));
        card.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
        container.addView(card);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getResources().getColor(R.color.text_main));
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private String titleForType() {
        if (TYPE_GAME_DETAILS.equals(detailType)) return "Detalji po igrama";
        if (TYPE_SUCCESS.equals(detailType)) return "Uspešnost po igrama";
        return "Prosek po igrama";
    }

    private long longFrom(DocumentSnapshot doc, String field) { return doc == null ? 0 : longValue(doc.get(field)); }
    private long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    private double doubleValue(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : 0; }
    private double percent(long numerator, long denominator) { return denominator == 0 ? 0 : (numerator * 100.0) / denominator; }
    private Map<String, Object> mapFrom(DocumentSnapshot doc, String field) {
        return doc != null && doc.get(field) instanceof Map ? (Map<String, Object>) doc.get(field) : new HashMap<>();
    }
    private double averagePercent(Map<String, Object> values, int count) {
        if (count <= 0) return 0;
        double sum = 0;
        for (int i = 1; i <= count; i++) sum += doubleValue(values.get(String.valueOf(i)));
        return sum / count;
    }
    private double maxPercent(Map<String, Object> values, int count) {
        double max = 0;
        for (int i = 1; i <= count; i++) max = Math.max(max, doubleValue(values.get(String.valueOf(i))));
        return max;
    }
    private String formatPercent(double value) { return formatNumber(value) + "%"; }
    private String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.US, "%.1f", value);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onDestroy() {
        for (ListenerRegistration listener : listeners) listener.remove();
        super.onDestroy();
    }
}
