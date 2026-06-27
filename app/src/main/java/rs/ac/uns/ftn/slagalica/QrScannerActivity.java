package rs.ac.uns.ftn.slagalica;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

import rs.ac.uns.ftn.slagalica.data.repository.FirebaseAuthRepository;
import rs.ac.uns.ftn.slagalica.data.repository.FriendRepository;

public class QrScannerActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 501;

    private FirebaseAuthRepository authRepository;
    private FriendRepository friendRepository;
    private DecoratedBarcodeView scanner;
    private TextView status;
    private String uid = "";
    private boolean handled = false;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (handled || result == null || result.getText() == null) {
                return;
            }
            handled = true;
            scanner.pause();
            handlePayload(result.getText());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        authRepository = new FirebaseAuthRepository(this);
        friendRepository = new FriendRepository(this);
        scanner = findViewById(R.id.barcodeScanner);
        status = findViewById(R.id.tvQrStatus);
        scanner.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        scanner.initializeFromIntent(getIntent());

        FirebaseUser user = authRepository.currentUser();
        if (user == null || user.isAnonymous()) {
            finish();
            return;
        }
        uid = user.getUid();
        if (hasCameraPermission()) {
            startScanning();
        } else {
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private void startScanning() {
        status.setText("Usmerite kameru ka QR kodu.");
        scanner.decodeContinuous(callback);
        scanner.resume();
    }

    private void handlePayload(String payload) {
        String targetUid = parseUid(payload);
        if (targetUid.isEmpty()) {
            status.setText("Neispravan QR kod.");
            show("Neispravan QR kod");
            handled = false;
            scanner.resume();
            return;
        }
        if (targetUid.equals(uid)) {
            status.setText("Ne mozete dodati sebe.");
            show("Ne mozete dodati sebe");
            handled = false;
            scanner.resume();
            return;
        }
        status.setText("Slanje zahteva...");
        friendRepository.sendFriendRequest(uid, targetUid)
                .addOnSuccessListener(id -> {
                    show("Zahtev poslat");
                    finish();
                })
                .addOnFailureListener(e -> {
                    status.setText(e.getMessage() == null ? "Greska pri slanju zahteva." : e.getMessage());
                    show(e.getMessage());
                    handled = false;
                    scanner.resume();
                });
    }

    private String parseUid(String payload) {
        if (payload == null) return "";
        String clean = payload.trim();
        if (!clean.contains("friend_add") && !clean.startsWith("uid:")) {
            return "";
        }
        int key = clean.indexOf("\"uid\"");
        if (key >= 0) {
            int colon = clean.indexOf(':', key);
            int firstQuote = clean.indexOf('"', colon + 1);
            int secondQuote = clean.indexOf('"', firstQuote + 1);
            if (firstQuote >= 0 && secondQuote > firstQuote) {
                return clean.substring(firstQuote + 1, secondQuote);
            }
        }
        if (clean.startsWith("uid:")) {
            return clean.substring(4).trim();
        }
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanner != null && hasCameraPermission() && !handled) {
            scanner.resume();
        }
    }

    @Override
    protected void onPause() {
        if (scanner != null) {
            scanner.pause();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                status.setText("Kamera dozvola je odbijena.");
                show("Kamera dozvola je odbijena");
            }
        }
    }

    private void show(String message) {
        Toast.makeText(this, message == null ? "Greska" : message, Toast.LENGTH_SHORT).show();
    }
}
