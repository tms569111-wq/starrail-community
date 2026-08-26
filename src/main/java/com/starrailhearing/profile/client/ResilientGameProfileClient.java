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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResilientGameProfileClient implements GameProfileClient {

    private final List<ProfileProviderClient> providers;
    private final AppProperties.ProfileClient properties;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<ProfileProvider, CircuitState> circuits = new EnumMap<>(ProfileProvider.class);

    public ResilientGameProfileClient(
            List<ProfileProviderClient> providers,
            AppProperties properties,
            Clock clock
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(client -> client.provider() == ProfileProvider.ENKA ? 0 : 1))
                .toList();
        this.properties = properties.profileClient();
        this.clock = clock;
        for (ProfileProviderClient provider : providers) {
            circuits.put(provider.provider(), new CircuitState());
        }
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        Instant now = clock.instant();
        CacheEntry cached = cache.get(uid);
        if (!forceUpdate && cached != null && now.isBefore(cached.expiresAt())) {
            return cached.profile();
        }

        AppException last = null;
        AppException lookupFailure = null;
        for (ProfileProviderClient provider : providers) {
            CircuitState circuit = circuits.get(provider.provider());
            if (circuit.isOpen(now)) continue;
            try {
                PublicGameProfile profile = provider.fetch(uid, forceUpdate);
                circuit.success();
                cache.put(uid, new CacheEntry(profile, now.plus(properties.cacheTtl())));
                return profile;
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
        if (!forceUpdate && cached != null) return cached.profile();
        if (lookupFailure != null) throw lookupFailure;
        throw last == null ? new AppException(ErrorCode.UPSTREAM_UNAVAILABLE) : last;
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

    private record CacheEntry(PublicGameProfile profile, Instant expiresAt) {
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
