package com.bif.app.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.data.repository.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ChangePasswordViewModel extends ViewModel {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private final MutableLiveData<UiState> changePasswordState = new MutableLiveData<>(new UiState.Idle());
    private final AuthRepository authRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public ChangePasswordViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<UiState> getChangePasswordState() {
        return changePasswordState;
    }

    public void changePassword(@NonNull String currentPassword,
                               @NonNull String newPassword) {
        changePasswordState.setValue(new UiState.Loading());

        ioExecutor.execute(() -> {
            AuthRepository.Result<?> result = authRepository.changePassword(currentPassword, newPassword);
            if (result instanceof AuthRepository.Result.Success) {
                changePasswordState.postValue(new UiState.Success());
                return;
            }

            AuthRepository.Result.Error<?> error = (AuthRepository.Result.Error<?>) result;
            changePasswordState.postValue(new UiState.Error(error.message));
        });
    }

    public void clearChangePasswordState() {
        UiState current = changePasswordState.getValue();
        if (current instanceof UiState.Success || current instanceof UiState.Error) {
            changePasswordState.setValue(new UiState.Idle());
        }
    }

    public enum ValidationError {
        NONE,
        CURRENT_PASSWORD_EMPTY,
        NEW_PASSWORD_TOO_SHORT,
        CONFIRM_PASSWORD_MISMATCH
    }

    public ValidationError validate(String currentPassword, String newPassword, String confirmPassword) {
        String current = currentPassword == null ? "" : currentPassword.trim();
        String next = newPassword == null ? "" : newPassword;
        String confirm = confirmPassword == null ? "" : confirmPassword;

        if (current.isEmpty()) {
            return ValidationError.CURRENT_PASSWORD_EMPTY;
        }

        if (next.length() < MIN_PASSWORD_LENGTH) {
            return ValidationError.NEW_PASSWORD_TOO_SHORT;
        }

        if (!next.equals(confirm)) {
            return ValidationError.CONFIRM_PASSWORD_MISMATCH;
        }

        return ValidationError.NONE;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdownNow();
    }

    public abstract static class UiState {
        private UiState() {
        }

        public static final class Idle extends UiState {
        }

        public static final class Loading extends UiState {
        }

        public static final class Success extends UiState {
        }

        public static final class Error extends UiState {
            private final String message;

            public Error(String message) {
                this.message = message == null ? "Unknown error" : message;
            }

            public String getMessage() {
                return message;
            }
        }
    }
}
