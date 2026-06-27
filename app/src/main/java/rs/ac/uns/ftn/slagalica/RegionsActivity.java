package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionDashboard;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;
import rs.ac.uns.ftn.slagalica.util.GuestSession;

public class RegionsActivity extends AppCompatActivity {
    private RegionRepository regionRepository;
    private FirebaseAuthRepository authRepository;
    private RegionMapView mapView;
    private TextView tvStatus;
    private LinearLayout rankingContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regions);
        regionRepository = new RegionRepository(this);
        authRepository = new FirebaseAuthRepository(this);
        mapView = findViewById(R.id.regionMapView);
        tvStatus = findViewById(R.id.tvRegionStatus);
        rankingContainer = findViewById(R.id.regionRankingContainer);
        mapView.setOnRegionClickListener(this::openDetail);
        loadRegions();
    }

    private void loadRegions() {
        if (!regionRepository.isReady()) {
            tvStatus.setText(R.string.firebase_not_ready);
            return;
        }
        FirebaseUser user = authRepository.currentUser();
        String uid = user == null ? GuestSession.uid(this) : user.getUid();
        tvStatus.setText(R.string.regions_loading);
        regionRepository.loadDashboard(uid)
                .addOnSuccessListener(this::showDashboard)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null
                            ? getString(R.string.firebase_not_ready)
                            : e.getMessage();
                    tvStatus.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void showDashboard(RegionDashboard dashboard) {
        mapView.setData(dashboard.regions, dashboard.points, dashboard.currentUserRegionId);
        if (dashboard.currentUserRegionId == null || dashboard.currentUserRegionId.trim().isEmpty()) {
            tvStatus.setText(R.string.regions_missing_region);
        } else {
            tvStatus.setText(getString(R.string.regions_current_region, dashboard.currentUserRegionName));
        }
        rankingContainer.removeAllViews();
        for (int i = 0; i < dashboard.regions.size(); i++) {
            rankingContainer.addView(rankingRow(i + 1, dashboard.regions.get(i), dashboard.currentUserRegionId));
        }
    }

    private View rankingRow(int place, RegionStats stats, String currentRegionId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        row.setBackgroundResource(stats.regionId.equals(currentRegionId) ? R.drawable.bg_button_secondary : R.drawable.bg_card);
        row.setOnClickListener(v -> openDetail(stats.regionId));

        TextView icon = text(stats.iconName, 18, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(44));
        row.addView(icon, iconParams);

        TextView name = text(place + ". " + stats.regionName, 15, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMargins(dp(12), 0, dp(8), 0);
        row.addView(name, nameParams);

        TextView stars = text(getString(R.string.regions_stars_value, stats.monthlyStars), 14, false);
        stars.setGravity(android.view.Gravity.END);
        row.addView(stars);
        return row;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(Color.rgb(53, 43, 69));
        textView.setTextSize(sp);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private void openDetail(String regionId) {
        Intent intent = new Intent(this, RegionDetailActivity.class);
        intent.putExtra(RegionDetailActivity.EXTRA_REGION_ID, regionId);
        startActivity(intent);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
