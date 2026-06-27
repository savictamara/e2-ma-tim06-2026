package rs.ac.uns.ftn.slagalica;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.RegionRepository;
import rs.ac.uns.ftn.slagalica.data.repository.UserRepository;
import rs.ac.uns.ftn.slagalica.domain.model.RegionInfo;
import rs.ac.uns.ftn.slagalica.domain.model.RegionStats;
import rs.ac.uns.ftn.slagalica.domain.model.User;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";
    private FirebaseAuthRepository authRepository;
    private UserRepository userRepository;
    private RegionRepository regionRepository;
    private String selectedRegionId = "";
    private String selectedRegionName = "";
    private String pendingRegistrationUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authRepository = new FirebaseAuthRepository(this);
        userRepository = new UserRepository(this);
        regionRepository = new RegionRepository(this);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etRegion = findViewById(R.id.etRegion);
        RegionMapView registerRegionMap = findViewById(R.id.registerRegionMap);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etRepeatPassword = findViewById(R.id.etRepeatPassword);
        TextView tvRegisterMessage = findViewById(R.id.tvRegisterMessage);
        Button btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        registerRegionMap.setData(regionStats(), null, "");
        registerRegionMap.setOnRegionClickListener(regionId -> {
            RegionInfo info = RegionRepository.infoById(regionId);
            if (info == null) {
                return;
            }
            selectedRegionId = info.id;
            selectedRegionName = info.name;
            etRegion.setText(info.name);
            tvRegisterMessage.setText("Izabran region: " + info.name);
        });

        btnRegisterSubmit.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String region = selectedRegionName;
            String password = etPassword.getText().toString();
            String repeat = etRepeatPassword.getText().toString();

            if (email.isEmpty() || username.isEmpty() || password.isEmpty() || repeat.isEmpty()) {
                String message = getString(R.string.error_fill_fields);
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedRegionId.isEmpty()) {
                String message = "Morate izabrati region na mapi.";
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                String message = getString(R.string.invalid_email);
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
            if (password.length() < 6) {
                String message = "Password must have at least 6 characters.";
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!authRepository.isReady() || !userRepository.isReady() || !regionRepository.isReady()) {
                String message = getString(R.string.firebase_not_ready);
                tvRegisterMessage.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }

            btnRegisterSubmit.setEnabled(false);
            authRepository.register(email, password)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        pendingRegistrationUid = task.getResult().getUid();
                        return userRepository.createUser(new User(pendingRegistrationUid, email, username, region));
                    })
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return regionRepository.saveRegistrationRegion(pendingRegistrationUid, username, selectedRegionId);
                    })
                    .addOnSuccessListener(unused -> {
                        btnRegisterSubmit.setEnabled(true);
                        authRepository.logout();
                        String message = "Registracija je uspešna. Na Vašu email adresu poslat je link za potvrdu naloga. Proverite prijemno sanduče, kao i Spam/Neželjenu poštu.";
                        tvRegisterMessage.setText(message);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.go_register)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    startActivity(new Intent(this, LoginActivity.class));
                                    finish();
                                })
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        btnRegisterSubmit.setEnabled(true);
                        Log.e(TAG, "Registracija nije uspela", e);
                        String message = e.getMessage() == null ? getString(R.string.firebase_not_ready) : e.getMessage();
                        tvRegisterMessage.setText(message);
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private List<RegionStats> regionStats() {
        List<RegionStats> stats = new ArrayList<>();
        for (RegionInfo info : RegionRepository.regions()) {
            stats.add(new RegionStats(info.id, info.name, info.iconName));
        }
        return stats;
    }
}
