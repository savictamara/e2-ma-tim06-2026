package rs.ac.uns.ftn.slagalica;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rs.ac.uns.ftn.slagalica.domain.model.AppNotification;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
    public interface Listener {
        void onNotificationClick(AppNotification notification);

        void onMarkReadClick(AppNotification notification);
    }

    private final Listener listener;
    private final List<AppNotification> items = new ArrayList<>();

    public NotificationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<AppNotification> notifications) {
        items.clear();
        if (notifications != null) {
            items.addAll(notifications);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        AppNotification notification = items.get(position);
        holder.icon.setText(iconText(notification.type));
        holder.meta.setText(typeLabel(notification.type) + " | " + (notification.read ? "Procitano" : "Neprocitano"));
        holder.title.setText(notification.title);
        holder.message.setText(notification.message);
        holder.time.setText(formatTime(notification));
        holder.readButton.setEnabled(!notification.read);
        holder.readButton.setVisibility(notification.read ? View.GONE : View.VISIBLE);

        int metaColor = holder.itemView.getContext().getColor(notification.read ? R.color.text_muted : R.color.pink_dark);
        holder.meta.setTextColor(metaColor);
        holder.title.setTypeface(Typeface.DEFAULT, notification.read ? Typeface.NORMAL : Typeface.BOLD);

        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(notification));
        holder.readButton.setOnClickListener(v -> listener.onMarkReadClick(notification));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String iconText(String type) {
        if ("CHAT".equals(type)) return "CH";
        if ("RANKING".equals(type)) return "RG";
        if ("REWARD".equals(type)) return "RW";
        if ("FRIEND_INVITE".equals(type) || "FRIEND_REQUEST".equals(type)) return "FR";
        if ("LEAGUE".equals(type)) return "LG";
        return "OT";
    }

    private String typeLabel(String type) {
        if ("CHAT".equals(type)) return "Cet";
        if ("RANKING".equals(type)) return "Rang lista";
        if ("REWARD".equals(type)) return "Nagrada";
        if ("FRIEND_INVITE".equals(type) || "FRIEND_REQUEST".equals(type)) return "Prijateljstvo";
        if ("LEAGUE".equals(type)) return "Liga";
        return "Ostalo";
    }

    private String formatTime(AppNotification notification) {
        if (notification.createdAt == null) {
            return "Vreme se upisuje";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(notification.createdAt.toDate());
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView meta;
        final TextView title;
        final TextView message;
        final TextView time;
        final Button readButton;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.tvNotificationIcon);
            meta = itemView.findViewById(R.id.tvNotificationMeta);
            title = itemView.findViewById(R.id.tvNotificationTitle);
            message = itemView.findViewById(R.id.tvNotificationMessage);
            time = itemView.findViewById(R.id.tvNotificationTime);
            readButton = itemView.findViewById(R.id.btnNotificationRead);
        }
    }
}
