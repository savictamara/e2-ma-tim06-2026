package rs.ac.uns.ftn.slagalica;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etRegion = findViewById(R.id.etRegion);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etRepeatPassword = findViewById(R.id.etRepeatPassword);
        TextView tvRegisterMessage = findViewById(R.id.tvRegisterMessage);
        Button btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);

        btnRegisterSubmit.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String region = etRegion.getText().toString().trim();
            String password = etPassword.getText().toString();
            String repeat = etRepeatPassword.getText().toString();

            if (email.isEmpty() || username.isEmpty() || region.isEmpty() || password.isEmpty() || repeat.isEmpty()) {
                String message = getString(R.string.error_fill_fields);
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(repeat)) {
                String message = getString(R.string.error_password_mismatch);
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }

            String message = getString(R.string.mock_register_email);
            tvRegisterMessage.setText(message);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.go_register)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }
}
