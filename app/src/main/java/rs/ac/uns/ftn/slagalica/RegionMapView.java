package rs.ac.uns.ftn.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionInfo;
import rs.ac.uns.ftn.slagalica.domain.model.RegionPoint;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;

public class RegionMapView extends View {
    public interface OnRegionClickListener {
        void onRegionClick(String regionId);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RegionStats> regions = new ArrayList<>();
    private final List<RegionPoint> points = new ArrayList<>();
    private String currentRegionId = "";
    private String selectedRegionId = "";
    private OnRegionClickListener listener;

    public RegionMapView(Context context) {
        super(context);
        init();
    }

    public RegionMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        pointPaint.setStyle(Paint.Style.FILL);
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
        invalidate();
    }

    public void setSelectedRegionId(String regionId) {
        selectedRegionId = regionId == null ? "" : regionId;
        invalidate();
    }

    public String getSelectedRegionId() {
        return selectedRegionId;
    }

    public void setOnRegionClickListener(OnRegionClickListener listener) {
        this.listener = listener;
    }

    public static RegionPoint getRandomPointInsideRegion(String uid, String username, String regionId) {
        RegionInfo info = RegionRepository.infoById(regionId);
        if (info == null) {
            return null;
        }
        Random random = new Random();
        for (int i = 0; i < 200; i++) {
            float x = random.nextFloat();
            float y = random.nextFloat();
            if (info.contains(x, y)) {
                return new RegionPoint(uid, username, info.id, x, y);
            }
        }
        return new RegionPoint(uid, username, info.id, info.centerX(), info.centerY());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF bounds = mapBounds();
        drawMapContainer(canvas, bounds);
        drawRegions(canvas, bounds);
        drawPoints(canvas, bounds);
    }

    private void drawMapContainer(Canvas canvas, RectF bounds) {
        fillPaint.setColor(Color.rgb(253, 249, 255));
        strokePaint.setColor(Color.rgb(199, 178, 255));
        canvas.drawRoundRect(bounds, dp(18), dp(18), fillPaint);
        canvas.drawRoundRect(bounds, dp(18), dp(18), strokePaint);
    }

    private void drawRegions(Canvas canvas, RectF bounds) {
        textPaint.setTextSize(dp(11));
        for (RegionInfo info : RegionRepository.regions()) {
            boolean selected = info.id.equals(selectedRegionId);
            boolean current = info.id.equals(currentRegionId);
            Path path = pathFor(info, bounds);
            fillPaint.setColor(selected ? Color.rgb(238, 158, 198) : info.color);
            strokePaint.setColor(current || selected ? Color.rgb(53, 43, 69) : Color.WHITE);
            strokePaint.setStrokeWidth(current || selected ? dp(4) : dp(2));
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);

            float cx = mapX(info.centerX(), bounds);
            float cy = mapY(info.centerY(), bounds);
            fillPaint.setColor(Color.argb(210, 255, 255, 255));
            RectF icon = new RectF(cx - dp(18), cy - dp(16), cx + dp(18), cy + dp(16));
            canvas.drawRoundRect(icon, dp(9), dp(9), fillPaint);
            textPaint.setColor(Color.rgb(53, 43, 69));
            canvas.drawText(info.iconName, cx, cy + dp(5), textPaint);
        }
    }

    private void drawPoints(Canvas canvas, RectF bounds) {
        pointPaint.setColor(Color.rgb(53, 43, 69));
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(dp(1));
        for (RegionPoint point : points) {
            RegionInfo info = RegionRepository.infoById(point.regionId);
            if (info == null || !info.contains(point.x, point.y)) {
                continue;
            }
            float cx = mapX(point.x, bounds);
            float cy = mapY(point.y, bounds);
            canvas.drawCircle(cx, cy, dp(4), pointPaint);
            canvas.drawCircle(cx, cy, dp(4), strokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            RectF bounds = mapBounds();
            float x = unmapX(event.getX(), bounds);
            float y = unmapY(event.getY(), bounds);
            for (RegionInfo info : RegionRepository.regions()) {
                if (info.contains(x, y)) {
                    selectedRegionId = info.id;
                    invalidate();
                    if (listener != null) {
                        listener.onRegionClick(info.id);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private Path pathFor(RegionInfo info, RectF bounds) {
        Path path = new Path();
        for (int i = 0; i + 1 < info.polygon.length; i += 2) {
            float x = mapX(info.polygon[i], bounds);
            float y = mapY(info.polygon[i + 1], bounds);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    private RectF mapBounds() {
        return new RectF(dp(16), dp(14), getWidth() - dp(16), getHeight() - dp(14));
    }

    private float mapX(float x, RectF bounds) {
        return bounds.left + x * bounds.width();
    }

    private float mapY(float y, RectF bounds) {
        return bounds.top + y * bounds.height();
    }

    private float unmapX(float x, RectF bounds) {
        return Math.max(0f, Math.min(1f, (x - bounds.left) / bounds.width()));
    }

    private float unmapY(float y, RectF bounds) {
        return Math.max(0f, Math.min(1f, (y - bounds.top) / bounds.height()));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
