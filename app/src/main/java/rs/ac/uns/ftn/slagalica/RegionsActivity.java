package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import rs.ac.uns.ftn.slagalica.data.repository.ChallengeRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.ChallengeItem;
import rs.ac.uns.ftn.slagalica.domain.model.RegionDashboard;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class RegionsActivity extends AppCompatActivity {
    private static final String DEBUG_TAG = "RegionChallengeDebug";
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
    private String listeningRegionId = "";
    private ListenerRegistration challengeListener;

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
        btnCreateChallenge.setOnClickListener(v -> showCreateChallengeDialog());
        loadRegions();
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
        attachChallengeListener();
    }

    private void attachChallengeListener() {
        if (!challengeRepository.isReady()) {
            tvChallengeStatus.setText(R.string.firebase_not_ready);
            return;
        }
        if (TextUtils.isEmpty(uid)) {
            FirebaseUser user = authRepository.currentUser();
            uid = user == null ? GuestSession.uid(this) : user.getUid();
        }
        if (challengeListener != null && currentRegionId.equals(listeningRegionId)) {
            Log.d(DEBUG_TAG, "onResume refresh/listener state already attached regionId=" + currentRegionId);
            return;
        }
        detachChallengeListener();
        listeningRegionId = currentRegionId;
        tvChallengeStatus.setText("Ucitavanje izazova...");
        challengeListener = challengeRepository.listenRegionChallenges(currentRegionId, (snapshot, e) -> {
            if (e != null) {
                Log.e(DEBUG_TAG, "Failed to load challenges", e);
                tvChallengeStatus.setText("Izazovi trenutno nisu dostupni");
                Toast.makeText(this, "Izazovi trenutno nisu dostupni", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(DEBUG_TAG, "snapshot received count=" + (snapshot == null ? 0 : snapshot.size()));
            showChallenges(snapshot);
        });
        Log.d(DEBUG_TAG, "snapshot listener attached regionId=" + currentRegionId);
    }

    private void detachChallengeListener() {
        if (challengeListener != null) {
            challengeListener.remove();
            challengeListener = null;
        }
        listeningRegionId = "";
    }

    private void showCreateChallengeDialog() {
        Log.d(DEBUG_TAG, "create challenge clicked uid=" + uid + ", regionId=" + currentRegionId);
        View content = getLayoutInflater().inflate(R.layout.dialog_create_region_challenge, null, false);
        EditText starsInput = content.findViewById(R.id.etDialogChallengeStars);
        EditText tokensInput = content.findViewById(R.id.etDialogChallengeTokens);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        content.findViewById(R.id.btnCancelRegionChallenge).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnPostRegionChallenge).setOnClickListener(v -> {
            long stars = parseStake(starsInput);
            long tokens = parseStake(tokensInput);
            createChallenge(dialog, stars, tokens);
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void createChallenge(AlertDialog dialog, long stars, long tokens) {
        if (stars < 0 || stars > 10 || tokens < 0 || tokens > 2) {
            Toast.makeText(this, "Zvezde moraju biti 0-10, tokeni 0-2", Toast.LENGTH_SHORT).show();
            Log.d(DEBUG_TAG, "stake validation result invalid range stars=" + stars + ", tokens=" + tokens);
            return;
        }
        if (stars == 0 && tokens == 0) {
            Toast.makeText(this, "Ulog ne moze biti 0 za zvezde i tokene", Toast.LENGTH_SHORT).show();
            Log.d(DEBUG_TAG, "stake validation result both zero");
            return;
        }
        tvChallengeStatus.setText("Kreiranje izazova...");
        challengeRepository.createChallenge(uid, currentRegionId, "", stars, tokens)
                .addOnSuccessListener(id -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Izazov je objavljen", Toast.LENGTH_SHORT).show();
                    Log.d(DEBUG_TAG, "create action finished challengeId=" + id);
                    attachChallengeListener();
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
        List<DocumentSnapshot> challenges = new ArrayList<>(snapshot.getDocuments());
        challenges.sort((a, b) -> Long.compare(timestampMillis(b.getTimestamp("createdAt")),
                timestampMillis(a.getTimestamp("createdAt"))));
        Log.d(DEBUG_TAG, "challenge list sorted locally count=" + challenges.size());
        tvChallengeStatus.setText("Izazovi: " + challenges.size());
        Log.d(DEBUG_TAG, "challenge loaded count=" + challenges.size());
        int shown = 0;
        for (DocumentSnapshot doc : challenges) {
            if (shown >= 30) break;
            challengesContainer.addView(challengeRow(challengeRepository.itemFrom(doc)));
            shown++;
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

        card.addView(text(item.creatorUsername + " - " + statusLabel(item), 15, true));
        card.addView(text("Ulog: " + item.stakeStars + " zvezda, " + item.stakeTokens
                + " tokena | Igraci: " + item.currentPlayers + "/" + item.maxPlayers, 13, false));
        card.addView(text("Ukupan pot: " + item.poolStars + " zvezda, " + item.poolTokens + " tokena", 13, false));
        TextView progress = text("", 13, false);
        card.addView(progress);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        card.addView(actions);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(0, dp(8), 0, 0);
        card.addView(preview);
        challengeRepository.getParticipants(item.challengeId)
                .addOnSuccessListener(parts -> renderChallengeCardState(progress, actions, preview, item, parts))
                .addOnFailureListener(e -> {
                    Log.e(DEBUG_TAG, "Failed to load challenge participants challengeId=" + item.challengeId, e);
                    progress.setText("Ucesnici trenutno nisu dostupni");
                });
        return card;
    }

    private void renderChallengeCardState(TextView progress, LinearLayout actions, LinearLayout preview,
                                          ChallengeItem item, QuerySnapshot parts) {
        int participantCount = parts == null ? 0 : parts.size();
        int completedCount = Math.max(completedCount(parts), (int) item.completedPlayers);
        progress.setText("Zavrsilo: " + completedCount + "/" + participantCount);
        if (ChallengeRepository.FINISHED.equals(item.status)) {
            renderChallengeResultPreview(preview, item, parts);
            actions.removeAllViews();
            Button results = smallButton("Rezultati");
            results.setOnClickListener(v -> openChallengeResults(item.challengeId));
            actions.addView(results);
        } else {
            preview.removeAllViews();
            renderChallengeActions(actions, item, currentParticipant(parts), participantCount, completedCount);
        }
    }

    private void renderChallengeResultPreview(LinearLayout container, ChallengeItem item, QuerySnapshot parts) {
        container.removeAllViews();
        Log.d(DEBUG_TAG, "results rendered challengeId=" + item.challengeId);
        container.addView(text("Rezultati izazova", 14, true));
        container.addView(text("Pot: " + item.poolStars + " zvezda, " + item.poolTokens + " tokena", 13, false));
        boolean currentUserRewarded = false;
        if (parts != null) {
            List<DocumentSnapshot> sorted = new ArrayList<>(parts.getDocuments());
            sorted.sort((a, b) -> Long.compare(number(a.get("placement")), number(b.get("placement"))));
            for (DocumentSnapshot part : sorted) {
                long placement = number(part.get("placement"));
                long score = number(part.get("totalScore"));
                long rewardStars = number(part.get("rewardStars"));
                long rewardTokens = number(part.get("rewardTokens"));
                String reward = placement == 2
                        ? " - Vracen ulog"
                        : rewardStars > 0 || rewardTokens > 0
                        ? " - Nagrada: " + rewardStars + " zvezda, " + rewardTokens + " tokena"
                        : " - Bez nagrade";
                TextView row = text(placement + ". " + value(part.getString("username"), part.getId())
                        + "  " + score + " poena" + reward, 13, placement == 1);
                if (part.getId().equals(uid)) {
                    row.setBackgroundResource(R.drawable.bg_button_secondary);
                    row.setPadding(dp(8), dp(4), dp(8), dp(4));
                    currentUserRewarded = rewardStars > 0 || rewardTokens > 0;
                }
                container.addView(row);
            }
        }
        container.addView(text(currentUserRewarded ? "Osvojili ste nagradu." : "Niste osvojili nagradu.", 13, true));
    }

    private void renderChallengeActions(LinearLayout actions, ChallengeItem item, DocumentSnapshot participant,
                                        int participantCount, int completedCount) {
        actions.removeAllViews();
        boolean isParticipant = participant != null && participant.exists();
        boolean completed = isParticipant && Boolean.TRUE.equals(participant.getBoolean("finished"));
        Log.d("RegionsChallengeStateDebug", "current user participant completed value=" + completed
                + ", challenge status=" + item.status
                + ", challengeId=" + item.challengeId);
        Log.d(DEBUG_TAG, "region card rendered state/button challengeId=" + item.challengeId
                + ", status=" + item.status
                + ", participantCount=" + participantCount
                + ", completedParticipants=" + completedCount
                + ", isParticipant=" + isParticipant
                + ", completed=" + completed);
        if (isParticipant && !completed) {
            Button play = smallButton("Igraj");
            play.setOnClickListener(v -> openChallengeRun(item.challengeId));
            actions.addView(play);
        } else if (isParticipant) {
            Button waiting = smallButton(participantCount < 2
                    ? "Zavrsili ste izazov - ceka se jos igraca"
                    : "Zavrsili ste izazov - ceka se rezultat");
            waiting.setEnabled(false);
            actions.addView(waiting);
        } else if (!ChallengeRepository.FINISHED.equals(item.status) && participantCount < item.maxPlayers) {
            Button join = smallButton("Prihvati");
            join.setOnClickListener(v -> joinChallenge(item.challengeId));
            actions.addView(join);
        } else {
            Button full = smallButton("Popunjeno");
            full.setEnabled(false);
            actions.addView(full);
        }
        if (uid.equals(item.creatorUid) && !ChallengeRepository.FINISHED.equals(item.status) && completedCount >= 2) {
            Button finish = smallButton("Zavrsi izazov");
            finish.setOnClickListener(v -> finishChallenge(item.challengeId));
            actions.addView(finish);
        }
    }

    private void joinChallenge(String challengeId) {
        tvChallengeStatus.setText("Pridruzivanje izazovu...");
        challengeRepository.joinChallenge(challengeId, uid)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Pridruzili ste se izazovu", Toast.LENGTH_SHORT).show();
                    Log.d(DEBUG_TAG, "accept action finished challengeId=" + challengeId);
                    attachChallengeListener();
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
        Log.d(DEBUG_TAG, "play clicked challengeId=" + challengeId + ", uid=" + uid);
        Log.d(DEBUG_TAG, "challenge run started challengeId=" + challengeId + ", uid=" + uid);
        Intent intent = new Intent(this, ChallengeRunActivity.class);
        intent.putExtra(ChallengeRunActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
    }

    private void finishChallenge(String challengeId) {
        tvChallengeStatus.setText("Zavrsavanje izazova...");
        challengeRepository.finishChallengeManually(challengeId, uid)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Izazov je zavrsen", Toast.LENGTH_SHORT).show();
                    attachChallengeListener();
                })
                .addOnFailureListener(e -> showChallengeError(e, "Izazov nije zavrsen"));
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

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private long timestampMillis(Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.toDate().getTime();
    }

    private int completedCount(QuerySnapshot parts) {
        if (parts == null) return 0;
        int count = 0;
        for (DocumentSnapshot part : parts.getDocuments()) {
            if (Boolean.TRUE.equals(part.getBoolean("finished"))) count++;
        }
        Log.d(DEBUG_TAG, "completed participant count=" + count + "/" + parts.size());
        return count;
    }

    private DocumentSnapshot currentParticipant(QuerySnapshot parts) {
        if (parts == null) return null;
        for (DocumentSnapshot part : parts.getDocuments()) {
            if (part.getId().equals(uid)) return part;
        }
        return null;
    }

    private String value(String primary, String fallback) {
        return primary == null || primary.trim().isEmpty() ? fallback : primary.trim();
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

    private String statusLabel(ChallengeItem item) {
        if (ChallengeRepository.WAITING.equals(item.status) || ChallengeRepository.ACTIVE.equals(item.status)) {
            return item.currentPlayers >= item.maxPlayers ? "Popunjeno" : "Otvoren izazov";
        }
        if (ChallengeRepository.FINISHED.equals(item.status)) return "Rezultati izazova";
        if (ChallengeRepository.CANCELLED.equals(item.status)) return "Otkazano";
        return item.status == null ? "" : item.status;
    }

    private void showDashboard(RegionDashboard dashboard) {
        mapView.setData(dashboard.regions, dashboard.points, dashboard.currentUserRegionId);
        currentRegionId = dashboard.currentUserRegionId == null ? "" : dashboard.currentUserRegionId;
        if (dashboard.currentUserRegionId == null || dashboard.currentUserRegionId.trim().isEmpty()) {
            tvStatus.setText(R.string.regions_missing_region);
        } else {
            tvStatus.setText(getString(R.string.regions_current_region, dashboard.currentUserRegionName));
        }
        loadChallenges();
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
        Log.d(DEBUG_TAG, "onResume refresh/listener state regionId=" + currentRegionId
                + ", listenerAttached=" + (challengeListener != null));
        if (!TextUtils.isEmpty(currentRegionId)) {
            attachChallengeListener();
            Log.d(DEBUG_TAG, "completed challenge run returned");
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        detachChallengeListener();
        super.onStop();
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
