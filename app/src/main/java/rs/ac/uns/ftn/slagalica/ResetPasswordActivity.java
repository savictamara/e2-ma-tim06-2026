package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;

public class ResetPasswordActivity extends AppCompatActivity {
    private FirebaseAuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        authRepository = new FirebaseAuthRepository(this);

        EditText etOldPassword = findViewById(R.id.etOldPassword);
        EditText etNewPassword = findViewById(R.id.etNewPassword);
        EditText etRepeatNewPassword = findViewById(R.id.etRepeatNewPassword);
        TextView tvResetMessage = findViewById(R.id.tvResetMessage);
        Button btnResetSubmit = findViewById(R.id.btnResetSubmit);

        btnResetSubmit.setOnClickListener(v -> {
            String oldPassword = etOldPassword.getText().toString();
            String newPassword = etNewPassword.getText().toString();
            String repeat = etRepeatNewPassword.getText().toString();

            if (oldPassword.isEmpty() || newPassword.isEmpty() || repeat.isEmpty()) {
                String message = getString(R.string.error_fill_fields);
                tvResetMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(repeat)) {
                String message = getString(R.string.error_password_mismatch);
                tvResetMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!authRepository.isReady() || authRepository.currentUser() == null) {
                String message = getString(R.string.firebase_not_ready);
                tvResetMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            btnResetSubmit.setEnabled(false);
            authRepository.changePassword(oldPassword, newPassword)
                    .addOnSuccessListener(unused -> {
                        btnResetSubmit.setEnabled(true);
                        String message = getString(R.string.mock_reset_success);
                        tvResetMessage.setText(message);
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        btnResetSubmit.setEnabled(true);
                        String message = e.getMessage() == null ? getString(R.string.firebase_not_ready) : e.getMessage();
                        tvResetMessage.setText(message);
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    });
        });
    }
}
