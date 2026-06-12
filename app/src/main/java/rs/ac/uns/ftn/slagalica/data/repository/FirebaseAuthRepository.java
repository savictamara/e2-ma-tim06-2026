package rs.ac.uns.ftn.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import rs.ac.uns.ftn.slagalica.util.FirebaseInitializer;

public class FirebaseAuthRepository {
    private static final String TAG = "FirebaseAuthRepo";
    private final FirebaseAuth auth;

    public FirebaseAuthRepository(Context context) {
        FirebaseAuth instance = null;
        try {
            if (FirebaseInitializer.ensure(context)) {
                instance = FirebaseAuth.getInstance();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase Auth nije dostupan", e);
        }
        auth = instance;
    }

    public boolean isReady() {
        return auth != null;
    }

    public FirebaseUser currentUser() {
        return auth == null ? null : auth.getCurrentUser();
    }

    public Task<FirebaseUser> register(String email, String password) {
        if (auth == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return auth.createUserWithEmailAndPassword(email, password)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    FirebaseUser user = task.getResult().getUser();
                    if (user == null) {
                        throw new IllegalStateException("Korisnik nije kreiran");
                    }
                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (currentUser == null) {
                        throw new IllegalStateException("Korisnik nije prijavljen posle registracije");
                    }
                    return currentUser.sendEmailVerification().continueWith(verificationTask -> {
                        if (!verificationTask.isSuccessful()) {
                            Log.e(TAG, "Slanje verification email-a nije uspelo", verificationTask.getException());
                            throw verificationTask.getException();
                        }
                        return currentUser;
                    });
                });
    }

    public Task<FirebaseUser> login(String email, String password) {
        if (auth == null) {
            return Tasks.forException(new IllegalStateException("Firebase nije inicijalizovan"));
        }
        return auth.signInWithEmailAndPassword(email, password).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                throw new IllegalStateException("Korisnik nije pronadjen");
            }
            return user.reload().continueWithTask(reloadTask -> {
                if (!reloadTask.isSuccessful()) {
                    throw reloadTask.getException();
                }
                if (!user.isEmailVerified()) {
                    return user.sendEmailVerification().continueWithTask(resendTask -> {
                        if (!resendTask.isSuccessful()) {
                            Log.e(TAG, "Ponovno slanje verification email-a nije uspelo", resendTask.getException());
                            auth.signOut();
                            throw resendTask.getException();
                        }
                        auth.signOut();
                        return Tasks.forException(new SecurityException("Please verify your email first."));
                    });
                }
                return Tasks.forResult(user);
            });
        });
    }

    public Task<Void> changePassword(String oldPassword, String newPassword) {
        FirebaseUser user = currentUser();
        if (auth == null || user == null || user.getEmail() == null) {
            return Tasks.forException(new IllegalStateException("Korisnik nije prijavljen"));
        }
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
        return user.reauthenticate(credential).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return user.updatePassword(newPassword);
        });
    }

    public void logout() {
        if (auth != null) {
            auth.signOut();
        }
    }
}
