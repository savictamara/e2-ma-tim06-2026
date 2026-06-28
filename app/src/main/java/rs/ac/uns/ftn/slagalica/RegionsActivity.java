package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import rs.ac.uns.ftn.slagalica.data.repository.ChallengeRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.ChallengeItem;
import rs.ac.uns.ftn.slagalica.domain.model.RegionDashboard;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class RegionsActivity extends AppCompatActivity {
    private RegionRepository regionRepository;
    private ChallengeRepository challengeRepository;
    private FirebaseAuthRepository authRepository;
    private RegionMapView mapView;
    private TextView tvStatus;
    private TextView tvChallengeStatus;
    private LinearLayout rankingContainer;
    private LinearLayout challengesContainer;
    private EditText etChallengeStars;
    private EditText etChallengeTokens;
    private String uid = "";
    private String currentRegionId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regions);
        regionRepository = new RegionRepository(this);
        challengeRepository = new ChallengeRepository(this);
        authRepository = new FirebaseAuthRepository(this);
        mapView = findViewById(R.id.regionMapView);
        tvStatus = findViewById(R.id.tvRegionStatus);
        tvChallengeStatus = findViewById(R.id.tvChallengeStatus);
        rankingContainer = findViewById(R.id.regionRankingContainer);
        challengesContainer = findViewById(R.id.challengesContainer);
        etChallengeStars = findViewById(R.id.etChallengeStars);
        etChallengeTokens = findViewById(R.id.etChallengeTokens);
        Button btnCreateChallenge = findViewById(R.id.btnCreateChallenge);
        mapView.setOnRegionClickListener(this::openDetail);
        btnCreateChallenge.setOnClickListener(v -> createChallenge());
        loadRegions();
        loadChallenges();
    }

    private void loadRegions() {
        if (!regionRepository.isReady()) {
            tvStatus.setText(R.string.firebase_not_ready);
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        tvStatus.setText(R.string.regions_loading);
        regionRepository.loadDashboard(uid)
                .addOnSuccessListener(this::showDashboard)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null
                            ? getString(R.string.firebase_not_ready)
                            : e.getMessage();
                    tvStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void loadChallenges() {
        if (!challengeRepository.isReady()) {
            tvChallengeStatus.setText(R.string.firebase_not_ready);
            return;
        }
        if (TextUtils.isEmpty(uid)) {
            FirebaseUser user = authRepository.currentUser();
            uid = user == null ? GuestSession.uid(this) : user.getUid();
        }
        tvChallengeStatus.setText("Ucitavanje izazova...");
        challengeRepository.listOpenChallenges()
                .addOnSuccessListener(this::showChallenges)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null ? "Greska pri ucitavanju izazova" : e.getMessage();
                    tvChallengeStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void createChallenge() {
        long stars = parseStake(etChallengeStars);
        long tokens = parseStake(etChallengeTokens);
        if (stars < 0 || stars > 10 || tokens < 0 || tokens > 2) {
            Toast.makeText(this, "Zvezde moraju biti 0-10, tokeni 0-2", Toast.LENGTH_SHORT).show();
            return;
        }
        tvChallengeStatus.setText("Kreiranje izazova...");
        challengeRepository.createChallenge(uid, stars, tokens)
                .addOnSuccessListener(id -> {
                    etChallengeStars.setText("");
                    etChallengeTokens.setText("");
                    Toast.makeText(this, "Izazov je kreiran", Toast.LENGTH_SHORT).show();
                    loadChallenges();
                })
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null ? "Izazov nije kreiran" : e.getMessage();
                    tvChallengeStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void showChallenges(QuerySnapshot snapshot) {
        challengesContainer.removeAllViews();
        if (snapshot == null || snapshot.isEmpty()) {
            tvChallengeStatus.setText("Nema otvorenih izazova.");
            return;
        }
        tvChallengeStatus.setText("Izazovi: " + snapshot.size());
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            challengesContainer.addView(challengeRow(challengeRepository.itemFrom(doc)));
        }
    }

    private View challengeRow(ChallengeItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(params);

        card.addView(text(item.creatorUsername + " - " + statusLabel(item.status), 15, true));
        card.addView(text("Ulog: " + item.stakeStars + " zvezda, " + item.stakeTokens
                + " tokena | Igraci: " + item.currentPlayers + "/" + item.maxPlayers, 13, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        card.addView(actions);

        if (ChallengeRepository.WAITING.equals(item.status)) {
            Button join = smallButton("Pridruzi se");
            join.setEnabled(!uid.equals(item.creatorUid) && item.currentPlayers < item.maxPlayers);
            join.setOnClickListener(v -> joinChallenge(item.challengeId));
            actions.addView(join);

            Button start = smallButton("Pokreni");
            start.setEnabled(uid.equals(item.creatorUid) && item.currentPlayers >= 2);
            start.setOnClickListener(v -> startChallenge(item.challengeId));
            actions.addView(start);
        } else if (ChallengeRepository.ACTIVE.equals(item.status)) {
            Button play = smallButton("Igraj");
            play.setOnClickListener(v -> openChallengeRun(item.challengeId));
            actions.addView(play);
        } else if (ChallengeRepository.FINISHED.equals(item.status)) {
            Button results = smallButton("Rezultati");
            results.setOnClickListener(v -> openChallengeResults(item.challengeId));
            actions.addView(results);
        }
        return card;
    }

    private void joinChallenge(String challengeId) {
        tvChallengeStatus.setText("Pridruzivanje izazovu...");
        challengeRepository.joinChallenge(challengeId, uid)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Pridruzili ste se izazovu", Toast.LENGTH_SHORT).show();
                    loadChallenges();
                })
                .addOnFailureListener(e -> showChallengeError(e, "Ne mozete se pridruziti izazovu"));
    }

    private void startChallenge(String challengeId) {
        tvChallengeStatus.setText("Pokretanje izazova...");
        challengeRepository.startChallenge(challengeId, uid, false)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Izazov je pokrenut", Toast.LENGTH_SHORT).show();
                    openChallengeRun(challengeId);
                })
                .addOnFailureListener(e -> showChallengeError(e, "Izazov nije pokrenut"));
    }

    private void openChallengeRun(String challengeId) {
        Intent intent = new Intent(this, ChallengeRunActivity.class);
        intent.putExtra(ChallengeRunActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
    }

    private void openChallengeResults(String challengeId) {
        Intent intent = new Intent(this, ChallengeResultActivity.class);
        intent.putExtra(ChallengeResultActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
    }

    private void showChallengeError(Exception e, String fallback) {
        String message = e == null || e.getMessage() == null ? fallback : e.getMessage();
        tvChallengeStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        loadChallenges();
    }

    private long parseStake(EditText editText) {
        String value = editText.getText() == null ? "" : editText.getText().toString().trim();
        if (value.isEmpty()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private String statusLabel(String status) {
        if (ChallengeRepository.WAITING.equals(status)) return "ceka igrace";
        if (ChallengeRepository.ACTIVE.equals(status)) return "aktivan";
        if (ChallengeRepository.FINISHED.equals(status)) return "zavrsen";
        if (ChallengeRepository.CANCELLED.equals(status)) return "otkazan";
        return status == null ? "" : status;
    }

    private void showDashboard(RegionDashboard dashboard) {
        mapView.setData(dashboard.regions, dashboard.points, dashboard.currentUserRegionId);
        currentRegionId = dashboard.currentUserRegionId == null ? "" : dashboard.currentUserRegionId;
        if (dashboard.currentUserRegionId == null || dashboard.currentUserRegionId.trim().isEmpty()) {
            tvStatus.setText(R.string.regions_missing_region);
        } else {
            tvStatus.setText(getString(R.string.regions_current_region, dashboard.currentUserRegionName));
        }
        rankingContainer.removeAllViews();
        for (int i = 0; i < dashboard.regions.size(); i++) {
            rankingContainer.addView(rankingRow(i + 1, dashboard.regions.get(i), dashboard.currentUserRegionId));
        }
    }

    private View rankingRow(int place, RegionStats stats, String currentRegionId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        row.setBackgroundResource(stats.regionId.equals(currentRegionId) ? R.drawable.bg_button_secondary : R.drawable.bg_card);
        row.setOnClickListener(v -> openDetail(stats.regionId));

        TextView icon = text(medalForPlace(place), 22, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(44));
        row.addView(icon, iconParams);

        TextView name = text(regionIcon(stats.regionId) + " " + stats.regionName, 15, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(name, nameParams);

        TextView stars = text(getString(R.string.regions_stars_value, stats.monthlyStars), 14, false);
        stars.setGravity(android.view.Gravity.END);
        row.addView(stars);
        return row;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(Color.rgb(53, 43, 69));
        textView.setTextSize(sp);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private String regionShortLabel(String regionId) {
        if ("VOJVODINA".equals(regionId)) return "V";
        if ("BEOGRAD".equals(regionId)) return "BG";
        if ("SUMADIJA_ZAPADNA_SRBIJA".equals(regionId)) return "SZ";
        if ("JUZNA_ISTOCNA_SRBIJA".equals(regionId)) return "JI";
        if ("KOSOVO_METOHIJA".equals(regionId)) return "KM";
        return "";
    }

    private void openDetail(String regionId) {
        regionRepository.getRegionStats(regionId)
                .addOnSuccessListener(stats -> {
                    if (System.currentTimeMillis() >= 0) {
                        showRegionStatsDialog(regionId, stats);
                        return;
                    }
                    LinearLayout content = new LinearLayout(this);
                    content.setOrientation(LinearLayout.VERTICAL);
                    content.setPadding(dp(8), dp(4), dp(8), 0);
                    TextView title = text(regionIcon(stats.regionId) + "  " + stats.regionName, 22, true);
                    title.setGravity(Gravity.CENTER_HORIZONTAL);
                    content.addView(title);
                    if (stats.regionId.equals(currentRegionId)) {
                        TextView badge = text("Vaš region", 13, true);
                        badge.setGravity(Gravity.CENTER);
                        badge.setTextColor(Color.WHITE);
                        badge.setBackgroundResource(R.drawable.bg_button_primary);
                        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
                        badgeParams.setMargins(0, dp(8), 0, dp(8));
                        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
                        content.addView(badge, badgeParams);
                    }
                    content.addView(text("Mesečne zvezde: " + stats.monthlyStars, 15, false));
                    content.addView(text("Trenutni plasman: " + stats.currentRank, 15, false));
                    content.addView(text("Prva mesta: " + stats.firstPlaces, 15, false));
                    content.addView(text("Druga mesta: " + stats.secondPlaces, 15, false));
                    content.addView(text("Treća mesta: " + stats.thirdPlaces, 15, false));
                    content.addView(text("Aktivni igrači: " + stats.activePlayers, 15, false));
                    content.addView(text("Registrovani igrači: " + stats.totalPlayers, 15, false));
                    new AlertDialog.Builder(this)
                            .setView(content)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNegativeButton("Detalji", (dialog, which) -> {
                                Intent intent = new Intent(this, RegionDetailActivity.class);
                                intent.putExtra(RegionDetailActivity.EXTRA_REGION_ID, regionId);
                                startActivity(intent);
                            })
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        e == null || e.getMessage() == null ? "Region nije ucitan" : e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void showRegionStatsDialog(String regionId, RegionStats stats) {
        View content = getLayoutInflater().inflate(R.layout.dialog_region_stats, null, false);
        ((TextView) content.findViewById(R.id.tvRegionDialogIcon)).setText(regionShortLabel(stats.regionId));
        ((TextView) content.findViewById(R.id.tvRegionDialogName)).setText(stats.regionName);
        TextView badge = content.findViewById(R.id.tvRegionDialogCurrentBadge);
        badge.setVisibility(stats.regionId.equals(currentRegionId) ? View.VISIBLE : View.GONE);
        ((TextView) content.findViewById(R.id.tvRegionDialogMonthlyStars))
                .setText("Mesecne zvezde: " + stats.monthlyStars);
        ((TextView) content.findViewById(R.id.tvRegionDialogRank))
                .setText("Trenutni plasman: " + stats.currentRank);
        ((TextView) content.findViewById(R.id.tvRegionDialogFirst))
                .setText("Prva mesta: " + stats.firstPlaces);
        ((TextView) content.findViewById(R.id.tvRegionDialogSecond))
                .setText("Druga mesta: " + stats.secondPlaces);
        ((TextView) content.findViewById(R.id.tvRegionDialogThird))
                .setText("Treca mesta: " + stats.thirdPlaces);
        ((TextView) content.findViewById(R.id.tvRegionDialogActive))
                .setText("Aktivni igraci: " + stats.activePlayers);
        ((TextView) content.findViewById(R.id.tvRegionDialogRegistered))
                .setText("Registrovani igraci: " + stats.totalPlayers);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        content.findViewById(R.id.btnRegionDialogClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnRegionDialogDetails).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, RegionDetailActivity.class);
            intent.putExtra(RegionDetailActivity.EXTRA_REGION_ID, regionId);
            startActivity(intent);
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String medalForPlace(int place) {
        if (place == 1) return "🥇";
        if (place == 2) return "🥈";
        if (place == 3) return "🥉";
        return String.valueOf(place);
    }

    private String regionIcon(String regionId) {
        if ("VOJVODINA".equals(regionId)) return "🌾";
        if ("BEOGRAD".equals(regionId)) return "🏙";
        if ("SUMADIJA_ZAPADNA_SRBIJA".equals(regionId)) return "🌳";
        if ("JUZNA_ISTOCNA_SRBIJA".equals(regionId)) return "⛰";
        if ("KOSOVO_METOHIJA".equals(regionId)) return "🏰";
        return "•";
    }
}
