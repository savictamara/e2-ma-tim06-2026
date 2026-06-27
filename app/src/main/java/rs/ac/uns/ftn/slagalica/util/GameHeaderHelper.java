package rs.ac.uns.ftn.slagalica.util;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;

import rs.ac.uns.ftn.slagalica.R;

public class GameHeaderHelper {
    private static final String TAG = "GameHeaderHelper";

    private final TextView tvTitle;
    private final TextView tvPlayer1;
    private final TextView tvPlayer2;
    private final TextView tvScore;
    private final TextView tvStatus;
    private final FirebaseFirestore db;

    private String player1DisplayName = "Igrač 1";
    private String player2DisplayName = "Igrač 2";
    private String lastPlayer1Input = "";
    private String lastPlayer2Input = "";
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean challengeMode = false;
    private int challengeIndex = 0;
    private int challengeTotal = 6;

    public GameHeaderHelper(Context context, View root) {
        tvTitle = root.findViewById(R.id.tvHeaderGameTitle);
        tvPlayer1 = root.findViewById(R.id.tvHeaderPlayer1);
        tvPlayer2 = root.findViewById(R.id.tvHeaderPlayer2);
        tvScore = root.findViewById(R.id.tvHeaderScore);
        tvStatus = root.findViewById(R.id.tvHeaderStatus);
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Header username lookup disabled", e);
        }
        db = instance;
        renderPlayers();
    }

    public void updateGameTitle(String title) {
        if (tvTitle != null) {
            String clean = title == null ? "" : title;
            tvTitle.setText(challengeMode ? ("Izazov - " + clean).toUpperCase() : clean.toUpperCase());
        }
    }

    public void setChallengeMode(boolean enabled, int gameIndex, int totalGames) {
        challengeMode = enabled;
        challengeIndex = Math.max(1, gameIndex);
        challengeTotal = Math.max(1, totalGames);
        renderPlayers();
        if (enabled && tvStatus != null) {
            tvStatus.setText("Samostalna partija - Igra " + challengeIndex + "/" + challengeTotal);
        }
    }

    public void updatePlayers(String player1Name, int player1Score, String player2Name, int player2Score) {
        this.player1Score = player1Score;
        this.player2Score = player2Score;
        if (!same(lastPlayer1Input, player1Name)) {
            lastPlayer1Input = value(player1Name);
            player1DisplayName = fallbackName(lastPlayer1Input, "Igrač 1");
            loadUsername(lastPlayer1Input, true);
        }
        if (!same(lastPlayer2Input, player2Name)) {
            lastPlayer2Input = value(player2Name);
            player2DisplayName = fallbackName(lastPlayer2Input, "Igrač 2");
            loadUsername(lastPlayer2Input, false);
        }
        renderPlayers();
    }

    public void updateStatus(String status) {
        if (tvStatus != null) {
            tvStatus.setText(status == null ? "" : status);
        }
    }

    private void loadUsername(String uidOrName, boolean firstPlayer) {
        if (db == null || uidOrName == null || uidOrName.trim().isEmpty() || !looksLikeUid(uidOrName)) {
            renderPlayers();
            return;
        }
        db.collection("users").document(uidOrName).get()
                .addOnSuccessListener(document -> {
                    String username = document == null ? "" : document.getString("username");
                    if (username != null && !username.trim().isEmpty()) {
                        if (firstPlayer && same(lastPlayer1Input, uidOrName)) {
                            player1DisplayName = username.trim();
                        } else if (!firstPlayer && same(lastPlayer2Input, uidOrName)) {
                            player2DisplayName = username.trim();
                        }
                        renderPlayers();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Header username lookup failed", e));
    }

    private void renderPlayers() {
        if (challengeMode) {
            if (tvPlayer1 != null) {
                tvPlayer1.setText("Trenutni rezultat");
            }
            if (tvPlayer2 != null) {
                tvPlayer2.setVisibility(View.GONE);
            }
            if (tvScore != null) {
                tvScore.setText("Ukupan rezultat: " + player1Score);
            }
            return;
        }
        if (tvPlayer1 != null) {
            tvPlayer1.setVisibility(View.VISIBLE);
            tvPlayer1.setText(player1DisplayName);
        }
        if (tvPlayer2 != null) {
            tvPlayer2.setVisibility(View.VISIBLE);
            tvPlayer2.setText(player2DisplayName);
        }
        if (tvScore != null) {
            tvScore.setText(player1Score + " : " + player2Score);
        }
    }

    private String fallbackName(String input, String fallback) {
        if (input == null || input.trim().isEmpty() || looksLikeUid(input)) {
            return fallback;
        }
        return input.trim();
    }

    private boolean looksLikeUid(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("guest_") || trimmed.length() >= 20;
    }

    private boolean same(String left, String right) {
        return value(left).equals(value(right));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
