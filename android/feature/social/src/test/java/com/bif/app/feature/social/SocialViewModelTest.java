package com.bif.app.feature.social;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.repository.IFriendRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SocialViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IFriendRepository mockRepository;

    private SocialViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockRepository.getFriends()).thenReturn(new MutableLiveData<>());
        viewModel = new SocialViewModel(mockRepository);
    }

    @Test
    public void init_CallsRepositoryGetFriends() {
        verify(mockRepository).getFriends();
    }

    @Test
    public void addFriend_ValidData_CallsRepositoryWithCorrectModel() {
        // Act
        viewModel.addFriend("Cường", "C", 0xFF00FF);

        // Assert
        ArgumentCaptor<Friend> captor = ArgumentCaptor.forClass(Friend.class);
        verify(mockRepository).addFriend(captor.capture());

        Friend capturedFriend = captor.getValue();
        assertEquals("Cường", capturedFriend.getName());
        assertEquals("C", capturedFriend.getAvatarLetter());
        assertEquals(0xFF00FF, capturedFriend.getAvatarColor());
        assertTrue(capturedFriend.isOnline());
    }

    @Test
    public void deleteFriend_ValidFriend_CallsRepositoryDelete() {
        Friend friendToDelete = new Friend(1,"Huy", "H", 0x111111, false);

        viewModel.deleteFriend(friendToDelete);

        verify(mockRepository).deleteFriend(friendToDelete);
    }
}