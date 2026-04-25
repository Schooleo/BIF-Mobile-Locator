package com.bif.app.feature.profile;

import androidx.lifecycle.ViewModel;

public class ChangePasswordViewModel extends ViewModel {

    private static final int MIN_PASSWORD_LENGTH = 6;

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
}
