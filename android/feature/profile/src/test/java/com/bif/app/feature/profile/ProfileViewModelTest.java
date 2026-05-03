package com.bif.app.feature.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.bif.app.domain.repository.IProfileRepository;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.function.BooleanSupplier;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private Context context;

    @Mock
    private IProfileRepository profileRepository;

    @Test
    public void constructor_loadsLocalProfileIntoUiState() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
            new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "content://avatar")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        ProfileViewModel.ProfileUiState state = awaitProfileState(viewModel);

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
            new IProfileRepository.LocalProfile(true, "   ", "", "")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        ProfileViewModel.ProfileUiState state = awaitProfileState(viewModel);

        assertEquals("Not available", state.usernameForDisplay);
        assertEquals("Not available", state.emailForDisplay);
        assertEquals("G", state.avatarInitial);
    }

    @Test
    public void loadFromLocal_blankUsername_usesEmailInitial() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
            new IProfileRepository.LocalProfile(true, "", "bravo@bif.com", "")
        );

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);

        assertEquals("B", awaitProfileState(viewModel).avatarInitial);
    }

    @Test
    public void onAvatarSelected_savesAvatarReloadsAndEmitsMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
            .thenReturn(new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "old"))
            .thenReturn(new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "new"));

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.onAvatarSelected("new");

        verify(profileRepository, timeout(2000)).saveAvatarUri("new");
        verify(profileRepository, timeout(2000).atLeast(2)).readLocalProfile();
        awaitTrue(() -> {
            ProfileViewModel.ProfileUiState state = viewModel.getProfileState().getValue();
            return state != null && "new".equals(state.avatarUri);
        }, "avatar uri was not updated");
        awaitTrue(() -> Integer.valueOf(R.string.avatar_updated)
            .equals(viewModel.getMessageResId().getValue()),
            "avatar success message not emitted");
    }

    @Test
    public void refreshProfileFromServer_success_reloadsLocalState() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
                .thenReturn(new IProfileRepository.LocalProfile(true, "old", "old@bif.com", ""))
                .thenReturn(new IProfileRepository.LocalProfile(true, "new", "new@bif.com", ""));
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(0);
            callback.onSuccess();
            return null;
        }).when(profileRepository).syncProfileMetadata(any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.refreshProfileFromServer(true);

        awaitTrue(() -> {
            ProfileViewModel.ProfileUiState state = viewModel.getProfileState().getValue();
            return state != null && "new".equals(state.usernameRaw);
        }, "profile state did not refresh from server");
        verify(profileRepository).syncProfileMetadata(any());
    }

    @Test
    public void refreshProfileFromServer_failure_emitsErrorMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(0);
            callback.onFailure();
            return null;
        }).when(profileRepository).syncProfileMetadata(any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.refreshProfileFromServer(true);

        awaitTrue(() -> Integer.valueOf(R.string.profile_sync_failed)
            .equals(viewModel.getMessageResId().getValue()),
            "sync failure message not emitted");
    }

    @Test
    public void refreshProfileFromServer_backgroundFailure_staysSilent() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(0);
            callback.onFailure();
            return null;
        }).when(profileRepository).syncProfileMetadata(any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.refreshProfileFromServer(false);

        assertNull(viewModel.getMessageResId().getValue());
    }

    @Test
    public void updateProfile_success_reloadsAndEmitsSuccessMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile())
                .thenReturn(new IProfileRepository.LocalProfile(true, "old", "old@bif.com", ""))
                .thenReturn(new IProfileRepository.LocalProfile(true, "new", "old@bif.com", ""));
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onSuccess();
            return null;
        }).when(profileRepository).updateProfile(eq("new"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("new");

        verify(profileRepository).updateProfile(eq("new"), any());
        awaitTrue(() -> {
            ProfileViewModel.ProfileUiState state = viewModel.getProfileState().getValue();
            return state != null && "new".equals(state.usernameRaw);
        }, "profile username was not updated");
        awaitTrue(() -> Integer.valueOf(R.string.profile_updated)
            .equals(viewModel.getMessageResId().getValue()),
            "profile success message not emitted");
    }

    @Test
    public void updateProfile_failure_emitsFailureMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onFailure();
            return null;
        }).when(profileRepository).updateProfile(eq("alice"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("alice");

        awaitTrue(() -> Integer.valueOf(R.string.profile_update_failed)
            .equals(viewModel.getMessageResId().getValue()),
            "profile failure message not emitted");
    }

    @Test
    public void updateProfile_truncatesUsernameTo15Chars_beforeRepositoryCall() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onSuccess();
            return null;
        }).when(profileRepository).updateProfile(eq("123456789012345"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("12345678901234567890");

        verify(profileRepository).updateProfile(eq("123456789012345"), any());
    }

    @Test
    public void consumeMessage_clearsCurrentMessage() {
        when(context.getString(R.string.not_available)).thenReturn("Not available");
        when(profileRepository.readLocalProfile()).thenReturn(
                new IProfileRepository.LocalProfile(true, "alice", "alice@bif.com", "")
        );
        doAnswer(invocation -> {
            IProfileRepository.ProfileCallback callback = invocation.getArgument(1);
            callback.onFailure();
            return null;
        }).when(profileRepository).updateProfile(eq("alice"), any());

        ProfileViewModel viewModel = new ProfileViewModel(context, profileRepository);
        viewModel.updateProfile("alice");
        awaitTrue(() -> Integer.valueOf(R.string.profile_update_failed)
                .equals(viewModel.getMessageResId().getValue()),
                "profile failure message not emitted before consume");
        viewModel.consumeMessage();

        assertNull(viewModel.getMessageResId().getValue());
    }

    private ProfileViewModel.ProfileUiState awaitProfileState(ProfileViewModel viewModel) {
        awaitTrue(() -> viewModel.getProfileState().getValue() != null,
                "profile state was not posted");
        return viewModel.getProfileState().getValue();
    }

    private void awaitTrue(BooleanSupplier condition, String failureMessage) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting");
            }
        }
        fail(failureMessage);
    }
}
