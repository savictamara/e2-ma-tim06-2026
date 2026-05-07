package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
            String message = getString(R.string.mock_login_success);
            tvLoginMessage.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
}
