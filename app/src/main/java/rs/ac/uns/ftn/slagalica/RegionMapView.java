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

import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionInfo;
import rs.ac.uns.ftn.slagalica.domain.model.RegionPoint;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;

public class RegionMapView extends View {
    public interface OnRegionClickListener {
        void onRegionClick(String regionId);
    }

    private static final double MIN_LAT = 42.0;
    private static final double MAX_LAT = 46.3;
    private static final double MIN_LON = 18.7;
    private static final double MAX_LON = 23.0;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RegionStats> regions = new ArrayList<>();
    private final List<RegionPoint> points = new ArrayList<>();
    private final List<RegionHit> hits = new ArrayList<>();
    private String currentRegionId = "";
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
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<RegionStats> regionStats, List<RegionPoint> regionPoints, String selectedRegionId) {
        regions.clear();
        points.clear();
        if (regionStats != null) {
            regions.addAll(regionStats);
        }
        if (regionPoints != null) {
            points.addAll(regionPoints);
        }
        currentRegionId = selectedRegionId == null ? "" : selectedRegionId;
        invalidate();
    }

    public void setOnRegionClickListener(OnRegionClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hits.clear();
        float left = dp(16);
        float top = dp(14);
        float right = getWidth() - dp(16);
        float bottom = getHeight() - dp(14);

        fillPaint.setColor(Color.rgb(253, 249, 255));
        strokePaint.setColor(Color.rgb(199, 178, 255));
        RectF card = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(card, dp(18), dp(18), fillPaint);
        canvas.drawRoundRect(card, dp(18), dp(18), strokePaint);

        drawSerbiaShape(canvas, left, top, right, bottom);
        drawRegions(canvas, left, top, right, bottom);
        drawPoints(canvas, left, top, right, bottom);
    }

    private void drawSerbiaShape(Canvas canvas, float left, float top, float right, float bottom) {
        Path path = new Path();
        path.moveTo(x(20.0, left, right), y(46.1, top, bottom));
        path.lineTo(x(21.3, left, right), y(46.0, top, bottom));
        path.lineTo(x(21.9, left, right), y(45.0, top, bottom));
        path.lineTo(x(22.7, left, right), y(44.4, top, bottom));
        path.lineTo(x(22.4, left, right), y(43.4, top, bottom));
        path.lineTo(x(21.8, left, right), y(42.2, top, bottom));
        path.lineTo(x(20.5, left, right), y(42.0, top, bottom));
        path.lineTo(x(19.5, left, right), y(43.5, top, bottom));
        path.lineTo(x(19.1, left, right), y(44.5, top, bottom));
        path.lineTo(x(18.9, left, right), y(45.4, top, bottom));
        path.close();

        fillPaint.setColor(Color.rgb(247, 204, 224));
        strokePaint.setColor(Color.rgb(238, 158, 198));
        canvas.drawPath(path, fillPaint);
        canvas.drawPath(path, strokePaint);
    }

    private void drawRegions(Canvas canvas, float left, float top, float right, float bottom) {
        textPaint.setTextSize(dp(12));
        textPaint.setFakeBoldText(true);
        for (RegionStats stats : regions) {
            RegionInfo info = RegionRepository.infoById(stats.regionId);
            if (info == null) {
                continue;
            }
            float centerX = x(info.centerLongitude(), left, right);
            float centerY = y(info.centerLatitude(), top, bottom);
            boolean selected = stats.regionId.equals(currentRegionId);
            fillPaint.setColor(selected ? Color.rgb(238, 158, 198) : Color.rgb(220, 206, 255));
            strokePaint.setColor(selected ? Color.rgb(53, 43, 69) : Color.rgb(183, 152, 255));
            RectF icon = new RectF(centerX - dp(19), centerY - dp(18), centerX + dp(19), centerY + dp(18));
            canvas.drawRoundRect(icon, dp(12), dp(12), fillPaint);
            canvas.drawRoundRect(icon, dp(12), dp(12), strokePaint);
            textPaint.setColor(Color.rgb(53, 43, 69));
            canvas.drawText(stats.iconName, centerX, centerY + dp(5), textPaint);
            hits.add(new RegionHit(stats.regionId, icon));
        }
    }

    private void drawPoints(Canvas canvas, float left, float top, float right, float bottom) {
        pointPaint.setColor(Color.rgb(53, 43, 69));
        for (RegionPoint point : points) {
            canvas.drawCircle(x(point.longitude, left, right), y(point.latitude, top, bottom), dp(3), pointPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && listener != null) {
            for (RegionHit hit : hits) {
                if (hit.bounds.contains(event.getX(), event.getY())) {
                    listener.onRegionClick(hit.regionId);
                    return true;
                }
            }
        }
        return true;
    }

    private float x(double longitude, float left, float right) {
        double ratio = (longitude - MIN_LON) / (MAX_LON - MIN_LON);
        return (float) (left + ratio * (right - left));
    }

    private float y(double latitude, float top, float bottom) {
        double ratio = (MAX_LAT - latitude) / (MAX_LAT - MIN_LAT);
        return (float) (top + ratio * (bottom - top));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static class RegionHit {
        final String regionId;
        final RectF bounds;

        RegionHit(String regionId, RectF bounds) {
            this.regionId = regionId;
            this.bounds = bounds;
        }
    }
}
