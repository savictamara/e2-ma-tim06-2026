package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);

        EditText etIdentity = findViewById(R.id.etIdentity);
        EditText etPassword = findViewById(R.id.etPassword);
        TextView tvLoginMessage = findViewById(R.id.tvLoginMessage);
        Button btnLoginSubmit = findViewById(R.id.btnLoginSubmit);

        btnLoginSubmit.setOnClickListener(v -> {
            String identity = etIdentity.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (identity.isEmpty() || password.isEmpty()) {
                String message = getString(R.string.error_fill_fields);
                tvLoginMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!authRepository.isReady() || !userRepository.isReady()) {
                show(tvLoginMessage, getString(R.string.firebase_not_ready));
                return;
            }
            btnLoginSubmit.setEnabled(false);
            userRepository.emailForIdentity(identity)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        String email = task.getResult();
                        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            throw new IllegalArgumentException(getString(R.string.invalid_email));
                        }
                        return authRepository.login(email, password);
                    })
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return userRepository.updateUserState(task.getResult().getUid(), true, false, "");
                    })
                    .addOnSuccessListener(unused -> {
                        btnLoginSubmit.setEnabled(true);
                        show(tvLoginMessage, getString(R.string.mock_login_success));
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnLoginSubmit.setEnabled(true);
                        Log.e(TAG, "Login nije uspeo", e);
                        if (e instanceof SecurityException) {
                            show(tvLoginMessage, "Vaš nalog još nije potvrđen. Molimo proverite email i kliknite na link za potvrdu naloga.");
                            Toast.makeText(this, "Novi link za potvrdu naloga je poslat na Vašu email adresu.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        String msg = e.getMessage();
                        show(tvLoginMessage, msg == null ? getString(R.string.firebase_not_ready) : msg);
                    });
        });
    }

    private void show(TextView view, String message) {
        view.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
