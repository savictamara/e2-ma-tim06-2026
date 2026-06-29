package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.GameRepository;
import rs.ac.uns.ftn.slagalica.data.repository.TournamentRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;
import rs.ac.uns.ftn.slagalica.util.GameFlow;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class TournamentBracketActivity extends AppCompatActivity {
    private static final String TAG = "TournamentDebug";
    private static final String PERSIST_TAG = "TournamentPersistenceDebug";
    private static final String DISPLAY_TAG = "TournamentDisplayDebug";
    private TournamentRepository tournamentRepository;
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private ListenerRegistration listener;
    private ListenerRegistration queryListener;
    private String uid = "";
    private String tournamentId = "";
    private DocumentSnapshot lastTournament;
    private final Map<String, PlayerView> players = new HashMap<>();
    private TextView tvStatus;
    private TextView tvPlayers;
    private TextView tvSf1;
    private TextView tvSf2;
    private TextView tvFinal;
    private TextView tvWinner;
    private Button btnJoin;
    private Button btnPlay;
    private Button btnReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_bracket);
        FirebaseInitializer.ensure(this);
        tournamentRepository = new TournamentRepository(this);
        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        FirebaseUser user = authRepository.currentUser();
        uid = user == null ? GuestSession.uid(this) : user.getUid();
        tournamentId = value(getIntent().getStringExtra("tournamentId"));
        Log.d(PERSIST_TAG, "screen opened current uid=" + uid);
        tvStatus = findViewById(R.id.tvTournamentStatus);
        tvPlayers = findViewById(R.id.tvTournamentPlayers);
        tvSf1 = findViewById(R.id.tvTournamentSf1);
        tvSf2 = findViewById(R.id.tvTournamentSf2);
        tvFinal = findViewById(R.id.tvTournamentFinal);
        tvWinner = findViewById(R.id.tvTournamentWinner);
        btnJoin = findViewById(R.id.btnJoinTournament);
        btnPlay = findViewById(R.id.btnPlayTournamentMatch);
        btnReset = findViewById(R.id.btnResetTournament);
        btnJoin.setOnClickListener(v -> joinTournament());
        btnPlay.setOnClickListener(v -> playCurrentMatch());
        btnReset.setOnClickListener(v -> resetStuckTournament());
        btnPlay.setEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        discoverTournament();
    }

    private void discoverTournament() {
        if (tournamentRepository == null || !tournamentRepository.isReady()) {
            showEmptyState("Firebase nije spreman.");
            return;
        }
        removeListeners();
        tvStatus.setText("Ucitavanje turnira...");
        if (!tournamentId.isEmpty()) {
            Log.d(PERSIST_TAG, "explicit tournamentId from intent=" + tournamentId);
            attachTournamentListener(tournamentId);
            return;
        }
        tournamentRepository.findActiveTournamentForUser(uid)
                .addOnSuccessListener(active -> {
                    if (active != null && active.exists()) {
                        tournamentId = active.getId();
                        Log.d(PERSIST_TAG, "active tournament found tournamentId=" + tournamentId);
                        attachTournamentListener(tournamentId);
                    } else {
                        Log.d(PERSIST_TAG, "active tournament not found uid=" + uid);
                        attachJoinableListener();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(PERSIST_TAG, "active tournament lookup failed", e);
                    attachJoinableListener();
                });
    }

    private void joinTournament() {
        if (tournamentRepository == null || !tournamentRepository.isReady()) {
            show("Firebase nije spreman.");
            return;
        }
        tvStatus.setText("Prijava na turnir...");
        btnJoin.setEnabled(false);
        tournamentRepository.joinTournament(uid)
                .addOnSuccessListener(id -> {
                    tournamentId = id;
                    tvStatus.setText("Prijavljeni ste. Ceka se jos igraca.");
                    attachTournamentListener(id);
                })
                .addOnFailureListener(e -> {
                    btnJoin.setEnabled(true);
                    show(e == null || e.getMessage() == null ? "Turnir nije pokrenut" : e.getMessage());
                });
    }

    private void attachTournamentListener(String id) {
        removeListeners();
        tournamentId = id;
        listener = tournamentRepository.listenTournament(tournamentId, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Tournament listener failed", error);
                tvStatus.setText("Turnir trenutno nije dostupan.");
                return;
            }
            if (snapshot == null || !snapshot.exists()) return;
            Log.d(PERSIST_TAG, "tournamentId listened=" + snapshot.getId()
                    + ", snapshot status/currentPlayers/playerIds.size="
                    + snapshot.getString("status") + "/"
                    + number(snapshot.get("currentPlayers")) + "/"
                    + listSize(snapshot.get("playerIds")));
            render(snapshot);
        });
    }

    private void attachJoinableListener() {
        removeListeners();
        queryListener = tournamentRepository.listenJoinableTournament((snapshot, error) -> {
            if (error != null) {
                Log.e(PERSIST_TAG, "joinable tournament listener failed", error);
                showEmptyState("Turniri trenutno nisu dostupni.");
                return;
            }
            DocumentSnapshot joinable = chooseJoinable(snapshot);
            if (joinable == null) {
                Log.d(PERSIST_TAG, "joinable tournament not found");
                showEmptyState("Nema aktivnog turnira. Pridruzite se da kreirate novi.");
            } else {
                Log.d(PERSIST_TAG, "joinable tournament found tournamentId=" + joinable.getId());
                render(joinable);
            }
        });
    }

    private void render(DocumentSnapshot t) {
        lastTournament = t;
        loadMissingPlayers(t);
        String status = value(t.getString("status"));
        long current = number(t.get("currentPlayers"));
        boolean joined = containsUid(t.get("playerIds"), uid);
        tvStatus.setText(statusText(status, current, joined));
        tvPlayers.setText("Igraci: " + current + "/4\n" + join((List<?>) t.get("playerIds")));
        tvSf1.setText("SF1 - " + value(t.getString("sf1Status"), TournamentRepository.WAITING)
                + "\n" + playerLabel(t.getString("sf1Player1Uid"))
                + "\nvs\n" + playerLabel(t.getString("sf1Player2Uid"))
                + "\nPobednik: " + playerLabel(t.getString("sf1WinnerUid")));
        tvSf2.setText("SF2 - " + value(t.getString("sf2Status"), TournamentRepository.WAITING)
                + "\n" + playerLabel(t.getString("sf2Player1Uid"))
                + "\nvs\n" + playerLabel(t.getString("sf2Player2Uid"))
                + "\nPobednik: " + playerLabel(t.getString("sf2WinnerUid")));
        tvFinal.setText("FINAL - " + value(t.getString("finalStatus"), TournamentRepository.WAITING)
                + "\n" + playerLabel(t.getString("finalPlayer1Uid"))
                + "\nvs\n" + playerLabel(t.getString("finalPlayer2Uid")));
        tvWinner.setText(TournamentRepository.FINISHED.equals(status)
                ? "Pobednik turnira: " + playerLabel(t.getString("finalWinnerUid"))
                : userProgressText(t));
        btnJoin.setText(joined ? "Usli ste u turnir" : "Pridruzi se turniru");
        btnJoin.setEnabled(TournamentRepository.WAITING.equals(status) && current < 4 && !joined);
        PlayTarget target = currentUserPlayTarget(t);
        btnPlay.setEnabled(target != null);
        btnPlay.setText(target == null ? "Nema meca za igru" : target.label);
        btnPlay.setTag(target);
        Log.d(PERSIST_TAG, "UI rendered state tournamentId=" + t.getId()
                + ", status=" + status + ", currentPlayers=" + current
                + ", joined=" + joined + ", currentUserGameId=" + (target == null ? "" : target.gameId));
    }

    private PlayTarget currentUserPlayTarget(DocumentSnapshot t) {
        String status = value(t.getString("status"));
        if (TournamentRepository.SEMIFINALS.equals(status)) {
            if (uid.equals(t.getString("sf1Player1Uid")) || uid.equals(t.getString("sf1Player2Uid"))) {
                String sf1Status = value(t.getString("sf1Status"));
                if (TournamentRepository.READY.equals(sf1Status) || TournamentRepository.PLAYING.equals(sf1Status)) {
                    return new PlayTarget("SF1", value(t.getString("sf1MatchId")), "Igraj polufinale 1");
                }
            }
            if (uid.equals(t.getString("sf2Player1Uid")) || uid.equals(t.getString("sf2Player2Uid"))) {
                String sf2Status = value(t.getString("sf2Status"));
                if (TournamentRepository.READY.equals(sf2Status) || TournamentRepository.PLAYING.equals(sf2Status)) {
                    return new PlayTarget("SF2", value(t.getString("sf2MatchId")), "Igraj polufinale 2");
                }
            }
        }
        if (TournamentRepository.FINAL.equals(status)) {
            if (uid.equals(t.getString("finalPlayer1Uid")) || uid.equals(t.getString("finalPlayer2Uid"))) {
                String finalStatus = value(t.getString("finalStatus"));
                if (TournamentRepository.READY.equals(finalStatus) || TournamentRepository.PLAYING.equals(finalStatus)) {
                    return new PlayTarget("FINAL", value(t.getString("finalMatchId")), "Igraj finale");
                }
            }
        }
        return null;
    }

    private void playCurrentMatch() {
        Object tag = btnPlay.getTag();
        if (!(tag instanceof PlayTarget)) return;
        PlayTarget target = (PlayTarget) tag;
        if (target.gameId.isEmpty()) return;
        tournamentRepository.markMatchPlaying(tournamentId, target.round)
                .addOnFailureListener(e -> Log.e(PERSIST_TAG, "mark match playing failed round=" + target.round, e));
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra(GameFlow.EXTRA_GAME_ID, target.gameId);
        intent.putExtra(GameFlow.EXTRA_FULL_MATCH, true);
        intent.putExtra("tournamentRun", true);
        intent.putExtra("tournamentId", tournamentId);
        intent.putExtra("tournamentRound", target.round);
        startActivity(intent);
    }

    private String statusText(String status, long current, boolean joined) {
        if (TournamentRepository.WAITING.equals(status)) {
            return joined
                    ? "Usli ste u turnir. Ceka se jos igraca " + current + "/4."
                    : "Turnir ceka igrace " + current + "/4.";
        }
        if (TournamentRepository.SEMIFINALS.equals(status)) return "Polufinala su pocela.";
        if (TournamentRepository.FINAL.equals(status)) return "Finale je spremno.";
        if (TournamentRepository.FINISHED.equals(status)) return "Turnir je zavrsen.";
        if (TournamentRepository.CANCELLED.equals(status)) return "Turnir je ponisten.";
        return status;
    }

    private String userProgressText(DocumentSnapshot t) {
        String status = value(t.getString("status"));
        if (TournamentRepository.SEMIFINALS.equals(status)) {
            if (uid.equals(t.getString("sf1WinnerUid")) || uid.equals(t.getString("sf2WinnerUid"))) {
                return "Pobedili ste u polufinalu. Cekate finale.";
            }
            if (uid.equals(t.getString("sf1LoserUid")) || uid.equals(t.getString("sf2LoserUid"))) {
                return "Izgubili ste u polufinalu.";
            }
            return "Polufinala su spremna.";
        }
        if (TournamentRepository.FINAL.equals(status)) {
            if (uid.equals(t.getString("finalPlayer1Uid")) || uid.equals(t.getString("finalPlayer2Uid"))) {
                return "Igrajte finale.";
            }
            return "Turnir je u finalu.";
        }
        return "Pobednik: ?";
    }

    private String join(List<?> values) {
        if (values == null || values.isEmpty()) return "-";
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(playerLabel(value == null ? "" : String.valueOf(value)));
        }
        return builder.toString();
    }

    private String playerLabel(String value) {
        value = value(value);
        if (value.isEmpty()) return "?";
        PlayerView player = players.get(value);
        if (player == null) return "Igrac";
        return player.username + "\nLiga: " + player.leagueName + "\nAvatar: " + player.avatar;
    }

    private void loadMissingPlayers(DocumentSnapshot t) {
        loadPlayer(t.getString("sf1Player1Uid"));
        loadPlayer(t.getString("sf1Player2Uid"));
        loadPlayer(t.getString("sf2Player1Uid"));
        loadPlayer(t.getString("sf2Player2Uid"));
        loadPlayer(t.getString("sf1WinnerUid"));
        loadPlayer(t.getString("sf2WinnerUid"));
        loadPlayer(t.getString("sf1LoserUid"));
        loadPlayer(t.getString("sf2LoserUid"));
        loadPlayer(t.getString("finalPlayer1Uid"));
        loadPlayer(t.getString("finalPlayer2Uid"));
        loadPlayer(t.getString("finalWinnerUid"));
        loadPlayer(t.getString("finalLoserUid"));
        loadPlayer(t.getString("winnerUid"));
        Object raw = t.get("playerIds");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null) loadPlayer(String.valueOf(item));
            }
        }
    }

    private void loadPlayer(String playerUid) {
        playerUid = value(playerUid);
        if (playerUid.isEmpty() || players.containsKey(playerUid) || userRepository == null || !userRepository.isReady()) {
            return;
        }
        String finalUid = playerUid;
        players.put(finalUid, PlayerView.loading());
        userRepository.getUser(finalUid)
                .addOnSuccessListener(user -> {
                    PlayerView player = new PlayerView();
                    player.uid = finalUid;
                    player.username = firstNonEmpty(user.getString("username"), "Igrac");
                    player.leagueName = firstNonEmpty(user.getString("leagueName"), "Liga");
                    player.avatar = firstNonEmpty(user.getString("avatarId"), user.getString("avatar"), "star");
                    players.put(finalUid, player);
                    Log.d(DISPLAY_TAG, "player uid loaded=" + finalUid
                            + ", username loaded=" + player.username
                            + ", league loaded=" + player.leagueName
                            + ", rendered username instead of uid=true");
                    if (lastTournament != null) render(lastTournament);
                })
                .addOnFailureListener(e -> {
                    Log.e(DISPLAY_TAG, "player load failed uid=" + finalUid, e);
                    PlayerView player = new PlayerView();
                    player.uid = finalUid;
                    player.username = "Igrac";
                    player.leagueName = "Liga";
                    player.avatar = "star";
                    players.put(finalUid, player);
                    if (lastTournament != null) render(lastTournament);
                });
    }

    private DocumentSnapshot chooseJoinable(QuerySnapshot snapshot) {
        if (snapshot == null) return null;
        DocumentSnapshot best = null;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            if (TournamentRepository.WAITING.equals(doc.getString("status"))
                    && number(doc.get("currentPlayers")) < 4
                    && listSize(doc.get("playerIds")) < 4) {
                if (best == null) best = doc;
            }
        }
        return best;
    }

    private boolean containsUid(Object raw, String targetUid) {
        if (!(raw instanceof List)) return false;
        for (Object item : (List<?>) raw) {
            if (targetUid.equals(String.valueOf(item))) return true;
        }
        return false;
    }

    private int listSize(Object raw) {
        return raw instanceof List ? ((List<?>) raw).size() : 0;
    }

    private void showEmptyState(String message) {
        tournamentId = "";
        lastTournament = null;
        tvStatus.setText(message);
        tvPlayers.setText("Nema aktivnog turnira.");
        tvSf1.setText("SF1\n?");
        tvSf2.setText("SF2\n?");
        tvFinal.setText("FINAL\n?");
        tvWinner.setText("Pobednik: ?");
        btnJoin.setText("Pridruzi se turniru");
        btnJoin.setEnabled(true);
        btnPlay.setEnabled(false);
        btnPlay.setTag(null);
    }

    private void resetStuckTournament() {
        if (tournamentRepository == null || !tournamentRepository.isReady()) return;
        tournamentRepository.cancelActiveTournamentForUser(uid)
                .addOnSuccessListener(unused -> {
                    show("Turnir je oznacen kao ponisten.");
                    tournamentId = "";
                    players.clear();
                    discoverTournament();
                })
                .addOnFailureListener(e -> {
                    Log.e(PERSIST_TAG, "reset stuck tournament failed", e);
                    show("Turnir nije ponisten.");
                });
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class PlayTarget {
        final String round;
        final String gameId;
        final String label;

        PlayTarget(String round, String gameId, String label) {
            this.round = round;
            this.gameId = gameId == null ? "" : gameId;
            this.label = label;
        }
    }

    private static class PlayerView {
        String uid;
        String username;
        String leagueName;
        String avatar;

        static PlayerView loading() {
            PlayerView player = new PlayerView();
            player.username = "Igrac";
            player.leagueName = "Liga";
            player.avatar = "star";
            return player;
        }
    }

    private void show(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        removeListeners();
        super.onStop();
    }

    private void removeListeners() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        if (queryListener != null) {
            queryListener.remove();
            queryListener = null;
        }
    }
}
