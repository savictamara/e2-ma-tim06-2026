package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class KorakPoKorakActivity extends AppCompatActivity {
    private final String[] mockSteps = {
            "1. Muzicar", "2. Novi Sad", "3. Tamburica", "4. Balada", "5. Panonski", "6. Olivera", "7. Djordje "
    };
    private final TextView[] stepViews = new TextView[7];
    private int openedSteps = 0;
    private int points = 20;
    private final int mockPlayerTwoPoints = 14;
    private TextView tvTimer;
    private TextView tvPoints;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        stepViews[0] = findViewById(R.id.step1);
        stepViews[1] = findViewById(R.id.step2);
        stepViews[2] = findViewById(R.id.step3);
        stepViews[3] = findViewById(R.id.step4);
        stepViews[4] = findViewById(R.id.step5);
        stepViews[5] = findViewById(R.id.step6);
        stepViews[6] = findViewById(R.id.step7);

        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);
        EditText etSolution = findViewById(R.id.etSolution);
        TextView tvResult = findViewById(R.id.tvResult);
        Button btnOpenStep = findViewById(R.id.btnOpenStep);
        Button btnCheckSolution = findViewById(R.id.btnCheckSolution);

        updatePoints();
        startMockTimer();

        btnOpenStep.setOnClickListener(v -> {
            if (openedSteps < 7) {
                stepViews[openedSteps].setText(mockSteps[openedSteps]);
                openedSteps++;
                if (points > 0) {
                    points = Math.max(0, points - 2);
                    updatePoints();
                }
            }
        });

        btnCheckSolution.setOnClickListener(v -> {
            String solution = etSolution.getText().toString().trim();
            if (solution.equalsIgnoreCase("Balasevic") || solution.equalsIgnoreCase("Balasevic")) {
                tvResult.setText(getString(R.string.result_text, "Tacno, osvajate " + points + " bodova."));
            } else {
                tvResult.setText(getString(R.string.result_text, "Netacno. Pokusajte ponovo."));
            }
        });
    }

    private void startMockTimer() {
        new CountDownTimer(70000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
            }
        }.start();
    }

    private void updatePoints() {
        tvPoints.setText(getString(R.string.points_text, points));
        tvPlayer1Score.setText(getString(R.string.player_points, points));
        tvPlayer2Score.setText(getString(R.string.player_points, mockPlayerTwoPoints));
    }
}
