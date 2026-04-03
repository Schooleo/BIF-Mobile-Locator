package com.bif.app.feature.profile;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.repository.IProfileRepository;

import javax.inject.Inject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class ProfileViewModel extends ViewModel {

    public static class ProfileUiState {
        public final boolean isLoggedIn;
        public final String usernameRaw;
        public final String emailRaw;
        public final String usernameForDisplay;
        public final String emailForDisplay;
        public final String avatarUri;
        public final String avatarInitial;

        ProfileUiState(
                boolean isLoggedIn,
                String usernameRaw,
                String emailRaw,
                String usernameForDisplay,
                String emailForDisplay,
                String avatarUri,
                String avatarInitial
        ) {
            this.isLoggedIn = isLoggedIn;
            this.usernameRaw = usernameRaw;
            this.emailRaw = emailRaw;
            this.usernameForDisplay = usernameForDisplay;
            this.emailForDisplay = emailForDisplay;
            this.avatarUri = avatarUri;
            this.avatarInitial = avatarInitial;
        }
    }

    private final Context appContext;
    private final IProfileRepository profileRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<ProfileUiState> profileState = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageResId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    @Inject
    public ProfileViewModel(
            @ApplicationContext Context appContext,
            IProfileRepository profileRepository
    ) {
        this.appContext = appContext;
        this.profileRepository = profileRepository;
        loadFromLocal();
    }

    public LiveData<ProfileUiState> getProfileState() {
        return profileState;
    }

    public LiveData<Integer> getMessageResId() {
        return messageResId;
    }

    public LiveData<Boolean> getIsRefreshing() {
        return isRefreshing;
    }

    public void consumeMessage() {
        messageResId.postValue(null);
    }

    public void loadFromLocal() {
        ioExecutor.execute(() -> {
            IProfileRepository.LocalProfile localProfile =
                    profileRepository.readLocalProfile();
            boolean isLoggedIn = localProfile.isLoggedIn;
            String usernameRaw = localProfile.username;
            String emailRaw = localProfile.email;
            String avatarUri = localProfile.avatarUri;

            String usernameForDisplay = getStoredValue(usernameRaw);
            String emailForDisplay = getStoredValue(emailRaw);
            String avatarInitial = resolveAvatarInitial(usernameRaw, emailRaw);

            profileState.postValue(new ProfileUiState(
                    isLoggedIn,
                    usernameRaw,
                    emailRaw,
                    usernameForDisplay,
                    emailForDisplay,
                    avatarUri,
                    avatarInitial
            ));
        });
    }

    public void onAvatarSelected(@NonNull String avatarUri) {
        ioExecutor.execute(() -> {
            String stagedPath = copyToInternalStorage(avatarUri);
            if (stagedPath == null || stagedPath.isBlank()) {
                messageResId.postValue(R.string.profile_update_failed);
                return;
            }

            profileRepository.saveAvatarUri(stagedPath);
            loadFromLocal();
            messageResId.postValue(R.string.avatar_updated);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
    }

    public void refreshProfileFromServer() {
        refreshProfileFromServer(false);
    }

    public void refreshProfileFromServer(boolean showLoading) {
        if (showLoading) {
            isRefreshing.setValue(true);
        }

        profileRepository.syncProfileMetadata(new IProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                loadFromLocal();
                isRefreshing.postValue(false);
            }

            @Override
            public void onFailure() {
                isRefreshing.postValue(false);
                messageResId.postValue(R.string.profile_sync_failed);
            }
        });
    }

    public void updateProfile(@NonNull String updatedUsername) {
        profileRepository.updateProfile(updatedUsername,
                new IProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                loadFromLocal();
                messageResId.postValue(R.string.profile_updated);
            }

            @Override
            public void onFailure() {
                messageResId.postValue(R.string.profile_update_failed);
            }
        });
    }

    private String getStoredValue(String value) {
        if (value == null) {
            return appContext.getString(R.string.not_available);
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? appContext.getString(R.string.not_available) : trimmedValue;
    }

    private String resolveAvatarInitial(String username, String email) {
        if (username != null && !username.isBlank()) {
            return username.substring(0, 1).toUpperCase();
        }
        if (email != null && !email.isBlank()) {
            return email.substring(0, 1).toUpperCase();
        }
        return "G";
    }

    private String copyToInternalStorage(String uriString) {
        try {
            if (uriString != null
                    && !uriString.startsWith("content://")
                    && !uriString.startsWith("file://")) {
                return uriString;
            }

            Uri sourceUri = Uri.parse(uriString);
            File stagingDir = new File(appContext.getFilesDir(), "image-staging");
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                return null;
            }

            File outFile = new File(stagingDir,
                    "avatar-" + UUID.randomUUID() + ".jpg");
            try (InputStream input = appContext.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream output = new FileOutputStream(outFile)) {
                if (input == null) {
                    return null;
                }

                byte[] buffer = new byte[8192];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                output.flush();
            }
            return outFile.getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }

}