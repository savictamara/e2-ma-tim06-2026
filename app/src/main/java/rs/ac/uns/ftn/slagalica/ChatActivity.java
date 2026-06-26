package rs.ac.uns.ftn.slagalica;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rs.ac.uns.ftn.slagalica.data.repository.ChatRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.model.ChatMessage;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class ChatActivity extends AppCompatActivity {
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private ChatRepository chatRepository;
    private ListenerRegistration messageListener;
    private String uid = "";
    private String username = "";
    private String region = "";

    private TextView tvStatus;
    private LinearLayout messagesContainer;
    private ScrollView chatScroll;
    private EditText etMessage;
    private Button btnSend;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        tvStatus = findViewById(R.id.tvChatStatus);
        messagesContainer = findViewById(R.id.messagesContainer);
        chatScroll = findViewById(R.id.chatScroll);
        etMessage = findViewById(R.id.etChatMessage);
        btnSend = findViewById(R.id.btnSendChat);

        boolean firebaseReady = FirebaseInitializer.ensure(this);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        chatRepository = new ChatRepository(this);
        btnSend.setOnClickListener(v -> sendMessage());

        FirebaseUser user = authRepository.currentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        if (!firebaseReady || !userRepository.isReady() || !chatRepository.isReady()) {
            setUnavailable(getString(R.string.firebase_not_ready));
            return;
        }
        loadCurrentUser();
    }

    private void loadCurrentUser() {
        userRepository.getUser(uid)
                .addOnSuccessListener(user -> {
                    username = displayName(user);
                    region = value(user.getString("region")).trim();
                    if (region.isEmpty()) {
                        setUnavailable(getString(R.string.chat_missing_region));
                        return;
                    }
                    tvStatus.setText(getString(R.string.chat_region_status, region));
                    btnSend.setEnabled(true);
                    listenMessages();
                })
                .addOnFailureListener(e -> setUnavailable(e.getMessage()));
    }

    private void listenMessages() {
        if (messageListener != null) {
            messageListener.remove();
        }
        messageListener = chatRepository.listenMessages(region, (snapshot, error) -> {
            if (error != null) {
                show(error.getMessage());
                return;
            }
            if (snapshot == null) {
                return;
            }
            messagesContainer.removeAllViews();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                addMessage(ChatMessage.fromSnapshot(doc));
            }
            scrollToBottom();
        });
    }

    private void addMessage(ChatMessage message) {
        boolean mine = uid.equals(message.senderId);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowParams);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(mine ? R.drawable.bg_chat_mine : R.drawable.bg_chat_other);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.74f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(bubbleParams);

        TextView meta = new TextView(this);
        meta.setText(message.senderName + " | " + formatDate(message.timestamp));
        meta.setTextColor(mine ? getColor(R.color.white) : getColor(R.color.text_muted));
        meta.setTextSize(12);
        meta.setTypeface(Typeface.DEFAULT_BOLD);

        TextView text = new TextView(this);
        text.setText(message.text);
        text.setTextColor(mine ? getColor(R.color.white) : getColor(R.color.text_main));
        text.setTextSize(16);
        text.setPadding(0, dp(4), 0, 0);

        bubble.addView(meta);
        bubble.addView(text);
        row.addView(bubble);
        messagesContainer.addView(row);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (region.isEmpty()) {
            show(getString(R.string.chat_missing_region));
            return;
        }
        if (text.isEmpty()) {
            show(getString(R.string.chat_empty_message));
            return;
        }
        btnSend.setEnabled(false);
        chatRepository.sendMessage(uid, username, region, text)
                .addOnSuccessListener(ignored -> {
                    etMessage.setText("");
                    btnSend.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    show(e.getMessage());
                });
    }

    private String displayName(DocumentSnapshot user) {
        if (user != null && user.exists()) {
            String name = user.getString("username");
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
            String email = user.getString("email");
            if (email != null && !email.trim().isEmpty()) {
                return email.trim();
            }
        }
        return "Igrac";
    }

    private String formatDate(Timestamp timestamp) {
        Date date = timestamp == null ? new Date() : timestamp.toDate();
        return dateFormat.format(date);
    }

    private void setUnavailable(String message) {
        tvStatus.setText(message == null ? getString(R.string.firebase_not_ready) : message);
        btnSend.setEnabled(false);
        etMessage.setEnabled(false);
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? getString(R.string.firebase_not_ready) : message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (messageListener != null) {
            messageListener.remove();
        }
        super.onDestroy();
    }
}
