package com.bif.app.feature.favorites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Favorite;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {
    public interface OnFavoriteClickListener {
        void onFavoriteClicked(Favorite favorite);
        void onFavoriteRemoved(Favorite favorite);
    }

    private List<Favorite> favorites = new ArrayList<>();
    private final OnFavoriteClickListener listener;

    public FavoriteAdapter(OnFavoriteClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return favorites.size();
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);

        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        Favorite item = favorites.get(position);

        // Name: use name if available, otherwise fall back to address as the title
        String name = (item.name != null && !item.name.trim().isEmpty()) ? item.name : item.address;
        holder.tvName.setText(name != null ? name : "");

        // Address: only show as subtitle if it's different from what's used as the title
        if (item.name != null && !item.name.trim().isEmpty()
                && item.address != null && !item.address.trim().isEmpty()) {
            holder.tvAddress.setText(item.address);
            holder.tvAddress.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.tvAddress.setVisibility(android.view.View.GONE);
        }

        // Rating
        if (item.rating > 0) {
            holder.tvRating.setText(String.format(java.util.Locale.getDefault(), "★ %d", item.rating));
            holder.tvRating.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.tvRating.setVisibility(android.view.View.GONE);
        }

        // Notes
        if (item.notes != null && !item.notes.trim().isEmpty()) {
            holder.tvNotes.setText("Notes: " + item.notes);
            holder.tvNotes.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.tvNotes.setVisibility(android.view.View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onFavoriteClicked(item));
        holder.btnRemove.setOnClickListener(v -> listener.onFavoriteRemoved(item));

    }

    public void submitList(List<Favorite> favorites) {
        this.favorites = favorites != null ? favorites : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvAddress;
        final TextView tvRating;
        final TextView tvNotes;
        final ImageButton btnRemove;

        FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tv_item_name);
            tvAddress = itemView.findViewById(R.id.tv_item_address);
            tvRating = itemView.findViewById(R.id.tv_item_rating);
            tvNotes = itemView.findViewById(R.id.tv_item_notes);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
