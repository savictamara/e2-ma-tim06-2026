package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.domain.model.LeaderboardDashboard;
import rs.ac.uns.ftn.slagalica.domain.model.LeaderboardEntry;
import rs.ac.uns.ftn.slagalica.domain.model.LeagueDefinition;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class LeaderboardRepository {
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";
    private static final String TAG = "LeaderboardRepository";

    private final FirebaseFirestore db;
    private final NotificationRepository notificationRepository;

    public LeaderboardRepository(Context context) {
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firestore nije dostupan", e);
        }
        db = instance;
        notificationRepository = new NotificationRepository(context);
    }

    public boolean isReady() {
        return db != null;
    }

    public Task<Void> ensureCycles() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return ensureCycle(WEEKLY).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return ensureCycle(MONTHLY);
        });
    }

    public Task<LeaderboardDashboard> loadLeaderboard(String type, String currentUid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        String cleanType = MONTHLY.equals(type) ? MONTHLY : WEEKLY;
        return ensureCycles().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return db.collection("users").get().continueWith(userTask -> {
                if (!userTask.isSuccessful()) throw userTask.getException();
                LeaderboardDashboard dashboard = new LeaderboardDashboard();
                dashboard.cycleType = cleanType;
                dashboard.cycleId = currentCycleId(cleanType);
                dashboard.dateRange = dateRange(cleanType);
                for (DocumentSnapshot user : userTask.getResult().getDocuments()) {
                    long matches = longValue(user.get(cleanType.equals(WEEKLY) ? "weeklyMatchesPlayed" : "monthlyMatchesPlayed"));
                    if (matches <= 0) {
                        continue;
                    }
                    long stars = longValue(user.get(cleanType.equals(WEEKLY) ? "weeklyStars" : "monthlyStars"));
                    LeaderboardEntry entry = new LeaderboardEntry(
                            user.getId(),
                            firstNonEmpty(user.getString("username"), user.getString("email"), user.getId()),
                            firstNonEmpty(user.getString("leagueIcon"), "star"),
                            stars
                    );
                    entry.currentUser = user.getId().equals(currentUid);
                    dashboard.entries.add(entry);
                }
                dashboard.entries.sort((a, b) -> Long.compare(b.stars, a.stars));
                for (int i = 0; i < dashboard.entries.size(); i++) {
                    dashboard.entries.get(i).rank = i + 1;
                }
                return dashboard;
            });
        });
    }

    public Task<DocumentSnapshot> getPendingReward(String uid) {
        if (db == null || isBlank(uid)) {
            return Tasks.forException(new IllegalArgumentException("Korisnik nije prijavljen"));
        }
        return db.collection("users").document(uid).get();
    }

    public Task<Void> clearPendingReward(String uid) {
        if (db == null || isBlank(uid)) {
            return Tasks.forResult(null);
        }
        return db.collection("users").document(uid).set(mapOf("pendingReward", false), SetOptions.merge());
    }

    private Task<Void> ensureCycle(String type) {
        String current = currentCycleId(type);
        DocumentReference infoRef = db.collection("leaderboards").document(type);
        return infoRef.get().continueWithTask(infoTask -> {
            if (!infoTask.isSuccessful()) throw infoTask.getException();
            DocumentSnapshot info = infoTask.getResult();
            String stored = info.exists() ? info.getString("cycleId") : "";
            if (isBlank(stored)) {
                return infoRef.set(cycleInfo(type, current), SetOptions.merge());
            }
            if (current.equals(stored)) {
                return Tasks.forResult(null);
            }
            return processFinishedCycle(type, stored, current);
        });
    }

    private Task<Void> processFinishedCycle(String type, String previousCycle, String currentCycle) {
        DocumentReference cycleRef = db.collection("cycles").document(type).collection(previousCycle).document("meta");
        return cycleRef.get().continueWithTask(metaTask -> {
            if (!metaTask.isSuccessful()) throw metaTask.getException();
            DocumentSnapshot meta = metaTask.getResult();
            if (meta.exists() && Boolean.TRUE.equals(meta.getBoolean("processed"))) {
                return db.collection("leaderboards").document(type).set(cycleInfo(type, currentCycle), SetOptions.merge());
            }
            return db.collection("users").get().continueWithTask(usersTask -> {
                if (!usersTask.isSuccessful()) throw usersTask.getException();
                List<UserCycleResult> results = cycleResults(type, usersTask.getResult());
                Map<String, Boolean> rewardedUsers = new HashMap<>();
                WriteBatch batch = db.batch();
                batch.set(cycleRef, mapOf(
                        "cycleId", previousCycle,
                        "type", type,
                        "status", "processed",
                        "processed", true,
                        "endedAt", FieldValue.serverTimestamp()
                ), SetOptions.merge());

                for (int i = 0; i < results.size(); i++) {
                    UserCycleResult result = results.get(i);
                    result.rank = i + 1;
                    result.rewardTokens = rewardFor(type, result.rank);
                    DocumentReference resultRef = db.collection("cycles").document(type)
                            .collection(previousCycle).document("results")
                            .collection("players").document(result.uid);
                    batch.set(resultRef, mapOf(
                            "rank", result.rank,
                            "uid", result.uid,
                            "username", result.username,
                            "stars", result.stars,
                            "rewardTokens", result.rewardTokens
                    ), SetOptions.merge());

                    if (result.rewardTokens > 0) {
                        rewardedUsers.put(result.uid, true);
                        DocumentReference userRef = db.collection("users").document(result.uid);
                        batch.set(userRef, pendingRewardData(type, previousCycle, result.rank, result.rewardTokens),
                                SetOptions.merge());
                        DocumentReference notificationRef = userRef.collection("notifications").document();
                        batch.set(notificationRef, notificationRepository.notificationData(notificationRef.getId(),
                                "REWARD",
                                "Osvojili ste nagradu!",
                                rewardMessage(type, result.rank, result.rewardTokens),
                                "REWARD",
                                type + ":" + previousCycle,
                                "",
                                ""), SetOptions.merge());
                    }
                }

                for (DocumentSnapshot user : usersTask.getResult().getDocuments()) {
                    DocumentReference userRef = db.collection("users").document(user.getId());
                    Map<String, Object> updates = resetData(type, currentCycle);
                    if (MONTHLY.equals(type)
                            && longValue(user.get("monthlyMatchesPlayed")) > 0
                            && !rewardedUsers.containsKey(user.getId())
                            && !previousCycle.equals(user.getString("lastMonthlyPenaltyCycle"))) {
                        applyMonthlyPenalty(batch, user, userRef, updates, previousCycle);
                    }
                    batch.set(userRef, updates, SetOptions.merge());
                }
                batch.set(db.collection("leaderboards").document(type), cycleInfo(type, currentCycle), SetOptions.merge());
                batch.set(db.collection("cycles").document(type).collection(currentCycle).document("meta"),
                        mapOf("cycleId", currentCycle, "type", type, "status", "active",
                                "startedAt", FieldValue.serverTimestamp(), "processed", false),
                        SetOptions.merge());
                return batch.commit();
            });
        });
    }

    private List<UserCycleResult> cycleResults(String type, QuerySnapshot users) {
        List<UserCycleResult> results = new ArrayList<>();
        String starsField = WEEKLY.equals(type) ? "weeklyStars" : "monthlyStars";
        String matchesField = WEEKLY.equals(type) ? "weeklyMatchesPlayed" : "monthlyMatchesPlayed";
        for (DocumentSnapshot user : users.getDocuments()) {
            long matches = longValue(user.get(matchesField));
            if (matches <= 0) {
                continue;
            }
            results.add(new UserCycleResult(user.getId(),
                    firstNonEmpty(user.getString("username"), user.getString("email"), user.getId()),
                    longValue(user.get(starsField))));
        }
        results.sort((a, b) -> Long.compare(b.stars, a.stars));
        if (results.size() > 10) {
            return new ArrayList<>(results.subList(0, 10));
        }
        return results;
    }

    private Map<String, Object> pendingRewardData(String type, String cycleId, long rank, long tokens) {
        return mapOf(
                "tokens", FieldValue.increment(tokens),
                "pendingReward", true,
                "pendingRewardCycleType", type,
                "pendingRewardCycleId", cycleId,
                "pendingRewardPlacement", rank,
                "pendingRewardTokens", tokens,
                "lastWeeklyRewardCycle", WEEKLY.equals(type) ? cycleId : null,
                "lastMonthlyRewardCycle", MONTHLY.equals(type) ? cycleId : null
        );
    }

    private Map<String, Object> resetData(String type, String currentCycle) {
        if (WEEKLY.equals(type)) {
            return mapOf("weeklyStars", 0, "weeklyMatchesPlayed", 0,
                    "weeklyLeaderboardEligible", false, "weeklyCycleId", currentCycle);
        }
        return mapOf("monthlyStars", 0, "monthlyMatchesPlayed", 0,
                "monthlyLeaderboardEligible", false, "monthlyCycleId", currentCycle);
    }

    private void applyMonthlyPenalty(WriteBatch batch, DocumentSnapshot user, DocumentReference userRef,
                                     Map<String, Object> updates, String cycleId) {
        long oldStars = longValue(user.get("stars"));
        long newStars = (long) Math.floor(oldStars * 0.70d);
        LeagueDefinition oldLeague = leagueFromUser(user, oldStars);
        LeagueDefinition newLeague = LeagueDefinition.forStars(newStars);
        updates.put("stars", newStars);
        updates.put("league", newLeague.id);
        updates.put("leagueName", newLeague.name);
        updates.put("leagueIcon", newLeague.iconName);
        updates.put("leagueIconName", newLeague.iconName);
        updates.put("lastMonthlyPenaltyCycle", cycleId);
        Log.d(TAG, "League recalculation uid=" + user.getId()
                + ", oldStars=" + oldStars
                + ", newStars=" + newStars
                + ", oldLeague=" + oldLeague.id
                + ", newLeague=" + newLeague.id
                + ", reason=MONTHLY_PENALTY");
        if (oldLeague.id == newLeague.id) {
            return;
        }
        String direction = LeagueDefinition.direction(oldLeague.id, newLeague.id);
        String title = "PROMOTION".equals(direction)
                ? "Presli ste u novu ligu!"
                : "Pali ste u nizu ligu";
        String message = "PROMOTION".equals(direction)
                ? "Cestitamo! Presli ste iz " + oldLeague.name + " u " + newLeague.name + "."
                : "Presli ste iz " + oldLeague.name + " u " + newLeague.name + ".";
        updates.put("lastLeagueChangeAt", FieldValue.serverTimestamp());
        updates.put("pendingLeagueDialog", true);
        updates.put("pendingLeagueOldLevel", oldLeague.id);
        updates.put("pendingLeagueNewLevel", newLeague.id);
        updates.put("pendingLeagueDirection", direction);
        updates.put("pendingLeagueMessage", message);
        DocumentReference notificationRef = userRef.collection("notifications").document();
        batch.set(notificationRef, mapOf(
                "notificationId", notificationRef.getId(),
                "id", notificationRef.getId(),
                "type", "LEAGUE",
                "title", title,
                "message", message,
                "createdAt", FieldValue.serverTimestamp(),
                "read", false,
                "actionType", "LEAGUE",
                "actionTargetId", String.valueOf(newLeague.id),
                "senderUid", "",
                "senderName", "",
                "targetScreen", "LEAGUE"
        ));
    }

    private LeagueDefinition leagueFromUser(DocumentSnapshot user, long stars) {
        Object raw = user.get("league");
        if (raw instanceof Number) {
            return LeagueDefinition.byId(((Number) raw).longValue());
        }
        return LeagueDefinition.forStars(stars);
    }

    private Map<String, Object> cycleInfo(String type, String cycleId) {
        return mapOf("cycleId", cycleId, "type", type, "dateRange", dateRange(type),
                "updatedAt", FieldValue.serverTimestamp());
    }

    private int rewardFor(String type, long rank) {
        if (WEEKLY.equals(type)) {
            if (rank == 1) return 5;
            if (rank == 2) return 3;
            if (rank == 3) return 2;
            if (rank >= 4 && rank <= 10) return 1;
            return 0;
        }
        if (rank == 1) return 10;
        if (rank == 2) return 6;
        if (rank == 3) return 4;
        if (rank >= 4 && rank <= 10) return 2;
        return 0;
    }

    private String rewardMessage(String type, long rank, long tokens) {
        String cycle = WEEKLY.equals(type) ? "nedeljne" : "mesecne";
        return "Zavrsili ste na " + rank + ". mestu " + cycle + " rang liste i osvojili " + tokens + " tokena.";
    }

    public static String currentCycleId(String type) {
        Calendar calendar = Calendar.getInstance(Locale.US);
        if (WEEKLY.equals(type)) {
            calendar.setFirstDayOfWeek(Calendar.MONDAY);
            calendar.setMinimalDaysInFirstWeek(4);
            int week = calendar.get(Calendar.WEEK_OF_YEAR);
            int year = calendar.getWeekYear();
            return String.format(Locale.US, "%04d-W%02d", year, week);
        }
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    public static String dateRange(String type) {
        Calendar calendar = Calendar.getInstance(Locale.US);
        if (WEEKLY.equals(type)) {
            calendar.setFirstDayOfWeek(Calendar.MONDAY);
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            Date start = calendar.getTime();
            calendar.add(Calendar.DAY_OF_MONTH, 6);
            return formatDate(start) + " - " + formatDate(calendar.getTime());
        }
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date start = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return formatDate(start) + " - " + formatDate(calendar.getTime());
    }

    private static String formatDate(Date date) {
        Calendar c = new GregorianCalendar();
        c.setTime(date);
        String month = new DateFormatSymbols(Locale.US).getShortMonths()[c.get(Calendar.MONTH)];
        return c.get(Calendar.DAY_OF_MONTH) + " " + month + " " + c.get(Calendar.YEAR);
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null) {
                map.put(String.valueOf(values[i]), values[i + 1]);
            }
        }
        return map;
    }

    private static class UserCycleResult {
        final String uid;
        final String username;
        final long stars;
        long rank;
        long rewardTokens;

        UserCycleResult(String uid, String username, long stars) {
            this.uid = uid;
            this.username = username;
            this.stars = stars;
        }
    }
}
