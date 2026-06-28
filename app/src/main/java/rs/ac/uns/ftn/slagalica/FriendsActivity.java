package rs.ac.uns.ftn.slagalica;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.ArrayList;
import java.util.List;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FriendRepository;
import rs.ac.uns.ftn.slagalica.domain.model.FriendItem;
import rs.ac.uns.ftn.slagalica.util.GameFlow;

public class FriendsActivity extends AppCompatActivity {
    private FirebaseAuthRepository authRepository;
    private FriendRepository friendRepository;
    private String uid = "";
    private LinearLayout friendsContainer;
    private TextView tvStatus;
    private EditText etSearch;
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private ListenerRegistration outgoingInviteListener;
    private AlertDialog outgoingDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        authRepository = new FirebaseAuthRepository(this);
        friendRepository = new FriendRepository(this);
        friendsContainer = findViewById(R.id.friendsContainer);
        tvStatus = findViewById(R.id.tvFriendsStatus);
        etSearch = findViewById(R.id.etFriendSearch);
        Button btnSearch = findViewById(R.id.btnFriendSearch);
        Button btnMyQr = findViewById(R.id.btnMyQr);
        Button btnScanQr = findViewById(R.id.btnScanQr);

        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = user.getUid();
        btnSearch.setOnClickListener(v -> searchAndRequest());
        btnMyQr.setOnClickListener(v -> showMyQr());
        btnScanQr.setOnClickListener(v -> startActivity(new Intent(this, QrScannerActivity.class)));
        loadFriends();
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenIncoming();
    }

    private void listenIncoming() {
        clearListeners();
        ListenerRegistration friendListener = friendRepository.listenIncomingFriendRequests(uid, (snapshot, error) -> {
            if (snapshot == null || error != null) return;
            for (DocumentChange change : snapshot.getDocumentChanges()) {
                if (change.getType() == DocumentChange.Type.ADDED) showFriendRequest(change.getDocument());
            }
        });
        if (friendListener != null) listeners.add(friendListener);
        ListenerRegistration inviteListener = friendRepository.listenIncomingMatchInvites(uid, (snapshot, error) -> {
            if (snapshot == null || error != null) return;
            for (DocumentChange change : snapshot.getDocumentChanges()) {
                if (change.getType() == DocumentChange.Type.ADDED) showIncomingInvite(change.getDocument());
            }
        });
        if (inviteListener != null) listeners.add(inviteListener);
    }

    private void loadFriends() {
        friendsContainer.removeAllViews();
        friendRepository.getFriends(uid)
                .addOnSuccessListener(snapshot -> {
                    tvStatus.setText("Prijatelja: " + snapshot.size());
                    for (DocumentSnapshot friend : snapshot.getDocuments()) {
                        friendRepository.getUser(friend.getId())
                                .addOnSuccessListener(user -> {
                                    if (user.exists()) friendsContainer.addView(friendRow(toFriend(user)));
                                });
                    }
                })
                .addOnFailureListener(e -> show(e.getMessage()));
    }

    private View friendRow(FriendItem friend) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_friend, friendsContainer, false);
        ImageView avatar = row.findViewById(R.id.ivFriendAvatar);
        TextView name = row.findViewById(R.id.tvFriendName);
        TextView info = row.findViewById(R.id.tvFriendInfo);
        Button challenge = row.findViewById(R.id.btnChallengeFriend);
        avatar.setImageResource(drawableForId(friend.avatarId));
        name.setText(friend.username);
        info.setText("Rang: " + (friend.monthlyRank > 0 ? friend.monthlyRank : "-")
                + " | Zvezde: " + friend.stars
                + " | " + friend.leagueName
                + " | " + (friend.online ? "Online" : "Offline"));
        challenge.setText("Pozovi");
        if (friend.uid.equals(uid)) {
            challenge.setEnabled(false);
            challenge.setText("Vi");
        } else if (friend.inGame || !isBlank(friend.activeGameId)) {
            challenge.setEnabled(false);
            challenge.setText("U partiji");
        } else {
            challenge.setOnClickListener(v -> sendMatchInvite(friend));
        }
        return row;
    }

    private void searchAndRequest() {
        String username = etSearch.getText().toString().trim();
        friendRepository.searchByUsername(username)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        show("Korisnik nije pronadjen");
                        return;
                    }
                    DocumentSnapshot target = snapshot.getDocuments().get(0);
                    friendRepository.sendFriendRequest(uid, target.getId())
                            .addOnSuccessListener(id -> show("Zahtev poslat"))
                            .addOnFailureListener(e -> show(e.getMessage()));
                })
                .addOnFailureListener(e -> show(e.getMessage()));
    }

    private void showFriendRequest(DocumentSnapshot request) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_incoming_friend_invite, null);
        ((TextView) view.findViewById(R.id.tvIncomingFriendInvite))
                .setText(request.getString("fromUsername") + " zeli da vas doda za prijatelja.");
        new AlertDialog.Builder(this)
                .setTitle("Zahtev za prijateljstvo")
                .setView(view)
                .setPositiveButton("Prihvati", (d, w) -> friendRepository.respondFriendRequest(request.getId(), true)
                        .addOnSuccessListener(x -> loadFriends()))
                .setNegativeButton("Odbij", (d, w) -> friendRepository.respondFriendRequest(request.getId(), false))
                .show();
    }

    private void sendMatchInvite(FriendItem friend) {
        friendRepository.sendFriendlyInvite(uid, friend.uid)
                .addOnSuccessListener(inviteId -> {
                    show("Poziv je poslat");
                    showOutgoingInvite(inviteId);
                    loadFriends();
                })
                .addOnFailureListener(e -> show(e.getMessage()));
    }

    private void showOutgoingInvite(String inviteId) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_outgoing_match_invite, null);
        outgoingDialog = new AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setView(view)
                .setNegativeButton("Otkazi", (d, w) -> friendRepository.cancelInvite(inviteId))
                .show();
        outgoingInviteListener = friendRepository.listenInvite(inviteId, (snapshot, error) -> {
            if (snapshot == null || !snapshot.exists()) return;
            String status = snapshot.getString("status");
            if ("ACCEPTED".equals(status)) {
                closeOutgoing();
                openGame(snapshot.getString("gameId"));
            } else if ("DECLINED".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status)) {
                closeOutgoing();
                show("EXPIRED".equals(status) ? "Zahtev je istekao." : "Poziv nije prihvacen.");
            }
        });
        handler.postDelayed(() -> friendRepository.expireInvite(inviteId), 10_000L);
    }

    private void showIncomingInvite(DocumentSnapshot invite) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_incoming_match_invite, null);
        ((TextView) view.findViewById(R.id.tvIncomingInvite))
                .setText(invite.getString("fromUsername") + " vas je pozvao na prijateljsku partiju.");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setView(view)
                .setPositiveButton("Prihvati", null)
                .setNegativeButton("Odbij", (d, w) -> friendRepository.declineInvite(invite.getId()))
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                friendRepository.acceptInvite(invite.getId())
                        .addOnSuccessListener(gameId -> {
                            dialog.dismiss();
                            openGame(gameId);
                        })
                        .addOnFailureListener(e -> {
                            dialog.dismiss();
                            show(e.getMessage());
                        }));
        handler.postDelayed(() -> {
            if (dialog.isShowing()) dialog.dismiss();
            friendRepository.expireInvite(invite.getId());
        }, 10_000L);
    }

    private void showMyQr() {
        String payload = "{\"type\":\"friend_add\",\"uid\":\"" + uid + "\"}";
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_my_qr, null);
        ((ImageView) view.findViewById(R.id.ivMyQr)).setImageBitmap(simpleQr(payload, 220));
        ((TextView) view.findViewById(R.id.tvQrPayload)).setText(payload);
        new AlertDialog.Builder(this).setTitle("Moj QR kod").setView(view).setPositiveButton("OK", null).show();
    }

    private Bitmap simpleQr(String value, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
        }
    }

    private FriendItem toFriend(DocumentSnapshot user) {
        FriendItem item = new FriendItem();
        item.uid = user.getId();
        item.username = firstNonEmpty(user.getString("username"), user.getString("email"), user.getId());
        item.avatarId = firstNonEmpty(user.getString("avatarId"), "star");
        item.stars = longValue(user.get("stars"));
        item.league = longValue(user.get("league"));
        item.leagueName = firstNonEmpty(user.getString("leagueName"), "");
        item.leagueIconName = firstNonEmpty(user.getString("leagueIconName"), user.getString("leagueIcon"));
        item.monthlyRank = longValue(user.get("monthlyRank"));
        item.online = Boolean.TRUE.equals(user.getBoolean("online"));
        item.inGame = Boolean.TRUE.equals(user.getBoolean("inGame"));
        item.activeGameId = firstNonEmpty(user.getString("activeGameId"), user.getString("currentGameId"));
        return item;
    }

    private void openGame(String gameId) {
        if (isBlank(gameId)) return;
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra(GameFlow.EXTRA_GAME_ID, gameId);
        intent.putExtra(GameFlow.EXTRA_FULL_MATCH, true);
        startActivity(intent);
    }

    private void closeOutgoing() {
        if (outgoingInviteListener != null) outgoingInviteListener.remove();
        outgoingInviteListener = null;
        if (outgoingDialog != null && outgoingDialog.isShowing()) outgoingDialog.dismiss();
    }

    private int drawableForId(String id) {
        if ("heart".equals(id)) return R.drawable.heart_2;
        if ("circle".equals(id)) return R.drawable.circle;
        if ("triangle".equals(id)) return R.drawable.triangle;
        if ("skocko".equals(id)) return R.drawable.skocko;
        return R.drawable.star;
    }

    private long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private String firstNonEmpty(String... values) {
        for (String value : values) if (!isBlank(value)) return value.trim();
        return "";
    }
    private void show(String message) { Toast.makeText(this, message == null ? "Greska" : message, Toast.LENGTH_SHORT).show(); }
    private void clearListeners() {
        for (ListenerRegistration listener : listeners) listener.remove();
        listeners.clear();
    }

    @Override
    protected void onStop() {
        clearListeners();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        closeOutgoing();
        super.onDestroy();
    }
}
