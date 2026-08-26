package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResilientGameProfileClient implements GameProfileClient {

    private final List<ProfileProviderClient> providers;
    private final AppProperties.ProfileClient properties;
    private final Clock clock;
    private final ProfileResponseCache cache;
    private final ProfileProviderRequestPacer requestPacer;
    private final Map<String, CompletableFuture<PublicGameProfile>> inFlight = new ConcurrentHashMap<>();
    private final Map<ProfileProvider, CircuitState> circuits = new EnumMap<>(ProfileProvider.class);

    public ResilientGameProfileClient(
            List<ProfileProviderClient> providers,
            AppProperties properties,
            Clock clock,
            ProfileResponseCache cache,
            ProfileProviderRequestPacer requestPacer
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(client -> client.provider() == ProfileProvider.MIHOMO ? 0 : 1))
                .toList();
        this.properties = properties.profileClient();
        this.clock = clock;
        this.cache = cache;
        this.requestPacer = requestPacer;
        for (ProfileProviderClient provider : providers) {
            circuits.put(provider.provider(), new CircuitState());
        }
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        PublicGameProfile cached = cache.get(uid).orElse(null);
        if (cached != null) return cached;

        CompletableFuture<PublicGameProfile> started = new CompletableFuture<>();
        CompletableFuture<PublicGameProfile> existing = inFlight.putIfAbsent(uid, started);
        if (existing != null) return await(existing);

        try {
            cached = cache.get(uid).orElse(null);
            PublicGameProfile result = cached != null
                    ? cached
                    : fetchFromProvider(uid, forceUpdate);
            started.complete(result);
            return result;
        } catch (RuntimeException exception) {
            started.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(uid, started);
        }
    }

    private PublicGameProfile await(CompletableFuture<PublicGameProfile> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private PublicGameProfile fetchFromProvider(String uid, boolean forceUpdate) {
        Instant now = clock.instant();

        AppException last = null;
        AppException lookupFailure = null;
        for (ProfileProviderClient provider : providers) {
            CircuitState circuit = circuits.get(provider.provider());
            if (circuit.isOpen(now)) continue;
            requestPacer.await(provider.provider());
            try {
                PublicGameProfile profile = provider.fetch(uid, forceUpdate);
                circuit.success();
                Duration cacheTtl = effectiveCacheTtl(profile);
                PublicGameProfile cacheable = profile.withCacheTtlSeconds(cacheTtl.toSeconds());
                cache.put(uid, cacheable, cacheTtl);
                return cacheable;
            } catch (AppException exception) {
                last = exception;
                if (exception.getErrorCode() == ErrorCode.UPSTREAM_UNAVAILABLE
                        || exception.getErrorCode() == ErrorCode.PROFILE_SYNC_COOLDOWN) {
                    circuit.failure(now, properties.circuitFailureThreshold(),
                            properties.circuitOpenDuration());
                    continue;
                }
                if (exception.getErrorCode() == ErrorCode.PROFILE_LOOKUP_FAILED) {
                    lookupFailure = exception;
                    continue;
                }
                if (exception.getErrorCode() == ErrorCode.PROFILE_NOT_PUBLIC) continue;
                throw exception;
            }
        }
        if (lookupFailure != null) throw lookupFailure;
        throw last == null ? new AppException(ErrorCode.UPSTREAM_UNAVAILABLE) : last;
    }

    private Duration effectiveCacheTtl(PublicGameProfile profile) {
        Duration requested = profile.cacheTtlSeconds() > 0
                ? Duration.ofSeconds(profile.cacheTtlSeconds())
                : properties.cacheTtl();
        Duration maximum = properties.maximumCacheTtl();
        if (requested == null || requested.isNegative() || requested.isZero()) {
            requested = Duration.ofSeconds(1);
        }
        if (maximum != null && maximum.isPositive() && requested.compareTo(maximum) > 0) {
            return maximum;
        }
        return requested;
    }

    public List<ProfileProviderHealthView> providerStatuses() {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        return providers.stream().map(provider -> {
            CircuitState state = circuits.get(provider.provider());
            Instant retry = state.openUntil;
            return new ProfileProviderHealthView(
                    provider.provider(),
                    state.isOpen(now),
                    state.failures,
                    retry == null ? null : LocalDateTime.ofInstant(retry, zone)
            );
        }).toList();
    }

    private static final class CircuitState {
        private volatile int failures;
        private volatile Instant openUntil;

        synchronized boolean isOpen(Instant now) {
            if (openUntil != null && !now.isBefore(openUntil)) {
                failures = 0;
                openUntil = null;
            }
            return openUntil != null;
        }

        synchronized void success() {
            failures = 0;
            openUntil = null;
        }

        synchronized void failure(Instant now, int threshold, java.time.Duration openDuration) {
            failures++;
            if (failures >= Math.max(1, threshold)) openUntil = now.plus(openDuration);
        }
    }
}
