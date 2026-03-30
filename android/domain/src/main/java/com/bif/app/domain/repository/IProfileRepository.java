package com.bif.app.domain.repository;

public interface IProfileRepository {

    class LocalProfile {
        public final boolean isLoggedIn;
        public final String username;
        public final String email;
        public final String avatarUri;

        public LocalProfile(boolean isLoggedIn, String username,
                            String email, String avatarUri) {
            this.isLoggedIn = isLoggedIn;
            this.username = username;
            this.email = email;
            this.avatarUri = avatarUri;
        }
    }

    interface ProfileCallback {
        void onSuccess();

        void onFailure();
    }

    LocalProfile readLocalProfile();

    void saveAvatarUri(String avatarUri);

    void syncProfileMetadata(ProfileCallback callback);

    void updateProfile(String updatedUsername, ProfileCallback callback);
}
