package com.bif.app.feature.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class AiTripDraftStopPreviewAdapter
        extends RecyclerView.Adapter<AiTripDraftStopPreviewAdapter.StopPreviewViewHolder> {

    private static final int MAX_TIME_LABEL_LENGTH = 14;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "MM/dd - HH:mm",
            Locale.getDefault()
    ).withZone(ZoneId.systemDefault());

    private final List<SocialViewModel.AiDraftStopPreview> items = new ArrayList<>();

    void submit(List<SocialViewModel.AiDraftStopPreview> previews) {
        items.clear();
        if (previews != null) {
            items.addAll(previews);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StopPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_trip_draft_stop_preview, parent, false);
        return new StopPreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StopPreviewViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StopPreviewViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvStopIndex;
        private final TextView tvStopName;
        private final TextView tvStopTime;
        private final TextView tvStopNote;

        StopPreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStopIndex = itemView.findViewById(R.id.tv_stop_index);
            tvStopName = itemView.findViewById(R.id.tv_stop_name);
            tvStopTime = itemView.findViewById(R.id.tv_stop_time);
            tvStopNote = itemView.findViewById(R.id.tv_stop_note);
        }

        void bind(SocialViewModel.AiDraftStopPreview preview, int position) {
            if (preview == null) {
                tvStopIndex.setText("");
                tvStopName.setText("");
                tvStopTime.setText("");
                tvStopNote.setText("");
                return;
            }

            tvStopIndex.setText(itemView.getContext().getString(
                    R.string.trip_ai_stop_index,
                    position + 1
            ));
            tvStopName.setText(preview.getName());
            tvStopTime.setText(resolveTimeLabel(preview));
            tvStopNote.setText(resolveNoteLabel(preview));
        }

        private String resolveTimeLabel(SocialViewModel.AiDraftStopPreview preview) {
            String plannedDateTime = preview.getPlannedDateTime();
            String formattedTime = null;
            if (plannedDateTime != null && !plannedDateTime.trim().isEmpty()) {
                try {
                    formattedTime = TIME_FORMATTER.format(Instant.parse(plannedDateTime.trim()));
                } catch (Exception ignored) {
                    formattedTime = shortenTimeLabel(plannedDateTime);
                }
            }

            int duration = Math.max(0, preview.getDurationMinutes());
            if (formattedTime == null || formattedTime.trim().isEmpty()) {
                String startTime = preview.getStartTime();
                String endTime = preview.getEndTime();
                if (startTime != null && !startTime.trim().isEmpty()
                        && endTime != null && !endTime.trim().isEmpty()) {
                    formattedTime = startTime.trim() + "–" + endTime.trim();
                }
            }
            if (formattedTime == null || formattedTime.trim().isEmpty()) {
                return itemView.getContext().getString(R.string.trip_ai_duration_only, duration);
            }
            formattedTime = shortenTimeLabel(formattedTime);
            return itemView.getContext().getString(R.string.trip_ai_time_and_duration, formattedTime, duration);
        }

        private String resolveNoteLabel(SocialViewModel.AiDraftStopPreview preview) {
            String note = preview.getNote();
            if (note == null || note.trim().isEmpty()) {
                return itemView.getContext().getString(R.string.trip_stop_no_note);
            }
            return note.trim();
        }

        private String shortenTimeLabel(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            if (normalized.length() <= MAX_TIME_LABEL_LENGTH) {
                return normalized;
            }
            return normalized.substring(0, MAX_TIME_LABEL_LENGTH - 1) + "…";
        }
    }
}
