package com.bif.app.feature.social.core;

import com.bif.app.feature.social.R;

public abstract class UiState<T> {

    private UiState() {
    }

    public static <T> UiState<T> loading() {
        return new Loading<>();
    }

    public static <T> UiState<T> empty(String message) {
        return new Empty<>(message);
    }

    public static <T> UiState<T> error(String message) {
        return new Error<>(message);
    }

    public static <T> UiState<T> success(T data) {
        return new Success<>(data);
    }

    public static final class Loading<T> extends UiState<T> {
        private Loading() {
        }
    }

    public static final class Empty<T> extends UiState<T> {
        private final String message;

        private Empty(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class Error<T> extends UiState<T> {
        private final String message;

        private Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class Success<T> extends UiState<T> {
        private final T data;

        private Success(T data) {
            this.data = data;
        }

        public T getData() {
            return data;
        }
    }
}