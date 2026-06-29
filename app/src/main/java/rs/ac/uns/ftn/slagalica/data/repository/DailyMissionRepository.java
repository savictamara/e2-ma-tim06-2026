package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Date;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class DailyMissionRepository {
    private static final String TAG = "DailyMissionDebug";
    public static final String WIN_MATCH = "winMatch";
    public static final String SEND_CHAT = "sendChatMessage";
    public static final String PLAY_FRIENDLY = "playFriendlyMatch";
    public static final String WIN_TOURNAMENT = "winTournamentMatch";

    private final FirebaseFirestore db;

    public DailyMissionRepository(Context context) {
        FirebaseFirestore instance = null;
        if (FirebaseInitializer.ensure(context)) {
            instance = FirebaseFirestore.getInstance();
        }
        db = instance;
    }

    public boolean isReady() {
        return db != null;
    }

    public Task<Void> ensureToday(String uid) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        return db.runTransaction(transaction -> {
            DocumentReference ref = missionRef(uid);
            DocumentSnapshot mission = transaction.get(ref);
            if (!today().equals(mission.getString("date"))) {
                transaction.set(ref, freshMission(), SetOptions.merge());
                Log.d(TAG, "daily missions reset uid=" + uid + ", date=" + today());
            }
            return null;
        });
    }

    public Task<Void> completeMission(String uid, String missionField) {
        if (db == null) return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        if (isBlank(uid) || isBlank(missionField)) return Tasks.forResult(null);
        return db.runTransaction(transaction -> {
            DocumentReference missionRef = missionRef(uid);
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot mission = transaction.get(missionRef);
            Map<String, Object> updates = new HashMap<>();
            if (!today().equals(mission.getString("date"))) {
                updates.putAll(freshMission());
                mission = null;
            }
            String rewardField = missionField + "RewardClaimed";
            boolean alreadyCompleted = mission != null && Boolean.TRUE.equals(mission.getBoolean(missionField));
            boolean rewardClaimed = mission != null && Boolean.TRUE.equals(mission.getBoolean(rewardField));
            updates.put(missionField, true);
            if (!alreadyCompleted && !rewardClaimed) {
                updates.put(rewardField, true);
                transaction.set(userRef, mapOf("stars", FieldValue.increment(3)), SetOptions.merge());
                Log.d(TAG, "mission reward uid=" + uid + ", mission=" + missionField + ", stars=3");
            }
            boolean win = missionValue(mission, updates, WIN_MATCH);
            boolean chat = missionValue(mission, updates, SEND_CHAT);
            boolean friendly = missionValue(mission, updates, PLAY_FRIENDLY);
            boolean tournament = missionValue(mission, updates, WIN_TOURNAMENT);
            boolean bonusClaimed = mission != null && Boolean.TRUE.equals(mission.getBoolean("bonusClaimed"));
            if (win && chat && friendly && tournament && !bonusClaimed) {
                updates.put("bonusClaimed", true);
                transaction.set(userRef, mapOf(
                        "stars", FieldValue.increment(3),
                        "tokens", FieldValue.increment(2)
                ), SetOptions.merge());
                Log.d(TAG, "daily bonus uid=" + uid + ", stars=3, tokens=2");
            }
            transaction.set(missionRef, updates, SetOptions.merge());
            return null;
        });
    }

    public ListenerRegistration listenCurrent(String uid, EventListener<DocumentSnapshot> listener) {
        if (db == null || isBlank(uid)) return null;
        return missionRef(uid).addSnapshotListener(listener);
    }

    private DocumentReference missionRef(String uid) {
        return db.collection("users").document(uid).collection("dailyMissions").document("current");
    }

    private Map<String, Object> freshMission() {
        return mapOf(
                "date", today(),
                WIN_MATCH, false,
                SEND_CHAT, false,
                PLAY_FRIENDLY, false,
                WIN_TOURNAMENT, false,
                "winMatchRewardClaimed", false,
                "sendChatMessageRewardClaimed", false,
                "playFriendlyMatchRewardClaimed", false,
                "winTournamentMatchRewardClaimed", false,
                "bonusClaimed", false
        );
    }

    private boolean missionValue(DocumentSnapshot mission, Map<String, Object> updates, String key) {
        Object updated = updates.get(key);
        if (updated instanceof Boolean) return (Boolean) updated;
        return mission != null && Boolean.TRUE.equals(mission.getBoolean(key));
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
