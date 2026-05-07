package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SpojniceActivity extends AppCompatActivity {
    private final String[][] leftRounds = {
            {"Tesla", "Andric", "Novak", "Nusic", "Mokranjac"},
            {"Jabuka", "Sargarepa", "Spinat", "Sljiva", "Krompir"}
    };

    private final String[][] rightRounds = {
            {"Pisac", "Kompozitor", "Naucnik", "Teniser", "Dramaturg"},
            {"Vitaminski list", "Korenasto povrce", "Krtolasto povrce", "Vocna vrsta", "Jezgrasto voce"}
    };

    private final int[][] pairMap = {
            {2, 0, 3, 4, 1},
            {3, 1, 0, 4, 2}
    };

    private final Button[] leftButtons = new Button[5];
    private final Button[] rightButtons = new Button[5];

    private TextView tvRound;
    private TextView tvTimer;
    private TextView tvPoints;
    private TextView tvRoundPoints;
    private TextView tvStatus;
    private Button btnCheckRound;
    private int currentRound = 0;
    private int totalPoints = 0;
    private int currentRoundPoints = 0;
    private int selectedLeft = -1;
    private int selectedRight = -1;
    private final int[] selectedPairs = {-1, -1, -1, -1, -1};
    private CountDownTimer roundTimer;
    private boolean roundFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        tvRound = findViewById(R.id.tvRound);
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        tvRoundPoints = findViewById(R.id.tvRoundPoints);
        tvStatus = findViewById(R.id.tvStatus);
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
            } else if (currentRound == 0) {
                currentRound = 1;
                setupRound();
            }
        });

        setupRound();
    }

    private void setupRound() {
        selectedLeft = -1;
        selectedRight = -1;
        roundFinished = false;
        currentRoundPoints = 0;
        tvStatus.setText("");
        btnCheckRound.setText(R.string.spojnice_check_round);
        tvRound.setText(getString(R.string.spojnice_round, currentRound + 1, 2));
        tvPoints.setText(getString(R.string.spojnice_total_points, totalPoints));
        tvRoundPoints.setText(getString(R.string.spojnice_round_points, currentRoundPoints));

        for (int i = 0; i < 5; i++) {
            selectedPairs[i] = -1;
            leftButtons[i].setText(leftRounds[currentRound][i]);
            rightButtons[i].setText(rightRounds[currentRound][i]);
            leftButtons[i].setEnabled(true);
            rightButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundResource(R.drawable.bg_step);
            rightButtons[i].setBackgroundResource(R.drawable.bg_step);
        }

        startRoundTimer();
    }

    private void onLeftSelected(int leftIndex) {
        if (roundFinished) {
            return;
        }
        selectedLeft = leftIndex;
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
            refreshSelectionVisuals();
            return;
        }

        selectedRight = rightIndex;
        refreshSelectionVisuals();

        if (selectedLeft == -1 || selectedPairs[selectedLeft] != -1) {
            return;
        }

        selectedPairs[selectedLeft] = rightIndex;
        selectedLeft = -1;
        selectedRight = -1;
        refreshSelectionVisuals();
    }

    private void refreshSelectionVisuals() {
        for (int i = 0; i < leftButtons.length; i++) {
            if (selectedPairs[i] != -1) {
                leftButtons[i].setBackgroundResource(R.drawable.bg_answer_selected);
            } else if (i == selectedLeft) {
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

    private int findPairOwner(int rightIndex) {
        for (int i = 0; i < selectedPairs.length; i++) {
            if (selectedPairs[i] == rightIndex) {
                return i;
            }
        }
        return -1;
    }

    private void highlightWrongPair(int leftIndex) {
        leftButtons[leftIndex].setBackgroundResource(R.drawable.bg_answer_wrong);
        int pairedRight = selectedPairs[leftIndex];
        if (pairedRight >= 0) {
            int rightOwner = findPairOwner(pairedRight);
            if (rightOwner == leftIndex || (rightOwner >= 0 && selectedPairs[rightOwner] != pairMap[currentRound][rightOwner])) {
                rightButtons[pairedRight].setBackgroundResource(R.drawable.bg_answer_wrong);
            }
        }
    }

    private void finishRound() {
        roundFinished = true;
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        int matched = 0;
        for (int i = 0; i < pairMap[currentRound].length; i++) {
            boolean correct = selectedPairs[i] == pairMap[currentRound][i];
            if (correct) {
                matched++;
                leftButtons[i].setBackgroundResource(R.drawable.bg_answer_correct);
                rightButtons[selectedPairs[i]].setBackgroundResource(R.drawable.bg_answer_correct);
            } else {
                highlightWrongPair(i);
            }
            leftButtons[i].setEnabled(false);
            rightButtons[i].setEnabled(false);
        }

        currentRoundPoints = matched * 2;
        totalPoints += currentRoundPoints;
        tvRoundPoints.setText(getString(R.string.spojnice_round_points, currentRoundPoints));
        tvPoints.setText(getString(R.string.spojnice_total_points, totalPoints));
        tvStatus.setText(getString(R.string.spojnice_round_result, matched, currentRoundPoints));

        if (currentRound == 0) {
            btnCheckRound.setText(R.string.spojnice_next_round);
        } else {
            btnCheckRound.setText(R.string.spojnice_finished);
            btnCheckRound.setEnabled(false);
            tvStatus.setText(getString(R.string.spojnice_final_result, totalPoints));
        }
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
}
