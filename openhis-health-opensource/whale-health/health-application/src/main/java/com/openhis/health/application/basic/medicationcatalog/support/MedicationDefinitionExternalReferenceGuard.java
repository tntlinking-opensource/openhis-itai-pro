/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 药品目录废弃前跨聚合引用守卫（B.6.1.3.1）。
 */
@Component
@RequiredArgsConstructor
public class MedicationDefinitionExternalReferenceGuard {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 断言药品未被医嘱或发放引用。
     *
     * @param medicationId 药品定义主键
     */
    public void assertNoExternalReference(Long medicationId) {
        if (medicationId == null) {
            return;
        }
        if (medicationRequestQueryService.existsByMedicationId(medicationId)
            || medicationDispenseQueryService.existsByMedicationId(medicationId)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品已被业务引用，禁止废弃");
        }
    }

    /**
     * 批量断言药品未被业务引用。
     *
     * @param medicationIds 药品定义主键集合
     */
    public void assertNoExternalReference(Collection<Long> medicationIds) {
        if (medicationIds == null || medicationIds.isEmpty()) {
            return;
        }
        for (Long medicationId : medicationIds) {
            this.assertNoExternalReference(medicationId);
        }
    }
}
