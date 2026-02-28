package com.bif.app.feature.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bif.app.feature.favorites.R;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoriteDetailFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        String name = args.getString("favName", "");
        String address = args.getString("favAddress", "");
        String description = args.getString("favDescription", "");
        String notes = args.getString("favNotes", "");
        int rating = args.getInt("favRating", 0);

        TextView tvName = view.findViewById(R.id.tv_detail_name);
        TextView tvAddress = view.findViewById(R.id.tv_detail_address);
        TextView tvDescription = view.findViewById(R.id.tv_detail_description);
        TextView tvNotes = view.findViewById(R.id.tv_detail_notes);
        RatingBar ratingBar = view.findViewById(R.id.rating_bar);
        ImageView imgDetail = view.findViewById(R.id.img_detail);

        tvName.setText(name);
        tvAddress.setText(address);
        tvDescription.setText(description);
        tvNotes.setText(notes);
        ratingBar.setRating(rating);

        imgDetail.setImageResource(android.R.drawable.ic_menu_gallery);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
    }
}
