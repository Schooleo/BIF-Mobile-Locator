package com.bif.server.features.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final long RESOLVE_RATE_LIMIT_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final int RESOLVE_RATE_LIMIT_MAX_REQUESTS = 30;
    private static final String TOMCAT_REQUEST_FORWARDED_ATTRIBUTE = "org.apache.tomcat.request.forwarded";
    private static final String TOMCAT_REMOTE_ADDR_ATTRIBUTE = "org.apache.tomcat.remoteAddr";
    private static final String TOMCAT_ACCESS_LOG_REMOTE_ADDR_ATTRIBUTE = "org.apache.catalina.AccessLog.RemoteAddr";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/api/auth/forgot-password/**",
                "/ws",
                "/ws/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/error",
                "/graphiql",
                "/graphql"
            ).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/places/*/reviews").permitAll()
            .anyRequest().authenticated()
            )
                .addFilterBefore(placeResolveRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    private Filter placeResolveRateLimitFilter() {
        return new OncePerRequestFilter() {
            private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
            private final AtomicLong lastCleanupAt = new AtomicLong(System.currentTimeMillis());

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if (!HttpMethod.POST.matches(request.getMethod())
                        || !"/api/places/resolve".equals(request.getServletPath())) {
                    filterChain.doFilter(request, response);
                    return;
                }

                long now = System.currentTimeMillis();
                evictStaleCountersIfNeeded(now);

                String clientKey = resolveClientKey(request);

                WindowCounter counter = counters.compute(clientKey, (key, existing) -> {
                    if (existing == null || now - existing.windowStartMillis >= RESOLVE_RATE_LIMIT_WINDOW_MILLIS) {
                        return new WindowCounter(now);
                    }
                    return existing;
                });

                int requestCount = counter.requestCount.incrementAndGet();
                if (requestCount > RESOLVE_RATE_LIMIT_MAX_REQUESTS) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Too many resolve requests\"}");
                    return;
                }

                filterChain.doFilter(request, response);
            }

            private void evictStaleCountersIfNeeded(long now) {
                long lastCleanup = lastCleanupAt.get();
                if (now - lastCleanup < RESOLVE_RATE_LIMIT_WINDOW_MILLIS) {
                    return;
                }

                if (!lastCleanupAt.compareAndSet(lastCleanup, now)) {
                    return;
                }

                counters.entrySet().removeIf(entry ->
                        now - entry.getValue().windowStartMillis >= RESOLVE_RATE_LIMIT_WINDOW_MILLIS);
            }

            private String resolveClientKey(HttpServletRequest request) {
                String remoteAddr = request.getRemoteAddr();
                if (remoteAddr == null || remoteAddr.isBlank()) {
                    remoteAddr = "unknown";
                }

                Object tomcatRemoteAddr = request.getAttribute(TOMCAT_REMOTE_ADDR_ATTRIBUTE);
                if (tomcatRemoteAddr instanceof String addr && !addr.isBlank()) {
                    return addr;
                }

                Object tomcatAccessLogRemoteAddr = request.getAttribute(TOMCAT_ACCESS_LOG_REMOTE_ADDR_ATTRIBUTE);
                if (tomcatAccessLogRemoteAddr instanceof String addr && !addr.isBlank()) {
                    return addr;
                }

                Object forwardedAttribute = request.getAttribute(TOMCAT_REQUEST_FORWARDED_ATTRIBUTE);
                if (Boolean.TRUE.equals(forwardedAttribute)) {
                    return request.getRemoteAddr();
                }

                return remoteAddr;
            }


        };
    }

    private static final class WindowCounter {
        private final long windowStartMillis;
        private final AtomicInteger requestCount = new AtomicInteger(0);

        private WindowCounter(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
