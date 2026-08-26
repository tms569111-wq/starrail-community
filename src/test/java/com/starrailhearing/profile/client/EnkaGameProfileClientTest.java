package com.starrailhearing.profile.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EnkaGameProfileClientTest {

    @Test
    void 응답의_TTL을_프로필에_전달한다() throws Exception {
        var root = new ObjectMapper().readTree("""
                {
                  "ttl": 720,
                  "player_info": {
                    "uid": "826149992",
                    "nickname": "아르카",
                    "signature": "소개문",
                    "is_display": true,
                    "avatar_list": [
                      {"id": "1001", "name": "테스트", "eidolon": 2}
                    ]
                  }
                }
                """);
        EnkaGameProfileClient client = new EnkaGameProfileClient(RestClient.create());

        PublicGameProfile profile = client.parse(root, "826149992");

        assertThat(profile.cacheTtlSeconds()).isEqualTo(720);
        assertThat(profile.characters()).hasSize(1);
    }
}
