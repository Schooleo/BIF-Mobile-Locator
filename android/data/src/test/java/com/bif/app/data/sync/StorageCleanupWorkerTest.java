package com.bif.app.data.sync;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.bif.app.data.source.local.ProfileDao;
import com.bif.app.data.source.local.TripDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class StorageCleanupWorkerTest {

    @Mock
    private Context context;
    @Mock
    private WorkerParameters workerParameters;
    @Mock
    private ProfileDao profileDao;
    @Mock
    private TripDao tripDao;

    private File tempRoot;

    @Before
    public void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("storage-cleanup-test").toFile();
    }

    @After
    public void tearDown() {
        deleteRecursively(tempRoot);
    }

    @Test
    public void doWork_cleansSyncedAndOrphanFiles() throws IOException {
        when(context.getFilesDir()).thenReturn(tempRoot);

        File stagingDir = new File(tempRoot, "image-staging");
        assertTrue(stagingDir.mkdirs());

        File profileFile = new File(stagingDir, "profile.jpg");
        File tripFile = new File(stagingDir, "trip.jpg");
        File orphanFile = new File(stagingDir, "orphan.jpg");
        File referencedFile = new File(stagingDir, "keep.jpg");

        assertTrue(profileFile.createNewFile());
        assertTrue(tripFile.createNewFile());
        assertTrue(orphanFile.createNewFile());
        assertTrue(referencedFile.createNewFile());

        ProfileEntity profile = new ProfileEntity();
        profile.userId = "user-1";
        profile.localImagePath = profileFile.getAbsolutePath();

        TripStopEntity stop = new TripStopEntity();
        stop.id = "stop-1";
        stop.localImagePath = tripFile.getAbsolutePath();

        when(profileDao.getSyncedWithLocalImagePath()).thenReturn(List.of(profile));
        when(tripDao.getSyncedStopsWithLocalImagePath()).thenReturn(List.of(stop));
        when(profileDao.getAllReferencedLocalImagePaths())
                .thenReturn(List.of(referencedFile.getAbsolutePath()));
        when(tripDao.getAllReferencedLocalImagePaths())
                .thenReturn(Collections.emptyList());

        StorageCleanupWorker worker = new StorageCleanupWorker(
                context,
                workerParameters,
                profileDao,
                tripDao);

        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Success);
        assertTrue(!profileFile.exists());
        assertTrue(!tripFile.exists());
        assertTrue(!orphanFile.exists());
        assertTrue(referencedFile.exists());

        ArgumentCaptor<ProfileEntity> profileCaptor = ArgumentCaptor.forClass(ProfileEntity.class);
        verify(profileDao, atLeastOnce()).upsert(profileCaptor.capture());
        assertTrue(profileCaptor.getValue().localImagePath == null);

        ArgumentCaptor<TripStopEntity> stopCaptor = ArgumentCaptor.forClass(TripStopEntity.class);
        verify(tripDao, atLeastOnce()).upsertStop(stopCaptor.capture());
        assertTrue(stopCaptor.getValue().localImagePath == null);
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
