package rs.ac.uns.ftn.slagalica;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.NotificationRepository;
import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.NotificationHelper;

public class NotifikacijeActivity extends AppCompatActivity {
    private static final String TAG = "NotifikacijeActivity";
    private static final int REQUEST_POST_NOTIFICATIONS = 42;

    private FirebaseAuthRepository authRepository;
    private NotificationRepository notificationRepository;
    private ListenerRegistration notificationListener;
    private String uid = "";
    private final List<AppNotification> notifications = new ArrayList<>();
    private boolean firstSnapshotLoaded = false;
    private Filter filter = Filter.ALL;

    private LinearLayout notificationsContainer;
    private TextView tvStatus;
    private Button btnFilterAll;
    private Button btnFilterUnread;
    private Button btnFilterRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifikacije);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        Log.d(TAG, "Firebase ensure from NotifikacijeActivity=" + firebaseReady);
        NotificationHelper.createNotificationChannels(this);
        requestNotificationPermissionIfNeeded();

        authRepository = new FirebaseAuthRepository(this);
        notificationRepository = new NotificationRepository(this);
        notificationsContainer = findViewById(R.id.notificationsContainer);
        tvStatus = findViewById(R.id.tvStatus);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);
        btnFilterRead = findViewById(R.id.btnFilterRead);
        Button btnTestNotifications = findViewById(R.id.btnTestNotifications);
        Button btnMarkAll = findViewById(R.id.btnMarkAll);

        btnFilterAll.setOnClickListener(v -> setFilter(Filter.ALL));
        btnFilterUnread.setOnClickListener(v -> setFilter(Filter.UNREAD));
        btnFilterRead.setOnClickListener(v -> setFilter(Filter.READ));
        btnMarkAll.setOnClickListener(v -> markAllRead());
        btnTestNotifications.setOnClickListener(v -> sendTestNotifications());

        FirebaseUser user = authRepository.currentUser();
        if (!firebaseReady || !notificationRepository.isReady()) {
            setStatus("Firebase nije inicijalizovan");
            return;
        }
        if (user == null) {
            show("Morate biti ulogovani.");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = user.getUid();
        Log.d(TAG, "currentUserUid=" + uid);
        listenNotifications();
    }

    private void listenNotifications() {
        notificationListener = notificationRepository.listenNotifications(uid, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Notifications snapshot error", error);
                show(error.getMessage());
                return;
            }
            if (snapshot == null) {
                return;
            }
            notifications.clear();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                notifications.add(AppNotification.fromSnapshot(doc));
            }
            for (DocumentChange change : snapshot.getDocumentChanges()) {
                AppNotification notification = AppNotification.fromSnapshot(change.getDocument());
                if (!firstSnapshotLoaded) {
                    NotificationHelper.rememberDisplayed(notification.id);
                    continue;
                }
                if (change.getType() == DocumentChange.Type.ADDED && !notification.read
                        && NotificationHelper.markDisplayedIfNew(notification.id)) {
                    Log.d(TAG, "Show local system notification uid=" + uid + ", id=" + notification.id
                            + ", type=" + notification.type + ", title=" + notification.title);
                    NotificationHelper.showNotification(this, notification);
                }
            }
            firstSnapshotLoaded = true;
            renderNotifications();
        });
    }

    private void renderNotifications() {
        notificationsContainer.removeAllViews();
        int shown = 0;
        for (AppNotification notification : notifications) {
            if (!matchesFilter(notification)) {
                continue;
            }
            notificationsContainer.addView(createNotificationCard(notification));
            shown++;
        }
        updateFilterButtons();
        setStatus("Prikazano: " + shown + " / ukupno: " + notifications.size());
    }

    private View createNotificationCard(AppNotification notification) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int padding = dp(12);
        card.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(cardParams);

        TextView type = text(typeLabel(notification.type) + " • " + (notification.read ? "Pročitano" : "Nepročitano"), 13, false);
        type.setTextColor(getColor(notification.read ? R.color.text_muted : R.color.pink_dark));
        card.addView(type);

        TextView title = text(notification.title, 17, true);
        title.setTextColor(getColor(R.color.text_main));
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title);

        TextView message = text(notification.message, 14, false);
        message.setTextColor(getColor(R.color.text_main));
        message.setPadding(0, dp(4), 0, 0);
        card.addView(message);

        TextView time = text(formatTime(notification), 12, false);
        time.setTextColor(getColor(R.color.text_muted));
        time.setPadding(0, dp(6), 0, 0);
        card.addView(time);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        Button read = actionButton("Pročitano", R.drawable.bg_button_secondary, R.color.text_main);
        Button open = actionButton("Otvori", R.drawable.bg_button_primary, R.color.white);
        read.setEnabled(!notification.read);
        read.setOnClickListener(v -> markRead(notification));
        open.setOnClickListener(v -> openNotification(notification));
        actions.addView(read);
        actions.addView(open);
        card.addView(actions);
        return card;
    }

    private Button actionButton(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(getColor(textColor));
        button.setBackgroundResource(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private void setFilter(Filter nextFilter) {
        filter = nextFilter;
        renderNotifications();
    }

    private boolean matchesFilter(AppNotification notification) {
        if (filter == Filter.UNREAD) {
            return !notification.read;
        }
        if (filter == Filter.READ) {
            return notification.read;
        }
        return true;
    }

    private void updateFilterButtons() {
        btnFilterAll.setEnabled(filter != Filter.ALL);
        btnFilterUnread.setEnabled(filter != Filter.UNREAD);
        btnFilterRead.setEnabled(filter != Filter.READ);
    }

    private void markRead(AppNotification notification) {
        notificationRepository.markRead(uid, notification.id)
                .addOnSuccessListener(unused -> setStatus("Notifikacija je označena kao pročitana"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Mark notification read failed", e);
                    show(e.getMessage());
                });
    }

    private void markAllRead() {
        notificationRepository.markAllRead(uid)
                .addOnSuccessListener(unused -> setStatus("Sve notifikacije su označene kao pročitane"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Mark all notifications read failed", e);
                    show(e.getMessage());
                });
    }

    private void openNotification(AppNotification notification) {
        notificationRepository.markRead(uid, notification.id)
                .addOnSuccessListener(unused -> {
                    Intent intent = new Intent(this, NotificationDetailActivity.class);
                    intent.putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION_ID, notification.id);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Open notification mark read failed", e);
                    show("Greška pri označavanju kao pročitano");
                });
    }

    private Class<?> targetActivity(String targetScreen) {
        if (targetScreen == null) {
            return null;
        }
        String normalized = targetScreen.trim().toUpperCase(Locale.ROOT);
        if ("PROFILE".equals(normalized)) return ProfileActivity.class;
        if ("NOTIFICATIONS".equals(normalized)) return NotifikacijeActivity.class;
        if ("SKOCKO".equals(normalized)) return SkockoActivity.class;
        if ("KORAK_PO_KORAK".equals(normalized)) return KorakPoKorakActivity.class;
        if ("MOJ_BROJ".equals(normalized)) return MojBrojActivity.class;
        if ("KO_ZNA_ZNA".equals(normalized)) return KoZnaZnaActivity.class;
        if ("SPOJNICE".equals(normalized)) return SpojniceActivity.class;
        if ("ASOCIJACIJE".equals(normalized)) return AsocijacijeActivity.class;
        return null;
    }

    private void sendTestNotifications() {
        notificationRepository.createTestNotifications(uid)
                .addOnSuccessListener(unused -> setStatus("Test notifikacije su poslate"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Create test notifications failed", e);
                    show(e.getMessage());
                });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private String typeLabel(String type) {
        if ("CHAT".equals(type)) return "Čet";
        if ("RANKING".equals(type)) return "Rang lista";
        if ("REWARD".equals(type)) return "Nagrada";
        if ("FRIEND_REQUEST".equals(type)) return "Prijateljstvo";
        if ("LEAGUE".equals(type)) return "Liga";
        return "Ostalo";
    }

    private String formatTime(AppNotification notification) {
        if (notification.createdAt == null) {
            return "Vreme se upisuje";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(notification.createdAt.toDate());
    }

    private void setStatus(String status) {
        tvStatus.setText(status);
        Log.d(TAG, "Status=" + status + ", uid=" + uid + ", filter=" + filter);
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? "Greška" : message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (notificationListener != null) {
            notificationListener.remove();
        }
        super.onDestroy();
    }

    private enum Filter {
        ALL, UNREAD, READ
    }
}
