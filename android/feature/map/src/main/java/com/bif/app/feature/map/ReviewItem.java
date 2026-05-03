package com.bif.app.feature.map;

import com.bif.app.domain.model.Review;

public class ReviewItem {
    public static final int VIEW_TYPE_ADD = 0;
    public static final int VIEW_TYPE_MINE = 1;
    public static final int VIEW_TYPE_OTHERS = 2;

    public int viewType;
    public Review review;
    public boolean isMine;

    // For "Add Review" card at position 0
    public ReviewItem() {
        this.viewType = VIEW_TYPE_ADD;
        this.review = null;
        this.isMine = false;
    }

    // For other reviews
    public ReviewItem(int viewType, Review review, boolean isMine) {
        this.viewType = viewType;
        this.review = review;
        this.isMine = isMine;
    }
}
