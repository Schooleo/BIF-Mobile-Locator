package com.bif.app.feature.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private Context context;

    @Mock
    private ProfileRepository profileRepository;

    @Test
    public void constructor_loadsLocalProfileIntoUiState() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "content://avatar")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        ProfileViewModel.ProfileUiState state = viewModel.getProfileState().getValue();

        assertTrue(state.isLoggedIn);
        assertEquals("alice", state.usernameRaw);
        assertEquals("alice", state.usernameForDisplay);
        assertEquals("A", state.avatarInitial);
        assertEquals("content://avatar", state.avatarUri);
    }

    @Test
    public void loadFromLocal_blankFields_useNotAvailableAndGuestInitial() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "   ", "", "")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        ProfileViewModel.ProfileUiState state = viewModel.getProfileState().getValue();

        assertEquals("Not available", state.usernameForDisplay);
        assertEquals("Not available", state.emailForDisplay);
        assertEquals("G", state.avatarInitial);
    }

    @Test
    public void loadFromLocal_blankUsername_usesEmailInitial() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "", "bravo@bif.com", "")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);

        assertEquals("B", viewModel.getProfileState().getValue().avatarInitial);
    }

    @Test
    public void onAvatarSelected_savesAvatarReloadsAndEmitsMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
                .thenReturn(new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "old"))
                .thenReturn(new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "new"));

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.onAvatarSelected("new");

        verify(profileRepository).saveAvatarUri("new");
        verify(profileRepository, times(2)).readLocalProfile();
        assertEquals("new", viewModel.getProfileState().getValue().avatarUri);
        assertEquals(Integer.valueOf(R.string.avatar_updated), viewModel.getMessageResId().getValue());
    }

    @Test
    public void refreshProfileFromServer_success_reloadsLocalState() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
                .thenReturn(new ProfileRepository.LocalProfile(true, "old", "old@bif.com", ""))
                .thenReturn(new ProfileRepository.LocalProfile(true, "new", "new@bif.com", ""));
        doAnswer(invocation -> {
            ProfileRepository.ProfileCallback callback = invocation.getArgument(0);
            callback.onSuccess();
            return null;
        }).when(profileRepository).syncProfileMetadata(any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.refreshProfileFromServer();

        assertEquals("new", viewModel.getProfileState().getValue().usernameRaw);
        verify(profileRepository).syncProfileMetadata(any());
    }

    @Test
    public void refreshProfileFromServer_failure_emitsErrorMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            ProfileRepository.ProfileCallback callback = invocation.getArgument(0);
            callback.onFailure();
            return null;
        }).when(profileRepository).syncProfileMetadata(any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.refreshProfileFromServer();

        assertEquals(Integer.valueOf(R.string.profile_sync_failed), viewModel.getMessageResId().getValue());
    }

    @Test
    public void updateProfile_success_reloadsAndEmitsSuccessMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
                .thenReturn(new ProfileRepository.LocalProfile(true, "old", "old@bif.com", ""))
                .thenReturn(new ProfileRepository.LocalProfile(true, "new", "old@bif.com", ""));
        doAnswer(invocation -> {
            ProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onSuccess();
            return null;
        }).when(profileRepository).updateProfile(eq("new"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("new");

        verify(profileRepository).updateProfile(eq("new"), any());
        assertEquals("new", viewModel.getProfileState().getValue().usernameRaw);
        assertEquals(Integer.valueOf(R.string.profile_updated), viewModel.getMessageResId().getValue());
    }

    @Test
    public void updateProfile_failure_emitsFailureMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            ProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onFailure();
            return null;
        }).when(profileRepository).updateProfile(eq("alice"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("alice");

        assertEquals(Integer.valueOf(R.string.profile_update_failed), viewModel.getMessageResId().getValue());
    }

    @Test
    public void consumeMessage_clearsCurrentMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new ProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            ProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onFailure();
            return null;
        }).when(profileRepository).updateProfile(eq("alice"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("alice");
        viewModel.consumeMessage();

        assertNull(viewModel.getMessageResId().getValue());
    }
}