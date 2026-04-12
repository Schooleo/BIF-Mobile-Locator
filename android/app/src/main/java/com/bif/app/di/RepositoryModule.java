package com.bif.app.di;

import com.bif.app.data.repository.ChatRepository;
import com.bif.app.data.repository.FavoriteRepository;
import com.bif.app.data.repository.FriendRepository;
import com.bif.app.data.repository.FriendshipRepository;
import com.bif.app.data.repository.GroupRepository;
import com.bif.app.data.repository.LocationRepository;
import com.bif.app.data.repository.MapRepository;
import com.bif.app.data.repository.PlaceRepository;
import com.bif.app.data.repository.ProfileRepository;
import com.bif.app.data.repository.ReviewRepository;
import com.bif.app.data.repository.RouteRepository;
import com.bif.app.data.repository.TripRepository;
import com.bif.app.data.routing.EmbeddedBRouterEngine;
import com.bif.app.data.routing.OfflineRoutingEngine;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IFriendRepository;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.ILocationRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IProfileRepository;
import com.bif.app.domain.repository.IReviewRepository;
import com.bif.app.domain.repository.IRouteRepository;
import com.bif.app.domain.repository.ITripRepository;
import com.bif.app.domain.sync.ISyncInitializable;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class RepositoryModule {

    @Binds
    @Singleton
    public abstract IFavoriteRepository bindFavoriteRepository(FavoriteRepository repository);

    @Binds
    @Singleton
    public abstract ILocationRepository bindLocationRepository(LocationRepository repository);

    @Binds
    @Singleton
    public abstract IMapRepository bindMapRepository(MapRepository repository);

    @Binds
    @Singleton
    public abstract IPlaceRepository bindPlaceRepository(PlaceRepository repository);

    @Binds
    @Singleton
    public abstract IFriendRepository bindFriendRepository(FriendRepository repository);

    @Binds
    @Singleton
    public abstract IFriendshipRepository bindFriendshipRepository(FriendshipRepository repository);

    @Binds
    @Singleton
    public abstract IGroupRepository bindGroupRepository(GroupRepository repository);

    @Binds
    @Singleton
    public abstract IChatRepository bindChatRepository(ChatRepository repository);

    @Binds
    @Singleton
    public abstract ITripRepository bindTripRepository(TripRepository repository);

    @Binds
    @Singleton
    public abstract IProfileRepository bindProfileRepository(ProfileRepository repository);

    @Binds
    @Singleton
    public abstract IReviewRepository bindReviewRepository(ReviewRepository repository);

    @Binds
    @Singleton
    public abstract IRouteRepository bindRouteRepository(RouteRepository repository);

    @Binds
    @Singleton
    public abstract ISyncInitializable bindSyncInitializable(SyncManager syncManager);

    @Binds
    @Singleton
    public abstract OfflineRoutingEngine bindOfflineRoutingEngine(
            EmbeddedBRouterEngine routingEngine);
}
