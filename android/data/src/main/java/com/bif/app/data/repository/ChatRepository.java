package com.bif.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.ChatMessageDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ChatMapper;
import com.bif.app.data.source.local.ChatMessageDao;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.repository.IChatRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class ChatRepository implements IChatRepository {

    private final ChatMessageDao chatMessageDao;
    private final ChatMapper chatMapper;
    private final RestApiService restApiService;
    private final Context context;
    private final ExecutorService executorService;

    @Inject
    public ChatRepository(ChatMessageDao chatMessageDao,
                          ChatMapper chatMapper,
                          RestApiService restApiService,
                          @ApplicationContext Context context) {
        this.chatMessageDao = chatMessageDao;
        this.chatMapper = chatMapper;
        this.restApiService = restApiService;
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<List<ChatMessage>> getMessagesByGroup(String groupId) {
        String resolvedId = UserPreferences.getId(context);
        if (resolvedId == null || resolvedId.isEmpty()) {
            resolvedId = UserPreferences.getUsername(context);
        }
        final String currentUserId = resolvedId;
        return Transformations.map(
                chatMessageDao.getByGroupId(groupId),
                entities -> chatMapper.mapToDomainList(entities, currentUserId)
        );
    }

    @Override
    public void sendMessage(ChatMessage message) {
        executorService.execute(() -> {
            ChatMessageEntity entity = chatMapper.mapToEntity(message);
            chatMessageDao.insert(entity);

            ChatMessageDto dto = new ChatMessageDto();
            dto.id = message.getId();
            dto.groupId = message.getGroupId();
            dto.senderUserId = message.getSenderUserId();
            dto.content = message.getContent();
            dto.type = message.getType();
            dto.clientMessageId = message.getClientMessageId();

            restApiService.postChatMessage(dto).enqueue(new Callback<>() {
                @Override
                public void onResponse(@androidx.annotation.NonNull Call<ChatMessageDto> call,
                                       @androidx.annotation.NonNull Response<ChatMessageDto> response) {
                    // Server confirmed — could update local state
                }

                @Override
                public void onFailure(@androidx.annotation.NonNull Call<ChatMessageDto> call, @androidx.annotation.NonNull Throwable t) {
                    // Handle offline — message stays in local DB
                }
            });
        });
    }

    @Override
    public void sendLocationMessage(String groupId, String senderUserId,
                                     double latitude, double longitude,
                                     String address) {
        String id = UUID.randomUUID().toString();
        String clientMsgId = UUID.randomUUID().toString();
        ChatMessage message = new ChatMessage(
                id, groupId, senderUserId, null, address,
                "LOCATION", System.currentTimeMillis(), clientMsgId,
                latitude, longitude, address, false, true
        );
        sendMessage(message);
    }

    @Override
    public void refreshMessages(String groupId) {
        restApiService.getChatMessages(groupId).enqueue(
                new Callback<>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull Call<List<ChatMessageDto>> call,
                                           @androidx.annotation.NonNull Response<List<ChatMessageDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            executorService.execute(() -> {
                                List<ChatMessageEntity> entities = new ArrayList<>();
                                for (ChatMessageDto dto : response.body()) {
                                    entities.add(dtoToEntity(dto));
                                }
                                chatMessageDao.deleteByGroupId(groupId);
                                chatMessageDao.insertAll(entities);
                            });
                        }
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull Call<List<ChatMessageDto>> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        // Use cached data
                    }
                });
    }

    private ChatMessageEntity dtoToEntity(ChatMessageDto dto) {
        double lat = 0;
        double lng = 0;
        if (dto.sharedLocation != null) {
            lat = dto.sharedLocation.latitude;
            lng = dto.sharedLocation.longitude;
        }
        long sentAtMillis = 0;
        if (dto.sentAt != null) {
            try {
                sentAtMillis = java.time.Instant.parse(dto.sentAt).toEpochMilli();
            } catch (Exception e) {
                sentAtMillis = System.currentTimeMillis();
            }
        }
        return new ChatMessageEntity(
                dto.id != null ? dto.id : UUID.randomUUID().toString(),
                dto.groupId, dto.senderUserId, null,
                dto.content, dto.type, sentAtMillis,
                dto.clientMessageId, lat, lng,
                dto.sharedAddress, dto.confirmed
        );
    }
}
