package com.bif.app.data.sync.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.media.UploadSignatureResponseDto;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.UploadStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class ImageUploadWorkerTest {

    @Mock
    private Context context;
    @Mock
    private WorkerParameters workerParameters;
    @Mock
    private RestApiService restApiService;
    @Mock
    private ProfileDao profileDao;
    @Mock
    private TripDao tripDao;
    @Mock
    private SyncManager syncManager;

    @Test
    public void doWork_profileSignatureMissingPublicId_marksErrorAndFails() throws IOException {
        File localImage = File.createTempFile("avatar", ".jpg");
        localImage.deleteOnExit();

        ProfileEntity pending = new ProfileEntity();
        pending.userId = "user-1";
        pending.localImagePath = localImage.getAbsolutePath();
        pending.uploadStatus = UploadStatus.PENDING;

        UploadSignatureResponseDto signature = new UploadSignatureResponseDto();
        signature.signature = "sig";
        signature.timestamp = 123456L;
        signature.apiKey = "api-key";
        signature.cloudName = "cloud";
        signature.folder = "bif/avatar";
        signature.tags = "avatar";
        signature.publicId = "   ";

        @SuppressWarnings("unchecked")
        Call<UploadSignatureResponseDto> signatureCall = (Call<UploadSignatureResponseDto>) mock(Call.class);

        when(profileDao.getFirstPendingUpload()).thenReturn(pending);
        when(restApiService.getUploadSignature("avatar", null)).thenReturn(signatureCall);
        when(signatureCall.execute()).thenReturn(Response.success(signature));

        ImageUploadWorker worker = new ImageUploadWorker(
                context,
                workerParameters,
                restApiService,
                profileDao,
                tripDao,
                syncManager);

        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Failure);

        ArgumentCaptor<ProfileEntity> profileCaptor = ArgumentCaptor.forClass(ProfileEntity.class);
        verify(profileDao, atLeast(2)).upsert(profileCaptor.capture());
        ProfileEntity saved = profileCaptor.getAllValues()
                .get(profileCaptor.getAllValues().size() - 1);
        assertEquals(UploadStatus.ERROR, saved.uploadStatus);
    }

    @Test
    public void buildUploadOptions_requiresServerSignedPublicId() {
        ImageUploadWorker worker = new ImageUploadWorker(
                context,
                workerParameters,
                restApiService,
                profileDao,
                tripDao,
                syncManager);

        UploadSignatureResponseDto missingPublicId = new UploadSignatureResponseDto();
        missingPublicId.signature = "sig";
        missingPublicId.timestamp = 123L;
        missingPublicId.apiKey = "api";
        missingPublicId.folder = "folder";
        missingPublicId.publicId = " ";

        IllegalArgumentException expected = assertThrows(
                IllegalArgumentException.class,
                () -> worker.buildUploadOptions(missingPublicId));
        assertTrue(expected.getMessage().contains("publicId"));

        UploadSignatureResponseDto valid = new UploadSignatureResponseDto();
        valid.signature = "sig2";
        valid.timestamp = 456L;
        valid.apiKey = "api2";
        valid.folder = "bif/avatar";
        valid.tags = "avatar,user";
        valid.publicId = "  server-signed-id  ";

        Map<String, Object> options = worker.buildUploadOptions(valid);
        assertEquals("server-signed-id", options.get("public_id"));
        assertEquals("bif/avatar", options.get("folder"));
        assertEquals("sig2", options.get("signature"));

        UploadSignatureResponseDto missingFolder = new UploadSignatureResponseDto();
        missingFolder.signature = "sig3";
        missingFolder.timestamp = 789L;
        missingFolder.apiKey = "api3";
        missingFolder.publicId = "server-id";

        IllegalArgumentException missingFolderEx = assertThrows(
                IllegalArgumentException.class,
                () -> worker.buildUploadOptions(missingFolder));
        assertTrue(missingFolderEx.getMessage().contains("folder"));
    }
}
