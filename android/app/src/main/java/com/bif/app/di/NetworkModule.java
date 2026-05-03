package com.bif.app.di;

import com.apollographql.java.client.ApolloClient;
import com.bif.app.core.network.AuthInterceptor;
import com.bif.app.core.network.RestApiService;
import com.bif.app.network.TokenRefreshAuthenticator;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient(
            AuthInterceptor authInterceptor,
            TokenRefreshAuthenticator tokenRefreshAuthenticator) {

        // BODY logging can OOM on large/binary payloads (offline map downloads).
        // Keep debug logs useful while preventing full-body buffering.
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(
                com.bif.app.BuildConfig.DEBUG
                        ? HttpLoggingInterceptor.Level.BASIC
                        : HttpLoggingInterceptor.Level.NONE);

        return new OkHttpClient.Builder()
                .addInterceptor(authInterceptor) // Attaches JWT
                .authenticator(tokenRefreshAuthenticator)
                .addInterceptor(loggingInterceptor) // Prints the logs
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(75, TimeUnit.SECONDS)
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

    /*
     * Provides the STOMP WebSocket URL derived from the REST host.
     * Converts "http://HOST:PORT/api/" → "ws://HOST:PORT/ws/websocket"
     */
    @Provides
    @Singleton
    @Named("wsBaseUrl")
    public String provideWsBaseUrl() {
        String restBase = com.bif.app.BuildConfig.REST_BASE_URL;
        HttpUrl parsed = HttpUrl.parse(restBase);
        if (parsed != null) {
            String wsScheme = parsed.isHttps() ? "wss" : "ws";
            return wsScheme + "://" + parsed.host() + ":" + parsed.port() + "/ws/websocket";
        }

        // Fallback for malformed URL strings.
        String baseHost = restBase
                .replaceFirst("/api(?:/.*)?$", "")
                .replaceFirst("^http", "ws");
        return baseHost + "/ws/websocket";
    }
}
