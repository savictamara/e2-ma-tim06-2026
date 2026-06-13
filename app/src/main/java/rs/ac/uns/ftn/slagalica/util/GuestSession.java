package rs.ac.uns.ftn.slagalica.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class GuestSession {
    private static final String PREFS = "guest_session";
    private static final String KEY_UID = "guest_uid";

    private GuestSession() {
    }

    public static String uid(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uid = prefs.getString(KEY_UID, "");
        if (uid == null || uid.isEmpty()) {
            uid = "guest_" + UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString(KEY_UID, uid).apply();
        }
        return uid;
    }
}
