package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class KoZnaZnaActivity extends AppCompatActivity {
    private final String[] questions = {
            "Koji element ima hemijski simbol Au?",
            "Koliko kontinenata postoji?",
            "Koja planeta je poznata kao crvena planeta?",
            "Ko je napisao Na Drini cuprija?",
            "Koji je hemijski simbol za zlato?"
    };

    private final String[][] answers = {
            {"Zlato", "Srebro", "Gvozdje", "Kiseonik"},
            {"5", "6", "7", "8"},
            {"Venera", "Mars", "Jupiter", "Merkur"},
            {"Mesa Selimovic", "Ivo Andric", "Branko Copic", "Milos Crnjanski"},
            {"Ag", "Au", "Fe", "Pb"}
    };

    private final int[] correctIndex = {0, 2, 1, 1, 1};
    private final Button[] answerButtons = new Button[4];

    private TextView tvTimer;
    private TextView tvQuestionIndex;
    private TextView tvPoints;
    private TextView tvQuestion;
    private TextView tvRoundResult;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Score;
    private Button btnNextQuestion;

    private int questionIndex = 0;
    private int points = 0;
    private final int mockPlayerTwoPoints = 15;
    private boolean answered = false;
    private CountDownTimer questionTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        tvTimer = findViewById(R.id.tvTimer);
        tvQuestionIndex = findViewById(R.id.tvQuestionIndex);
        tvPoints = findViewById(R.id.tvPoints);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvRoundResult = findViewById(R.id.tvRoundResult);
        tvPlayer1Score = findViewById(R.id.tvKznzPlayer1);
        tvPlayer2Score = findViewById(R.id.tvKznzPlayer2);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);

        answerButtons[0] = findViewById(R.id.btnAnswer1);
        answerButtons[1] = findViewById(R.id.btnAnswer2);
        answerButtons[2] = findViewById(R.id.btnAnswer3);
        answerButtons[3] = findViewById(R.id.btnAnswer4);

        for (int i = 0; i < answerButtons.length; i++) {
            final int answerIndex = i;
            answerButtons[i].setOnClickListener(v -> onAnswerSelected(answerIndex));
        }

        btnNextQuestion.setOnClickListener(v -> {
            questionIndex++;
            if (questionIndex < questions.length) {
                showQuestion();
            } else {
                showFinalResult();
            }
        });

        showQuestion();
    }

    private void showQuestion() {
        answered = false;
        updateScoreAndHeader();
        tvQuestion.setText(questions[questionIndex]);
        tvRoundResult.setText("");
        btnNextQuestion.setEnabled(false);

        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setEnabled(true);
            answerButtons[i].setText(answers[questionIndex][i]);
            answerButtons[i].setBackgroundResource(R.drawable.bg_step);
        }

        startQuestionTimer();
    }

    private void onAnswerSelected(int selectedIndex) {
        if (answered) {
            return;
        }

        answered = true;
        if (questionTimer != null) {
            questionTimer.cancel();
        }

        for (Button answerButton : answerButtons) {
            answerButton.setEnabled(false);
        }

        if (selectedIndex == correctIndex[questionIndex]) {
            points += 10;
            answerButtons[selectedIndex].setBackgroundResource(R.drawable.bg_answer_correct);
            tvRoundResult.setText(getString(R.string.kznz_answer_correct));
        } else {
            points -= 5;
            answerButtons[selectedIndex].setBackgroundResource(R.drawable.bg_answer_wrong);
            answerButtons[correctIndex[questionIndex]].setBackgroundResource(R.drawable.bg_answer_correct);
            tvRoundResult.setText(getString(R.string.kznz_answer_wrong));
        }

        updateScoreAndHeader();
        btnNextQuestion.setEnabled(true);
    }

    private void startQuestionTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
        }

        questionTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.timer_text, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.timer_text, 0));
                if (!answered) {
                    answered = true;
                    for (Button answerButton : answerButtons) {
                        answerButton.setEnabled(false);
                    }
                    answerButtons[correctIndex[questionIndex]].setBackgroundResource(R.drawable.bg_answer_correct);
                    tvRoundResult.setText(getString(R.string.kznz_timeout));
                    btnNextQuestion.setEnabled(true);
                }
            }
        };
        questionTimer.start();
    }

    private void updateScoreAndHeader() {
        tvQuestionIndex.setText(getString(R.string.kznz_question_counter, questionIndex + 1, questions.length));
        tvPoints.setText(getString(R.string.points_text, points));
        tvPlayer1Score.setText(getString(R.string.player_points, points));
        tvPlayer2Score.setText(getString(R.string.player_points, mockPlayerTwoPoints));
    }

    private void showFinalResult() {
        if (questionTimer != null) {
            questionTimer.cancel();
        }

        tvQuestionIndex.setText(getString(R.string.kznz_finished));
        tvQuestion.setText(getString(R.string.kznz_final_score, points));
        tvTimer.setText(getString(R.string.timer_text, 0));
        tvRoundResult.setText(getString(R.string.kznz_mock_speed_rule));
        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText(R.string.kznz_done);

        for (Button answerButton : answerButtons) {
            answerButton.setEnabled(false);
            answerButton.setBackgroundResource(R.drawable.bg_step);
        }
    }
}
