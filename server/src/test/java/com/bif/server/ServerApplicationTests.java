package com.bif.server;

import com.bif.server.features.auth.repositories.RefreshTokenRepository;
import com.bif.server.features.auth.repositories.RevokedAccessTokenRepository;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.friendship.repositories.FriendshipRepository;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.repositories.RatingRepository;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.bif.server.features.user.repositories.UserRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

    @TestConfiguration
    static class MongoTestConfig {
        @Bean
        public MongoClient mongoClient() {
            return Mockito.mock(MongoClient.class);
        }

        @Bean
        public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
            return new SimpleMongoClientDatabaseFactory(mongoClient, "test");
        }

        @Bean
        public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
            return new MongoTemplate(mongoDatabaseFactory);
        }

        @Bean(name = "mongoMappingContext")
        public MongoMappingContext mongoMappingContext() {
            return new MongoMappingContext();
        }
    }

    @MockitoBean
    private PlaceSearchIndexSyncService placeSearchIndexSyncService;

    // Mock all repositories to satisfy service dependencies without triggering auto-config
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlaceRepository placeRepository;

    @MockitoBean
    private PlaceMappingRepository placeMappingRepository;

    @MockitoBean
    private RatingRepository ratingRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private RevokedAccessTokenRepository revokedAccessTokenRepository;

    @MockitoBean
    private ChatMessageRepository chatMessageRepository;

    @MockitoBean
    private FavoriteRepository favoriteRepository;

    @MockitoBean
    private FriendshipRepository friendshipRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private SyncChangeRepository syncChangeRepository;

    @MockitoBean
    private TripPlanRepository tripPlanRepository;

    @Test
    void contextLoads() {
    }

}

