package com.starrailhearing.profile.client;

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
public class MihomoGameProfileClient implements ProfileProviderClient {

    private static final Logger log = LoggerFactory.getLogger(MihomoGameProfileClient.class);

    private final RestClient restClient;

    public MihomoGameProfileClient(@Qualifier("mihomoRestClient") RestClient mihomoRestClient) {
        this.restClient = mihomoRestClient;
    }

    @Override
    public com.starrailhearing.character.domain.ProfileProvider provider() {
        return com.starrailhearing.character.domain.ProfileProvider.MIHOMO;
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        try {
            JsonNode root = restClient.get()
                    .uri(builder -> builder
                            .path("/sr_info_parsed/{uid}")
                            .queryParam("language", "kr")
                            .queryParam("version", "v2")
                            .queryParam("is_force_update", forceUpdate)
                            .build(uid))
                    .retrieve()
                    .body(JsonNode.class);

            return parse(root, uid);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            log.warn("MiHoMo profile request failed with HTTP {}", status.value());
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
        if (root == null || !root.hasNonNull("player")) {
            log.warn("MiHoMo profile response did not contain player data");
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        }

        JsonNode player = root.path("player");
        List<PublicCharacter> characters = new ArrayList<>();
        root.path("characters").forEach(node -> characters.add(new PublicCharacter(
                node.path("id").asString(),
                node.path("name").asString("이름 없음"),
                Math.max(0, Math.min(6, node.path("rank").asInt(0)))
        )));

        return new PublicGameProfile(
                provider(),
                player.path("uid").asString(requestedUid),
                player.path("nickname").asString("이름 없음"),
                player.path("signature").asString(""),
                player.path("is_display").asBoolean(false),
                List.copyOf(characters)
        );
    }
}
