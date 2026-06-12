package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;

public class ProfileActivity extends AppCompatActivity {
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            FirebaseUser user = authRepository.currentUser();
            if (user == null || !userRepository.isReady()) {
                authRepository.logout();
                Toast.makeText(this, R.string.firebase_not_ready, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            userRepository.updateUserState(user.getUid(), false, false, "")
                    .addOnCompleteListener(task -> {
                        authRepository.logout();
                        Toast.makeText(this, "Odjavljeni ste.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        });
    }
}
