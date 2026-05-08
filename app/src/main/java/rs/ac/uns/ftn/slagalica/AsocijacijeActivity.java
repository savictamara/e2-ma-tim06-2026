package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AsocijacijeActivity extends AppCompatActivity {
    private static final int ROUND_MS = 120_000;
    private static final int TOTAL_ROUNDS = 2;
    private final Random random = new Random();

    private final String[][] clues = {
            {"TOPLO", "LETO", "PLAZA", "SUNCE"},
            {"HLADNO", "SNEG", "LED", "SKIJANJE"},
            {"VETAR", "MUNJA", "OBLAK", "KISA"},
            {"JUTRO", "NOC", "KALENDAR", "MESEC"}
    };
    private final String[] columnSolutions = {"MORE", "ZIMA", "VREME", "DAN"};
    private final String finalSolution = "GODISNJA DOBA";

    private final int[] cellIds = {
            R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4,
            R.id.cell5, R.id.cell6, R.id.cell7, R.id.cell8,
            R.id.cell9, R.id.cell10, R.id.cell11, R.id.cell12,
            R.id.cell13, R.id.cell14, R.id.cell15, R.id.cell16
    };

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScoreP1;
    private TextView tvScoreP2;
    private TextView tvStatus;
    private EditText etAnswerA;
    private EditText etAnswerB;
    private EditText etAnswerC;
    private EditText etAnswerD;
    private EditText etFinal;
    private Button btnNextRound;
    private Button[] guessButtons;
    private EditText[] answerInputs;

    private int round = 1;
    private int activePlayer = 1;
    private int starterForRound = 1;
    private int scoreP1 = 0;
    private int scoreP2 = 0;
    private boolean roundFinished = false;
    private boolean finalSolved = false;
    private final boolean[][] opened = new boolean[4][4];
    private final boolean[] solvedColumns = new boolean[4];
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        tvRound = findViewById(R.id.tvRound);
        tvCurrentPlayer = findViewById(R.id.tvCurrentPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScoreP1 = findViewById(R.id.tvScoreP1);
        tvScoreP2 = findViewById(R.id.tvScoreP2);
        tvStatus = findViewById(R.id.tvStatus);
        etAnswerA = findViewById(R.id.etAnswerA);
        etAnswerB = findViewById(R.id.etAnswerB);
        etAnswerC = findViewById(R.id.etAnswerC);
        etAnswerD = findViewById(R.id.etAnswerD);
        etFinal = findViewById(R.id.etFinal);
        Button btnOpenField = findViewById(R.id.btnOpenField);
        Button btnGuessFinal = findViewById(R.id.btnGuessFinal);
        btnNextRound = findViewById(R.id.btnNextRound);
        guessButtons = new Button[] {
                findViewById(R.id.btnGuessA),
                findViewById(R.id.btnGuessB),
                findViewById(R.id.btnGuessC),
                findViewById(R.id.btnGuessD)
        };
        answerInputs = new EditText[] { etAnswerA, etAnswerB, etAnswerC, etAnswerD };

        btnOpenField.setOnClickListener(v -> openRandomField());
        guessButtons[0].setOnClickListener(v -> guessColumn(0));
        guessButtons[1].setOnClickListener(v -> guessColumn(1));
        guessButtons[2].setOnClickListener(v -> guessColumn(2));
        guessButtons[3].setOnClickListener(v -> guessColumn(3));
        btnGuessFinal.setOnClickListener(v -> guessFinal());
        btnNextRound.setOnClickListener(v -> nextRound());

        setupRound();
    }

    private void setupRound() {
        stopTimer();
        roundFinished = false;
        finalSolved = false;
        activePlayer = starterForRound;
        for (int c = 0; c < 4; c++) {
            solvedColumns[c] = false;
            for (int r = 0; r < 4; r++) {
                opened[c][r] = false;
                TextView cell = findViewById(cellIds[c * 4 + r]);
                cell.setText(String.format(Locale.ROOT, "%c%d: ?", 'A' + c, r + 1));
            }
        }
        clearInputs();
        btnNextRound.setEnabled(false);
        tvStatus.setText("");
        updateHeader();
        startTimer();
    }

    private void clearInputs() {
        etAnswerA.setText("");
        etAnswerB.setText("");
        etAnswerC.setText("");
        etAnswerD.setText("");
        etFinal.setText("");
    }

    private void openRandomField() {
        if (roundFinished) return;
        List<int[]> closed = new ArrayList<>();
        for (int c = 0; c < 4; c++) {
            if (solvedColumns[c]) continue;
            for (int r = 0; r < 4; r++) {
                if (!opened[c][r]) closed.add(new int[]{c, r});
            }
        }
        if (closed.isEmpty()) return;
        int[] choice = closed.get(random.nextInt(closed.size()));
        int c = choice[0];
        int r = choice[1];
        opened[c][r] = true;
        TextView cell = findViewById(cellIds[c * 4 + r]);
        cell.setText(String.format(Locale.ROOT, "%c%d: %s", 'A' + c, r + 1, clues[c][r]));
        switchPlayer();
        tvStatus.setText("Otvoreno polje. Sledeci igrac je na potezu.");
    }

    private void guessColumn(int col) {
        if (roundFinished) return;
        if (solvedColumns[col]) {
            tvStatus.setText("Kolona je vec resena.");
            return;
        }
        String answer = answerInputs[col].getText().toString().trim();
        if (answer.equalsIgnoreCase(columnSolutions[col])) {
            solvedColumns[col] = true;
            int openedCount = countOpenedInColumn(col);
            int points = 2 + (4 - openedCount);
            addPoints(activePlayer, points);
            revealColumn(col);
            tvStatus.setText("Tacna kolona. +" + points + " poena. Igrac nastavlja.");
        } else {
            tvStatus.setText("Netacno resenje kolone.");
            switchPlayer();
        }
        updateHeader();
    }

    private void guessFinal() {
        if (roundFinished) return;
        String answer = etFinal.getText().toString().trim();
        if (answer.equalsIgnoreCase(finalSolution)) {
            finalSolved = true;
            int finalPoints = 7;
            int columnsPoints = 0;
            for (int col = 0; col < 4; col++) {
                if (!solvedColumns[col]) {
                    if (countOpenedInColumn(col) == 0) {
                        columnsPoints += 6;
                    } else {
                        columnsPoints += 2 + (4 - countOpenedInColumn(col));
                    }
                    solvedColumns[col] = true;
                    revealColumn(col);
                }
            }
            addPoints(activePlayer, finalPoints + columnsPoints);
            tvStatus.setText("Tacno konacno resenje. +" + (finalPoints + columnsPoints) + " poena.");
            finishRound();
        } else {
            tvStatus.setText("Netacno konacno resenje.");
            switchPlayer();
            updateHeader();
        }
    }

    private void revealColumn(int col) {
        for (int r = 0; r < 4; r++) {
            opened[col][r] = true;
            TextView cell = findViewById(cellIds[col * 4 + r]);
            cell.setText(String.format(Locale.ROOT, "%c%d: %s", 'A' + col, r + 1, clues[col][r]));
        }
    }

    private int countOpenedInColumn(int col) {
        int count = 0;
        for (int r = 0; r < 4; r++) if (opened[col][r]) count++;
        return count;
    }

    private void switchPlayer() {
        activePlayer = activePlayer == 1 ? 2 : 1;
        updateHeader();
    }

    private void addPoints(int player, int points) {
        if (player == 1) scoreP1 += points;
        else scoreP2 += points;
    }

    private void finishRound() {
        roundFinished = true;
        stopTimer();
        btnNextRound.setEnabled(true);
        if (round >= TOTAL_ROUNDS) {
            btnNextRound.setEnabled(false);
            tvStatus.append(" Kraj igre.");
        }
        updateHeader();
    }

    private void nextRound() {
        if (!roundFinished || round >= TOTAL_ROUNDS) return;
        round++;
        starterForRound = 2;
        setupRound();
    }

    private void startTimer() {
        timer = new CountDownTimer(ROUND_MS, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }
            @Override public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                tvStatus.setText("Vreme runde je isteklo.");
                finishRound();
            }
        };
        timer.start();
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvCurrentPlayer.setText("Na potezu: Igrac " + activePlayer);
        tvScoreP1.setText("Igrac 1: " + scoreP1 + " poena");
        tvScoreP2.setText("Igrac 2: " + scoreP2 + " poena");
    }

    private void stopTimer() {
        if (timer != null) timer.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}
