/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.issue.support;

import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.terminology.entity.Concept;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossQuantityUnitDisplayResolverTest {

    @Mock
    private CodeSystemQueryService codeSystemQueryService;

    @InjectMocks
    private BossQuantityUnitDisplayResolver resolver;

    @Test
    void toDisplay_mapMiss_fallsBackToSingleLookup() {
        Concept concept = new Concept();
        concept.setCode("branch");
        concept.setDisplay("支");
        // 预热 Map 非空但缺 branch（模拟缓存残缺）
        Map<String, String> warm = new HashMap<>();
        warm.put("6", "次");
        when(codeSystemQueryService.findConceptByCodeSystemNameAndCode(
                        eq(TerminologyCodeSystemNameConstants.UNIT_CODE), eq("branch")))
                .thenReturn(concept);

        assertEquals("支", resolver.toDisplay("branch", warm));
        assertEquals("支", warm.get("branch"));
    }

    @Test
    void toDisplay_mapHit_usesWarmLabel() {
        Map<String, String> warm = Map.of("6", "次");
        assertEquals("次", resolver.toDisplay("6", warm));
    }

    @Test
    void warmLabelMap_readsUnitCodeSystem() {
        Concept concept = new Concept();
        concept.setCode("6");
        concept.setDisplay("次");
        when(codeSystemQueryService.findConceptsByCodeSystemName(TerminologyCodeSystemNameConstants.UNIT_CODE))
                .thenReturn(List.of(concept));
        when(codeSystemQueryService.findConceptsByCodeSystemName(TerminologyCodeSystemNameConstants.DOSE_UNIT))
                .thenReturn(List.of());
        when(codeSystemQueryService.findConceptsByCodeSystemName("split_unit")).thenReturn(List.of());
        when(codeSystemQueryService.findConceptsByCodeSystemName("sales_unit")).thenReturn(List.of());
        when(codeSystemQueryService.findConceptsByCodeSystemName("purchase_unit")).thenReturn(List.of());
        when(codeSystemQueryService.findConceptsByCodeSystemName("stock_unit")).thenReturn(List.of());

        Map<String, String> map = resolver.warmLabelMap();
        assertEquals("次", map.get("6"));
    }
}
