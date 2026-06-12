package rs.ac.uns.ftn.slagalica.util;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public final class FirebaseInitializer {
    private static final String TAG = "FirebaseInitializer";

    private FirebaseInitializer() {
    }

    public static boolean ensure(Context context) {
        if (context == null) {
            Log.e(TAG, "Firebase init failed: context is null");
            return false;
        }
        Context appContext = context.getApplicationContext();
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(appContext);
                Log.d(TAG, "FirebaseApp.initializeApp result=" + (app != null));
            }
            boolean initialized = !FirebaseApp.getApps(appContext).isEmpty();
            Log.d(TAG, "Firebase initialized=" + initialized + ", apps=" + FirebaseApp.getApps(appContext).size());
            return initialized;
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed", e);
            return false;
        }
    }
}
