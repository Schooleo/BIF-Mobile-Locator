package com.bif.app.di;

import com.apollographql.java.client.ApolloClient;
import com.bif.app.core.network.AuthInterceptor;
import com.bif.app.core.network.RestApiService;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient(AuthInterceptor authInterceptor) {

        // Prints network traffic to Logcat
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .addInterceptor(authInterceptor) // Attaches JWT
                .addInterceptor(loggingInterceptor) // Prints the logs
                .build();
    }

    @Provides
    @Singleton
    public ApolloClient provideApolloClient(OkHttpClient okHttpClient) {
        return new ApolloClient.Builder()
                .serverUrl(com.bif.app.BuildConfig.GRAPHQL_URL)
                .okHttpClient(okHttpClient)
                .build();
    }

    @Provides
    @Singleton
    public Retrofit provideRetrofit(OkHttpClient okHttpClient) {
        return new Retrofit.Builder()
                .baseUrl(com.bif.app.BuildConfig.REST_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @Provides
    @Singleton
    public RestApiService provideRestApiService(Retrofit retrofit) {
        return retrofit.create(RestApiService.class);
    }
}

