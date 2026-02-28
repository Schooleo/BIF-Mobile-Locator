package com.bif.app.di;

import com.bif.app.data.repository.FavoriteRepository;
import com.bif.app.data.repository.LocationRepository;
import com.bif.app.data.repository.MapRepository;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.ILocationRepository;
import com.bif.app.domain.repository.IMapRepository;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

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
}
