package com.openhis.whale.booking.application.policy.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReleasePolicyJsonSupportTest {

    @Test
    void parseChannelQuotas_emptyReturnsEmptyMap() {
        assertThat(ReleasePolicyJsonSupport.parseChannelQuotas(null)).isEmpty();
        assertThat(ReleasePolicyJsonSupport.parseChannelQuotas("  ")).isEmpty();
    }

    @Test
    void parseAndWrite_roundTrip() {
        Map<String, Integer> quotas = new LinkedHashMap<>();
        quotas.put("WINDOW", 60);
        quotas.put("MINIAPP", 40);

        String json = ReleasePolicyJsonSupport.writeChannelQuotas(quotas);
        Map<String, Integer> parsed = ReleasePolicyJsonSupport.parseChannelQuotas(json);

        assertThat(parsed).containsExactlyEntriesOf(quotas);
    }

    @Test
    void writeChannelQuotas_emptyReturnsNull() {
        assertThat(ReleasePolicyJsonSupport.writeChannelQuotas(null)).isNull();
        assertThat(ReleasePolicyJsonSupport.writeChannelQuotas(Map.of())).isNull();
    }
}
