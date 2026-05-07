package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnReset = findViewById(R.id.btnReset);
        Button btnKorak = findViewById(R.id.btnKorak);
        Button btnMojBroj = findViewById(R.id.btnMojBroj);

        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        btnReset.setOnClickListener(v -> startActivity(new Intent(this, ResetPasswordActivity.class)));
        btnKorak.setOnClickListener(v -> startActivity(new Intent(this, KorakPoKorakActivity.class)));
        btnMojBroj.setOnClickListener(v -> startActivity(new Intent(this, MojBrojActivity.class)));
    }
}
