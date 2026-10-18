/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.support;

import com.zxplat.domain.administration.encounter.queries.HistoricalFollowUpEligibilityQuery;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.share.constants.OutpatientRegistrationConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 复诊0元号挂号资格门禁（IF-REG-06 · BR-REG-020）。
 *
 * <p>号源下拉不做前端收窄；选定名称「复诊0元号」并在预结算写侧校验同科室 72 小时内已接诊前次就诊。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientHistoricalFollowUpRegistrationGate {

    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 校验患者是否可使用复诊0元号；非该号源直接放行。
     *
     * @param patientId         患者主键
     * @param organizationId    本次挂号科室主键
     * @param healthcareService 所选号源；为空时不校验
     * @param asOf              资格参照时刻（与预结算 occurredAt 一致）
     * @throws BusinessException 不满足复诊资格时抛出 {@code REG-BIZ-420}
     */
    public void assertEligibleForHistoricalFollowUpRegistration(
        Long patientId,
        Long organizationId,
        HealthcareService healthcareService,
        LocalDateTime asOf) {
        // Gate：非「复诊0元号」（按名称识别）不在此门禁范围
        if (!healthcareServiceQueryService.isHistoricalFollowUpService(healthcareService)) {
            return;
        }
        if (patientId == null || organizationId == null) {
            throw new BusinessException("REG-VAL-001", "请补齐挂号要素");
        }
        LocalDateTime referenceTime = asOf;
        if (referenceTime == null) {
            referenceTime = LocalDateTime.now();
        }
        HistoricalFollowUpEligibilityQuery eligibilityQuery = new HistoricalFollowUpEligibilityQuery();
        eligibilityQuery.setPatientId(patientId);
        eligibilityQuery.setOrganizationId(organizationId);
        eligibilityQuery.setAsOf(referenceTime);
        if (healthcareService.getId() != null) {
            eligibilityQuery.setExcludedServiceTypeId(healthcareService.getId());
        }
        // Pre：同科室 72h 内已接诊前次就诊（排除复诊0元号自身，防连环续挂）
        boolean qualified = encounterQueryService.existsQualifyingHistoricalFollowUpPriorVisit(eligibilityQuery);
        if (!qualified) {
            throw new BusinessException(
                OutpatientRegistrationConstants.REG_REASON_CODE_HISTORICAL_FOLLOW_UP_INELIGIBLE,
                OutpatientRegistrationConstants.REG_MESSAGE_HISTORICAL_FOLLOW_UP_INELIGIBLE);
        }
    }
}
