package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class EnkaGameProfileClient implements ProfileProviderClient {

    private static final Logger log = LoggerFactory.getLogger(EnkaGameProfileClient.class);

    private final RestClient restClient;

    public EnkaGameProfileClient(@Qualifier("enkaRestClient") RestClient enkaRestClient) {
        this.restClient = enkaRestClient;
    }

    @Override
    public ProfileProvider provider() {
        return ProfileProvider.ENKA;
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        try {
            JsonNode root = restClient.get()
                    .uri("/api/hsr/uid/{uid}", uid)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(root, uid);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            log.warn("Enka profile request failed with HTTP {}", status.value());
            if (status.value() == 429) {
                throw new AppException(ErrorCode.PROFILE_SYNC_COOLDOWN, exception);
            }
            if (status.value() == 400 || status.value() == 404 || status.value() == 422) {
                throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED, exception);
            }
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        }
    }

    private PublicGameProfile parse(JsonNode root, String requestedUid) {
        if (root == null) {
            log.warn("Enka profile response was empty");
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        }
        JsonNode player = root.hasNonNull("detailInfo")
                ? root.path("detailInfo")
                : root.path("player_info");
        if (player.isMissingNode() || player.isEmpty()) {
            log.warn("Enka profile response did not contain player data");
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        }

        JsonNode avatars = firstArray(
                player.path("avatarDetailList"),
                player.path("avatar_list"),
                root.path("avatarDetailList"),
                root.path("characters")
        );
        List<PublicCharacter> characters = new ArrayList<>();
        avatars.forEach(node -> {
            String externalId = firstText(node, "avatarId", "avatar_id", "id");
            if (!externalId.isBlank()) {
                characters.add(new PublicCharacter(
                        externalId,
                        firstText(node, "name", "avatarName"),
                        Math.max(0, Math.min(6, firstInt(node, "rank", "eidolon")))
                ));
            }
        });

        return new PublicGameProfile(
                provider(),
                firstTextOr(player, requestedUid, "uid"),
                firstTextOr(player, "이름 없음", "nickname"),
                firstTextOr(player, "", "signature"),
                player.path("isDisplayAvatar").asBoolean(
                        player.path("is_display").asBoolean(!characters.isEmpty())
                ),
                List.copyOf(characters)
        );
    }

    private JsonNode firstArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate.isArray()) return candidate;
        }
        return candidates[0];
    }

    private String firstText(JsonNode node, String... fields) {
        return firstTextOr(node, "", fields);
    }

    private String firstTextOr(JsonNode node, String fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asString().isBlank()) {
                return value.asString();
            }
        }
        return fallback;
    }

    private int firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) return node.path(field).asInt(0);
        }
        return 0;
    }
}
