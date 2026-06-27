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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rs.ac.uns.ftn.slagalica.RegionMapView;
import rs.ac.uns.ftn.slagalica.domain.model.RegionDashboard;
import rs.ac.uns.ftn.slagalica.domain.model.RegionInfo;
import rs.ac.uns.ftn.slagalica.domain.model.RegionPoint;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class RegionRepository {
    private static final String TAG = "RegionRepository";
    private final FirebaseFirestore db;

    public RegionRepository(Context context) {
        FirebaseFirestore instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseFirestore.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firestore nije dostupan", e);
        }
        db = instance;
    }

    public boolean isReady() {
        return db != null;
    }

    public Task<Void> saveRegistrationRegion(String uid, String username, String regionId) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        RegionInfo info = infoById(regionId);
        if (isBlank(uid) || info == null) {
            return Tasks.forException(new IllegalArgumentException("Region nije izabran"));
        }
        RegionPoint point = RegionMapView.getRandomPointInsideRegion(uid, username, info.id);
        if (point == null) {
            return Tasks.forException(new IllegalArgumentException("Region nije izabran"));
        }
        WriteBatch batch = db.batch();
        DocumentReference userRef = db.collection("users").document(uid);
        batch.set(userRef, mapOf(
                "regionId", info.id,
                "regionName", info.name,
                "region", info.name,
                "regionPointX", point.x,
                "regionPointY", point.y,
                "monthlyRegionStars", 0,
                "monthlyStars", 0,
                "monthlyStarsCycleMonth", currentCycleId(),
                "avatarFrame", "NONE",
                "avatarFrameType", "NONE",
                "avatarFrameColor", ""
        ), SetOptions.merge());
        batch.set(regionRef(info.id), baseStatsData(info), SetOptions.merge());
        batch.set(regionRef(info.id), mapOf("totalRegisteredPlayers", FieldValue.increment(1),
                "totalPlayers", FieldValue.increment(1)), SetOptions.merge());
        batch.set(pointRef(info.id, uid), pointData(point, username), SetOptions.merge());
        return batch.commit();
    }

    public Task<Void> addRegionalMonthlyStars(String uid, long starsWon) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        if (isBlank(uid) || starsWon <= 0) {
            return Tasks.forResult(null);
        }
        return db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot user = transaction.get(userRef);
            if (!user.exists()) {
                return null;
            }
            String regionId = regionIdForUser(user);
            RegionInfo info = infoById(regionId);
            if (info == null) {
                return null;
            }
            String currentCycle = currentCycleId();
            transaction.set(userRef, mapOf(
                    "regionId", info.id,
                    "regionName", info.name,
                    "region", firstNonEmpty(user.getString("region"), info.name),
                    "monthlyRegionStars", FieldValue.increment(starsWon),
                    "monthlyStars", FieldValue.increment(starsWon),
                    "monthlyStarsCycleMonth", currentCycle,
                    "lastActiveAt", FieldValue.serverTimestamp()
            ), SetOptions.merge());
            transaction.set(regionRef(info.id), baseStatsData(info), SetOptions.merge());
            transaction.set(regionRef(info.id), mapOf(
                    "monthlyStars", FieldValue.increment(starsWon),
                    "cycleId", currentCycle
            ), SetOptions.merge());
            return null;
        });
    }

    public Task<RegionDashboard> loadDashboard(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return ensureMonthlyCycle().continueWithTask(cycleTask -> {
            if (!cycleTask.isSuccessful()) {
                throw cycleTask.getException();
            }
            Task<DocumentSnapshot> currentUserTask = db.collection("users").document(uid).get();
            Task<QuerySnapshot> usersTask = db.collection("users").get();
            Task<QuerySnapshot> statsTask = db.collection("regionStats").get();
            return Tasks.whenAllSuccess(currentUserTask, usersTask, statsTask).continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                DocumentSnapshot currentUser = (DocumentSnapshot) task.getResult().get(0);
                QuerySnapshot users = (QuerySnapshot) task.getResult().get(1);
                QuerySnapshot storedStats = (QuerySnapshot) task.getResult().get(2);
                BuildResult result = buildDashboard(currentUser, users, storedStats);
                return result.batch.commit().continueWith(commitTask -> result.dashboard);
            });
        });
    }

    public Task<RegionStats> getRegionStats(String regionId) {
        if (db == null || isBlank(regionId)) {
            return Tasks.forException(new IllegalArgumentException("Region nije izabran"));
        }
        RegionInfo info = infoById(regionId);
        if (info == null) {
            return Tasks.forException(new IllegalArgumentException("Region nije pronadjen"));
        }
        return ensureMonthlyCycle().continueWithTask(cycleTask -> Tasks.whenAllSuccess(regionRef(info.id).get(), db.collection("regionStats").get()).continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            RegionStats stats = statsFromDoc((DocumentSnapshot) task.getResult().get(0), info);
            QuerySnapshot allStats = (QuerySnapshot) task.getResult().get(1);
            stats.currentRank = currentRank(info.id, allStats);
            return stats;
        }));
    }

    public Task<Void> ensureMonthlyCycle() {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        String current = currentCycleId();
        return db.collection("regionStats").get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            String previous = "";
            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                String cycle = doc.getString("cycleId");
                if (isBlank(cycle)) {
                    cycle = doc.getString("lastCycleMonth");
                }
                if (!isBlank(cycle) && !current.equals(cycle)) {
                    previous = cycle;
                    break;
                }
            }
            if (isBlank(previous)) {
                return ensureCurrentCycleDocs(current);
            }
            return processPreviousCycle(previous, current, task.getResult());
        });
    }

    private Task<Void> processPreviousCycle(String previous, String current, QuerySnapshot statsSnapshot) {
        DocumentReference cycleRef = db.collection("regionCycles").document(previous);
        return cycleRef.get().continueWithTask(cycleTask -> {
            if (!cycleTask.isSuccessful()) {
                throw cycleTask.getException();
            }
            DocumentSnapshot cycleDoc = cycleTask.getResult();
            if (cycleDoc.exists() && Boolean.TRUE.equals(cycleDoc.getBoolean("processed"))) {
                return ensureCurrentCycleDocs(current);
            }

            List<RegionStats> previousStats = new ArrayList<>();
            for (RegionInfo info : regions()) {
                DocumentSnapshot doc = findDoc(statsSnapshot, info.id);
                previousStats.add(statsFromDoc(doc, info));
            }
            previousStats.sort((a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));

            Map<String, Long> ranks = new HashMap<>();
            for (int i = 0; i < previousStats.size() && i < 3; i++) {
                if (previousStats.get(i).monthlyStars > 0) {
                    ranks.put(previousStats.get(i).regionId, (long) i + 1);
                }
            }

            return db.collection("users").get().continueWithTask(usersTask -> {
                if (!usersTask.isSuccessful()) {
                    throw usersTask.getException();
                }
                WriteBatch batch = db.batch();
                batch.set(cycleRef, mapOf(
                        "cycleId", previous,
                        "month", monthFromCycle(previous),
                        "year", yearFromCycle(previous),
                        "status", "processed",
                        "endedAt", FieldValue.serverTimestamp(),
                        "processed", true
                ), SetOptions.merge());

                for (RegionStats stats : previousStats) {
                    long rank = ranks.containsKey(stats.regionId) ? ranks.get(stats.regionId) : 0;
                    batch.set(cycleRef.collection("ranking").document(stats.regionId), mapOf(
                            "regionId", stats.regionId,
                            "monthlyStars", stats.monthlyStars,
                            "rank", rank
                    ), SetOptions.merge());

                    RegionInfo info = infoById(stats.regionId);
                    Map<String, Object> updates = baseStatsData(info);
                    updates.put("monthlyStars", 0);
                    updates.put("cycleId", current);
                    updates.put("lastCycleRank", rank);
                    updates.put("lastCycleFrame", frameForRank(rank));
                    updates.put("previousCyclePlacement", rank);
                    if (rank == 1) updates.put("firstPlaces", FieldValue.increment(1));
                    if (rank == 2) updates.put("secondPlaces", FieldValue.increment(1));
                    if (rank == 3) updates.put("thirdPlaces", FieldValue.increment(1));
                    batch.set(regionRef(stats.regionId), updates, SetOptions.merge());
                }

                for (DocumentSnapshot user : usersTask.getResult().getDocuments()) {
                    String regionId = regionIdForUser(user);
                    long rank = ranks.containsKey(regionId) ? ranks.get(regionId) : 0;
                    String frame = frameForRank(rank);
                    batch.set(db.collection("users").document(user.getId()), mapOf(
                            "monthlyRegionStars", 0,
                            "monthlyStars", 0,
                            "monthlyStarsCycleMonth", current,
                            "avatarFrame", frame,
                            "avatarFrameType", frame,
                            "avatarFrameColor", colorForFrame(frame)
                    ), SetOptions.merge());
                }

                batch.set(db.collection("regionCycles").document(current), mapOf(
                        "cycleId", current,
                        "month", monthFromCycle(current),
                        "year", yearFromCycle(current),
                        "status", "active",
                        "startedAt", FieldValue.serverTimestamp(),
                        "processed", false
                ), SetOptions.merge());
                return batch.commit();
            });
        });
    }

    private Task<Void> ensureCurrentCycleDocs(String current) {
        WriteBatch batch = db.batch();
        batch.set(db.collection("regionCycles").document(current), mapOf(
                "cycleId", current,
                "month", monthFromCycle(current),
                "year", yearFromCycle(current),
                "status", "active",
                "startedAt", FieldValue.serverTimestamp(),
                "processed", false
        ), SetOptions.merge());
        for (RegionInfo info : regions()) {
            Map<String, Object> data = baseStatsData(info);
            data.put("cycleId", current);
            batch.set(regionRef(info.id), data, SetOptions.merge());
        }
        return batch.commit();
    }

    private BuildResult buildDashboard(DocumentSnapshot currentUser, QuerySnapshot users, QuerySnapshot storedStats) {
        Map<String, RegionStats> stats = baseStats();
        Map<String, DocumentSnapshot> storedByRegion = new HashMap<>();
        for (DocumentSnapshot doc : storedStats.getDocuments()) {
            storedByRegion.put(doc.getId(), doc);
        }
        for (RegionInfo info : regions()) {
            DocumentSnapshot stored = storedByRegion.get(info.id);
            stats.put(info.id, statsFromDoc(stored, info));
        }

        RegionDashboard dashboard = new RegionDashboard();
        String currentRegionId = currentUser == null ? "" : regionIdForUser(currentUser);
        dashboard.currentUserRegionId = currentRegionId;
        RegionInfo currentInfo = infoById(currentRegionId);
        dashboard.currentUserRegionName = currentInfo == null ? "" : currentInfo.name;

        WriteBatch batch = db.batch();
        Map<String, Long> active = new HashMap<>();
        Map<String, Long> total = new HashMap<>();
        for (DocumentSnapshot user : users.getDocuments()) {
            String regionId = regionIdForUser(user);
            RegionInfo info = infoById(regionId);
            if (info == null) {
                continue;
            }
            total.put(regionId, total.getOrDefault(regionId, 0L) + 1);
            if (Boolean.TRUE.equals(user.getBoolean("online"))) {
                active.put(regionId, active.getOrDefault(regionId, 0L) + 1);
            }
            RegionPoint point = pointForUser(user, info, batch);
            if (point != null) {
                dashboard.points.add(point);
            }
            RegionStats regionStats = stats.get(regionId);
            if (regionStats != null && regionStats.monthlyStars == 0) {
                regionStats.monthlyStars += monthlyStarsFallback(user);
            }
        }

        for (RegionStats regionStats : stats.values()) {
            regionStats.activePlayers = active.getOrDefault(regionStats.regionId, 0L);
            regionStats.totalPlayers = total.getOrDefault(regionStats.regionId, regionStats.totalPlayers);
            dashboard.regions.add(regionStats);
            batch.set(regionRef(regionStats.regionId), mapOf(
                    "activePlayers", regionStats.activePlayers,
                    "totalRegisteredPlayers", regionStats.totalPlayers,
                    "totalPlayers", regionStats.totalPlayers
            ), SetOptions.merge());
        }
        Collections.sort(dashboard.regions, (a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));
        for (int i = 0; i < dashboard.regions.size(); i++) {
            dashboard.regions.get(i).currentRank = i + 1;
        }
        return new BuildResult(dashboard, batch);
    }

    private long currentRank(String regionId, QuerySnapshot snapshot) {
        List<RegionStats> stats = new ArrayList<>();
        for (RegionInfo info : regions()) {
            stats.add(statsFromDoc(findDoc(snapshot, info.id), info));
        }
        stats.sort((a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));
        for (int i = 0; i < stats.size(); i++) {
            if (regionId.equals(stats.get(i).regionId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private RegionPoint pointForUser(DocumentSnapshot user, RegionInfo info, WriteBatch batch) {
        String username = firstNonEmpty(user.getString("username"), user.getId());
        Float x = floatValue(user.get("regionPointX"));
        Float y = floatValue(user.get("regionPointY"));
        RegionPoint point;
        if (x != null && y != null && info.contains(x, y)) {
            point = new RegionPoint(user.getId(), username, info.id, x, y);
        } else {
            point = RegionMapView.getRandomPointInsideRegion(user.getId(), username, info.id);
            if (point == null) {
                return null;
            }
            batch.set(db.collection("users").document(user.getId()), mapOf(
                    "regionId", info.id,
                    "regionName", info.name,
                    "region", firstNonEmpty(user.getString("region"), info.name),
                    "regionPointX", point.x,
                    "regionPointY", point.y
            ), SetOptions.merge());
        }
        batch.set(pointRef(info.id, user.getId()), pointData(point, username), SetOptions.merge());
        return point;
    }

    public static List<RegionInfo> regions() {
        return Arrays.asList(
                new RegionInfo("VOJVODINA", "Vojvodina", "V", 0xFFDCCEFF, 0, 0, 0, 0,
                        new float[]{0.18f, 0.05f, 0.63f, 0.04f, 0.69f, 0.24f, 0.55f, 0.34f, 0.22f, 0.31f, 0.13f, 0.18f}),
                new RegionInfo("BEOGRAD", "Beograd", "BG", 0xFFF7CCE0, 0, 0, 0, 0,
                        new float[]{0.42f, 0.32f, 0.59f, 0.34f, 0.61f, 0.46f, 0.45f, 0.47f, 0.37f, 0.40f}),
                new RegionInfo("SUMADIJA_ZAPADNA_SRBIJA", "Sumadija i Zapadna Srbija", "SZ", 0xFFC7B2FF, 0, 0, 0, 0,
                        new float[]{0.19f, 0.32f, 0.43f, 0.31f, 0.47f, 0.48f, 0.42f, 0.69f, 0.24f, 0.72f, 0.11f, 0.55f}),
                new RegionInfo("JUZNA_ISTOCNA_SRBIJA", "Juzna i Istocna Srbija", "JI", 0xFFBFE6D4, 0, 0, 0, 0,
                        new float[]{0.58f, 0.35f, 0.80f, 0.45f, 0.86f, 0.74f, 0.66f, 0.96f, 0.39f, 0.73f, 0.46f, 0.49f}),
                new RegionInfo("KOSOVO_METOHIJA", "Kosovo i Metohija", "KM", 0xFFFFD9A8, 0, 0, 0, 0,
                        new float[]{0.31f, 0.70f, 0.55f, 0.74f, 0.65f, 0.94f, 0.44f, 0.98f, 0.26f, 0.86f})
        );
    }

    public static RegionInfo infoById(String regionId) {
        if (regionId == null) {
            return null;
        }
        String normalized = regionIdForName(regionId);
        for (RegionInfo info : regions()) {
            if (info.id.equals(normalized)) {
                return info;
            }
        }
        return null;
    }

    public static String regionIdForName(String rawRegion) {
        if (rawRegion == null) {
            return "";
        }
        String normalized = rawRegion.trim().toUpperCase(Locale.US)
                .replace("Š", "S").replace("Đ", "DJ").replace("Č", "C")
                .replace("Ć", "C").replace("Ž", "Z")
                .replace(" ", "_").replace("-", "_").replace("/", "_");
        if ("VOJVODINA".equals(normalized) || "V".equals(normalized)) return "VOJVODINA";
        if ("BEOGRAD".equals(normalized) || "BG".equals(normalized)) return "BEOGRAD";
        if ("SUMADIJA".equals(normalized) || "ZAPADNA_SRBIJA".equals(normalized)
                || "SUMADIJA_I_ZAPADNA_SRBIJA".equals(normalized) || "SUMADIJA_ZAPADNA_SRBIJA".equals(normalized)
                || "SU".equals(normalized) || "ZS".equals(normalized)) return "SUMADIJA_ZAPADNA_SRBIJA";
        if ("JUZNA_SRBIJA".equals(normalized) || "ISTOCNA_SRBIJA".equals(normalized)
                || "JUZNA_I_ISTOCNA_SRBIJA".equals(normalized) || "JUZNA_ISTOCNA_SRBIJA".equals(normalized)
                || "JS".equals(normalized) || "IS".equals(normalized)) return "JUZNA_ISTOCNA_SRBIJA";
        if ("KOSOVO_I_METOHIJA".equals(normalized) || "KOSOVO_METOHIJA".equals(normalized)
                || "KM".equals(normalized)) return "KOSOVO_METOHIJA";
        return normalized;
    }

    private Map<String, RegionStats> baseStats() {
        Map<String, RegionStats> stats = new HashMap<>();
        for (RegionInfo info : regions()) {
            stats.put(info.id, new RegionStats(info.id, info.name, info.iconName));
        }
        return stats;
    }

    private RegionStats statsFromDoc(DocumentSnapshot doc, RegionInfo info) {
        RegionStats stats = new RegionStats(info.id, info.name, info.iconName);
        if (doc != null && doc.exists()) {
            stats.regionName = firstNonEmpty(doc.getString("displayName"), doc.getString("regionName"), info.name);
            stats.iconName = firstNonEmpty(doc.getString("iconName"), info.iconName);
            stats.monthlyStars = longValue(doc.get("monthlyStars"));
            stats.firstPlaces = longValue(doc.get("firstPlaces"));
            stats.secondPlaces = longValue(doc.get("secondPlaces"));
            stats.thirdPlaces = longValue(doc.get("thirdPlaces"));
            stats.activePlayers = longValue(doc.get("activePlayers"));
            stats.totalPlayers = longValue(firstNumber(doc.get("totalRegisteredPlayers"), doc.get("totalPlayers")));
            stats.lastCycleMonth = firstNonEmpty(doc.getString("cycleId"), doc.getString("lastCycleMonth"));
            stats.previousCyclePlacement = longValue(firstNumber(doc.get("lastCycleRank"), doc.get("previousCyclePlacement")));
        }
        return stats;
    }

    private Map<String, Object> baseStatsData(RegionInfo info) {
        return mapOf(
                "regionId", info.id,
                "displayName", info.name,
                "regionName", info.name,
                "iconName", info.iconName,
                "lastCycleFrame", "NONE"
        );
    }

    private Map<String, Object> pointData(RegionPoint point, String username) {
        return mapOf(
                "uid", point.uid,
                "displayName", firstNonEmpty(username, point.username),
                "username", firstNonEmpty(username, point.username),
                "x", point.x,
                "y", point.y,
                "regionId", point.regionId,
                "createdAt", FieldValue.serverTimestamp()
        );
    }

    private String regionIdForUser(DocumentSnapshot user) {
        String regionId = user.getString("regionId");
        if (!isBlank(regionId)) {
            return regionIdForName(regionId);
        }
        return regionIdForName(user.getString("region"));
    }

    private long monthlyStarsFallback(DocumentSnapshot user) {
        Object value = user.get("monthlyRegionStars");
        if (!(value instanceof Number)) {
            value = user.get("monthlyStars");
        }
        return longValue(value);
    }

    private DocumentSnapshot findDoc(QuerySnapshot snapshot, String id) {
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            if (id.equals(doc.getId())) {
                return doc;
            }
        }
        return null;
    }

    private DocumentReference regionRef(String regionId) {
        return db.collection("regionStats").document(regionId);
    }

    private DocumentReference pointRef(String regionId, String uid) {
        return regionRef(regionId).collection("points").document(uid);
    }

    private String currentCycleId() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private int monthFromCycle(String cycle) {
        try {
            return Integer.parseInt(cycle.substring(5, 7));
        } catch (Exception e) {
            return Calendar.getInstance().get(Calendar.MONTH) + 1;
        }
    }

    private int yearFromCycle(String cycle) {
        try {
            return Integer.parseInt(cycle.substring(0, 4));
        } catch (Exception e) {
            return Calendar.getInstance().get(Calendar.YEAR);
        }
    }

    private String frameForRank(long rank) {
        if (rank == 1) return "GOLD";
        if (rank == 2) return "SILVER";
        if (rank == 3) return "BRONZE";
        return "NONE";
    }

    private String colorForFrame(String frame) {
        if ("GOLD".equals(frame)) return "#FFD700";
        if ("SILVER".equals(frame)) return "#C0C0C0";
        if ("BRONZE".equals(frame)) return "#CD7F32";
        return "";
    }

    private Object firstNumber(Object first, Object second) {
        return first instanceof Number ? first : second;
    }

    private Float floatValue(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : null;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static class BuildResult {
        final RegionDashboard dashboard;
        final WriteBatch batch;

        BuildResult(RegionDashboard dashboard, WriteBatch batch) {
            this.dashboard = dashboard;
            this.batch = batch;
        }
    }
}
