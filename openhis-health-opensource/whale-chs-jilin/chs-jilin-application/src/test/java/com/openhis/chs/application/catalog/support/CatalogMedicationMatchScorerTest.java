package com.openhis.chs.application.catalog.support;

import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.CatalogMatchCandidateDto;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMedicationMatchScorerTest {

    @Test
    void score_prefersNationalCodeAndAprvno() {
        YbNationalCatalogItem match = row("X001", "阿司匹林", "国药准字H123", "100mg");
        YbNationalCatalogItem other = row("X002", "阿司匹林肠溶片", "国药准字H999", "50mg");
        SuggestMedicationCatalogMappingRequest req = new SuggestMedicationCatalogMappingRequest()
            .setNationalDrugCode("X001")
            .setApprovalNumber("国药准字H123")
            .setName("阿司匹林");
        List<CatalogMatchCandidateDto> ranked = CatalogMedicationMatchScorer.scoreAndSort(
            List.of(other, match), req, 10);
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).getHilistCode()).isEqualTo("X001");
        assertThat(CatalogMedicationMatchScorer.resolveRecommendLevel(ranked)).isEqualTo("UNIQUE_SUGGEST");
    }

    private static YbNationalCatalogItem row(String code, String name, String aprvno, String spec) {
        YbNationalCatalogItem item = new YbNationalCatalogItem();
        item.setHilistCode(code);
        item.setHilistName(name);
        item.setAprvno(aprvno);
        item.setSpec(spec);
        item.setValiFlag("1");
        item.setListType("101");
        item.setSourceInfno("1312");
        return item;
    }
}
