package rs.ac.uns.ftn.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private FirebaseAuthRepository authRepository;
    private Button btnLogin;
    private Button btnRegister;
    private Button btnReset;
    private Button btnProfile;
    private Button btnNotifikacije;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Firebase ensure from MainActivity=" + FirebaseInitializer.ensure(this));
        setContentView(R.layout.activity_main);
        authRepository = new FirebaseAuthRepository(this);

        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnReset = findViewById(R.id.btnReset);
        Button btnKorak = findViewById(R.id.btnKorak);
        Button btnMojBroj = findViewById(R.id.btnMojBroj);
        btnProfile = findViewById(R.id.btnProfileTop);
        Button btnKoZnaZna = findViewById(R.id.btnKoZnaZna);
        Button btnSpojnice = findViewById(R.id.btnSpojnice);
        Button btnAsocijacije = findViewById(R.id.btnAsocijacije);
        Button btnSkocko = findViewById(R.id.btnSkocko);
        btnNotifikacije = findViewById(R.id.btnNotifikacije);

        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        btnReset.setOnClickListener(v -> startActivity(new Intent(this, ResetPasswordActivity.class)));
        btnKorak.setOnClickListener(v -> startGame(KorakPoKorakActivity.class));
        btnMojBroj.setOnClickListener(v -> startGame(MojBrojActivity.class));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnKoZnaZna.setOnClickListener(v -> startGame(KoZnaZnaActivity.class));
        btnSpojnice.setOnClickListener(v -> startGame(SpojniceActivity.class));
        btnAsocijacije.setOnClickListener(v -> startGame(AsocijacijeActivity.class));
        btnSkocko.setOnClickListener(v -> startGame(SkockoActivity.class));
        btnNotifikacije.setOnClickListener(v -> startActivity(new Intent(this, NotifikacijeActivity.class)));
        updateAccountUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountUi();
    }

    private void updateAccountUi() {
        FirebaseUser user = authRepository.currentUser();
        boolean isLoggedIn = user != null && !user.isAnonymous();
        btnProfile.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        btnNotifikacije.setVisibility(View.VISIBLE);
        btnLogin.setVisibility(View.VISIBLE);
        btnRegister.setVisibility(View.VISIBLE);
        btnReset.setVisibility(View.VISIBLE);
        Log.d(TAG, "Main account UI isLoggedIn=" + isLoggedIn
                + ", hasFirebaseUser=" + (user != null)
                + ", anonymous=" + (user != null && user.isAnonymous()));
    }

    private void startGame(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }
}
