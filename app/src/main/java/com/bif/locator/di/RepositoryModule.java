package com.bif.locator.di;

import com.bif.locator.data.repository.FavoriteRepository;
import com.bif.locator.data.repository.LocationRepository;
import com.bif.locator.data.repository.MapRepository;
import com.bif.locator.domain.repository.IFavoriteRepository;
import com.bif.locator.domain.repository.ILocationRepository;
import com.bif.locator.domain.repository.IMapRepository;

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
