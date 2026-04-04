package com.bif.app.feature.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.feature.map.R;

import java.util.ArrayList;
import java.util.List;

public class ReviewAdapter extends ListAdapter<ReviewItem, RecyclerView.ViewHolder> {

    private static final DiffUtil.ItemCallback<ReviewItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ReviewItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ReviewItem oldItem, @NonNull ReviewItem newItem) {
            if (oldItem.viewType != newItem.viewType) {
                return false;
            }

            if (oldItem.viewType == ReviewItem.VIEW_TYPE_ADD) {
                return true;
            }

            if (oldItem.review == null || newItem.review == null) {
                return false;
            }

            return oldItem.review.userId.equals(newItem.review.userId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ReviewItem oldItem, @NonNull ReviewItem newItem) {
            if (oldItem.viewType != newItem.viewType) {
                return false;
            }

            if (oldItem.viewType == ReviewItem.VIEW_TYPE_ADD) {
                return true;
            }

            if (oldItem.review == null || newItem.review == null) {
                return oldItem.review == newItem.review;
            }

            return oldItem.review.userId.equals(newItem.review.userId) &&
                    oldItem.review.rating == newItem.review.rating &&
                    oldItem.review.comment.equals(newItem.review.comment) &&
                    oldItem.review.userName.equals(newItem.review.userName);
        }
    };

    public ReviewAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= getItemCount()) {
            return ReviewItem.VIEW_TYPE_OTHERS;
        }
        return getItem(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == ReviewItem.VIEW_TYPE_ADD) {
            View view = inflater.inflate(R.layout.item_review_add, parent, false);
            return new AddReviewViewHolder(view);
        } else if (viewType == ReviewItem.VIEW_TYPE_MINE) {
            View view = inflater.inflate(R.layout.item_review_mine, parent, false);
            return new MyReviewViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_review_others, parent, false);
            return new OthersReviewViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ReviewItem item = getItem(position);

        if (holder instanceof AddReviewViewHolder) {
            // No binding needed for add review card
            return;
        } else if (holder instanceof MyReviewViewHolder && item.viewType == ReviewItem.VIEW_TYPE_MINE) {
            MyReviewViewHolder viewHolder = (MyReviewViewHolder) holder;
            viewHolder.bind(item.review);
        } else if (holder instanceof OthersReviewViewHolder && item.viewType == ReviewItem.VIEW_TYPE_OTHERS) {
            OthersReviewViewHolder viewHolder = (OthersReviewViewHolder) holder;
            viewHolder.bind(item.review);
        }
    }

    // =============== ViewHolder Classes ===============

    private static class AddReviewViewHolder extends RecyclerView.ViewHolder {
        public AddReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            // No special initialization needed
        }
    }

    private static class MyReviewViewHolder extends RecyclerView.ViewHolder {
        private final RatingBar rbReviewRating;
        private final TextView tvReviewComment;
        private final TextView tvReviewDate;

        public MyReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            rbReviewRating = itemView.findViewById(R.id.rb_review_rating);
            tvReviewComment = itemView.findViewById(R.id.tv_review_comment);
            tvReviewDate = itemView.findViewById(R.id.tv_review_date);
        }

        public void bind(com.bif.app.core.network.dto.place.PlaceReviewDto review) {
            if (review == null) {
                return;
            }

            rbReviewRating.setRating(review.rating);
            tvReviewComment.setText(review.comment != null ? review.comment : "");
            tvReviewDate.setText("Today"); // You can calculate relative time here
        }
    }

    private static class OthersReviewViewHolder extends RecyclerView.ViewHolder {
        private final RatingBar rbReviewRating;
        private final TextView tvReviewAuthor;
        private final TextView tvReviewComment;
        private final TextView tvReviewDate;

        public OthersReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            rbReviewRating = itemView.findViewById(R.id.rb_review_rating);
            tvReviewAuthor = itemView.findViewById(R.id.tv_review_author);
            tvReviewComment = itemView.findViewById(R.id.tv_review_comment);
            tvReviewDate = itemView.findViewById(R.id.tv_review_date);
        }

        public void bind(com.bif.app.core.network.dto.place.PlaceReviewDto review) {
            if (review == null) {
                return;
            }

            rbReviewRating.setRating(review.rating);
            tvReviewAuthor.setText(review.userName != null ? review.userName : "Anonymous");
            tvReviewComment.setText(review.comment != null ? review.comment : "");
            tvReviewDate.setText("1 week ago"); // You can calculate relative time here
        }
    }

    // =============== Utility Methods ===============

    /**
     * Filter reviews by star rating while keeping the "Add/Mine" card at position 0
     */
    public void filterByStarRating(List<ReviewItem> allReviews, int starRating) {
        List<ReviewItem> filteredList = new ArrayList<>();

        // Always keep the first item (Add/Mine card)
        if (!allReviews.isEmpty()) {
            filteredList.add(allReviews.get(0));
        }

        // Filter by rating
        for (int i = 1; i < allReviews.size(); i++) {
            ReviewItem item = allReviews.get(i);
            if (starRating == 0) {
                // Show all
                filteredList.add(item);
            } else if (item.review != null && item.review.rating == starRating) {
                filteredList.add(item);
            }
        }

        submitList(filteredList);
    }

    /**
     * Show all reviews including the "Add/Mine" card at position 0
     */
    public void showAllReviews(List<ReviewItem> allReviews) {
        List<ReviewItem> displayList = new ArrayList<>(allReviews);
        submitList(displayList);
    }
}
