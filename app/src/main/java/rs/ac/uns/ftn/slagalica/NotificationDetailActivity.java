package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.DateFormat;
import java.util.Locale;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.NotificationRepository;
import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class NotificationDetailActivity extends AppCompatActivity {
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    private static final String TAG = "NotificationDetailActivity";

    private FirebaseAuthRepository authRepository;
    private NotificationRepository notificationRepository;
    private String uid = "";
    private String notificationId = "";

    private TextView tvTitle;
    private TextView tvType;
    private TextView tvMessage;
    private TextView tvTime;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        tvTitle = findViewById(R.id.tvNotificationTitle);
        tvType = findViewById(R.id.tvNotificationType);
        tvMessage = findViewById(R.id.tvNotificationMessage);
        tvTime = findViewById(R.id.tvNotificationTime);
        tvStatus = findViewById(R.id.tvNotificationReadStatus);
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        authRepository = new FirebaseAuthRepository(this);
        notificationRepository = new NotificationRepository(this);
        boolean firebaseReady = FirebaseInitializer.ensure(this);
        if (!firebaseReady || !notificationRepository.isReady()) {
            show("Firebase nije inicijalizovan");
            finish();
            return;
        }

        FirebaseUser user = authRepository.currentUser();
        if (user == null) {
            show("Morate biti ulogovani.");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        uid = user.getUid();
        notificationId = getIntent().getStringExtra(EXTRA_NOTIFICATION_ID);
        if (notificationId == null || notificationId.isEmpty()) {
            showNotFound();
            return;
        }

        loadNotification();
    }

    private void loadNotification() {
        notificationRepository.getNotification(uid, notificationId)
                .addOnSuccessListener(this::showNotification)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Read notification failed", e);
                    show("Greška pri učitavanju obaveštenja");
                });
    }

    private void showNotification(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            showNotFound();
            return;
        }

        AppNotification notification = AppNotification.fromSnapshot(snapshot);
        tvTitle.setText(notification.title);
        tvType.setText(typeLabel(notification.type));
        tvMessage.setText(notification.message);
        tvMessage.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        tvTime.setText(formatTime(notification));
        tvStatus.setText("Status: " + (notification.read ? "Pročitano" : "Nepročitano"));

        if (!notification.read) {
            notificationRepository.markRead(uid, notification.id)
                    .addOnSuccessListener(unused -> tvStatus.setText("Status: Pročitano"))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Mark notification read from detail failed", e);
                        show("Greška pri označavanju kao pročitano");
                    });
        }
    }

    private void showNotFound() {
        tvTitle.setText("Obaveštenje nije pronađeno.");
        tvType.setText("");
        tvMessage.setText("Obaveštenje nije pronađeno.");
        tvTime.setText("");
        tvStatus.setText("");
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

    private void show(String message) {
        Toast.makeText(this, message == null ? "Greška" : message, Toast.LENGTH_SHORT).show();
    }
}
