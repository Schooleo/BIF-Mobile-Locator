package com.bif.app.feature.favorites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.feature.favorites.R;
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

        holder.tvAddress.setText("Address: "+ (item.address != null ? item.address : ""));
        holder.tvDescription.setText("Description: "+ (item.description != null ? item.description : ""));
        holder.tvNotes.setText("Notes: "+ (item.notes != null ? item.notes : ""));
        holder.itemView.setOnClickListener(v -> listener.onFavoriteClicked(item));
        holder.btnRemove.setOnClickListener(v -> listener.onFavoriteRemoved(item));

        if (item.imagePath != null && !item.imagePath.isEmpty()) {
            // dùng tạm vì đang dùng mockdata
            holder.imgFavorite.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            holder.imgFavorite.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    public void submitList(List<Favorite> favorites) {
        this.favorites = favorites != null ? favorites : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgFavorite;
        final TextView tvAddress;
        final TextView tvDescription;
        final TextView tvNotes;
        final ImageButton btnRemove;

        FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);

            imgFavorite = itemView.findViewById(R.id.image_favorite);
            tvAddress = itemView.findViewById(R.id.tv_item_address);
            tvDescription = itemView.findViewById(R.id.tv_item_description);
            tvNotes = itemView.findViewById(R.id.tv_item_notes);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
