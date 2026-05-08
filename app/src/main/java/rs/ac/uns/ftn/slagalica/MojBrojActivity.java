package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {
    private final Random random = new Random();
    private int targetNumber = 0;
    private int points = 0;
    private CountDownTimer roundTimer;
    private boolean roundEnded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        TextView tvTarget = findViewById(R.id.tvTarget);
        TextView tvTimer = findViewById(R.id.tvMojBrojTimer);
        TextView tvNumbers = findViewById(R.id.tvNumbers);
        TextView tvResult = findViewById(R.id.tvMojBrojResult);
        TextView tvPoints = findViewById(R.id.tvMojBrojPoints);
        EditText etExpression = findViewById(R.id.etExpression);

        Button btnStopTarget = findViewById(R.id.btnStopTarget);
        Button btnStopNumbers = findViewById(R.id.btnStopNumbers);
        Button btnDelete = findViewById(R.id.btnDelete);
        Button btnConfirm = findViewById(R.id.btnConfirm);

        updateTarget(tvTarget);
        updatePoints(tvPoints);
        startTimer(tvTimer, etExpression, btnStopTarget, btnStopNumbers, btnDelete, btnConfirm, tvResult);

        btnStopTarget.setOnClickListener(v -> {
            if (roundEnded) {
                return;
            }
            targetNumber = 100 + random.nextInt(900);
            updateTarget(tvTarget);
        });

        btnStopNumbers.setOnClickListener(v -> {
            if (roundEnded) {
                return;
            }
            int[] values = {
                    1 + random.nextInt(9),
                    1 + random.nextInt(9),
                    1 + random.nextInt(9),
                    1 + random.nextInt(9),
                    new int[]{10, 15, 20}[random.nextInt(3)],
                    new int[]{25, 50, 75, 100}[random.nextInt(4)]
            };
            tvNumbers.setText(values[0] + "  " + values[1] + "  " + values[2] + "  " + values[3] + "  " + values[4] + "  " + values[5]);
        });

        btnDelete.setOnClickListener(v -> {
            if (roundEnded) {
                return;
            }
            String expression = etExpression.getText().toString();
            if (!expression.isEmpty()) {
                etExpression.setText(expression.substring(0, expression.length() - 1));
                etExpression.setSelection(etExpression.getText().length());
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (roundEnded) {
                return;
            }
            String expression = etExpression.getText().toString().trim();
            if (expression.isEmpty()) {
                tvResult.setText(getString(R.string.result_text, "Unesite izraz."));
                points = 0;
            } else {
                int mockResult = 100 + random.nextInt(900);
                int distance = Math.abs(targetNumber - mockResult);
                if (distance == 0) {
                    points = 10;
                } else if (distance <= 10) {
                    points = 5;
                } else {
                    points = 0;
                }
                tvResult.setText(getString(R.string.result_text, "Rezultat izraza: " + mockResult));
            }
            updatePoints(tvPoints);
        });
    }

    private void startTimer(
            TextView tvTimer,
            EditText etExpression,
            Button btnStopTarget,
            Button btnStopNumbers,
            Button btnDelete,
            Button btnConfirm,
            TextView tvResult
    ) {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        tvTimer.setText(getString(R.string.timer_text_60));
        roundTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                roundEnded = true;
                tvTimer.setText(getString(R.string.timer_text, 0));
                etExpression.setEnabled(false);
                btnStopTarget.setEnabled(false);
                btnStopNumbers.setEnabled(false);
                btnDelete.setEnabled(false);
                btnConfirm.setEnabled(false);
                tvResult.setText(getString(R.string.moj_broj_round_end));
            }
        };
        roundTimer.start();
    }

    @Override
    protected void onDestroy() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        super.onDestroy();
    }

    private void updateTarget(TextView tvTarget) {
        tvTarget.setText(getString(R.string.target_number, targetNumber));
    }

    private void updatePoints(TextView tvPoints) {
        tvPoints.setText(getString(R.string.points_text, points));
    }
}
