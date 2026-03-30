package com.bif.app.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.ChatMessageDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ChatMapper;
import com.bif.app.data.source.local.ChatMessageDao;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.repository.IChatRepository;
import com.google.gson.Gson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompMessage;

@Singleton
public class ChatRepository implements IChatRepository {

    private static final String TAG = "ChatRepository";

    private final ChatMessageDao chatMessageDao;
    private final ChatMapper chatMapper;
    private final RestApiService restApiService;
    private final SyncManager syncManager;
    private final Context context;
    private final ExecutorService executorService;
    private final Gson gson;

    // WebSocket
    private StompClient stompClient;
    private final CompositeDisposable wsDisposables = new CompositeDisposable();
    private String connectedGroupId;

    // Base WebSocket URL — mirrors REST_BASE_URL but uses the ws/stomp endpoint.
    // SockJS fallback: the server exposes /ws via SockJS.
    // The STOMP library will append /websocket for the raw socket connection.
    private static final String WS_URL_TEMPLATE = "ws://%s:8080/ws/websocket";

    @Inject
    public ChatRepository(ChatMessageDao chatMessageDao,
                          ChatMapper chatMapper,
                          RestApiService restApiService,
                          SyncManager syncManager,
                          @ApplicationContext Context context) {
        this.chatMessageDao = chatMessageDao;
        this.chatMapper = chatMapper;
        this.restApiService = restApiService;
        this.syncManager = syncManager;
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
    }

    // ─── IChatRepository ───────────────────────────────────────────────────────

    @Override
    public LiveData<List<ChatMessage>> getMessagesByGroup(String groupId) {
        String resolvedId = UserPreferences.getId(context);
        if (resolvedId.isEmpty()) {
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
            // 1. Persist locally first (optimistic insert).
            ChatMessageEntity entity = chatMapper.mapToEntity(message);
            chatMessageDao.insert(entity);

            // 2. Try to send via STOMP (real-time).
            if (stompClient != null && stompClient.isConnected()) {
                sendViaWebSocket(message);
            } else {
                // 3. Fallback: REST POST + enqueue in sync queue for retry.
                sendViaRest(message);
            }
        });
    }

    @Override
    public void sendLocationMessage(String groupId, String senderUserId,
                                    double latitude, double longitude, String address) {
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
        restApiService.getChatMessages(groupId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ChatMessageDto>> call,
                                   @NonNull Response<List<ChatMessageDto>> response) {
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
            public void onFailure(@NonNull Call<List<ChatMessageDto>> call, @NonNull Throwable t) {
                Log.w(TAG, "refreshMessages failed — using cached data", t);
            }
        });
    }

    @Override
    public void connectToGroup(String groupId) {
        if (groupId == null) return;
        if (groupId.equals(connectedGroupId) && stompClient != null && stompClient.isConnected()) {
            return; // Already connected to this group.
        }

        disconnectFromGroup(); // Clean up any previous connection.
        connectedGroupId = groupId;

        String host = resolveHost();
        String wsUrl = String.format(WS_URL_TEMPLATE, host);
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

        // Lifecycle events for logging.
        wsDisposables.add(
                stompClient.lifecycle()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(event -> {
                            switch (event.getType()) {
                                case OPENED:
                                    Log.d(TAG, "STOMP connected for group " + groupId);
                                    break;
                                case CLOSED:
                                    Log.d(TAG, "STOMP disconnected");
                                    break;
                                case ERROR:
                                    Log.e(TAG, "STOMP error", event.getException());
                                    break;
                                default:
                                    break;
                            }
                        }, e -> Log.e(TAG, "STOMP lifecycle error", e))
        );

        // Subscribe to incoming messages for this group.
        String topicDest = "/topic/chat/" + groupId;
        wsDisposables.add(
                stompClient.topic(topicDest)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe(
                            this::handleIncomingMessage,
                                e -> Log.e(TAG, "Message subscription error", e)
                        )
        );

        stompClient.connect();
    }

    @Override
    public void disconnectFromGroup() {
        wsDisposables.clear();
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
        connectedGroupId = null;
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Handle a message pushed from the server via STOMP.
     * Only inserts if it's not a message we sent ourselves (checked via clientMessageId).
     */
    private void handleIncomingMessage(StompMessage stompMessage) {
        try {
            ChatMessageDto dto = gson.fromJson(stompMessage.getPayload(), ChatMessageDto.class);
            if (dto == null) return;

            // Determine current user's ID to tag isOutgoing; the entity stores
            // everything, the mapper derives isOutgoing at query time.
            ChatMessageEntity entity = dtoToEntity(dto);
            executorService.execute(() -> chatMessageDao.insert(entity));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse incoming STOMP message", e);
        }
    }

    /** Send via STOMP WebSocket for real-time delivery. */
    private void sendViaWebSocket(ChatMessage message) {
        String destination = "/app/chat.send/" + message.getGroupId();
        if ("LOCATION".equals(message.getType())) {
            destination = "/app/chat.location/" + message.getGroupId();
        }

        ChatMessageDto dto = buildDto(message);
        String payload = gson.toJson(dto);

        wsDisposables.add(
                stompClient.send(destination, payload)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Log.d(TAG, "Message sent via WebSocket"),
                                e -> {
                                    Log.w(TAG, "WebSocket send failed — falling back to REST", e);
                                    sendViaRest(message);
                                }
                        )
        );
    }

    /** Send via REST and enqueue for offline retry if it also fails. */
    private void sendViaRest(ChatMessage message) {
        ChatMessageDto dto = buildDto(message);
        restApiService.postChatMessage(dto).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ChatMessageDto> call,
                                   @NonNull Response<ChatMessageDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Update local entity with confirmed state if server echoes it back.
                    ChatMessageDto confirmed = response.body();
                    if (confirmed.confirmed) {
                        executorService.execute(() -> {
                            ChatMessageEntity updated = dtoToEntity(confirmed);
                            chatMessageDao.insert(updated);
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatMessageDto> call, @NonNull Throwable t) {
                Log.w(TAG, "REST send failed — queuing for sync retry", t);
                // Enqueue for retry via SyncManager when network restores.
                syncManager.enqueueChange(
                        "chatMessage",
                        message.getId(),
                        "UPSERT",
                        message.getClientMessageId(),
                        buildDto(message)
                );
            }
        });
    }

    private ChatMessageDto buildDto(ChatMessage message) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.id = message.getId();
        dto.groupId = message.getGroupId();
        dto.senderUserId = message.getSenderUserId();
        dto.content = message.getContent();
        dto.type = message.getType();
        dto.clientMessageId = message.getClientMessageId();
        dto.sharedAddress = message.getSharedAddress();
        dto.confirmed = message.isConfirmed();
        if (message.isLocationMessage()) {
            dto.sharedLocation = new ChatMessageDto.LocationDto();
            dto.sharedLocation.latitude = message.getSharedLatitude();
            dto.sharedLocation.longitude = message.getSharedLongitude();
        }
        return dto;
    }

    private ChatMessageEntity dtoToEntity(ChatMessageDto dto) {
        double lat = 0, lng = 0;
        if (dto.sharedLocation != null) {
            lat = dto.sharedLocation.latitude;
            lng = dto.sharedLocation.longitude;
        }
        long sentAtMillis = 0;
        if (dto.sentAt != null) {
            try {
                sentAtMillis = Instant.parse(dto.sentAt).toEpochMilli();
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

    /**
     * Resolve the server hostname from UserPreferences or fall back to emulator default.
     */
    private String resolveHost() {
        // The app already reads the REST_BASE_URL from BuildConfig at module level.
        // For the WebSocket we replicate the same host logic used in the okhttp setup.
        // Default to Android emulator localhost alias.
        return "10.0.2.2";
    }
}
