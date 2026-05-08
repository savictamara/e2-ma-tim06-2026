package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class SkockoActivity extends AppCompatActivity {
    private static final char[] SYMBOLS = {'S', 'K', 'O', 'H', 'T', 'Z'};
    private static final int[] SYMBOL_DRAWABLES = {
            R.drawable.skocko,
            R.drawable.black_square,
            R.drawable.circle,
            R.drawable.heart_2,
            R.drawable.triangle,
            R.drawable.star
    };
    private static final int ROUND_MS = 30_000;
    private static final int STEAL_MS = 10_000;
    private final Random random = new Random();
    private final int[][] rowSlotIds = {
            {R.id.row1slot1, R.id.row1slot2, R.id.row1slot3, R.id.row1slot4},
            {R.id.row2slot1, R.id.row2slot2, R.id.row2slot3, R.id.row2slot4},
            {R.id.row3slot1, R.id.row3slot2, R.id.row3slot3, R.id.row3slot4},
            {R.id.row4slot1, R.id.row4slot2, R.id.row4slot3, R.id.row4slot4},
            {R.id.row5slot1, R.id.row5slot2, R.id.row5slot3, R.id.row5slot4},
            {R.id.row6slot1, R.id.row6slot2, R.id.row6slot3, R.id.row6slot4}
    };
    private final int[] rowFeedbackIds = {
            R.id.row1feedback, R.id.row2feedback, R.id.row3feedback,
            R.id.row4feedback, R.id.row5feedback, R.id.row6feedback
    };

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScoreP1;
    private TextView tvScoreP2;
    private TextView tvStatus;
    private ImageView[] guessSlots;
    private ViewGroup paletteBar;
    private Button btnCheck;

    private final int[] draftSymbolIndex = {-1, -1, -1, -1};

    private int activePlayer = 1;
    private int scoreP1 = 0;
    private int scoreP2 = 0;
    private int attempt = 0;
    private String target = "";
    private boolean roundFinished = false;
    private boolean stealPhase = false;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        tvRound = findViewById(R.id.tvRound);
        tvCurrentPlayer = findViewById(R.id.tvCurrentPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScoreP1 = findViewById(R.id.tvScoreP1);
        tvScoreP2 = findViewById(R.id.tvScoreP2);
        tvStatus = findViewById(R.id.tvStatus);
        paletteBar = findViewById(R.id.paletteBar);
        guessSlots = new ImageView[] {
                findViewById(R.id.ivGuessSlot1),
                findViewById(R.id.ivGuessSlot2),
                findViewById(R.id.ivGuessSlot3),
                findViewById(R.id.ivGuessSlot4)
        };
        btnCheck = findViewById(R.id.btnCheck);

        bindPaletteSymbols();
        btnCheck.setOnClickListener(v -> submitGuess());

        setupRound();
    }

    private void bindPaletteSymbols() {
        setPaletteClick(findViewById(R.id.ivPaletteS), 0);
        setPaletteClick(findViewById(R.id.ivPaletteK), 1);
        setPaletteClick(findViewById(R.id.ivPaletteO), 2);
        setPaletteClick(findViewById(R.id.ivPaletteH), 3);
        setPaletteClick(findViewById(R.id.ivPaletteT), 4);
        setPaletteClick(findViewById(R.id.ivPaletteZ), 5);
    }

    private void setPaletteClick(ImageView view, final int symbolIndex) {
        view.setOnClickListener(v -> appendSymbolDraft(symbolIndex));
    }

    private void setupRound() {
        stopTimer();
        roundFinished = false;
        stealPhase = false;
        attempt = 0;
        target = randomTarget();
        resetBoardRows();
        resetDraftGuess();
        tvStatus.setText("");
        updateHeader();
        refreshCheckAvailability();
        startMainTimer();
    }

    private void submitGuess() {
        if (roundFinished) return;
        String guess = readDraftGuess();
        if (!isDraftComplete()) return;
        if (!isValidGuess(guess)) return;
        if (!stealPhase && attempt >= 6) return;

        if (stealPhase) {
            resolveStealGuess(guess);
            return;
        }

        attempt++;
        int exact = countExact(guess, target);
        int colorOnly = countColorOnly(guess, target);
        fillGuessRow(attempt - 1, guess, exact, colorOnly);

        if (guess.equals(target)) {
            int gained = scoreForAttempt(attempt);
            addPoints(activePlayer, gained);
            tvStatus.setText("Pogodak u pokusaju " + attempt + ". +" + gained + " poena.");
            finishRound();
            return;
        }

        if (attempt >= 6) {
            tvStatus.setText("Nije pogodjeno. Protivnik ima jednu sansu od 10s za 10 poena.");
            startStealPhase();
            resetDraftGuess();
            return;
        }

        resetDraftGuess();
        updateHeader();
    }

    private void resolveStealGuess(String guess) {
        int opponent = activePlayer == 1 ? 2 : 1;
        if (guess.equals(target)) {
            addPoints(opponent, 10);
            tvStatus.setText("Protivnik je pogodio i dobija +10 poena.");
        } else {
            tvStatus.setText("Protivnik nije pogodio. Bez dodatnih poena.");
        }
        finishRound();
    }

    private void startMainTimer() {
        timer = new CountDownTimer(ROUND_MS, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }
            @Override public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                if (!roundFinished) {
                    tvStatus.setText("Isteklo vreme. Protivnik ima jednu sansu od 10s.");
                    startStealPhase();
                }
            }
        };
        timer.start();
    }

    private void startStealPhase() {
        stopTimer();
        stealPhase = true;
        resetDraftGuess();
        timer = new CountDownTimer(STEAL_MS, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                tvTimer.setText("Bonus pokusaj: " + (millisUntilFinished / 1000) + "s");
            }
            @Override public void onFinish() {
                tvTimer.setText("Bonus pokusaj: 0s");
                if (!roundFinished) {
                    tvStatus.setText("Bonus vreme isteklo.");
                    finishRound();
                }
            }
        };
        timer.start();
    }

    private void finishRound() {
        roundFinished = true;
        stopTimer();
        tvStatus.append(" Kraj igre.");
        paletteBar.setEnabled(false);
        for (int i = 0; i < paletteBar.getChildCount(); i++) {
            paletteBar.getChildAt(i).setEnabled(false);
        }
        btnCheck.setEnabled(false);
        updateHeader();
    }

    private boolean isValidGuess(String guess) {
        if (guess.length() != 4) return false;
        Set<Character> allowed = new HashSet<>();
        for (char c : SYMBOLS) allowed.add(c);
        for (int i = 0; i < guess.length(); i++) {
            if (!allowed.contains(guess.charAt(i))) return false;
        }
        return true;
    }

    private void appendSymbolDraft(int symbolIndex) {
        if (roundFinished) return;
        if (!isDraftComplete()) {
            for (int i = 0; i < draftSymbolIndex.length; i++) {
                if (draftSymbolIndex[i] < 0) {
                    draftSymbolIndex[i] = symbolIndex;
                    break;
                }
            }
            redrawDraftSlots();
            refreshCheckAvailability();
        }
        // Dok su sva 4 popunjena, dalji simbol se ne dodaje dok se ne klikne Proveri
    }

    private boolean isDraftComplete() {
        for (int v : draftSymbolIndex) if (v < 0) return false;
        return true;
    }

    private String readDraftGuess() {
        if (!isDraftComplete()) {
            tvStatus.setText(getString(R.string.skocko_need_four));
            return "";
        }
        StringBuilder sb = new StringBuilder(4);
        for (int idx : draftSymbolIndex) sb.append(SYMBOLS[idx]);
        return sb.toString().trim().toUpperCase(Locale.ROOT);
    }

    private void redrawDraftSlots() {
        for (int i = 0; i < guessSlots.length; i++) {
            int idx = draftSymbolIndex[i];
            if (idx < 0) {
                guessSlots[i].setImageDrawable(null);
            } else {
                guessSlots[i].setImageResource(SYMBOL_DRAWABLES[idx]);
            }
        }
    }

    private void resetDraftGuess() {
        Arrays.fill(draftSymbolIndex, -1);
        redrawDraftSlots();
        refreshCheckAvailability();
        if (!roundFinished && paletteBar != null) {
            paletteBar.setEnabled(true);
            for (int i = 0; i < paletteBar.getChildCount(); i++) {
                paletteBar.getChildAt(i).setEnabled(true);
                paletteBar.getChildAt(i).setAlpha(1f);
            }
        }
    }

    private void refreshCheckAvailability() {
        if (roundFinished) {
            btnCheck.setEnabled(false);
            return;
        }
        btnCheck.setEnabled(isDraftComplete());
        if (paletteBar != null && !roundFinished) {
            boolean full = isDraftComplete();
            float alpha = full ? 0.42f : 1f;
            for (int i = 0; i < paletteBar.getChildCount(); i++) {
                View child = paletteBar.getChildAt(i);
                child.setAlpha(alpha);
                child.setClickable(!full);
                child.setEnabled(!full);
            }
        }
    }

    private void resetBoardRows() {
        for (int rowIndex = 0; rowIndex < rowSlotIds.length; rowIndex++) {
            for (int col = 0; col < 4; col++) {
                ImageView slot = findViewById(rowSlotIds[rowIndex][col]);
                slot.setImageDrawable(null);
            }
            TextView feedback = findViewById(rowFeedbackIds[rowIndex]);
            feedback.setText(buildFeedbackSquares(0, 0));
            feedback.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void fillGuessRow(int rowIndex, String guess, int exact, int colorOnly) {
        for (int col = 0; col < 4; col++) {
            ImageView slot = findViewById(rowSlotIds[rowIndex][col]);
            int sym = symbolCharToIndex(guess.charAt(col));
            slot.setImageResource(SYMBOL_DRAWABLES[sym]);
        }
        TextView feedback = findViewById(rowFeedbackIds[rowIndex]);
        feedback.setText(buildFeedbackSquares(exact, colorOnly));
        tvStatus.setText("Red " + (rowIndex + 1) + " -> Tacno mesto: " + exact + ", Tacan simbol: " + colorOnly);
    }

    private int symbolCharToIndex(char c) {
        for (int i = 0; i < SYMBOLS.length; i++) if (SYMBOLS[i] == c) return i;
        return 0;
    }

    private CharSequence buildFeedbackSquares(int exact, int colorOnly) {
        String squares = "\u25A0\u25A0\u25A0\u25A0";
        SpannableString span = new SpannableString(squares);
        for (int i = 0; i < 4; i++) {
            int color = Color.parseColor("#BDBDBD");
            if (i < exact) {
                color = Color.parseColor("#EF5350");
            } else if (i < exact + colorOnly) {
                color = Color.parseColor("#FFD54F");
            }
            span.setSpan(new ForegroundColorSpan(color), i, i + 1, 0);
        }
        return span;
    }

    private int scoreForAttempt(int attemptNum) {
        if (attemptNum <= 2) return 20;
        if (attemptNum <= 4) return 15;
        return 10;
    }

    private int countExact(String guess, String solution) {
        int exact = 0;
        for (int i = 0; i < 4; i++) if (guess.charAt(i) == solution.charAt(i)) exact++;
        return exact;
    }

    private int countColorOnly(String guess, String solution) {
        int[] freqGuess = new int[128];
        int[] freqSol = new int[128];
        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) != solution.charAt(i)) {
                freqGuess[guess.charAt(i)]++;
                freqSol[solution.charAt(i)]++;
            }
        }
        int count = 0;
        for (char symbol : SYMBOLS) count += Math.min(freqGuess[symbol], freqSol[symbol]);
        return count;
    }

    private String randomTarget() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(SYMBOLS[random.nextInt(SYMBOLS.length)]);
        return sb.toString();
    }

    private void addPoints(int player, int points) {
        if (player == 1) scoreP1 += points;
        else scoreP2 += points;
    }

    private void updateHeader() {
        tvRound.setText(getString(R.string.skocko_round_label));
        tvCurrentPlayer.setText(stealPhase ? "Bonus potez protivnika igraca " + activePlayer : "Na potezu: Igrac " + activePlayer);
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
