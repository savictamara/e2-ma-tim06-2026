package rs.ac.uns.ftn.slagalica;

import android.os.Bundle;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NotifikacijeActivity extends AppCompatActivity {
    private static final String CHANNEL_CHAT = "sys_chat";
    private static final String CHANNEL_RANK = "sys_rank";
    private static final String CHANNEL_REWARDS = "sys_rewards";
    private static final String CHANNEL_OTHER = "sys_other";
    private final java.util.List<AppNotification> allNotifications = new java.util.ArrayList<>();
    private java.util.List<Integer> visibleIndexes = new java.util.ArrayList<>();
    private ArrayAdapter<String> adapter;
    private boolean showRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifikacije);

        createSystemChannels();

        Switch swChat = findViewById(R.id.swChat);
        Switch swRank = findViewById(R.id.swRank);
        Switch swRewards = findViewById(R.id.swRewards);
        Switch swOther = findViewById(R.id.swOther);
        Button btnTabUnread = findViewById(R.id.btnTabUnread);
        Button btnTabRead = findViewById(R.id.btnTabRead);
        Button btnMarkAll = findViewById(R.id.btnMarkAll);
        ListView lvNotifications = findViewById(R.id.lvNotifications);
        TextView tvStatus = findViewById(R.id.tvStatus);

        swChat.setChecked(true);
        swRank.setChecked(true);
        swRewards.setChecked(true);
        swOther.setChecked(true);

        seedNotifications();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new java.util.ArrayList<>());
        lvNotifications.setAdapter(adapter);
        refreshVisibleList();
        tvStatus.setText(getString(R.string.notifikacije_status_on));

        Runnable refreshStatus = () -> {
            boolean anyEnabled = swChat.isChecked() || swRank.isChecked() || swRewards.isChecked() || swOther.isChecked();
            tvStatus.setText(getString(anyEnabled ? R.string.notifikacije_status_on : R.string.notifikacije_status_off));
        };

        swChat.setOnCheckedChangeListener((buttonView, isChecked) -> refreshStatus.run());
        swRank.setOnCheckedChangeListener((buttonView, isChecked) -> refreshStatus.run());
        swRewards.setOnCheckedChangeListener((buttonView, isChecked) -> refreshStatus.run());
        swOther.setOnCheckedChangeListener((buttonView, isChecked) -> refreshStatus.run());

        btnTabUnread.setOnClickListener(v -> {
            showRead = false;
            refreshVisibleList();
            tvStatus.setText(getString(R.string.notifikacije_status_tab_unread));
        });

        btnTabRead.setOnClickListener(v -> {
            showRead = true;
            refreshVisibleList();
            tvStatus.setText(getString(R.string.notifikacije_status_tab_read));
        });

        lvNotifications.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleIndexes.size()) return;
            int absoluteIndex = visibleIndexes.get(position);
            allNotifications.get(absoluteIndex).read = true;
            refreshVisibleList();
            tvStatus.setText(getString(R.string.notifikacije_status_single_marked));
        });

        btnMarkAll.setOnClickListener(v -> {
            for (AppNotification item : allNotifications) item.read = true;
            refreshVisibleList();
            tvStatus.setText(getString(R.string.notifikacije_status_marked));
        });
    }

    private void createSystemChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_CHAT,
                "Obavestenja u cetu",
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_RANK,
                "Obavestenja o rangiranju",
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_REWARDS,
                "Obavestenja o nagradama",
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_OTHER,
                "Ostale sistemske notifikacije",
                NotificationManager.IMPORTANCE_DEFAULT
        ));
    }

    private void seedNotifications() {
        allNotifications.clear();
        allNotifications.add(new AppNotification("[CET]", "Prijatelj te je pozvao u cet partiju.", false));
        allNotifications.add(new AppNotification("[RANG]", "Napredovao/la si u Srebrnu ligu.", false));
        allNotifications.add(new AppNotification("[NAGRADA]", "Osvojio/la si dnevnu nagradu: 20 tokena.", false));
        allNotifications.add(new AppNotification("[OSTALO]", "Sistem: odrzavanje servera je zakazano veceras.", false));
    }

    private void refreshVisibleList() {
        java.util.List<String> items = new java.util.ArrayList<>();
        visibleIndexes = new java.util.ArrayList<>();
        for (int i = 0; i < allNotifications.size(); i++) {
            AppNotification item = allNotifications.get(i);
            if (item.read == showRead) {
                items.add(item.prefix + " " + item.message);
                visibleIndexes.add(i);
            }
        }
        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
    }

    private static class AppNotification {
        final String prefix;
        final String message;
        boolean read;

        AppNotification(String prefix, String message, boolean read) {
            this.prefix = prefix;
            this.message = message;
            this.read = read;
        }
    }
}
