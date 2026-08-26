package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ResilientGameProfileClient.class);

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
        log.info("Profile provider order={}", this.providers.stream().map(ProfileProviderClient::provider).toList());
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        Instant now = clock.instant();
        String maskedUid = maskUid(uid);
        CacheEntry cached = cache.get(uid);
        if (!forceUpdate && cached != null && now.isBefore(cached.expiresAt())) {
            log.info("PROFILE shared JVM cache hit uid={} provider={}", maskedUid, cached.profile().provider());
            return cached.profile();
        }

        AppException last = null;
        AppException lookupFailure = null;
        for (ProfileProviderClient provider : providers) {
            CircuitState circuit = circuits.get(provider.provider());
            if (circuit.isOpen(now)) {
                log.warn(
                        "PROFILE provider skipped because circuit is open provider={} uid={} retryAt={}",
                        provider.provider(),
                        maskedUid,
                        circuit.openUntil
                );
                continue;
            }

            log.info("PROFILE provider attempt provider={} uid={} forceUpdate={}", provider.provider(), maskedUid, forceUpdate);
            try {
                PublicGameProfile profile = provider.fetch(uid, forceUpdate);
                circuit.success();
                cache.put(uid, new CacheEntry(profile, now.plus(properties.cacheTtl())));
                log.info(
                        "PROFILE provider success provider={} uid={} characters={}",
                        provider.provider(),
                        maskedUid,
                        profile.characters().size()
                );
                return profile;
            } catch (AppException exception) {
                last = exception;
                ErrorCode errorCode = exception.getErrorCode();
                log.warn(
                        "PROFILE provider failure provider={} uid={} errorCode={}",
                        provider.provider(),
                        maskedUid,
                        errorCode
                );

                if (errorCode == ErrorCode.UPSTREAM_UNAVAILABLE
                        || errorCode == ErrorCode.PROFILE_UPSTREAM_THROTTLED) {
                    circuit.failure(now, properties.circuitFailureThreshold(),
                            properties.circuitOpenDuration());
                    log.warn(
                            "PROFILE fallback after provider failure provider={} uid={} failures={} circuitOpenUntil={}",
                            provider.provider(),
                            maskedUid,
                            circuit.failures,
                            circuit.openUntil
                    );
                    continue;
                }
                if (errorCode == ErrorCode.PROFILE_LOOKUP_FAILED) {
                    lookupFailure = exception;
                    log.info("PROFILE lookup failed on provider={}, trying next provider uid={}", provider.provider(), maskedUid);
                    continue;
                }
                if (errorCode == ErrorCode.PROFILE_NOT_PUBLIC) {
                    log.info("PROFILE display data missing on provider={}, trying next provider uid={}", provider.provider(), maskedUid);
                    continue;
                }

                log.warn("PROFILE request stopped without fallback uid={} errorCode={}", maskedUid, errorCode);
                throw exception;
            }
        }

        if (!forceUpdate && cached != null) {
            log.warn("PROFILE all providers failed; serving stale JVM cache uid={} provider={}", maskedUid, cached.profile().provider());
            return cached.profile();
        }
        if (lookupFailure != null) {
            log.warn("PROFILE all providers exhausted with lookup failure uid={}", maskedUid);
            throw lookupFailure;
        }
        ErrorCode finalCode = last == null ? ErrorCode.UPSTREAM_UNAVAILABLE : last.getErrorCode();
        log.error("PROFILE all providers failed uid={} finalErrorCode={}", maskedUid, finalCode);
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

    private String maskUid(String uid) {
        if (uid == null || uid.length() < 4) return "****";
        return "*****" + uid.substring(uid.length() - 4);
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
