package rs.ac.uns.ftn.slagalica;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionInfo;
import rs.ac.uns.ftn.slagalica.domain.model.RegionPoint;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;

public class RegionMapView extends MapView {
    public interface OnRegionClickListener {
        void onRegionClick(String regionId);
    }

    private final List<RegionStats> regions = new ArrayList<>();
    private final List<RegionPoint> points = new ArrayList<>();
    private String currentRegionId = "";
    private String selectedRegionId = "";
    private OnRegionClickListener listener;
    private boolean shouldFitBounds = true;

    public RegionMapView(Context context) {
        super(context);
        init(context);
    }

    public RegionMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
        setMultiTouchControls(true);
        getController().setZoom(7.1);
        getController().setCenter(new GeoPoint(44.05, 20.75));
        setMinZoomLevel(6.5);
        setMaxZoomLevel(12.0);
        setMapOrientation(0f);
        setTilesScaledToDpi(true);
    }

    public void setData(List<RegionStats> regionStats, List<RegionPoint> regionPoints, String currentUserRegionId) {
        regions.clear();
        points.clear();
        if (regionStats != null) {
            regions.addAll(regionStats);
        }
        if (regions.isEmpty()) {
            for (RegionInfo info : RegionRepository.regions()) {
                regions.add(new RegionStats(info.id, info.name, info.iconName));
            }
        }
        if (regionPoints != null) {
            points.addAll(regionPoints);
        }
        currentRegionId = currentUserRegionId == null ? "" : currentUserRegionId;
        if (selectedRegionId.isEmpty()) {
            selectedRegionId = currentRegionId;
        }
        shouldFitBounds = true;
        render();
    }

    public void setSelectedRegionId(String regionId) {
        selectedRegionId = regionId == null ? "" : regionId;
        render();
    }

    public String getSelectedRegionId() {
        return selectedRegionId;
    }

    public void setOnRegionClickListener(OnRegionClickListener listener) {
        this.listener = listener;
    }

    private void render() {
        getOverlays().clear();
        drawRegionPolygons();
        drawRegionLabels();
        drawPlayerMarkers();
        if (shouldFitBounds) {
            fitSerbiaBoundsWhenReady();
        }
        invalidate();
    }

    private void fitSerbiaBoundsWhenReady() {
        post(() -> {
            shouldFitBounds = false;
            try {
                BoundingBox bounds = allRegionBounds();
                if (bounds == null || getWidth() == 0 || getHeight() == 0) {
                    applyFallbackView();
                    return;
                }
                setMapOrientation(0f);
                zoomToBoundingBox(bounds, false, dp(52));
            } catch (Exception ignored) {
                applyFallbackView();
            }
        });
    }

    private void applyFallbackView() {
        setMapOrientation(0f);
        getController().setCenter(new GeoPoint(44.05, 20.75));
        getController().setZoom(7.1);
    }

    private BoundingBox allRegionBounds() {
        double north = -90.0;
        double south = 90.0;
        double east = -180.0;
        double west = 180.0;
        boolean hasPoint = false;
        for (RegionInfo info : RegionRepository.regions()) {
            for (GeoPoint point : geoPoints(info)) {
                north = Math.max(north, point.getLatitude());
                south = Math.min(south, point.getLatitude());
                east = Math.max(east, point.getLongitude());
                west = Math.min(west, point.getLongitude());
                hasPoint = true;
            }
        }
        return hasPoint ? new BoundingBox(north, east, south, west) : null;
    }

    private void drawRegionPolygons() {
        for (RegionInfo info : orderedRegionsForDrawing()) {
            Polygon polygon = new Polygon(this);
            polygon.setPoints(geoPoints(info));
            polygon.setTitle(info.name);
            polygon.setSubDescription(regionIcon(info.id));
            polygon.getFillPaint().setColor(fillColor(info));
            boolean highlighted = info.id.equals(currentRegionId) || info.id.equals(selectedRegionId);
            polygon.getOutlinePaint().setColor(highlighted
                    ? Color.rgb(82, 58, 135) : Color.argb(210, 78, 78, 88));
            polygon.getOutlinePaint().setStrokeWidth(highlighted ? dpFloat(2.5f) : dpFloat(1.3f));
            polygon.getOutlinePaint().setStyle(Paint.Style.STROKE);
            polygon.setOnClickListener((poly, mapView, eventPos) -> {
                selectedRegionId = info.id;
                if (listener != null) {
                    listener.onRegionClick(info.id);
                }
                render();
                return true;
            });
            getOverlays().add(polygon);
        }
    }

    private void drawRegionLabels() {
        for (RegionInfo info : RegionRepository.regions()) {
            Marker marker = new Marker(this);
            marker.setPosition(new GeoPoint(info.centerLatitude(), info.centerLongitude()));
            marker.setTitle(info.name);
            marker.setSnippet(regionIcon(info.id));
            marker.setTextLabelFontSize(12);
            marker.setTextLabelBackgroundColor(Color.argb(120, 255, 255, 255));
            marker.setTextLabelForegroundColor(Color.rgb(53, 43, 69));
            marker.setIcon(textIcon(regionIcon(info.id), 11));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setOnMarkerClickListener((m, mapView) -> {
                selectedRegionId = info.id;
                if (listener != null) {
                    listener.onRegionClick(info.id);
                }
                render();
                return true;
            });
            getOverlays().add(marker);
        }
    }

    private void drawPlayerMarkers() {
        List<GeoPoint> placed = new ArrayList<>();
        for (RegionPoint point : points) {
            RegionInfo info = RegionRepository.infoById(point.regionId);
            if (info == null || !isPointInPolygon(point.latitude, point.longitude, geoPoints(info))) {
                continue;
            }
            GeoPoint position = spreadPoint(point, info, placed);
            placed.add(position);
            Marker marker = new Marker(this);
            marker.setPosition(position);
            marker.setTitle(point.username);
            marker.setSnippet("Zvezde: " + point.stars + " | Liga: " + point.leagueName);
            marker.setIcon(dotIcon(colorForRegion(point.regionId), 14));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setOnMarkerClickListener((m, mapView) -> {
                if (listener != null) {
                    listener.onRegionClick(point.regionId);
                }
                m.showInfoWindow();
                return true;
            });
            getOverlays().add(marker);
        }
    }

    private int fillColor(RegionInfo info) {
        int base = colorForRegion(info.id);
        int alpha = info.id.equals(currentRegionId) || info.id.equals(selectedRegionId) ? 74 : 40;
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base));
    }

    private List<RegionInfo> orderedRegionsForDrawing() {
        List<RegionInfo> ordered = new ArrayList<>();
        addRegionIfPresent(ordered, "SUMADIJA_ZAPADNA_SRBIJA");
        addRegionIfPresent(ordered, "JUZNA_ISTOCNA_SRBIJA");
        addRegionIfPresent(ordered, "KOSOVO_METOHIJA");
        addRegionIfPresent(ordered, "VOJVODINA");
        addRegionIfPresent(ordered, "BEOGRAD");
        for (RegionInfo info : RegionRepository.regions()) {
            if (!containsRegion(ordered, info.id)) {
                ordered.add(info);
            }
        }
        return ordered;
    }

    private void addRegionIfPresent(List<RegionInfo> regions, String regionId) {
        RegionInfo info = RegionRepository.infoById(regionId);
        if (info != null) {
            regions.add(info);
        }
    }

    private boolean containsRegion(List<RegionInfo> regions, String regionId) {
        for (RegionInfo info : regions) {
            if (info.id.equals(regionId)) {
                return true;
            }
        }
        return false;
    }

    private GeoPoint spreadPoint(RegionPoint point, RegionInfo info, List<GeoPoint> placed) {
        double lat = point.latitude;
        double lon = point.longitude;
        List<GeoPoint> polygon = geoPoints(info);
        for (int i = 0; i < placed.size(); i++) {
            GeoPoint other = placed.get(i);
            if (Math.abs(other.getLatitude() - lat) < 0.012 && Math.abs(other.getLongitude() - lon) < 0.012) {
                double angle = (i % 8) * Math.PI / 4.0;
                double offset = 0.008 + Math.min(0.012, (i % 3) * 0.004);
                double nextLat = lat + Math.sin(angle) * offset;
                double nextLon = lon + Math.cos(angle) * offset;
                if (isPointInPolygon(nextLat, nextLon, polygon)) {
                    lat = nextLat;
                    lon = nextLon;
                }
            }
        }
        return new GeoPoint(lat, lon);
    }

    private int colorForRegion(String regionId) {
        if ("VOJVODINA".equals(regionId)) return Color.rgb(117, 201, 139);
        if ("BEOGRAD".equals(regionId)) return Color.rgb(111, 181, 232);
        if ("SUMADIJA_ZAPADNA_SRBIJA".equals(regionId)) return Color.rgb(242, 174, 101);
        if ("JUZNA_ISTOCNA_SRBIJA".equals(regionId)) return Color.rgb(169, 137, 223);
        if ("KOSOVO_METOHIJA".equals(regionId)) return Color.rgb(234, 133, 170);
        return Color.rgb(160, 160, 160);
    }

    private String regionIcon(String regionId) {
        if ("VOJVODINA".equals(regionId)) return "V";
        if ("BEOGRAD".equals(regionId)) return "BG";
        if ("SUMADIJA_ZAPADNA_SRBIJA".equals(regionId)) return "SZ";
        if ("JUZNA_ISTOCNA_SRBIJA".equals(regionId)) return "JI";
        if ("KOSOVO_METOHIJA".equals(regionId)) return "KM";
        return "";
    }

    private Drawable dotIcon(int color, int dpSize) {
        int size = dp(dpSize);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size * 0.36f, paint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable textIcon(String text, int sp) {
        int size = dp(20);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(215, 255, 255, 255));
        canvas.drawCircle(size / 2f, size / 2f, size * 0.48f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dpFloat(0.8f)));
        paint.setColor(Color.argb(170, 82, 78, 91));
        canvas.drawCircle(size / 2f, size / 2f, size * 0.45f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(53, 43, 69));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(text, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private List<GeoPoint> geoPoints(RegionInfo info) {
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (int i = 0; i + 1 < info.polygon.length; i += 2) {
            geoPoints.add(new GeoPoint(info.polygon[i + 1], info.polygon[i]));
        }
        return geoPoints;
    }

    public static RegionPoint getRandomPointInsideRegion(String uid, String username, String regionId) {
        RegionInfo info = RegionRepository.infoById(regionId);
        if (info == null) {
            return null;
        }
        List<GeoPoint> polygon = new ArrayList<>();
        for (int i = 0; i + 1 < info.polygon.length; i += 2) {
            polygon.add(new GeoPoint(info.polygon[i + 1], info.polygon[i]));
        }
        Random random = new Random();
        for (int i = 0; i < 600; i++) {
            double lat = info.minLatitude + random.nextDouble() * (info.maxLatitude - info.minLatitude);
            double lon = info.minLongitude + random.nextDouble() * (info.maxLongitude - info.minLongitude);
            if (isPointInPolygon(lat, lon, polygon)) {
                return new RegionPoint(uid, username, info.id, lat, lon);
            }
        }
        GeoPoint centroid = centroid(polygon);
        return new RegionPoint(uid, username, info.id, centroid.getLatitude(), centroid.getLongitude());
    }

    public static boolean isPointInPolygon(double lat, double lon, List<GeoPoint> polygon) {
        boolean inside = false;
        int count = polygon == null ? 0 : polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            double yi = polygon.get(i).getLatitude();
            double xi = polygon.get(i).getLongitude();
            double yj = polygon.get(j).getLatitude();
            double xj = polygon.get(j).getLongitude();
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / ((yj - yi) == 0 ? 0.0000001 : (yj - yi)) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static GeoPoint centroid(List<GeoPoint> polygon) {
        double lat = 0;
        double lon = 0;
        if (polygon == null || polygon.isEmpty()) {
            return new GeoPoint(44.0, 20.8);
        }
        for (GeoPoint point : polygon) {
            lat += point.getLatitude();
            lon += point.getLongitude();
        }
        return new GeoPoint(lat / polygon.size(), lon / polygon.size());
    }
}
