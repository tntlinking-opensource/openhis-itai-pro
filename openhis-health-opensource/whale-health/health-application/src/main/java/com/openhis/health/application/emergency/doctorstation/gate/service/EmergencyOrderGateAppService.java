/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.emergency.doctorstation.gate.service;

import com.openhis.health.application.emergency.doctorstation.gate.request.VerifyBeforeOrderRequest;
import com.openhis.health.application.emergency.doctorstation.gate.response.VerifyBeforeOrderResponse;
import com.openhis.health.application.emergency.doctorstation.support.EmergencyEncounterContextSupport;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.emergency.emgtriage.queries.EmgTriageQueryService;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageByEncounterQueryModel;
import com.openhis.health.domain.share.enums.EncounterStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 急诊开嘱前校验：挂号是否在有效期内、是否已指定主诊断、是否已完成预检分诊。
 */
@Service
@RequiredArgsConstructor
public class EmergencyOrderGateAppService {

    /** 急诊挂号默认有效时长（小时），机构参数未接入前使用。 */
    private static final long DEFAULT_REGISTRATION_VALID_HOURS = 72L;

    private final EmergencyEncounterContextSupport encounterContextSupport;
    private final EncounterQueryService encounterQueryService;
    private final EmgTriageQueryService emgTriageQueryService;

    public VerifyBeforeOrderResponse verifyBeforeOrder(VerifyBeforeOrderRequest request) {
        Encounter encounter = encounterContextSupport.requireEmergencyEncounter(
            request.getEncounterId(), request.getPatientId());

        boolean registrationValid = evaluateRegistration(encounter);
        boolean diagnosisComplete = evaluateDiagnosis(request.getEncounterId());
        boolean triageLinked = evaluateTriage(request.getEncounterId());

        VerifyBeforeOrderResponse resp = new VerifyBeforeOrderResponse();
        resp.setRegistrationValid(registrationValid);
        resp.setDiagnosisComplete(diagnosisComplete);
        resp.setTriageLinked(triageLinked);
        resp.setAllPassed(registrationValid && diagnosisComplete && triageLinked);
        resp.setRegistrationMessage(registrationValid
            ? "挂号有效"
            : "挂号已超过有效时间或就诊状态不允许开立医嘱");
        resp.setDiagnosisMessage(diagnosisComplete
            ? "诊断完整"
            : "请先完善诊断并指定主诊断");
        resp.setTriageMessage(triageLinked
            ? "分诊有效"
            : "请先完成预检分诊");
        return resp;
    }

    private boolean evaluateRegistration(Encounter encounter) {
        if (StringUtils.isBlank(encounter.getStatusEnum())) {
            return false;
        }
        if (EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(encounter.getStatusEnum())) {
            return false;
        }
        if (EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
            return false;
        }
        LocalDateTime start = encounter.getStartTime();
        if (start == null) {
            return true;
        }
        long hours = ChronoUnit.HOURS.between(start, LocalDateTime.now());
        return hours <= DEFAULT_REGISTRATION_VALID_HOURS;
    }

    private boolean evaluateDiagnosis(Long encounterId) {
        List<EncounterDiagnosisQueryModel> rows = encounterQueryService.listEncounterDiagnoses(encounterId);
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        return rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getPrimaryFlag()));
    }

    private boolean evaluateTriage(Long encounterId) {
        return emgTriageQueryService.getEmgTriageByEncounterId(encounterId)
            .map(this::triageReady)
            .orElse(false);
    }

    private boolean triageReady(EmgTriageByEncounterQueryModel triage) {
        if (triage.getTriageLevelEnum() == null) {
            return false;
        }
        return triage.getId() != null;
    }
}
