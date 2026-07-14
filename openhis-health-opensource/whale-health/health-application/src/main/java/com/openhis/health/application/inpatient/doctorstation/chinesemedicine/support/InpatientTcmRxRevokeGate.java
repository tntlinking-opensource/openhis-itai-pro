/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support;

import com.openhis.health.application.shared.advice.InpatientAdviceRevokeGate;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationTcmConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BR-IP-TCM-009：住院中药签退/删除门控（执行/发药/费用）。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmRxRevokeGate {

    private final InpatientAdviceRevokeGate adviceRevokeGate;

    /**
     * 断言目标行无净执行、无已完成发药、无不可回退费用。
     *
     * @param encounterId 就诊主键
     * @param medReqIds   药品申请主键列表
     */
    public void assertNoNetExecutionOrCompletedDispenseOrLockedCharge(Long encounterId, List<Long> medReqIds) {
        adviceRevokeGate.assertMedicationWithdrawAllowed(
            encounterId,
            medReqIds,
            ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
            InpatientDoctorStationTcmConstants.MSG_REVOKE_BLOCKED);
    }
}
