package rs.ac.uns.ftn.slagalica;

import android.app.Application;

import rs.ac.uns.ftn.slagalica.util.NotificationHelper;

public class SlagalicaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannels(this);
    }
}
