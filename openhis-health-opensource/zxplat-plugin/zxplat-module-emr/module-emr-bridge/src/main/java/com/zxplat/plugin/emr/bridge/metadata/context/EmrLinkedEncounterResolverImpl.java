/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.metadata.context;

import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.plugin.emr.domain.loader.EmrLinkedEncounterResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link EmrLinkedEncounterResolver}：委托 Encounter 域按 {@code amb_encounter_id} 解析关联就诊。
 */
@Component
@Primary
public class EmrLinkedEncounterResolverImpl implements EmrLinkedEncounterResolver {

    private final EncounterQueryService encounterQueryService;

    public EmrLinkedEncounterResolverImpl(EncounterQueryService encounterQueryService) {
        this.encounterQueryService = encounterQueryService;
    }

    @Override
    public List<String> resolveLinkedEncounterIds(String encounterId) {
        if (!StringUtils.hasText(encounterId)) {
            return Collections.emptyList();
        }
        Long pk;
        try {
            pk = Long.parseLong(encounterId.trim());
        } catch (NumberFormatException ex) {
            return List.of(encounterId.trim());
        }
        List<Long> ids = encounterQueryService.resolveLinkedEncounterIds(pk);
        if (ids == null || ids.isEmpty()) {
            return List.of(encounterId.trim());
        }
        List<String> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id != null) {
                result.add(String.valueOf(id));
            }
        }
        return result;
    }
}
