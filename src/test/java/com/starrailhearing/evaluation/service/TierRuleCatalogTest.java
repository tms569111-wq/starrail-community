package com.starrailhearing.evaluation.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TierRuleCatalogTest {

    @Test
    void 버전_4_5_규칙을_읽는다() {
        TierVersionRules rules = new TierRuleCatalog(new ObjectMapper()).load("4.5");

        assertThat(rules.version()).isEqualTo("4.5");
        assertThat(rules.minimumSample()).isEqualTo(5);
        assertThat(rules.tiers()).hasSize(5);
        assertThat(rules.characterCopy()).isEmpty();
    }
}
