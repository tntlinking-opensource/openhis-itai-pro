/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.context;

import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.wi.domain.loader.WorkInjuryPatientNameLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/** HIS 就诊上下文取患者姓名。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class ChsWorkInjuryPatientNameLookup implements WorkInjuryPatientNameLookup {

    private final EncounterContextQueryService encounterContextQueryService;

    @Override
    public String findNameByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        try {
            OutpatientEncounterContextQueryModel encounter =
                    encounterContextQueryService.mustGetOutpatientContextByEncounterId(encounterId);
            if (encounter != null && isNotEmpty(encounter.getPatientName())) {
                return encounter.getPatientName().trim();
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}
