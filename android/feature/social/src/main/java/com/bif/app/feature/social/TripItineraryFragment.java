package com.bif.app.feature.social;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TripItineraryFragment extends Fragment {

    private TripDetailViewModel viewModel;
    private TripPlan currentTrip;
    private ItineraryAdapter adapter;
    private PendingStopEdit pendingStopEdit;
    private String pendingRemovedStopId;

    public static TripItineraryFragment newInstance(String tripId) {
        TripItineraryFragment fragment = new TripItineraryFragment();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trip_itinerary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnAddStop = view.findViewById(R.id.btn_add_stop);
        RecyclerView rvItinerary = view.findViewById(R.id.rv_itinerary);
        TextView tvEmpty = view.findViewById(R.id.tv_itinerary_empty);

        String tripId = "";
        Bundle args = getArguments();
        if (args != null) {
            tripId = args.getString("tripId", "");
        }
        String finalTripId = tripId;

        adapter = new ItineraryAdapter(requireContext(), new ItineraryAdapter.StopActionListener() {
            @Override
            public void onDeleteStop(@NonNull TripStop stop) {
                confirmDeleteStop(stop);
            }

            @Override
            public void onEditStop(@NonNull TripStop stop) {
                showStopEditDialog(stop);
            }
        });
        rvItinerary.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvItinerary.setAdapter(adapter);

        btnAddStop.setOnClickListener(v -> {
            android.net.Uri destination = UriUtils.buildUri("/social/add-trip-stop")
                    .buildUpon()
                    .appendQueryParameter("tripId", finalTripId)
                    .build();
            Navigation.findNavController(v).navigate(destination);
        });

        viewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        if (viewModel.getTrip() != null) {
            viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(trip, tvEmpty));
        }
    }

    private void bindTrip(@Nullable TripPlan trip, @NonNull TextView tvEmpty) {
        currentTrip = trip;
        List<TripStop> stops = trip == null || trip.getStops() == null
                ? Collections.emptyList()
                : new ArrayList<>(trip.getStops());

        stops.sort((left, right) -> {
            long leftAnchor = left.getArrivalTime() > 0
                    ? left.getArrivalTime()
                    : (left.getDepartureTime() > 0 ? left.getDepartureTime() : 0L);
            long rightAnchor = right.getArrivalTime() > 0
                    ? right.getArrivalTime()
                    : (right.getDepartureTime() > 0 ? right.getDepartureTime() : 0L);
            if (leftAnchor <= 0L && rightAnchor <= 0L) {
                return Integer.compare(left.getOrderIndex(), right.getOrderIndex());
            }
            if (leftAnchor <= 0L) {
                return 1;
            }
            if (rightAnchor <= 0L) {
                return -1;
            }
            int compare = Long.compare(leftAnchor, rightAnchor);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(left.getOrderIndex(), right.getOrderIndex());
        });
        adapter.setItems(stops);
        tvEmpty.setVisibility(stops.isEmpty() ? View.VISIBLE : View.GONE);

        maybeResolvePendingStopOperations(stops);
    }

    private void showStopEditDialog(@NonNull TripStop stop) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_trip_stop, null, false);
        EditText noteInput = content.findViewById(R.id.et_edit_stop_note);
        TextView dateInput = content.findViewById(R.id.tv_edit_stop_date);
        TextView timeInput = content.findViewById(R.id.tv_edit_stop_time);
        MaterialButton btnCancel = content.findViewById(R.id.btn_edit_stop_cancel);
        MaterialButton btnSave = content.findViewById(R.id.btn_edit_stop_save);

        String originalNote = stop.getNote() == null ? "" : stop.getNote();
        noteInput.setText(originalNote);

        final Calendar[] selected = new Calendar[1];
        long anchor = stop.getArrivalTime() > 0 ? stop.getArrivalTime() : stop.getDepartureTime();
        if (anchor > 0) {
            selected[0] = Calendar.getInstance();
            selected[0].setTimeInMillis(anchor);
        }

        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(requireContext());
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(requireContext());

        Runnable refreshDateTime = () -> {
            if (selected[0] == null) {
                dateInput.setText("");
                timeInput.setText("");
                return;
            }
            dateInput.setText(dateFormat.format(selected[0].getTime()));
            timeInput.setText(timeFormat.format(selected[0].getTime()));
        };
        if (anchor > 0) {
            refreshDateTime.run();
        } else {
            dateInput.setText("");
            timeInput.setText("");
        }

        dateInput.setOnClickListener(v -> {
            if (selected[0] == null) {
                selected[0] = Calendar.getInstance();
                selected[0].set(Calendar.HOUR_OF_DAY, 9);
                selected[0].set(Calendar.MINUTE, 0);
                selected[0].set(Calendar.SECOND, 0);
                selected[0].set(Calendar.MILLISECOND, 0);
            }
            DatePickerDialog dateDialog = new DatePickerDialog(
                    requireContext(),
                    (picker, year, month, dayOfMonth) -> {
                        selected[0].set(Calendar.YEAR, year);
                        selected[0].set(Calendar.MONTH, month);
                        selected[0].set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        refreshDateTime.run();
                    },
                    selected[0].get(Calendar.YEAR),
                    selected[0].get(Calendar.MONTH),
                    selected[0].get(Calendar.DAY_OF_MONTH)
            );

            if (currentTrip != null) {
                if (currentTrip.getStartAt() > 0) {
                    dateDialog.getDatePicker().setMinDate(currentTrip.getStartAt());
                }
                if (currentTrip.getEndAt() > 0) {
                    dateDialog.getDatePicker().setMaxDate(currentTrip.getEndAt());
                }
            }
            dateDialog.show();
        });

        timeInput.setOnClickListener(v -> {
            if (selected[0] == null) {
                selected[0] = Calendar.getInstance();
                selected[0].set(Calendar.SECOND, 0);
                selected[0].set(Calendar.MILLISECOND, 0);
            }
            TimePickerDialog timeDialog = new TimePickerDialog(
                    requireContext(),
                    (picker, hourOfDay, minute) -> {
                        selected[0].set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selected[0].set(Calendar.MINUTE, minute);
                        selected[0].set(Calendar.SECOND, 0);
                        selected[0].set(Calendar.MILLISECOND, 0);
                        refreshDateTime.run();
                    },
                    selected[0].get(Calendar.HOUR_OF_DAY),
                    selected[0].get(Calendar.MINUTE),
                    true
            );
            timeDialog.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            long selectedMillis = selected[0] != null ? selected[0].getTimeInMillis() : 0L;
            if (selectedMillis > 0L && !isWithinTripRange(selectedMillis)) {
                Toast.makeText(requireContext(), R.string.trip_stop_time_out_of_range, Toast.LENGTH_SHORT).show();
                return;
            }

            String newNote = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
            String safeStopId = stop.getId();
            if (safeStopId == null || safeStopId.trim().isEmpty()) {
                dialog.dismiss();
                return;
            }
            pendingStopEdit = new PendingStopEdit(safeStopId, newNote, selectedMillis);
            viewModel.updateStopDetails(stop, newNote, selectedMillis);

            dialog.dismiss();
        });

        dialog.show();
    }

    private void confirmDeleteStop(@NonNull TripStop stop) {
        String stopTitle = stop.getTitle() == null || stop.getTitle().trim().isEmpty()
                ? getString(R.string.trip_stop_untitled)
                : stop.getTitle().trim();

        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.delete),
                getString(R.string.trip_stop_delete_confirm, stopTitle),
                getString(R.string.delete),
                getString(R.string.cancel),
                () -> {
                    String stopId = stop.getId();
                    if (stopId == null || stopId.trim().isEmpty()) {
                        return;
                    }
                    pendingRemovedStopId = stopId;
                    viewModel.removeStop(stopId);
                }
        );
    }

    private void maybeResolvePendingStopOperations(@NonNull List<TripStop> stops) {
        if (pendingRemovedStopId != null && !pendingRemovedStopId.trim().isEmpty()) {
            boolean stillExists = false;
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                if (pendingRemovedStopId.equals(stop.getId())) {
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                Toast.makeText(requireContext(), R.string.remove, Toast.LENGTH_SHORT).show();
                pendingRemovedStopId = null;
            }
        }

        if (pendingStopEdit != null) {
            for (TripStop stop : stops) {
                if (stop == null || !pendingStopEdit.stopId.equals(stop.getId())) {
                    continue;
                }

                String currentNote = stop.getNote() == null ? "" : stop.getNote().trim();
                long currentSchedule = stop.getArrivalTime() > 0
                        ? stop.getArrivalTime()
                        : stop.getDepartureTime();
                if (currentSchedule <= 0L) {
                    currentSchedule = 0L;
                }

                if (pendingStopEdit.note.equals(currentNote)
                        && pendingStopEdit.scheduledAtMillis == currentSchedule) {
                    Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show();
                    pendingStopEdit = null;
                }
                break;
            }
        }
    }

    private boolean isWithinTripRange(long selectedMillis) {
        if (currentTrip == null) {
            return true;
        }
        long startAt = currentTrip.getStartAt();
        long endAt = currentTrip.getEndAt();
        if (startAt > 0 && selectedMillis < startAt) {
            return false;
        }
        return endAt <= 0 || selectedMillis <= endAt;
    }

    private static class ItineraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_DAY_HEADER = 0;
        private static final int TYPE_STOP = 1;

        private final List<RowItem> items = new ArrayList<>();
        private final Set<String> expandedStopIds = new HashSet<>();
        private final Map<TripStop, String> fallbackStopKeys = new IdentityHashMap<>();
        private final Context context;
        private final StopActionListener stopActionListener;
        private final java.text.DateFormat dateHeaderFormatter;
        private final java.text.DateFormat timeFormatter;

        ItineraryAdapter(@NonNull Context context, @NonNull StopActionListener stopActionListener) {
            this.context = context;
            this.stopActionListener = stopActionListener;
            this.dateHeaderFormatter = android.text.format.DateFormat.getMediumDateFormat(context);
            this.timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
        }

        @Override
        public int getItemViewType(int position) {
            RowItem item = items.get(position);
            return item.type == RowItem.Type.DAY_HEADER ? TYPE_DAY_HEADER : TYPE_STOP;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_DAY_HEADER) {
                View view = inflater.inflate(R.layout.item_itinerary_day_header, parent, false);
                return new DayHeaderViewHolder(view);
            }
            View view = inflater.inflate(R.layout.item_itinerary_stop, parent, false);
            return new StopViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowItem rowItem = items.get(position);
            if (holder instanceof DayHeaderViewHolder) {
                DayHeaderViewHolder dayHolder = (DayHeaderViewHolder) holder;
                dayHolder.header.setText(rowItem.dayLabel);
                String label = rowItem.stopCount == 1
                    ? dayHolder.itemView.getContext().getString(R.string.trip_stop_count_single, rowItem.stopCount)
                    : dayHolder.itemView.getContext().getString(R.string.trip_stop_count_plural, rowItem.stopCount);
                dayHolder.count.setText(label);
                return;
            }

            if (!(holder instanceof StopViewHolder) || rowItem.stop == null) {
                return;
            }

            StopViewHolder stopHolder = (StopViewHolder) holder;
            TripStop stop = rowItem.stop;
            stopHolder.index.setText(String.valueOf(rowItem.dayOrderIndex));

            String title = stop.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = stopHolder.itemView.getContext().getString(R.string.trip_stop_untitled);
            }
            stopHolder.title.setText(title);

            String timePart = "";
            if (stop.getArrivalTime() > 0) {
                timePart = timeFormatter.format(new Date(stop.getArrivalTime()));
            } else if (stop.getDepartureTime() > 0) {
                timePart = timeFormatter.format(new Date(stop.getDepartureTime()));
            }
            stopHolder.time.setText(timePart.isEmpty()
                    ? stopHolder.itemView.getContext().getString(R.string.trip_stop_no_note)
                    : timePart);

            String addressText = stop.getAddress();
            if (TextUtils.isEmpty(addressText)) {
                addressText = stopHolder.itemView.getContext().getString(R.string.trip_stop_no_note);
            }
            String noteText = stop.getNote();
            if (TextUtils.isEmpty(noteText)) {
                noteText = stopHolder.itemView.getContext().getString(R.string.trip_stop_no_note);
            }

            String stopKey = getStopKey(stop);
            boolean expanded = expandedStopIds.contains(stopKey);
            stopHolder.detailsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            stopHolder.subtitle.setVisibility(expanded ? View.GONE : View.VISIBLE);
                stopHolder.subtitle.setText(addressText);
                stopHolder.address.setText(stopHolder.itemView.getContext().getString(R.string.trip_stop_address_label, addressText));
            stopHolder.notes.setText(stopHolder.itemView.getContext().getString(
                    R.string.trip_stop_notes_label,
                    noteText));
            stopHolder.rating.setText(R.string.trip_stop_rating_none);
                stopHolder.expandIcon.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
                stopHolder.expandIcon.setContentDescription(stopHolder.itemView.getContext().getString(
                    expanded ? R.string.collapse_less : R.string.expand_more));

            stopHolder.itemView.setOnClickListener(v -> {
                if (expanded) {
                    expandedStopIds.remove(stopKey);
                } else {
                    expandedStopIds.add(stopKey);
                }
                int adapterPosition = stopHolder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
            });

            boolean hasPersistedId = stop.getId() != null && !stop.getId().trim().isEmpty();
            stopHolder.btnDelete.setOnClickListener(null);
            stopHolder.btnEdit.setOnClickListener(null);
            stopHolder.btnDelete.setEnabled(hasPersistedId);
            stopHolder.btnEdit.setEnabled(hasPersistedId);
            if (hasPersistedId) {
                stopHolder.btnDelete.setOnClickListener(v -> stopActionListener.onDeleteStop(stop));
                stopHolder.btnEdit.setOnClickListener(v -> stopActionListener.onEditStop(stop));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void setItems(@NonNull List<TripStop> data) {
            items.clear();

            Map<TripStop, String> nextFallbackStopKeys = new IdentityHashMap<>();
            Set<String> activeStopKeys = new HashSet<>();

            String currentKey = null;
            int dayCount = 0;
            int dayStartIndex = -1;
            for (TripStop stop : data) {
                long anchorTime = stop.getArrivalTime() > 0
                        ? stop.getArrivalTime()
                        : stop.getDepartureTime();
                String dayKey;
                String dayLabel;
                if (anchorTime > 0) {
                    dayKey = dateHeaderFormatter.format(new Date(anchorTime));
                    dayLabel = dayKey;
                } else {
                    dayKey = "NO_DATE";
                    dayLabel = context.getString(R.string.trip_no_date);
                }

                if (!dayKey.equals(currentKey)) {
                    if (dayStartIndex >= 0) {
                        RowItem previous = items.get(dayStartIndex);
                        previous.stopCount = dayCount;
                    }
                    dayCount = 0;
                    items.add(RowItem.dayHeader(dayLabel));
                    dayStartIndex = items.size() - 1;
                    currentKey = dayKey;
                }
                dayCount++;
                String stopKey = getStopKey(stop);
                activeStopKeys.add(stopKey);
                if (stop.getId() == null || stop.getId().trim().isEmpty()) {
                    nextFallbackStopKeys.put(stop, stopKey);
                }
                items.add(RowItem.stop(stop, dayCount));
            }

            fallbackStopKeys.clear();
            fallbackStopKeys.putAll(nextFallbackStopKeys);
            expandedStopIds.retainAll(activeStopKeys);

            if (dayStartIndex >= 0) {
                RowItem previous = items.get(dayStartIndex);
                previous.stopCount = dayCount;
            }

            notifyDataSetChanged();
        }

        @NonNull
        private String getStopKey(@NonNull TripStop stop) {
            String stopId = stop.getId();
            if (stopId != null && !stopId.trim().isEmpty()) {
                return stopId;
            }
            String existing = fallbackStopKeys.get(stop);
            if (existing != null) {
                return existing;
            }
            String created = "draft-stop-" + UUID.randomUUID();
            fallbackStopKeys.put(stop, created);
            return created;
        }

        static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView count;

            DayHeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                header = itemView.findViewById(R.id.tv_day_header);
                count = itemView.findViewById(R.id.tv_day_stop_count);
            }
        }

        static class StopViewHolder extends RecyclerView.ViewHolder {
            final TextView index;
            final TextView title;
            final TextView time;
            final ImageView expandIcon;
            final TextView subtitle;
            final View detailsContainer;
            final TextView address;
            final TextView notes;
            final TextView rating;
            final MaterialButton btnEdit;
            final MaterialButton btnDelete;

            StopViewHolder(@NonNull View itemView) {
                super(itemView);
                index = itemView.findViewById(R.id.tv_stop_index);
                title = itemView.findViewById(R.id.tv_stop_title);
                time = itemView.findViewById(R.id.tv_stop_time);
                expandIcon = itemView.findViewById(R.id.iv_stop_expand_icon);
                subtitle = itemView.findViewById(R.id.tv_stop_subtitle);
                detailsContainer = itemView.findViewById(R.id.layout_stop_details);
                address = itemView.findViewById(R.id.tv_stop_address);
                notes = itemView.findViewById(R.id.tv_stop_notes);
                rating = itemView.findViewById(R.id.tv_stop_rating);
                btnEdit = itemView.findViewById(R.id.btn_edit_stop);
                btnDelete = itemView.findViewById(R.id.btn_delete_stop);
            }
        }

        interface StopActionListener {
            void onDeleteStop(@NonNull TripStop stop);
            void onEditStop(@NonNull TripStop stop);
        }

        static class RowItem {
            enum Type {
                DAY_HEADER,
                STOP
            }

            final Type type;
            final String dayLabel;
            final TripStop stop;
            int stopCount;
            final int dayOrderIndex;

            private RowItem(Type type, String dayLabel, TripStop stop, int dayOrderIndex) {
                this.type = type;
                this.dayLabel = dayLabel;
                this.stop = stop;
                this.dayOrderIndex = dayOrderIndex;
            }

            static RowItem dayHeader(String dayLabel) {
                return new RowItem(Type.DAY_HEADER, dayLabel, null, 0);
            }

            static RowItem stop(TripStop stop, int dayOrderIndex) {
                return new RowItem(Type.STOP, null, stop, dayOrderIndex);
            }
        }
    }

    private static class PendingStopEdit {
        final String stopId;
        final String note;
        final long scheduledAtMillis;

        PendingStopEdit(String stopId, String note, long scheduledAtMillis) {
            this.stopId = stopId;
            this.note = note == null ? "" : note;
            this.scheduledAtMillis = Math.max(0L, scheduledAtMillis);
        }
    }
}

