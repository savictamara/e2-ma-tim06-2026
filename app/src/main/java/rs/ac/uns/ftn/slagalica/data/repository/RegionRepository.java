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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

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

    public Task<RegionDashboard> loadDashboard(String uid) {
        if (db == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
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
    }

    public Task<RegionStats> getRegionStats(String regionId) {
        if (db == null || isBlank(regionId)) {
            return Tasks.forException(new IllegalArgumentException("Region nije izabran"));
        }
        RegionInfo info = infoById(regionId);
        if (info == null) {
            return Tasks.forException(new IllegalArgumentException("Region nije pronadjen"));
        }
        return db.collection("regionStats").document(regionId).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return statsFromDoc(task.getResult(), info);
        });
    }

    public static List<RegionInfo> regions() {
        return Arrays.asList(
                new RegionInfo("vojvodina", "Vojvodina", "V", 45.0, 46.2, 18.8, 21.4),
                new RegionInfo("beograd", "Beograd", "BG", 44.65, 44.95, 20.25, 20.65),
                new RegionInfo("sumadija", "Sumadija", "SU", 43.7, 44.5, 20.2, 21.1),
                new RegionInfo("zapadna_srbija", "Zapadna Srbija", "ZS", 43.3, 44.6, 19.1, 20.2),
                new RegionInfo("istocna_srbija", "Istocna Srbija", "IS", 43.4, 44.8, 21.0, 22.9),
                new RegionInfo("juzna_srbija", "Juzna Srbija", "JS", 42.2, 43.7, 20.5, 22.4),
                new RegionInfo("kosovo_i_metohija", "Kosovo i Metohija", "KM", 42.0, 43.3, 20.0, 21.8)
        );
    }

    public static RegionInfo infoById(String regionId) {
        for (RegionInfo info : regions()) {
            if (info.id.equals(regionId)) {
                return info;
            }
        }
        return null;
    }

    public static String regionIdForName(String rawRegion) {
        if (rawRegion == null) {
            return "";
        }
        String normalized = rawRegion.trim().toLowerCase(Locale.US)
                .replace("š", "s").replace("đ", "dj").replace("č", "c")
                .replace("ć", "c").replace("ž", "z")
                .replace(" ", "_").replace("-", "_");
        for (RegionInfo info : regions()) {
            if (info.id.equals(normalized) || info.name.toLowerCase(Locale.US).replace(" ", "_").equals(normalized)) {
                return info.id;
            }
        }
        return normalized;
    }

    private BuildResult buildDashboard(DocumentSnapshot currentUser, QuerySnapshot users, QuerySnapshot storedStats) {
        String currentMonth = currentMonth();
        Map<String, RegionStats> stats = baseStats();
        Map<String, DocumentSnapshot> storedByRegion = new HashMap<>();
        for (DocumentSnapshot doc : storedStats.getDocuments()) {
            storedByRegion.put(doc.getId(), doc);
        }
        boolean cycleChanged = hasCycleChanged(storedByRegion, currentMonth);
        Map<String, Long> previousPlacement = cycleChanged
                ? previousPlacements(storedByRegion)
                : storedPlacements(storedByRegion);

        RegionDashboard dashboard = new RegionDashboard();
        String currentRegionId = regionIdForName(currentUser.getString("region"));
        dashboard.currentUserRegionId = currentRegionId;
        RegionInfo currentInfo = infoById(currentRegionId);
        dashboard.currentUserRegionName = currentInfo == null ? value(currentUser.getString("region")) : currentInfo.name;

        for (DocumentSnapshot user : users.getDocuments()) {
            String regionId = regionIdForName(user.getString("region"));
            RegionInfo info = infoById(regionId);
            if (info == null) {
                continue;
            }
            RegionStats regionStats = stats.get(regionId);
            regionStats.totalPlayers++;
            if (Boolean.TRUE.equals(user.getBoolean("online"))) {
                regionStats.activePlayers++;
            }
            regionStats.monthlyStars += monthlyStars(user, currentMonth, cycleChanged);
            dashboard.points.add(pointForUser(user, info));
        }

        for (RegionStats regionStats : stats.values()) {
            DocumentSnapshot stored = storedByRegion.get(regionStats.regionId);
            if (stored != null && stored.exists()) {
                regionStats.firstPlaces = longValue(stored.get("firstPlaces"));
                regionStats.secondPlaces = longValue(stored.get("secondPlaces"));
                regionStats.thirdPlaces = longValue(stored.get("thirdPlaces"));
                regionStats.previousCyclePlacement = longValue(stored.get("previousCyclePlacement"));
            }
            Long placement = previousPlacement.get(regionStats.regionId);
            if (cycleChanged && placement != null) {
                regionStats.previousCyclePlacement = placement;
                if (placement == 1) regionStats.firstPlaces++;
                if (placement == 2) regionStats.secondPlaces++;
                if (placement == 3) regionStats.thirdPlaces++;
            }
            regionStats.lastCycleMonth = currentMonth;
            dashboard.regions.add(regionStats);
        }
        Collections.sort(dashboard.regions, (a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));

        WriteBatch batch = db.batch();
        writeStats(batch, dashboard.regions);
        writePoints(batch, dashboard.points);
        resetUserMonthlyStarsIfNeeded(batch, users, currentMonth, cycleChanged);
        applyAvatarFrames(batch, users, previousPlacement);
        return new BuildResult(dashboard, batch);
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
            stats.regionName = valueOr(doc.getString("regionName"), info.name);
            stats.iconName = valueOr(doc.getString("iconName"), info.iconName);
            stats.monthlyStars = longValue(doc.get("monthlyStars"));
            stats.firstPlaces = longValue(doc.get("firstPlaces"));
            stats.secondPlaces = longValue(doc.get("secondPlaces"));
            stats.thirdPlaces = longValue(doc.get("thirdPlaces"));
            stats.activePlayers = longValue(doc.get("activePlayers"));
            stats.totalPlayers = longValue(doc.get("totalPlayers"));
            stats.lastCycleMonth = value(doc.getString("lastCycleMonth"));
            stats.previousCyclePlacement = longValue(doc.get("previousCyclePlacement"));
        }
        return stats;
    }

    private boolean hasCycleChanged(Map<String, DocumentSnapshot> storedByRegion, String currentMonth) {
        for (DocumentSnapshot doc : storedByRegion.values()) {
            String storedMonth = doc.getString("lastCycleMonth");
            if (!isBlank(storedMonth) && !currentMonth.equals(storedMonth)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Long> previousPlacements(Map<String, DocumentSnapshot> storedByRegion) {
        List<DocumentSnapshot> docs = new ArrayList<>(storedByRegion.values());
        docs.sort((a, b) -> Long.compare(longValue(b.get("monthlyStars")), longValue(a.get("monthlyStars"))));
        Map<String, Long> placements = new HashMap<>();
        for (int i = 0; i < docs.size() && i < 3; i++) {
            if (longValue(docs.get(i).get("monthlyStars")) > 0) {
                placements.put(docs.get(i).getId(), (long) i + 1);
            }
        }
        return placements;
    }

    private Map<String, Long> storedPlacements(Map<String, DocumentSnapshot> storedByRegion) {
        Map<String, Long> placements = new HashMap<>();
        for (DocumentSnapshot doc : storedByRegion.values()) {
            long placement = longValue(doc.get("previousCyclePlacement"));
            if (placement >= 1 && placement <= 3) {
                placements.put(doc.getId(), placement);
            }
        }
        return placements;
    }

    private void writeStats(WriteBatch batch, List<RegionStats> stats) {
        for (RegionStats region : stats) {
            Map<String, Object> data = new HashMap<>();
            data.put("regionName", region.regionName);
            data.put("iconName", region.iconName);
            data.put("monthlyStars", region.monthlyStars);
            data.put("firstPlaces", region.firstPlaces);
            data.put("secondPlaces", region.secondPlaces);
            data.put("thirdPlaces", region.thirdPlaces);
            data.put("activePlayers", region.activePlayers);
            data.put("totalPlayers", region.totalPlayers);
            data.put("lastCycleMonth", region.lastCycleMonth);
            data.put("previousCyclePlacement", region.previousCyclePlacement);
            batch.set(db.collection("regionStats").document(region.regionId), data, SetOptions.merge());
        }
    }

    private void writePoints(WriteBatch batch, List<RegionPoint> points) {
        for (RegionPoint point : points) {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", point.uid);
            data.put("username", point.username);
            data.put("latitude", point.latitude);
            data.put("longitude", point.longitude);
            data.put("createdAt", FieldValue.serverTimestamp());
            batch.set(db.collection("regionStats").document(point.regionId)
                    .collection("points").document(point.uid), data, SetOptions.merge());
        }
    }

    private void applyAvatarFrames(WriteBatch batch, QuerySnapshot users, Map<String, Long> placements) {
        for (DocumentSnapshot user : users.getDocuments()) {
            String regionId = regionIdForName(user.getString("region"));
            Long placement = placements.get(regionId);
            String color = "#8A2BE2";
            if (placement != null && placement == 1) color = "#FFD700";
            if (placement != null && placement == 2) color = "#C0C0C0";
            if (placement != null && placement == 3) color = "#CD7F32";
            DocumentReference userRef = db.collection("users").document(user.getId());
            batch.set(userRef, mapOf("avatarFrameColor", color), SetOptions.merge());
        }
    }

    private RegionPoint pointForUser(DocumentSnapshot user, RegionInfo info) {
        Random random = new Random((user.getId() + info.id).hashCode());
        double lat = info.minLatitude + random.nextDouble() * (info.maxLatitude - info.minLatitude);
        double lon = info.minLongitude + random.nextDouble() * (info.maxLongitude - info.minLongitude);
        String username = valueOr(user.getString("username"), user.getId());
        return new RegionPoint(user.getId(), username, info.id, lat, lon);
    }

    private void resetUserMonthlyStarsIfNeeded(WriteBatch batch, QuerySnapshot users, String currentMonth, boolean cycleChanged) {
        if (!cycleChanged) {
            return;
        }
        for (DocumentSnapshot user : users.getDocuments()) {
            DocumentReference userRef = db.collection("users").document(user.getId());
            batch.set(userRef, mapOf(
                    "monthlyStars", 0,
                    "monthlyStarsCycleMonth", currentMonth
            ), SetOptions.merge());
        }
    }

    private long monthlyStars(DocumentSnapshot user, String currentMonth, boolean cycleChanged) {
        String userMonth = user.getString("monthlyStarsCycleMonth");
        if (cycleChanged && !currentMonth.equals(userMonth)) {
            return 0;
        }
        if (!isBlank(userMonth) && !currentMonth.equals(userMonth)) {
            return 0;
        }
        Object monthly = user.get("monthlyStars");
        if (monthly instanceof Number) {
            return ((Number) monthly).longValue();
        }
        return 0;
    }

    private String currentMonth() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String valueOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
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
