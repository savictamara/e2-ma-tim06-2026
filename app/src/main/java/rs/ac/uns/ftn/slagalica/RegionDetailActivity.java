package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;

public class RegionDetailActivity extends AppCompatActivity {
    public static final String EXTRA_REGION_ID = "regionId";

    private RegionRepository regionRepository;
    private TextView tvTitle;
    private TextView tvIcon;
    private TextView tvMonthlyStars;
    private TextView tvFirst;
    private TextView tvSecond;
    private TextView tvThird;
    private TextView tvActive;
    private TextView tvTotal;
    private TextView tvPrevious;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region_detail);
        regionRepository = new RegionRepository(this);
        tvTitle = findViewById(R.id.tvRegionDetailTitle);
        tvIcon = findViewById(R.id.tvRegionDetailIcon);
        tvMonthlyStars = findViewById(R.id.tvRegionMonthlyStars);
        tvFirst = findViewById(R.id.tvRegionFirstPlaces);
        tvSecond = findViewById(R.id.tvRegionSecondPlaces);
        tvThird = findViewById(R.id.tvRegionThirdPlaces);
        tvActive = findViewById(R.id.tvRegionActivePlayers);
        tvTotal = findViewById(R.id.tvRegionTotalPlayers);
        tvPrevious = findViewById(R.id.tvRegionPreviousPlacement);
        findViewById(R.id.btnRegionBack).setOnClickListener(v -> finish());
        loadRegion();
    }

    private void loadRegion() {
        String regionId = getIntent().getStringExtra(EXTRA_REGION_ID);
        if (!regionRepository.isReady()) {
            Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        regionRepository.getRegionStats(regionId)
                .addOnSuccessListener(this::showStats)
                .addOnFailureListener(e -> {
                    String message = e == null || e.getMessage() == null
                            ? getString(R.string.firebase_not_ready)
                            : e.getMessage();
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    private void showStats(RegionStats stats) {
        tvTitle.setText(stats.regionName);
        tvIcon.setText(stats.iconName);
        tvMonthlyStars.setText(getString(R.string.region_detail_monthly_stars, stats.monthlyStars));
        tvFirst.setText(getString(R.string.region_detail_first, stats.firstPlaces));
        tvSecond.setText(getString(R.string.region_detail_second, stats.secondPlaces));
        tvThird.setText(getString(R.string.region_detail_third, stats.thirdPlaces));
        tvActive.setText(getString(R.string.region_detail_active, stats.activePlayers));
        tvTotal.setText(getString(R.string.region_detail_total, stats.totalPlayers));
        tvPrevious.setText(getString(R.string.region_detail_previous, placementText(stats.previousCyclePlacement)));
    }

    private String placementText(long placement) {
        if (placement == 1) return getString(R.string.region_place_first);
        if (placement == 2) return getString(R.string.region_place_second);
        if (placement == 3) return getString(R.string.region_place_third);
        return getString(R.string.region_place_none);
    }
}
