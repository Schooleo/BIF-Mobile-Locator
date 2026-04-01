package com.bif.app.feature.map.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.feature.map.R;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryAdapter extends
        RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    public interface OnQueryClickListener {
        void onQueryClick(String query);
    }

    private List<String> items = new ArrayList<>();
    private final OnQueryClickListener listener;

    public SearchHistoryAdapter(OnQueryClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<String> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String query = items.get(position);
        holder.tvQuery.setText(query);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQueryClick(query);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvQuery;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuery = itemView.findViewById(R.id.tv_history_query);
        }
    }
}

