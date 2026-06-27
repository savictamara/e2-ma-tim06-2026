package rs.ac.uns.ftn.slagalica;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import rs.ac.uns.ftn.slagalica.domain.model.LeaderboardEntry;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.Holder> {
    private final List<LeaderboardEntry> entries = new ArrayList<>();

    public void submit(List<LeaderboardEntry> next) {
        entries.clear();
        if (next != null) entries.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LeaderboardEntry entry = entries.get(position);
        holder.rank.setText(String.valueOf(entry.rank));
        holder.username.setText(entry.username);
        holder.stars.setText(entry.stars + " zvezda");
        holder.league.setImageResource(drawableForId(entry.leagueIcon));
        holder.itemView.setBackgroundResource(entry.currentUser ? R.drawable.bg_button_secondary : R.drawable.bg_card);
        holder.username.setTypeface(Typeface.DEFAULT, entry.currentUser ? Typeface.BOLD : Typeface.NORMAL);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private int drawableForId(String id) {
        if ("heart".equals(id)) return R.drawable.heart_2;
        if ("circle".equals(id)) return R.drawable.circle;
        if ("triangle".equals(id)) return R.drawable.triangle;
        if ("skocko".equals(id)) return R.drawable.skocko;
        if ("ic_league_0".equals(id)) return R.drawable.ic_league_0;
        if ("ic_league_1".equals(id)) return R.drawable.ic_league_1;
        if ("ic_league_2".equals(id)) return R.drawable.ic_league_2;
        if ("ic_league_3".equals(id)) return R.drawable.ic_league_3;
        if ("ic_league_4".equals(id)) return R.drawable.ic_league_4;
        if ("ic_league_5".equals(id)) return R.drawable.ic_league_5;
        return R.drawable.star;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank;
        final ImageView league;
        final TextView username;
        final TextView stars;

        Holder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.tvRank);
            league = itemView.findViewById(R.id.ivLeague);
            username = itemView.findViewById(R.id.tvUsername);
            stars = itemView.findViewById(R.id.tvStars);
        }
    }
}
