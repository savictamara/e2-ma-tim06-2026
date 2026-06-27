package rs.ac.uns.ftn.slagalica;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.StatsRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.model.LeagueDefinition;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";
    private static final String[] STAT_DOCS = {
            "summary", "ko_zna_zna", "moj_broj", "korak_po_korak", "spojnice", "asocijacije", "skocko"
    };

    private final Map<String, DocumentSnapshot> stats = new HashMap<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private StatsRepository statsRepository;
    private FirebaseUser currentUser;
    private String uid;

    private ImageView ivAvatar;
    private ImageView ivLeagueIcon;
    private ImageView ivQrCode;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegionBasic;
    private TextView tvTokens;
    private TextView tvStars;
    private TextView tvLeague;
    private TextView tvRegion;
    private ProgressBar progressLeague;
    private TextView tvLeagueProgress;
    private TextView tvQrValue;
    private TextView tvStatsSummary;
    private TextView tvStatsGames;
    private TextView tvStatsDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        statsRepository = new StatsRepository(this);
        bindViews();

        currentUser = authRepository.currentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            goToLogin();
            return;
        }
        if (!userRepository.isReady()) {
            Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        uid = currentUser.getUid();
        setupQr(uid);
        userRepository.ensureProfileDefaults(uid, currentUser.getEmail())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Profile defaults failed", e);
                    Toast.makeText(this, "Profil nije uspeo da se inicijalizuje.", Toast.LENGTH_SHORT).show();
                });
        userRepository.ensureLeagueConsistency(uid)
                .addOnFailureListener(e -> Log.e(TAG, "League consistency check failed", e));
        statsRepository.ensureDefaultStats(uid)
                .addOnFailureListener(e -> Log.e(TAG, "Stats defaults failed", e));
        listenProfile();
        listenStats();
    }

    private void bindViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        ivLeagueIcon = findViewById(R.id.ivLeagueIcon);
        ivQrCode = findViewById(R.id.ivQrCode);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvRegionBasic = findViewById(R.id.tvRegionBasic);
        tvTokens = findViewById(R.id.tvTokens);
        tvStars = findViewById(R.id.tvStars);
        tvLeague = findViewById(R.id.tvLeague);
        tvRegion = findViewById(R.id.tvRegion);
        progressLeague = findViewById(R.id.progressLeague);
        tvLeagueProgress = findViewById(R.id.tvLeagueProgress);
        tvQrValue = findViewById(R.id.tvQrValue);
        tvStatsSummary = findViewById(R.id.tvStatsSummary);
        tvStatsGames = findViewById(R.id.tvStatsGames);
        tvStatsDetails = findViewById(R.id.tvStatsDetails);
        Button btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        Button btnLogout = findViewById(R.id.btnLogout);
        ivAvatar.setOnClickListener(v -> showAvatarPicker());
        btnChangeAvatar.setOnClickListener(v -> showAvatarPicker());
        btnLogout.setOnClickListener(v -> logout());
        renderEmptyStats();
    }

    private void listenProfile() {
        ListenerRegistration registration = userRepository.listenUser(uid, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "User profile listener failed", error);
                Toast.makeText(this, "Profil nije dostupan.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            bindUser(snapshot);
        });
        if (registration != null) {
            listeners.add(registration);
        }
    }

    private void listenStats() {
        for (String statDoc : STAT_DOCS) {
            ListenerRegistration registration = userRepository.listenUserStats(uid, statDoc, (snapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Stats listener failed: " + statDoc, error);
                    Toast.makeText(this, "Statistika nije dostupna.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    stats.put(statDoc, snapshot);
                } else {
                    stats.remove(statDoc);
                }
                bindStats();
            });
            if (registration != null) {
                listeners.add(registration);
            }
        }
    }

    private void bindUser(DocumentSnapshot user) {
        String email = firstNonEmpty(user.getString("email"), currentUser == null ? "" : currentUser.getEmail(), "");
        String username = firstNonEmpty(user.getString("username"), usernameFromEmail(email), uid);
        String region = firstNonEmpty(user.getString("region"), "Region nije podešen");
        long tokens = longValue(user.get("tokens"));
        long stars = longValue(user.get("stars"));
        LeagueDefinition league = LeagueDefinition.forStars(stars);
        String leagueName = firstNonEmpty(user.getString("leagueName"), league.name);
        String leagueIcon = firstNonEmpty(user.getString("leagueIconName"), user.getString("leagueIcon"), league.iconName);

        tvUsername.setText("Korisničko ime: " + username);
        tvEmail.setText("Email: " + email);
        tvRegionBasic.setText("Region: " + region);
        tvTokens.setText("Broj tokena: " + tokens);
        tvStars.setText("Ukupan broj zvezda: " + stars);
        tvLeague.setText("Liga: " + leagueName);
        tvRegion.setText("Region za koji igra: " + region);
        ivLeagueIcon.setImageResource(drawableForId(leagueIcon));
        ivAvatar.setImageResource(drawableForId(firstNonEmpty(user.getString("avatarId"), user.getString("avatar"), "star")));
        bindLeagueProgress(stars, league);
        applyAvatarFrame(user);
    }

    private void bindLeagueProgress(long stars, LeagueDefinition league) {
        LeagueDefinition next = LeagueDefinition.nextAfter(league);
        if (next == null) {
            progressLeague.setProgress(100);
            tvLeagueProgress.setText("Maksimalna liga dostignuta.");
            return;
        }
        long range = Math.max(1, next.requiredStars - league.requiredStars);
        long current = Math.max(0, stars - league.requiredStars);
        int progress = (int) Math.min(100, (current * 100) / range);
        progressLeague.setProgress(progress);
        tvLeagueProgress.setText(stars + " / " + next.requiredStars + " zvezda do " + next.name);
    }

    private void applyAvatarFrame(DocumentSnapshot user) {
        String frame = firstNonEmpty(user.getString("avatarFrame"), user.getString("avatarFrameType"), "NONE");
        String color = firstNonEmpty(user.getString("avatarFrameColor"), colorForFrame(frame));
        if ("NONE".equals(frame) || color.isEmpty()) {
            ivAvatar.setBackgroundResource(R.drawable.bg_avatar_frame);
            ivAvatar.setPadding(dp(14), dp(14), dp(14), dp(14));
            return;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(16));
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(5), Color.parseColor(color));
        ivAvatar.setBackground(drawable);
        ivAvatar.setPadding(dp(14), dp(14), dp(14), dp(14));
    }

    private String colorForFrame(String frame) {
        if ("GOLD".equals(frame)) return "#FFD700";
        if ("SILVER".equals(frame)) return "#C0C0C0";
        if ("BRONZE".equals(frame)) return "#CD7F32";
        return "";
    }

    private void setupQr(String uid) {
        String value = "slagalica-user:" + uid;
        tvQrValue.setText(value);
        ivQrCode.setImageBitmap(SimpleQr.create(value, 160));
    }

    private void showAvatarPicker() {
        String[] names = {"Zvezda", "Srce", "Krug", "Trougao", "Skočko"};
        String[] ids = {"star", "heart", "circle", "triangle", "skocko"};
        new AlertDialog.Builder(this)
                .setTitle("Promeni avatar")
                .setItems(names, (dialog, which) -> userRepository.updateAvatar(uid, ids[which])
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Avatar update failed", e);
                            Toast.makeText(this, "Avatar nije sačuvan.", Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    private void bindStats() {
        DocumentSnapshot summary = stats.get("summary");
        long gamesPlayed = longFrom(summary, "gamesPlayed");
        long gamesWon = longFrom(summary, "gamesWon");
        long gamesLost = longFrom(summary, "gamesLost");
        double winPercent = percentOrField(summary, "winPercent", gamesWon, gamesPlayed);
        double lossPercent = percentOrField(summary, "lossPercent", gamesLost, gamesPlayed);

        tvStatsSummary.setText("Ukupan broj odigranih partija: " + gamesPlayed
                + "\nPobede: " + gamesWon + " (" + formatPercent(winPercent) + ")"
                + "\nPorazi: " + gamesLost + " (" + formatPercent(lossPercent) + ")"
                + (gamesPlayed == 0 ? "\nNema odigranih partija" : ""));
        tvStatsGames.setText(buildAverageScores(summary));
        tvStatsDetails.setText(buildDetailedStats());
    }

    private String buildAverageScores(DocumentSnapshot summary) {
        Map<String, Object> averageScores = mapFrom(summary, "averageScoresByGame");
        Map<String, Object> played = mapFrom(summary, "playedByGame");
        String[] games = {"Ko zna zna", "Moj broj", "Korak po korak", "Spojnice", "Asocijacije", "Skočko"};
        StringBuilder text = new StringBuilder("Prosečno osvojenih bodova po igri:");
        for (String game : games) {
            text.append("\n").append(game).append(": ")
                    .append(formatNumber(doubleValue(averageScores.get(game))))
                    .append(" poena, partija: ").append(longValue(played.get(game)));
        }
        return text.toString();
    }

    private String buildDetailedStats() {
        DocumentSnapshot knowIt = stats.get("ko_zna_zna");
        long correct = longFrom(knowIt, "correctAnswers");
        long wrong = longFrom(knowIt, "wrongAnswers");

        DocumentSnapshot myNumber = stats.get("moj_broj");
        long myRounds = longFrom(myNumber, "roundsPlayed");
        long exactHits = longFrom(myNumber, "exactHits");

        DocumentSnapshot step = stats.get("korak_po_korak");
        Map<String, Object> stepPercent = mapFrom(step, "percentByStep");

        DocumentSnapshot associations = stats.get("asocijacije");
        long solved = longFrom(associations, "solved");
        long unsolved = longFrom(associations, "unsolved");

        DocumentSnapshot skocko = stats.get("skocko");
        Map<String, Object> skockoPercent = mapFrom(skocko, "percentByAttempt");

        DocumentSnapshot connections = stats.get("spojnice");
        long successfulPairs = longFrom(connections, "successfulPairs");
        long attemptedPairs = longFrom(connections, "attemptedPairs");
        double connectionPercent = percentOrField(connections, "successPercent", successfulPairs, attemptedPairs);

        return "Ko zna zna: pogođeno " + correct + ", promašeno " + wrong
                + ", uspešnost " + formatPercent(percent(correct, correct + wrong))
                + "\nMoj broj: tačan broj " + exactHits + "/" + myRounds
                + " (" + formatPercent(percentOrField(myNumber, "exactHitPercent", exactHits, myRounds)) + ")"
                + "\nKorak po korak po koraku: " + formatPercentMap(stepPercent, 7)
                + "\nAsocijacije: rešeno " + solved + ", nerešeno " + unsolved
                + "\nSkočko po pokušaju: " + formatPercentMap(skockoPercent, 6)
                + "\nSpojnice: uspešno povezano " + successfulPairs + "/" + attemptedPairs
                + " (" + formatPercent(connectionPercent) + ")";
    }

    private void renderEmptyStats() {
        tvStatsSummary.setText("Ukupan broj odigranih partija: 0\nPobede: 0 (0%)\nPorazi: 0 (0%)\nNema odigranih partija");
        tvStatsGames.setText("Prosečno osvojenih bodova po igri:\nKo zna zna: 0 poena, partija: 0\nMoj broj: 0 poena, partija: 0\nKorak po korak: 0 poena, partija: 0\nSpojnice: 0 poena, partija: 0\nAsocijacije: 0 poena, partija: 0\nSkočko: 0 poena, partija: 0");
        tvStatsDetails.setText("Ko zna zna: pogođeno 0, promašeno 0, uspešnost 0%\nMoj broj: tačan broj 0/0 (0%)\nKorak po korak po koraku: 1: 0%, 2: 0%, 3: 0%, 4: 0%, 5: 0%, 6: 0%, 7: 0%\nAsocijacije: rešeno 0, nerešeno 0\nSkočko po pokušaju: 1: 0%, 2: 0%, 3: 0%, 4: 0%, 5: 0%, 6: 0%\nSpojnice: uspešno povezano 0/0 (0%)");
    }

    private void logout() {
        userRepository.logoutUserState(uid).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Logout state update failed", task.getException());
                Toast.makeText(this, "Odjava nije uspela da ažurira profil.", Toast.LENGTH_SHORT).show();
            }
            authRepository.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int drawableForId(String id) {
        if ("heart".equals(id)) {
            return R.drawable.heart_2;
        } else if ("circle".equals(id)) {
            return R.drawable.circle;
        } else if ("triangle".equals(id)) {
            return R.drawable.triangle;
        } else if ("skocko".equals(id)) {
            return R.drawable.skocko;
        } else if ("ic_league_0".equals(id)) {
            return R.drawable.ic_league_0;
        } else if ("ic_league_1".equals(id)) {
            return R.drawable.ic_league_1;
        } else if ("ic_league_2".equals(id)) {
            return R.drawable.ic_league_2;
        } else if ("ic_league_3".equals(id)) {
            return R.drawable.ic_league_3;
        } else if ("ic_league_4".equals(id)) {
            return R.drawable.ic_league_4;
        } else if ("ic_league_5".equals(id)) {
            return R.drawable.ic_league_5;
        }
        return R.drawable.star;
    }

    private League leagueForStars(long stars) {
        if (stars >= 1600) return new League("Liga 5", "star");
        if (stars >= 800) return new League("Liga 4", "star");
        if (stars >= 400) return new League("Liga 3", "star");
        if (stars >= 200) return new League("Liga 2", "star");
        if (stars >= 100) return new League("Liga 1", "star");
        return new League("Početna liga", "star");
    }

    private String usernameFromEmail(String email) {
        return email != null && email.contains("@") ? email.substring(0, email.indexOf("@")) : "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private long longFrom(DocumentSnapshot doc, String field) {
        return doc == null ? 0 : longValue(doc.get(field));
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private double doubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private double percentOrField(DocumentSnapshot doc, String field, long numerator, long denominator) {
        if (doc != null && doc.get(field) instanceof Number) {
            return ((Number) doc.get(field)).doubleValue();
        }
        return percent(numerator, denominator);
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : (numerator * 100.0) / denominator;
    }

    private Map<String, Object> mapFrom(DocumentSnapshot doc, String field) {
        if (doc == null || !(doc.get(field) instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) doc.get(field);
    }

    private String formatPercentMap(Map<String, Object> values, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(i + 1).append(": ").append(formatPercent(doubleValue(values.get(String.valueOf(i + 1)))));
        }
        return text.toString();
    }

    private String formatPercent(double value) {
        return formatNumber(value) + "%";
    }

    private String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.US, "%.1f", value);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        super.onDestroy();
    }

    private static class League {
        final String name;
        final String icon;

        League(String name, String icon) {
            this.name = name;
            this.icon = icon;
        }
    }

    private static class SimpleQr {
        private static final int VERSION_SIZE = 33;
        private static final int DATA_CODEWORDS = 80;
        private static final int ECC_CODEWORDS = 20;

        static Bitmap create(String text, int size) {
            byte[] data = encodeData(text);
            byte[] ecc = reedSolomon(data, ECC_CODEWORDS);
            byte[] all = new byte[data.length + ecc.length];
            System.arraycopy(data, 0, all, 0, data.length);
            System.arraycopy(ecc, 0, all, data.length, ecc.length);
            int[][] matrix = buildMatrix(all);
            int quiet = 4;
            int modules = VERSION_SIZE + quiet * 2;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            int scale = Math.max(1, size / modules);
            int offset = (size - modules * scale) / 2;
            bitmap.eraseColor(Color.WHITE);
            for (int y = 0; y < modules; y++) {
                for (int x = 0; x < modules; x++) {
                    int mx = x - quiet;
                    int my = y - quiet;
                    if (mx >= 0 && my >= 0 && mx < VERSION_SIZE && my < VERSION_SIZE && matrix[my][mx] == 1) {
                        for (int py = 0; py < scale; py++) {
                            for (int px = 0; px < scale; px++) {
                                int bx = offset + x * scale + px;
                                int by = offset + y * scale + py;
                                if (bx >= 0 && by >= 0 && bx < size && by < size) {
                                    bitmap.setPixel(bx, by, Color.BLACK);
                                }
                            }
                        }
                    }
                }
            }
            return bitmap;
        }

        private static byte[] encodeData(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            BitBuffer bits = new BitBuffer();
            bits.append(4, 4);
            bits.append(bytes.length, 8);
            for (byte b : bytes) {
                bits.append(b & 0xFF, 8);
            }
            bits.append(0, Math.min(4, DATA_CODEWORDS * 8 - bits.size()));
            while (bits.size() % 8 != 0) {
                bits.append(0, 1);
            }
            byte[] data = bits.toBytes(DATA_CODEWORDS);
            for (int i = bits.size() / 8, pad = 0; i < DATA_CODEWORDS; i++, pad++) {
                data[i] = (byte) (pad % 2 == 0 ? 0xEC : 0x11);
            }
            return data;
        }

        private static int[][] buildMatrix(byte[] codewords) {
            int[][] m = new int[VERSION_SIZE][VERSION_SIZE];
            boolean[][] reserved = new boolean[VERSION_SIZE][VERSION_SIZE];
            for (int y = 0; y < VERSION_SIZE; y++) {
                for (int x = 0; x < VERSION_SIZE; x++) {
                    m[y][x] = 0;
                }
            }
            finder(m, reserved, 0, 0);
            finder(m, reserved, VERSION_SIZE - 7, 0);
            finder(m, reserved, 0, VERSION_SIZE - 7);
            alignment(m, reserved, 24, 24);
            for (int i = 8; i < VERSION_SIZE - 8; i++) {
                set(m, reserved, i, 6, i % 2 == 0);
                set(m, reserved, 6, i, i % 2 == 0);
            }
            set(m, reserved, 8, VERSION_SIZE - 8, true);
            reserveFormat(reserved);
            placeData(m, reserved, codewords);
            writeFormat(m, reserved);
            return m;
        }

        private static void finder(int[][] m, boolean[][] r, int x, int y) {
            for (int dy = -1; dy <= 7; dy++) {
                for (int dx = -1; dx <= 7; dx++) {
                    int xx = x + dx;
                    int yy = y + dy;
                    if (xx < 0 || yy < 0 || xx >= VERSION_SIZE || yy >= VERSION_SIZE) continue;
                    boolean dark = dx >= 0 && dy >= 0 && dx <= 6 && dy <= 6
                            && (dx == 0 || dy == 0 || dx == 6 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                    set(m, r, xx, yy, dark);
                }
            }
        }

        private static void alignment(int[][] m, boolean[][] r, int cx, int cy) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    boolean dark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                    set(m, r, cx + dx, cy + dy, dark);
                }
            }
        }

        private static void reserveFormat(boolean[][] r) {
            for (int i = 0; i < 9; i++) {
                r[8][i] = true;
                r[i][8] = true;
            }
            for (int i = 0; i < 8; i++) {
                r[VERSION_SIZE - 1 - i][8] = true;
                r[8][VERSION_SIZE - 1 - i] = true;
            }
        }

        private static void placeData(int[][] m, boolean[][] r, byte[] codewords) {
            int bit = 0;
            int totalBits = codewords.length * 8;
            int dir = -1;
            for (int x = VERSION_SIZE - 1; x > 0; x -= 2) {
                if (x == 6) x--;
                for (int step = 0; step < VERSION_SIZE; step++) {
                    int y = dir == -1 ? VERSION_SIZE - 1 - step : step;
                    for (int dx = 0; dx < 2; dx++) {
                        int xx = x - dx;
                        if (r[y][xx]) continue;
                        boolean dark = bit < totalBits && (((codewords[bit / 8] >> (7 - (bit % 8))) & 1) == 1);
                        if ((y + xx) % 2 == 0) dark = !dark;
                        m[y][xx] = dark ? 1 : 0;
                        bit++;
                    }
                }
                dir = -dir;
            }
        }

        private static void writeFormat(int[][] m, boolean[][] r) {
            int bits = 0b111011111000100;
            int[][] a = {{8,0},{8,1},{8,2},{8,3},{8,4},{8,5},{8,7},{8,8},{7,8},{5,8},{4,8},{3,8},{2,8},{1,8},{0,8}};
            int[][] b = {{VERSION_SIZE-1,8},{VERSION_SIZE-2,8},{VERSION_SIZE-3,8},{VERSION_SIZE-4,8},{VERSION_SIZE-5,8},{VERSION_SIZE-6,8},{VERSION_SIZE-7,8},{8,VERSION_SIZE-8},{8,VERSION_SIZE-7},{8,VERSION_SIZE-6},{8,VERSION_SIZE-5},{8,VERSION_SIZE-4},{8,VERSION_SIZE-3},{8,VERSION_SIZE-2},{8,VERSION_SIZE-1}};
            for (int i = 0; i < 15; i++) {
                boolean dark = ((bits >> i) & 1) == 1;
                m[a[i][1]][a[i][0]] = dark ? 1 : 0;
                r[a[i][1]][a[i][0]] = true;
                m[b[i][1]][b[i][0]] = dark ? 1 : 0;
                r[b[i][1]][b[i][0]] = true;
            }
        }

        private static void set(int[][] m, boolean[][] r, int x, int y, boolean dark) {
            m[y][x] = dark ? 1 : 0;
            r[y][x] = true;
        }

        private static byte[] reedSolomon(byte[] data, int ecLen) {
            int[] gen = {1};
            for (int i = 0; i < ecLen; i++) {
                gen = multiply(gen, new int[]{1, exp(i)});
            }
            int[] rem = new int[ecLen];
            for (byte datum : data) {
                int factor = (datum & 0xFF) ^ rem[0];
                System.arraycopy(rem, 1, rem, 0, ecLen - 1);
                rem[ecLen - 1] = 0;
                for (int i = 0; i < ecLen; i++) {
                    rem[i] ^= multiply(factor, gen[i + 1]);
                }
            }
            byte[] out = new byte[ecLen];
            for (int i = 0; i < ecLen; i++) out[i] = (byte) rem[i];
            return out;
        }

        private static int[] multiply(int[] a, int[] b) {
            int[] out = new int[a.length + b.length - 1];
            for (int i = 0; i < a.length; i++) {
                for (int j = 0; j < b.length; j++) {
                    out[i + j] ^= multiply(a[i], b[j]);
                }
            }
            return out;
        }

        private static int multiply(int a, int b) {
            if (a == 0 || b == 0) return 0;
            return exp(log(a) + log(b));
        }

        private static int exp(int e) {
            int x = 1;
            for (int i = 0; i < e % 255; i++) {
                x <<= 1;
                if ((x & 0x100) != 0) x ^= 0x11D;
            }
            return x;
        }

        private static int log(int value) {
            int x = 1;
            for (int i = 0; i < 255; i++) {
                if (x == value) return i;
                x <<= 1;
                if ((x & 0x100) != 0) x ^= 0x11D;
            }
            return 0;
        }
    }

    private static class BitBuffer {
        private final List<Integer> bits = new ArrayList<>();

        void append(int value, int length) {
            for (int i = length - 1; i >= 0; i--) {
                bits.add((value >> i) & 1);
            }
        }

        int size() {
            return bits.size();
        }

        byte[] toBytes(int length) {
            byte[] out = new byte[length];
            for (int i = 0; i < bits.size() && i / 8 < length; i++) {
                out[i / 8] |= bits.get(i) << (7 - (i % 8));
            }
            return out;
        }
    }
}
