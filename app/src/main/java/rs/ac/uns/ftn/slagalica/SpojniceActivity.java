package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SpojniceActivity extends AppCompatActivity {
    private final String[] leftItems = {"Tesla", "Andric", "Novak", "Nusic", "Mokranjac"};
    private final String[] rightItems = {"Pisac", "Kompozitor", "Naucnik", "Teniser", "Dramaturg"};
    private final int[] pairMap = {2, 0, 3, 4, 1};

    private final Button[] leftButtons = new Button[5];
    private final Button[] rightButtons = new Button[5];
    private final int[] selectedPairs = {-1, -1, -1, -1, -1};

    private TextView tvRound;
    private TextView tvTimer;
    private TextView tvPoints;
    private TextView tvStatus;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;
    private Button btnCheckRound;

    private int points = 0;
    private int selectedLeft = -1;
    private int selectedRight = -1;
    private final int mockPlayerTwoPoints = 8;
    private boolean roundFinished = false;
    private CountDownTimer roundTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        tvRound = findViewById(R.id.tvRound);
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayer1Score = findViewById(R.id.tvSpojnicePlayer1);
        tvPlayer2Score = findViewById(R.id.tvSpojnicePlayer2);
        btnCheckRound = findViewById(R.id.btnCheckRound);

        leftButtons[0] = findViewById(R.id.btnLeft1);
        leftButtons[1] = findViewById(R.id.btnLeft2);
        leftButtons[2] = findViewById(R.id.btnLeft3);
        leftButtons[3] = findViewById(R.id.btnLeft4);
        leftButtons[4] = findViewById(R.id.btnLeft5);

        rightButtons[0] = findViewById(R.id.btnRight1);
        rightButtons[1] = findViewById(R.id.btnRight2);
        rightButtons[2] = findViewById(R.id.btnRight3);
        rightButtons[3] = findViewById(R.id.btnRight4);
        rightButtons[4] = findViewById(R.id.btnRight5);

        for (int i = 0; i < leftButtons.length; i++) {
            final int leftIndex = i;
            leftButtons[i].setOnClickListener(v -> onLeftSelected(leftIndex));
        }

        for (int i = 0; i < rightButtons.length; i++) {
            final int rightIndex = i;
            rightButtons[i].setOnClickListener(v -> onRightSelected(rightIndex));
        }

        btnCheckRound.setOnClickListener(v -> {
            if (!roundFinished) {
                finishRound();
            }
        });

        setupRound();
    }

    private void setupRound() {
        selectedLeft = -1;
        selectedRight = -1;
        points = 0;
        roundFinished = false;
        tvRound.setText(getString(R.string.round_one_of_two));
        tvStatus.setText("");
        btnCheckRound.setEnabled(true);
        btnCheckRound.setText(R.string.spojnice_check_round);
        tvPoints.setText(getString(R.string.spojnice_points_single_round, points));
        updateMockPlayerScores();

        for (int i = 0; i < 5; i++) {
            selectedPairs[i] = -1;
            leftButtons[i].setText(leftItems[i]);
            rightButtons[i].setText(rightItems[i]);
            leftButtons[i].setEnabled(true);
            rightButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundResource(R.drawable.bg_step);
            rightButtons[i].setBackgroundResource(R.drawable.bg_step);
        }

        startRoundTimer();
    }

    private void onLeftSelected(int leftIndex) {
        if (roundFinished || selectedPairs[leftIndex] != -1) {
            return;
        }
        selectedLeft = leftIndex;
        selectedRight = -1;
        refreshSelectionVisuals();
    }

    private void onRightSelected(int rightIndex) {
        if (roundFinished) {
            return;
        }

        boolean rightTaken = false;
        for (int pair : selectedPairs) {
            if (pair == rightIndex) {
                rightTaken = true;
                break;
            }
        }
        if (rightTaken) {
            return;
        }

        selectedRight = rightIndex;
        if (selectedLeft == -1) {
            refreshSelectionVisuals();
            return;
        }

        selectedPairs[selectedLeft] = selectedRight;
        selectedLeft = -1;
        selectedRight = -1;
        refreshSelectionVisuals();
    }

    private void refreshSelectionVisuals() {
        for (int i = 0; i < leftButtons.length; i++) {
            if (selectedPairs[i] != -1 || i == selectedLeft) {
                leftButtons[i].setBackgroundResource(R.drawable.bg_answer_selected);
            } else {
                leftButtons[i].setBackgroundResource(R.drawable.bg_step);
            }
        }

        for (int i = 0; i < rightButtons.length; i++) {
            boolean isPaired = false;
            for (int selectedPair : selectedPairs) {
                if (selectedPair == i) {
                    isPaired = true;
                    break;
                }
            }
            if (isPaired || i == selectedRight) {
                rightButtons[i].setBackgroundResource(R.drawable.bg_answer_selected);
            } else {
                rightButtons[i].setBackgroundResource(R.drawable.bg_step);
            }
        }
    }

    private void finishRound() {
        roundFinished = true;
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        int matched = 0;
        for (int i = 0; i < pairMap.length; i++) {
            boolean correct = selectedPairs[i] == pairMap[i];
            if (correct) {
                matched++;
                leftButtons[i].setBackgroundResource(R.drawable.bg_answer_correct);
                rightButtons[selectedPairs[i]].setBackgroundResource(R.drawable.bg_answer_correct);
            } else {
                leftButtons[i].setBackgroundResource(R.drawable.bg_answer_wrong);
                if (selectedPairs[i] >= 0) {
                    rightButtons[selectedPairs[i]].setBackgroundResource(R.drawable.bg_answer_wrong);
                }
            }
            leftButtons[i].setEnabled(false);
            rightButtons[i].setEnabled(false);
        }

        points = matched * 2;
        tvPoints.setText(getString(R.string.spojnice_points_single_round, points));
        updateMockPlayerScores();
        tvStatus.setText(getString(R.string.spojnice_round_result, matched, points));
        btnCheckRound.setText(R.string.spojnice_finished);
        btnCheckRound.setEnabled(false);
    }

    private void startRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        roundTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                if (!roundFinished) {
                    finishRound();
                }
            }
        };
        roundTimer.start();
    }

    private void updateMockPlayerScores() {
        tvPlayer1Score.setText(getString(R.string.player_points, points));
        tvPlayer2Score.setText(getString(R.string.player_points, mockPlayerTwoPoints));
    }

    @Override
    protected void onDestroy() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        super.onDestroy();
    }
}
