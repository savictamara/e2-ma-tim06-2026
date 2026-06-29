package rs.ac.uns.ftn.slagalica;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FriendRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.LeaderboardRepository;
import rs.ac.uns.ftn.slagalica.data.repository.NotificationRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;
import rs.ac.uns.ftn.slagalica.domain.model.LeagueDefinition;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;
import rs.ac.uns.ftn.slagalica.util.GuestSession;
import rs.ac.uns.ftn.slagalica.util.NotificationHelper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_POST_NOTIFICATIONS = 77;
    private FirebaseAuthRepository authRepository;
    private GameRepository gameRepository;
    private NotificationRepository notificationRepository;
    private FriendRepository friendRepository;
    private LeaderboardRepository leaderboardRepository;
    private UserRepository userRepository;
    private ListenerRegistration notificationListener;
    private ListenerRegistration friendlyInviteListener;
    private Button btnLogin;
    private Button btnRegister;
    private Button btnReset;
    private Button btnProfile;
    private Button btnNotifikacije;
    private boolean leagueDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Firebase ensure from MainActivity=" + FirebaseInitializer.ensure(this));
        setContentView(R.layout.activity_main);
        authRepository = new FirebaseAuthRepository(this);
        gameRepository = new GameRepository(this);
        notificationRepository = new NotificationRepository(this);
        friendRepository = new FriendRepository(this);
        leaderboardRepository = new LeaderboardRepository(this);
        userRepository = new UserRepository(this);
        NotificationHelper.createNotificationChannels(this);
        requestNotificationPermissionIfNeeded();

        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnReset = findViewById(R.id.btnReset);
        Button btnKorak = findViewById(R.id.btnKorak);
        Button btnMojBroj = findViewById(R.id.btnMojBroj);
        btnProfile = findViewById(R.id.btnProfileTop);
        Button btnKoZnaZna = findViewById(R.id.btnKoZnaZna);
        Button btnSpojnice = findViewById(R.id.btnSpojnice);
        Button btnAsocijacije = findViewById(R.id.btnAsocijacije);
        Button btnSkocko = findViewById(R.id.btnSkocko);
        btnNotifikacije = findViewById(R.id.btnNotifikacije);
        Button btnFullMatch = findViewById(R.id.btnFullMatch);
        Button btnChat = findViewById(R.id.btnChat);
        Button btnFriends = findViewById(R.id.btnFriends);
        Button btnRegions = findViewById(R.id.btnRegions);
        Button btnLeaderboards = findViewById(R.id.btnLeaderboards);
        Button btnTournament = findViewById(R.id.btnTournament);
        Button btnDailyMissions = findViewById(R.id.btnDailyMissions);

        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        btnReset.setOnClickListener(v -> startActivity(new Intent(this, ResetPasswordActivity.class)));
        btnKorak.setOnClickListener(v -> startGame(KorakPoKorakActivity.class));
        btnMojBroj.setOnClickListener(v -> startGame(MojBrojActivity.class));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnKoZnaZna.setOnClickListener(v -> startGame(KoZnaZnaActivity.class));
        btnSpojnice.setOnClickListener(v -> startGame(SpojniceActivity.class));
        btnAsocijacije.setOnClickListener(v -> startGame(AsocijacijeActivity.class));
        btnSkocko.setOnClickListener(v -> startGame(SkockoActivity.class));
        btnNotifikacije.setOnClickListener(v -> startActivity(new Intent(this, NotifikacijeActivity.class)));
        btnFullMatch.setOnClickListener(v -> startFullMatch());
        btnChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        btnFriends.setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));
        btnRegions.setOnClickListener(v -> startActivity(new Intent(this, RegionsActivity.class)));
        btnLeaderboards.setOnClickListener(v -> startActivity(new Intent(this, LeaderboardActivity.class)));
        btnTournament.setOnClickListener(v -> startActivity(new Intent(this, TournamentBracketActivity.class)));
        btnDailyMissions.setOnClickListener(v -> startActivity(new Intent(this, DailyMissionsActivity.class)));
        updateAccountUi();
        grantDailyTokens();
        ensureLeaderboardsAndRewards();
        startNotificationListener();
        startFriendlyInviteListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountUi();
        grantDailyTokens();
        checkPendingLeagueDialog();
        checkPendingReward();
        startFriendlyInviteListener();
    }

    private void grantDailyTokens() {
        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous() || userRepository == null || !userRepository.isReady()) {
            return;
        }
        userRepository.ensureLeagueConsistency(user.getUid())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return userRepository.grantDailyLeagueTokens(user.getUid());
                })
                .addOnSuccessListener(unused -> checkPendingLeagueDialog())
                .addOnFailureListener(e -> Log.e(TAG, "League consistency/daily grant failed", e));
    }

    private void checkPendingLeagueDialog() {
        if (leagueDialogShowing || userRepository == null || !userRepository.isReady()) {
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous()) {
            return;
        }
        userRepository.getUser(user.getUid())
                .addOnSuccessListener(doc -> {
                    if (doc == null || !Boolean.TRUE.equals(doc.getBoolean("pendingLeagueDialog"))) {
                        return;
                    }
                    leagueDialogShowing = true;
                    long oldLevel = longValue(doc.get("pendingLeagueOldLevel"));
                    long newLevel = longValue(doc.get("pendingLeagueNewLevel"));
                    String message = doc.getString("pendingLeagueMessage");
                    LeagueDefinition oldLeague = LeagueDefinition.byId(oldLevel);
                    LeagueDefinition newLeague = LeagueDefinition.byId(newLevel);
                    new AlertDialog.Builder(this)
                            .setTitle("Promena lige")
                            .setMessage((message == null ? "" : message)
                                    + "\n\n" + oldLeague.name + " -> " + newLeague.name)
                            .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                    userRepository.clearPendingLeagueDialog(user.getUid())
                                            .addOnCompleteListener(task -> leagueDialogShowing = false))
                            .setOnCancelListener(dialog -> {
                                userRepository.clearPendingLeagueDialog(user.getUid());
                                leagueDialogShowing = false;
                            })
                            .show();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Pending league dialog check failed", e));
    }

    private void ensureLeaderboardsAndRewards() {
        if (leaderboardRepository == null || !leaderboardRepository.isReady()) {
            return;
        }
        leaderboardRepository.ensureCycles()
                .addOnSuccessListener(unused -> checkPendingReward())
                .addOnFailureListener(e -> Log.e(TAG, "Leaderboard cycle ensure failed", e));
    }

    private void checkPendingReward() {
        if (leaderboardRepository == null || !leaderboardRepository.isReady()) {
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous()) {
            return;
        }
        leaderboardRepository.getPendingReward(user.getUid())
                .addOnSuccessListener(doc -> {
                    if (doc != null && Boolean.TRUE.equals(doc.getBoolean("pendingReward"))) {
                        Intent intent = new Intent(this, RewardActivity.class);
                        intent.putExtra(RewardActivity.EXTRA_CYCLE_TYPE, doc.getString("pendingRewardCycleType"));
                        intent.putExtra(RewardActivity.EXTRA_PLACEMENT, longValue(doc.get("pendingRewardPlacement")));
                        intent.putExtra(RewardActivity.EXTRA_TOKENS, longValue(doc.get("pendingRewardTokens")));
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Pending reward check failed", e));
    }

    private void updateAccountUi() {
        FirebaseUser user = authRepository.currentUser();
        boolean isLoggedIn = user != null && !user.isAnonymous();
        btnProfile.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        btnNotifikacije.setVisibility(View.VISIBLE);
        btnLogin.setVisibility(View.VISIBLE);
        btnRegister.setVisibility(View.VISIBLE);
        btnReset.setVisibility(View.VISIBLE);
        Log.d(TAG, "Main account UI isLoggedIn=" + isLoggedIn
                + ", hasFirebaseUser=" + (user != null)
                + ", anonymous=" + (user != null && user.isAnonymous()));
    }

    private void startGame(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void startFullMatch() {
        if (gameRepository == null || !gameRepository.isReady()) {
            android.widget.Toast.makeText(this, R.string.firebase_not_ready, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        String uid = user == null ? GuestSession.uid(this) : user.getUid();
        gameRepository.joinOrCreateFullMatch(uid)
                .addOnSuccessListener(gameId -> {
                    Intent intent = new Intent(this, KoZnaZnaActivity.class);
                    intent.putExtra(GameFlow.EXTRA_GAME_ID, gameId);
                    intent.putExtra(GameFlow.EXTRA_FULL_MATCH, true);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null
                            ? getString(R.string.firebase_not_ready)
                            : e.getMessage();
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    private void startNotificationListener() {
        if (notificationListener != null || notificationRepository == null || !notificationRepository.isReady()) {
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        String uid = user == null ? GuestSession.uid(this) : user.getUid();
        final boolean[] firstSnapshot = {true};
        notificationListener = notificationRepository.listenNotifications(uid, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Notification listener error", error);
                return;
            }
            if (snapshot == null) {
                return;
            }
            int unreadCount = 0;
            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                if (!Boolean.TRUE.equals(doc.getBoolean("read"))) {
                    unreadCount++;
                }
            }
            updateNotificationBadge(unreadCount);
            for (DocumentChange change : snapshot.getDocumentChanges()) {
                AppNotification notification = AppNotification.fromSnapshot(change.getDocument());
                if (firstSnapshot[0]) {
                    NotificationHelper.rememberDisplayed(notification.id);
                    continue;
                }
                if (change.getType() == DocumentChange.Type.ADDED && !notification.read
                        && NotificationHelper.markDisplayedIfNew(notification.id)) {
                    Log.d(TAG, "Show notification from MainActivity uid=" + uid + ", id=" + notification.id);
                    NotificationHelper.showNotification(this, notification);
                }
            }
            firstSnapshot[0] = false;
        });
    }

    private void startFriendlyInviteListener() {
        if (friendlyInviteListener != null || friendRepository == null || !friendRepository.isReady()) {
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous()) {
            return;
        }
        String uid = user.getUid();
        friendlyInviteListener = friendRepository.listenIncomingMatchInvites(uid, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Friendly invite listener error", error);
                return;
            }
            if (snapshot == null) {
                return;
            }
            for (DocumentChange change : snapshot.getDocumentChanges()) {
                if (change.getType() == DocumentChange.Type.ADDED) {
                    showFriendlyInviteDialog(change.getDocument());
                }
            }
        });
    }

    private void showFriendlyInviteDialog(com.google.firebase.firestore.DocumentSnapshot invite) {
        String inviteId = invite.getId();
        String fromUsername = invite.getString("fromUsername");
        new AlertDialog.Builder(this)
                .setTitle("Poziv za prijateljsku partiju")
                .setMessage((fromUsername == null ? "Prijatelj" : fromUsername)
                        + " vas poziva na prijateljsku partiju.")
                .setPositiveButton("Prihvati", (dialog, which) -> friendRepository.acceptInvite(inviteId)
                        .addOnSuccessListener(gameId -> {
                            Intent intent = new Intent(this, KoZnaZnaActivity.class);
                            intent.putExtra(GameFlow.EXTRA_GAME_ID, gameId);
                            intent.putExtra(GameFlow.EXTRA_FULL_MATCH, true);
                            startActivity(intent);
                        })
                        .addOnFailureListener(e -> android.widget.Toast.makeText(this,
                                e == null || e.getMessage() == null ? "Poziv nije prihvacen" : e.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Odbij", (dialog, which) -> friendRepository.declineInvite(inviteId))
                .show();
    }

    private void updateNotificationBadge(int unreadCount) {
        if (btnNotifikacije == null) {
            return;
        }
        String base = getString(R.string.go_notifikacije);
        btnNotifikacije.setText(unreadCount > 0 ? base + " (" + unreadCount + ")" : base);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    @Override
    protected void onDestroy() {
        if (notificationListener != null) {
            notificationListener.remove();
        }
        if (friendlyInviteListener != null) {
            friendlyInviteListener.remove();
        }
        super.onDestroy();
    }
}
